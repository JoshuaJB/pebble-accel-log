package me.jbakita.pebbledatalogging;
import android.content.Context;
import java.util.UUID;
import com.getpebble.android.kit.PebbleKit;

public class DataLogReceiver extends PebbleKit.PebbleDataLogReceiver {
    // Declare the UUID as a class member
    private static final UUID WATCHAPP_UUID = UUID.fromString("631b528e-c553-486c-b5ac-da08f63f01de");
    // This ID must match that used on the Pebble
    private static final int DATA_LOG_ID = 0;

    public DataLogReceiver() {
        super(WATCHAPP_UUID);
    }

    @Override
    public void receiveData(Context context, UUID logUuid, Long timestamp, Long tag, int data) {
        if (tag.intValue() == DATA_LOG_ID) {
            // Do something with the data
        }
    }

    @Override
    public void onFinishSession(Context context, UUID logUuid, Long timestamp, Long tag) {
        super.onFinishSession(context, logUuid, timestamp, tag);
        // App closed on Pebble, we can shutdown
    }

}
