package me.jbakita.pebbledatalogging;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.ArrayList;

/**
 * MainActivity class
 * Implements a PebbleDataLogReceiver to process received log data, 
 * as well as a finished session.
 */
public class MainActivity extends Activity {

    // WATCHAPP_UUID *MUST* match the UUID used in the watchapp
    private static final UUID WATCHAPP_UUID = UUID.fromString("631b528e-c553-486c-b5ac-da08f63f01de");
    // 'features' needs to be kept in sync with the watchapp menu items and ordering
    private String[] features = {
        "DOMINANT_WRIST",
        "NON_DOMINATE_WRIST",
        "WAIST",
        "RIGHT_ANKLE",
        "LEFT_ANKLE",
        "UPPER_DOMINATE_ARM",
        "UPPER_NON_DOMINATE_ARM",
        "RIGHT_THIGH",
        "LEFT_THIGH",
        "CHEST",
        "NECK"
    };
    private String[] activityStrings = {"Pushups", "Situps", "Jumping Jacks", "Stretching", "Running", "Walking"};

    private PebbleDataLogReceiver dataloggingReceiver = null;
    private final ArrayList<Sensor> sensors = new ArrayList<Sensor>();
    private final ArrayList<MotionActivity> activities = new ArrayList<MotionActivity>();
    private ArrayAdapter<Sensor> adapter;
    private Button startStopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get listview
        ListView sensorsView = (ListView)findViewById(R.id.listView);
        // Setup progress bar
        ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
        sensorsView.setEmptyView(progressBar);

        // Setup data adapter
        adapter = new ArrayAdapter<Sensor>(this, android.R.layout.simple_list_item_2, android.R.id.text1, sensors) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = (TextView) view.findViewById(android.R.id.text1);
                TextView text2 = (TextView) view.findViewById(android.R.id.text2);

