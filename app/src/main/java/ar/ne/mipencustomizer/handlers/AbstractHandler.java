package ar.ne.mipencustomizer.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import ar.ne.mipencustomizer.MPCKeyEvent;
import ar.ne.mipencustomizer.Settings;

public abstract class AbstractHandler extends Handler {
    protected Settings settings;

    public AbstractHandler(Settings settings) {
        super(Looper.myLooper());
        this.settings = settings;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        MPCKeyEvent event = MPCKeyEvent.parse(msg);
        if (event != null) {
            handleEvent(event);
        }
    }

    public void handleEvent(@NonNull MPCKeyEvent event) {
        throw new UnsupportedOperationException("handleEvent not implemented");
    }
}
