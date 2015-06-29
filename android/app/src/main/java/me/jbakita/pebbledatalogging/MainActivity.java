package me.jbakita.pebbledatalogging;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;

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
    private enum Feature {
        DOMINANT_WRIST,
        NON_DOMINATE_WRIST,
        WAIST,
        RIGHT_ANKLE,
        LEFT_ANKLE,
        UPPER_DOMINATE_ARM,
        UPPER_NON_DOMINATE_ARM,
        RIGHT_THIGH,
        LEFT_THIGH,
        CHEST,
        NECK
    }

    private PebbleDataLogReceiver dataloggingReceiver;
    private final ArrayList<SensorListItem> sensors = new ArrayList<SensorListItem>();
    private ArrayAdapter<SensorListItem> adapter;
    private boolean justFinished = false;

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
        adapter = new ArrayAdapter<SensorListItem>(this, android.R.layout.simple_list_item_2, android.R.id.text1, sensors) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = (TextView) view.findViewById(android.R.id.text1);
                TextView text2 = (TextView) view.findViewById(android.R.id.text2);

                text1.setText(sensors.get(position).getTitle());
                text2.setText(sensors.get(position).getBody());
                return view;
            }
        };

        // Add listview display adapter
        sensorsView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Define data reception behavior
        PebbleDataLogReceiver dataloggingReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID) {

            @Override
            public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, byte[] data) {
                // Reset the state if we just finished a run
                if (justFinished) {
                    // TODO: Reset State
                    //resultBuilder.delete(0, resultBuilder.length());
                    //textView.setText("Waiting for logging data...");
                    justFinished = false;
                }
                // Check this is our data log
                if (tag.intValue() <= Feature.values().length) {
                    int x = decodeBytes(data[1], data[0]);
                    int y = decodeBytes(data[3], data[2]);
                    int z = decodeBytes(data[5], data[4]);

                    if (sensors.indexOf(new SensorListItem(Feature.values()[tag.intValue()].name(), 0)) != -1) {
                        sensors.get(sensors.indexOf(new SensorListItem(Feature.values()[tag.intValue()].name(), 0))).addSamples(1);
                        // TODO: Save reading
                    }
                    else {
                        sensors.add(new SensorListItem(Feature.values()[tag.intValue()].name(), 1));
                        // TODO: Save reading
                    }
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);
                justFinished = true;
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

    private int decodeBytes(byte lowerBits, byte upperBits) {
        /* Note on Java and Bitwise Operators
         *  Java bitwise operators only work on ints and longs,
         *  Bytes will undergo promotion with sign extension first.
         *  So, we have to undo the sign extension on the lower order
         *  bits here.
         */
        int ans = upperBits;
        ans <<= 8;
        ans |= lowerBits & 0x000000FF;
        return ans;
    }

    private class SensorListItem {
        private String name;
        private int samples;
        public SensorListItem(String name, int samples) {
            this.name = name;
            this.samples = samples;
        }
        public void addSamples(int samples) {
            this.samples += samples;
        }
        public String getTitle() {
            return name;
        }
        public String getBody() {
            return samples + " samples taken.";
        }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof String)
                return name.equals(obj);
            else if (obj instanceof  SensorListItem)
                if (name.equals(((SensorListItem) obj).getTitle()))
                    return true;
            return false;
        }
    }
}
