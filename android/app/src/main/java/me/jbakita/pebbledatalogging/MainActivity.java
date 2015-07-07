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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
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
                    if (sensors.indexOf(new Sensor(features[tag.intValue()])) == -1) {
                        /* Add new sensor
                         * To conserve data, but maximize accuracy, the first 'reading'
                         * for each data log ID is the beginning timestamp and the
                         * second 'reading' is the sampling rate.
                         */
                        long beginningTimestamp = decodeBytes(Arrays.copyOf(data, 6));
                        Sensor sensor = new Sensor(features[tag.intValue()]);
                        sensor.setBeginningTimestamp(beginningTimestamp);
                        sensors.add(sensor);
                    }
                    else if (sensors.get(sensors.indexOf(new Sensor(features[tag.intValue()]))).getSampleRate() == 0) {
                        // Finish sensor initialization. See above comment.
                        Sensor sensor = sensors.get(sensors.indexOf(new Sensor(features[tag.intValue()])));
                        sensor.setSampleRate((int)decodeBytes(data));
                    }
                    else {
                        // Update existing sensor
                        int x = (int)decodeBytes(new byte[]{data[0], data[1]});
                        int y = (int)decodeBytes(new byte[]{data[2], data[3]});
                        int z = (int)decodeBytes(new byte[]{data[4], data[5]});
                        Sensor sensor = sensors.get(sensors.indexOf(new Sensor(features[tag.intValue()])));
                        sensor.addReading(new Reading(x, y, z));
                        // Refresh UI
                        adapter.notifyDataSetChanged();
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
        for (int i = 0; i < activities.size(); i++) {
            for (int j = 0; j < sensors.size(); j++) {
                ArrayList<Reading> readings = sensors.get(j).getReadings();
                // TODO: Handle missing/unavailable external storage
                try {
                    // Get/create our application's save folder
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/PebbleDataLogging/");
                    dir.mkdir();
                    // Create the file in the <activity name>-<sensor name>-<system time>.csv format
                    File file = new File(dir, activities.get(i).name + " " + features[j] + " " + DateFormat.getDateTimeInstance().format(new Date()) + ".csv");
                    FileOutputStream outputStream = new FileOutputStream(file);
                    // Write the colunm headers
                    outputStream.write(String.format("X,    Y,    Z,    Time\n").getBytes());
                    // Write all the readings which correlate to our current activity
                    for (int k = 0; k < readings.size(); k++) {
                        // TODO: Warn when it seems like a sensor data set is incomplete
                        //if (readings.get(k).timestamp >= activities.get(i).startTime && readings.get(k).timestamp < activities.get(i).endTime) {
                            outputStream.write(String.format(Locale.US, "%+5d,%+5d,%+5d,%14d\n", readings.get(k).x, readings.get(k).y, readings.get(k).z, readings.get(k).timestamp).getBytes());
                        //}
                    }
                    outputStream.close();
                    // Workaround for Android bug #38282
                    MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                } catch (Exception e) {e.printStackTrace();}
            }
        }
        Log.w("MainActivity", sensors.toString());
    }
    private class Sensor {
        private String name;
        private long currTimestamp = 0;
        private int sampleRate = 0;
        private ArrayList<Reading> readings = new ArrayList<Reading>();
        /* Initialize the sensor with a name. Setting the sample rate, and start time are required before adding readings.
         * @param name The sensor name, used only for display
         */
        public Sensor(String name) {
            this.name = name;
        }
        /* Set the sensor sample rate in HZ, required before adding readings
         */
        public void setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
        }
        /* Set the timestamp in ms since the POSIX epoch at which the sensor started
         */
        public void setBeginningTimestamp(long t) {
            currTimestamp = t;
        }
        /* Add a sequential accelerometer reading. The time is automatically calculated.
         * @param r the reading to add
         * @throws UnsupportedOperationException if the sample rate and beginning timestamp
         *                                       have yet to be set.
         */
        public void addReading(Reading r) {
            // Check that everything is setup
            if (sampleRate == 0)
                throw new UnsupportedOperationException("No sample rate set on sensor");
            else if (currTimestamp == 0)
                throw new UnsupportedOperationException("No starting timestamp set on sensor");
            // Log the reading
            if (readings.size() == 0)
                r.setTimestamp(currTimestamp);
            else
                r.setTimestamp(currTimestamp += 1000 / sampleRate);
            readings.add(r);
        }
        public String getTitle() {
            return name;
        }
        public String getInfo() {
            return readings.size() + " readings taken over " + (readings.size() / sampleRate) / 60 + "m " + (readings.size() / sampleRate) % 60 + "s";
        }
        public int getSampleRate() {
            return sampleRate;
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
