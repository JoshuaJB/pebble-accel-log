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
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
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
        "NON_DOMINANT_WRIST",
        "WAIST",
        "RIGHT_ANKLE",
        "LEFT_ANKLE",
        "UPPER_DOMINANT_ARM",
        "UPPER_NON_DOMINANT_ARM",
        "RIGHT_THIGH",
        "LEFT_THIGH",
        "CHEST",
        "NECK"
    };
    private String[] activityStrings = {"Pushups", "Situps", "Jumping Jacks", "Staying Still", "Jogging", "Walking"};

    private PebbleDataLogReceiver dataloggingReceiver = null;
    private final ArrayList<Sensor> sensors = new ArrayList<>();
    private final ArrayList<MotionActivity> activities = new ArrayList<>();
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

        // Setup save buttons
        Button saveButton = (Button)findViewById(R.id.savebutton);
        saveButton.setOnClickListener(new saveListener());
        saveButton.setText("Save");

        Button saveAllButton = (Button)findViewById(R.id.saveallbutton);
        saveAllButton.setOnClickListener(new saveAllListener());
        saveAllButton.setText("Save All");

        // Display instructions
        displayDialog("Instructions",
                "(1) Open the accelerometer app on the Pebble. \n" +
                "(2) In the Pebble app, select the part of the body where the Pebble is attched to. " +
                "Then press any button except the back button to start logging. \n" +
                "(3) Press the Start button on the Android app. \n" +
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
                        sensor.addReading(new AccelerometerReading(x, y, z));
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
        if (dataloggingReceiver != null) {
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

    /**
     * Save the contents of each sensor for each activity
     * @param saveAll If true, ignore activities and dump unbounded sensor data
     */
    private void finishAndSaveReading(boolean saveAll) {
        Log.d("MainActivity", sensors.toString());
        if (!isExternalStorageWritable()) {
            displayDialog("Error", "External storage is not writable. Unable to save readings.");
            return;
        }
        try {
            if (saveAll) {
                for (Sensor sensor : sensors) {
                    saveSensorReadings("All Readings", sensor, sensor.getStartTime(), sensor.getStopTime());
                }
            }
            else {
                for (MotionActivity activity : activities) {
                    for (Sensor sensor : sensors) {
                        saveSensorReadings(activity.name, sensor, activity.getStartTime(), activity.getStopTime());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            displayDialog("Error", "Unable to completely save readings. See ADB log for details.");
            return;
        }
        displayDialog("Success", "Data successfully saved.");
    }

    private void saveSensorReadings(String name, Sensor sensor, long startTime, long stopTime) throws IOException {
        ArrayList<AccelerometerReading> readings = sensor.getReadings();
        long lastReading = 0;
        long firstReading = 0;
        // Get/create our application's save folder
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/PebbleDataLogging/");
        // Make sure that the path is a directory if it exists, otherwise create it
        if (dir.exists() && !dir.isDirectory()) {
            displayDialog("Error", "Unable to save readings. Save path exists, but is not a directory");
            return;
        }
        else if (!dir.exists() && !dir.mkdir()) {
            displayDialog("Error", "Unable to create directory in which to save readings. Maybe out of space?");
            return;
        }
        // Create the file in the <activity name>-<sensor name>-<system time>.csv format
        File file = new File(dir, name + " " + sensor.getTitle() + " " + DateFormat.getDateTimeInstance().format(new Date()) + ".csv");
        FileOutputStream outputStream = new FileOutputStream(file);

        // Write the column headers
        outputStream.write((AccelerometerReading.CSV_HEADER + "\n").getBytes());
        // Write all the readings which correlate to our current activity
        for (int k = 0; k < readings.size(); k++) {
            if (readings.get(k).getTimestamp() >= startTime && readings.get(k).getTimestamp() < stopTime) {
                if (firstReading == 0)
                    firstReading = readings.get(k).getTimestamp();
                outputStream.write((readings.get(k).toCSV() + "\n").getBytes());
                lastReading = readings.get(k).getTimestamp();
            }
        }
        // Do some validation on the dataset
        if (lastReading + 1000 < stopTime) {
            displayDialog("Warning!", "It seems like the dataset you just saved stopped sooner than expected. Make sure that you have all your sensor data.");
        }
        if (firstReading - 1000 > startTime) {
            displayDialog("Warning!", "It seems like the dataset you just saved started later than expected. Make sure that you have all your sensor data.");
        }
        outputStream.close();
        // Workaround for Android bug #38282
        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
    }

    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private AlertDialog displayDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setTitle(title)
                .setNeutralButton("Okay", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }
    private class Sensor {
        private String name;
        private long lastTimestamp = 0;
        private ArrayList<AccelerometerReading> readings = new ArrayList<>();
        private ArrayList<AccelerometerReading> readingBuffer = new ArrayList<>();
        /* Initialize the sensor with a name. Setting the sample rate, and start time are required before adding readings.
         * @param name The sensor name, used only for display
         * @param timestamp ms since POSIX epoch at which this sensor started (GMT)
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
        public void addReading(AccelerometerReading r) {
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
            return getStopTime() - getStartTime();
        }
        public void addTimestamp(long t) {
            if (readingBuffer.isEmpty())
                throw new UnsupportedOperationException("No readings in buffer. Cannot add timestamp.");
            long dur = t - lastTimestamp;
            double readingSize = dur / (double)(readingBuffer.size() + 1);
            AccelerometerReading reading0 = readingBuffer.remove(0);
            reading0.setTimestamp(lastTimestamp);
            readings.add(reading0);
            for (AccelerometerReading r : readingBuffer) {
                r.setTimestamp(lastTimestamp += readingSize);
                readings.add(r);
            }
            lastTimestamp = t;
            readingBuffer.clear();
        }
        public ArrayList<AccelerometerReading> getReadings() {
            return readings;
        }
        public long getStartTime() {
            if (readings.isEmpty())
                throw new UnsupportedOperationException("No readings. Cannot determine start time.");
            return readings.get(0).getTimestamp();
        }
        public long getStopTime() {
            if (readings.isEmpty())
                throw new UnsupportedOperationException("No readings. Cannot determine stop time.");
            return readings.get(readings.size() - 1).getTimestamp();
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
    private class MotionActivity {
        private long startTime;
        private long stopTime = -1;
        public String name = "";
        public MotionActivity(long startTime) {
            this.startTime = startTime;
        }
        public boolean isFinished() {
            return stopTime != -1;
        }
        public void finish(long time) {
            stopTime = time;
        }
        public long getStartTime() {
            return startTime;
        }
        public long getStopTime() {
            if (!isFinished())
                throw new UnsupportedOperationException("Activity incomplete. End time unavailable.");
            return stopTime;
        }

    }
    private class startStopListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (activities.isEmpty() || activities.get(activities.size() - 1).isFinished()) {
                // Start recording
                startStopButton.setText("Stop");
                activities.add(new MotionActivity(System.currentTimeMillis()));
            }
            else {
                // End recording
                startStopButton.setText("Start");
                activities.get(activities.size() - 1).finish(System.currentTimeMillis());
                getMotionActivity(activities.get(activities.size() - 1));
            }
        }
    }
    private class saveListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            finishAndSaveReading(false);
        }
    }
    private class saveAllListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            finishAndSaveReading(true);
        }
    }
}
