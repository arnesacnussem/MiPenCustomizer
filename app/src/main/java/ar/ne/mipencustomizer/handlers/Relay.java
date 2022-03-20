package ar.ne.mipencustomizer.handlers;

import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import androidx.annotation.NonNull;

import ar.ne.mipencustomizer.MPCKeyEvent;
import ar.ne.mipencustomizer.Settings;

public class Relay extends AbstractHandler {
    private static final String TAG = "ar.ne.mipencustomizer.handlers.Relay";
    private final String name;
    public Messenger lastMessenger;

    public Relay(Settings settings, String name) {
        super(settings);
        this.name = name;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        lastMessenger = msg.replyTo;
    }

    public void forwardEvent(MPCKeyEvent event) {
        if (lastMessenger != null) {
            try {
                lastMessenger.send(event.getMsg());
            } catch (Exception e) {
                Log.w(TAG, this.name + ": Failed to forward event", e);
            }
        } else
            Log.w(TAG, this.name + ": No last messenger, cannot forward event");
    }
}
