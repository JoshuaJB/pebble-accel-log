package me.jbakita.pebbledatalogging;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
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

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.ArrayList;

/**
 * MainActivity class
 * Implements a PebbleDataLogReceiver to process received log data, 
 * as well as a finished session.
 */
public class MainActivity extends Activity implements View.OnClickListener{

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
    private String[] activities = {"Pushups", "Situps", "Jumping Jacks", "Stretching", "Running", "Walking"};

    private PebbleDataLogReceiver dataloggingReceiver = null;
    private final ArrayList<Sensor> sensors = new ArrayList<>();
    private ArrayAdapter<Sensor> adapter;
    private Button startStopButton;
    private long activityStart = 0;
    private long activityEnd = 0;

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
        startStopButton.setOnClickListener(this);
        startStopButton.setText("Start");
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

    @Override
    public void onClick(View v) {
        if (activityStart == 0 && activityEnd == 0) {
            // Clear previous state
            sensors.clear();
            adapter.notifyDataSetChanged();
            // Switch to recording mode
            startStopButton.setText("Stop");
            activityStart = System.currentTimeMillis();
        }
        else if (activityEnd == 0) {
            // Finish recording
            startStopButton.setText("Save");
            activityEnd = System.currentTimeMillis();
        }
        else {
            // Save data
            finishAndSaveReading();
            // Begin reset for next mode
            startStopButton.setText("Start");
            activityEnd = 0;
            activityStart = 0;
        }
    }

    private void finishAndSaveReading() {
        /* TODO: Get activity type from user then save the sensor readings truncated *
         *       to the difference between activityStart and activityEnd.            */
        Log.w("MainActivity", sensors.toString());
    }
    private class Sensor {
        private String name;
        private long currTimestamp = 0;
        private int sampleRate = 0;
        private ArrayList<Reading> readings = new ArrayList<>();
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
            return readings.size() + " readings taken.";
        }
        public int getSampleRate() {
            return sampleRate;
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
}
