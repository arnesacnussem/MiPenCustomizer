package ar.ne.mipencustomizer.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.HashSet;

import ar.ne.mipencustomizer.MPCKeyEvent;
import ar.ne.mipencustomizer.MainActivity;
import ar.ne.mipencustomizer.Settings;

public class FrameworkEventHandler extends AbstractHandler {
    private static final String TAG = "ar.ne.mipencustomizer.handlers.FrameworkEventHandler";
    private final OnKeyEventListener mKeyEventListener;
    private final Handler longPressHandler;
    private final HashSet<Integer> mLongPressedKeys = new HashSet<>(2);

    public FrameworkEventHandler(Settings settings, OnKeyEventListener keyEventListener) {
        super(settings);
        mKeyEventListener = keyEventListener;
        longPressHandler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                MPCKeyEvent event = ((MPCKeyEvent) msg.obj);
                mLongPressedKeys.remove(event.keycode);
                FrameworkEventHandler.this.mKeyEventListener.onKeyEvent(event.setType(MPCKeyEvent.EventType.LongPress));
                Log.d(TAG, "longPressHandler: send long press event=" + event);
            }
        };
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        if (MainActivity.uiHandler != null) MainActivity.uiHandler.sendMessage(Message.obtain(msg));
        MPCKeyEvent event = MPCKeyEvent.parse(msg);
        if (event != null) {
            Log.d(TAG, "FrameworkEventHandler: event=" + event);
            if (!settings.isLongPressEnabled()) {
                this.mKeyEventListener.onKeyEvent(event);
            } else {
                if (event.down) {
                    mLongPressedKeys.add(event.keycode);
                    longPressHandler.sendMessageDelayed(Message.obtain(longPressHandler, event.keycode, event), settings.getLongPressTime());
                } else if (mLongPressedKeys.contains(event.keycode)) {
                    longPressHandler.removeMessages(event.keycode);
                    mLongPressedKeys.remove(event.keycode);
                    this.mKeyEventListener.onKeyEvent(event);
                }
            }


        } else {
            Log.e(TAG, "handleMessage: Unknown message: " + msg);
        }
    }

    public interface OnKeyEventListener {

        void onKeyEvent(MPCKeyEvent event);
    }
}
