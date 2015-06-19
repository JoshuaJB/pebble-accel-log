package me.jbakita.pebbledatalogging;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;

import java.util.UUID;

/**
 * MainActivity class
 * Implements a PebbleDataLogReceiver to process received log data, 
 * as well as a finished session.
 */
public class MainActivity extends Activity {

    // Configuration
    // WATCHAPP_UUID and DATA_LOG_ID *MUST* match those used on the watchapp
    private static final UUID WATCHAPP_UUID = UUID.fromString("631b528e-c553-486c-b5ac-da08f63f01de");
    private static final int DATA_LOG_ID = 164;

    // App elements
    private PebbleDataLogReceiver dataloggingReceiver;
    private TextView textView;
    private StringBuilder resultBuilder = new StringBuilder();
    private boolean justFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup TextView
        textView = (TextView)findViewById(R.id.text_view);
        textView.setText("Waiting for logging data...");
        textView.setMovementMethod(new ScrollingMovementMethod());
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
                    resultBuilder.delete(0, resultBuilder.length());
                    textView.setText("Waiting for logging data...");
                    justFinished = false;
                }
                // Check this is our data log
                if(tag.intValue() == DATA_LOG_ID) {
                    /* Note on Java and Bitwise Operators
                     *  Java bitwise operaters only work on ints and longs,
                     *  Bytes will undergo promotion with sign extension first.
                     *  So, we have to undo the sign extension on the lower order
                     *  bits here.
                     */
                    int x = data[0];
                    x <<= 8;
                    x |= data[1] & 0x000000FF;
                    int y = data[2];
                    y <<= 8;
                    y |= data[3] & 0x000000FF;
                    int z = data[4];
                    z <<= 8;
                    z |= data[5] & 0x000000FF;
                    // Take the accelerometer reading and append it to what we're displaying
                    resultBuilder.append("X: " + x + ", Y: " + y + ", Z: " + z + "\n");
                    textView.setText("Session Running...\n" + "Results are: \n\n" + resultBuilder.toString());
                }
            }

            @Override
            public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
                super.onFinishSession(context, logUuid, timestamp, tag);

                // Tweak the displayed text to say that we're done
                textView.setText("Session finished!\n" + "Results were: \n\n" + resultBuilder.toString());
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
}