                text1.setText(sensors.get(position).getTitle());
                text2.setText(sensors.get(position).getInfo());
                return view;
            }
        };

        // Add listview display adapter
        sensorsView.setAdapter(adapter);

        // Setup start/stop button
        startStopButton = (Button)findViewById(R.id.startstopbutton);
        startStopButton.setOnClickListener(new startStopListener());
        startStopButton.setText("Start");

        // Setup save button
        Button saveButton = (Button)findViewById(R.id.savebutton);
        saveButton.setOnClickListener(new saveListener());
        saveButton.setText("Save");

        //Display instructions
        displayDialog("Instructions", "(1) Press the Start button on the Anroid app. \n" +
                "(2) Open the accelerometer app on the Pebble. \n" +
                "(3) In the Pebble app, select the part of the body where the Pebble is attched to. " +
                "Then press any button except the back button to start. \n" +
                "(4) When you are finished recording, press the Stop button on the Android app. \n" +
                "(5) Press any button except the back button on the Pebble. \n" +
                "(6) When the data shows up, press the Save button to save the data on your phone " +
                "(or press the Start button again to collect more data. \n" +
                "(7) To locate the data, open your phone's file manager app, open the Downloads folder, then open the PebbleDataLogging folder.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Define data reception behavior
        dataloggingReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID) {
            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                // Check this is a valid data log
                if (tag.intValue() <= features.length) {
                    // To distinguish between timestamps and readings,
                    // the first bit is 0 for readings and 1 for timestamp
                    /* To conserve data, but maximize accuracy, the first 'reading'
                     * for each data log ID is the beginning timestamp.
                     */
                    if (sensors.indexOf(new Sensor(features[tag.intValue()], 0)) == -1) {
                        // First reading must be a timestamp
                        if (data[0] >> 31 != 0xffffffff) {
                            displayDialog("Error", "It seems like a data buffer is out of sync. Data will be corrupted. Please flush buffers and try again.");
                            return;
                        }
                        // Erase the tag bit with sign extension to prep for decoding
                        data[0] = (byte)(data[0] << 25 >> 25);
                        // Decode and save
                        Sensor sensor = new Sensor(features[tag.intValue()], decodeBytes(data));
                        sensors.add(sensor);
                    }
                    else if (data[0] >> 31 == 0xffffffff) {
                        // We have a timestamp
                        // Erase the tag bit with sign extension to prep for decoding
                        data[0] = (byte)(data[0] << 25 >> 25);
                        // Decode and add timestamp
                        long syncTimestamp = decodeBytes(data);
                        Sensor sensor = sensors.get(sensors.indexOf(new Sensor(features[tag.intValue()], 0)));
                        sensor.addTimestamp(syncTimestamp);
                        // Refresh UI
                        adapter.notifyDataSetChanged();
                    }
                    else {
                        // We have a reading
                        // Erase the tag bit with sign extension to prep for decoding
                        data[0] = (byte)(data[0] << 25 >> 25);
                        // Decode and add reading
                        int x = (int)decodeBytes(new byte[]{data[0], data[1]});
                        int y = (int)decodeBytes(new byte[]{data[2], data[3]});
                        int z = (int)decodeBytes(new byte[]{data[4], data[5]});
                        Sensor sensor = sensors.get(sensors.indexOf(new Sensor(features[tag.intValue()], 0)));
                        sensor.addReading(new Reading(x, y, z));
                    }
                }
                else {
                    throw new IllegalArgumentException(tag.intValue() + " is not a valid data log ID.");
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);
            }

        };
        // Register DataLogging Receiver
        PebbleKit.registerDataLogReceiver(this, dataloggingReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Always unregister callbacks
        if(dataloggingReceiver != null) {
            unregisterReceiver(dataloggingReceiver);
        }
    }

    /* Decode an array of bytes to an integer
     * @param bytes An array of bytes, big endian
     */
    private long decodeBytes(byte[] bytes) {
        /* Note on Java and Bitwise Operators
         *  Java bitwise operators only work on ints and longs,
         *  Bytes will undergo promotion with sign extension first.
         *  So, we have to undo the sign extension on the lower order
         *  bits here.
         */
        long ans = bytes[0];
        for (int i = 1; i < bytes.length; i++) {
            ans <<= 8;
            ans |= bytes[i] & 0x000000FF;
        }
        return ans;
    }

    private void getMotionActivity(final MotionActivity act) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("What activity did you complete?")
                .setItems(activityStrings, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        act.name = activityStrings[which];
                    }
                });
        builder.create().show();

    }

    private void finishAndSaveReading() {

        if (!isExternalStorageWritable()) {
            displayDialog("Error", "External Storage not found.");
        } else {
            for (int i = 0; i < activities.size(); i++) {
                for (int j = 0; j < sensors.size(); j++) {
                    ArrayList<Reading> readings = sensors.get(j).getReadings();
                    try {
                        long lastReading = 0;
                        long firstReading = 0;
                        // Get/create our application's save folder
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/PebbleDataLogging/");
                        dir.mkdir();
                        // Create the file in the <activity name>-<sensor name>-<system time>.csv format
                        File file = new File(dir, activities.get(i).name + " " + sensors.get(j).getTitle() + " " + DateFormat.getDateTimeInstance().format(new Date()) + ".csv");
                        FileOutputStream outputStream = new FileOutputStream(file);
                        // Write the colunm headers
                        outputStream.write("X(mG),Y(mG),Z(mG),Time(ms)\n".getBytes());
                        // Write all the readings which correlate to our current activity
                        for (int k = 0; k < readings.size(); k++) {
                            if (readings.get(k).timestamp >= activities.get(i).startTime && readings.get(k).timestamp < activities.get(i).endTime) {
                                if (firstReading == 0)
                                    firstReading = readings.get(k).timestamp;
                                outputStream.write(String.format(Locale.US, "%+5d,%+5d,%+5d,%14d\n", readings.get(k).x, readings.get(k).y, readings.get(k).z, readings.get(k).timestamp).getBytes());
                                lastReading = readings.get(k).timestamp;
                            }
                        }
                        // Do some validation on the dataset
                        if (lastReading + 1000 < activities.get(i).endTime) {
                            displayDialog("Warning!", "It seems like the dataset you just saved stopped sooner than expected. Make sure that you have all your sensor data.");
                        }
                        else if (firstReading - 1000 > activities.get(i).startTime) {
                            displayDialog("Warning!", "It seems like the dataset you just saved started later than expected. Make sure that you have all your sensor data.");
                        }
                        outputStream.close();
                        // Workaround for Android bug #38282
                        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                    } catch (Exception e) {e.printStackTrace();}
                }
            }
        }
        Log.w("MainActivity", sensors.toString());
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private AlertDialog displayDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setTitle(title)
                .setNeutralButton("Okay", null);
        AlertDialog dia = builder.create();
        dia.show();
        return dia;
    }
    private class Sensor {
        private String name;
        private long lastTimestamp = 0;
        private ArrayList<Reading> readings = new ArrayList<Reading>();
        private ArrayList<Reading> readingBuffer = new ArrayList<Reading>();
        /* Initialize the sensor with a name. Setting the sample rate, and start time are required before adding readings.
         * @param name The sensor name, used only for display
         * @param timestamp ms since POSIX epoch at which this sensor started
         */
        public Sensor(String name, long timestamp) {
            this.name = name;
            this.lastTimestamp = timestamp;
        }
        /* Add a sequential accelerometer reading. The time is automatically calculated.
         * @param r the reading to add
         * @throws UnsupportedOperationException if the sample rate and beginning timestamp
         *                                       have yet to be set.
         */
        public void addReading(Reading r) {
            // Check that everything is setup
            if (lastTimestamp == 0)
                throw new UnsupportedOperationException("No starting timestamp set on sensor");
            // Log the reading (we add timestamps later)
            readingBuffer.add(r);
        }
        public String getTitle() {
            return name;
        }
        public String getInfo() {
            return readings.size() + " readings taken over " + getDuration() / 60000 + "m " + getDuration() % 60000 / 1000 + "s " + getDuration() % 60000 % 1000 + "ms";
        }
        /* Get the duration of time that this sensor has been monitoring in ms
         */
        public long getDuration() {
            if (readings.isEmpty())
                return 0;
            return readings.get(readings.size() - 1).timestamp - readings.get(0).timestamp;
        }
        public void addTimestamp(long t) {
            if (readingBuffer.isEmpty())
                throw new UnsupportedOperationException("No readings in buffer. Cannot add timestamp.");
            long dur = t - lastTimestamp;
            double readingSize = dur / (double)(readingBuffer.size() + 1);
            Reading reading0 = readingBuffer.remove(0);
            reading0.setTimestamp(lastTimestamp);
            readings.add(reading0);
            for (Reading r : readingBuffer) {
                r.setTimestamp(lastTimestamp += readingSize);
                readings.add(r);
            }
            lastTimestamp = t;
            readingBuffer.clear();
        }
        public ArrayList<Reading> getReadings() {
            return readings;
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof String)
                return name.equals(obj);
            else if (obj instanceof Sensor)
                if (name.equals(((Sensor) obj).getTitle()))
                    return true;
            return false;
        }
        @Override
        public String toString() {
            return readings.toString();
        }
    }
    private class Reading {
        public int x;
        public int y;
        public int z;
        public long timestamp = 0;
        public Reading(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        /* Set reading timestamp
         * @param posixMSTime time in ms since UNIX epoch
         */
        public void setTimestamp(long posixMSTime) {
            timestamp = posixMSTime;
        }
        @Override
        public String toString() {
            return String.format("\nX: %+5d, Y: %+5d, Z: %+5d, Time: %s", x, y, z, DateFormat.getDateTimeInstance().format(new Date(timestamp)));
        }
    }
    private class MotionActivity {
        public long startTime = -1;
        public long endTime = -1;
        public String name = "";
        public MotionActivity(long startTime) {
            this.startTime = startTime;
        }
        public boolean isFinished() {
            return startTime != -1 && endTime != -1;
        }

    }
    private class startStopListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (activities.isEmpty() || activities.get(activities.size() - 1).isFinished()) {
                // Start recording
                startStopButton.setText("Stop");
                activities.add(new MotionActivity(System.currentTimeMillis() + TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings()));
            }
            else {
                // End recording
                startStopButton.setText("Start");
                activities.get(activities.size() - 1).endTime = System.currentTimeMillis() + TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings();
                getMotionActivity(activities.get(activities.size() - 1));
            }
        }
    }
    private class saveListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            finishAndSaveReading();
        }
    }
}
