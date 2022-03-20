package ar.ne.mipencustomizer;

import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MPCKeyEvent {
    /**
     * 事件类型：长按/点击
     */
    public EventType type;
    /**
     * 按键码
     */
    public int keycode;
    /**
     * 按键按下状态，长按事件时为null
     */
    public Boolean down;

    public MPCKeyEvent(int keycode, boolean down) {
        this.type = EventType.Click;
        this.keycode = keycode;
        this.down = down;
    }

    public MPCKeyEvent(int keycode) {
        this.type = EventType.LongPress;
        this.keycode = keycode;
    }

    public MPCKeyEvent setType(EventType type) {
        this.type = type;
        return this;
    }

    private MPCKeyEvent() {
    }

    @Nullable
    public static MPCKeyEvent parse(@NonNull Message msg) {
        MPCKeyEvent event;
        try {
            event = new MPCKeyEvent();
            event.type = msg.what == 0 ? EventType.Click : EventType.LongPress;
            event.keycode = msg.arg1;
            event.down = event.type == EventType.LongPress ? null : msg.arg2 == 1;
        } catch (Exception e) {
            Log.e("MPCKeyEvent", String.format("Unable to parse message: what=%s arg1=%s arg2=%s", msg.what, msg.arg1, msg.arg2));
            return null;
        }
        return event;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("MPCKeyEvent{ Type=%s Keycode=%s down=%s }", type.name(), keycode, down);
    }

    public Message getMsg() {
        if (down == null)
            return Message.obtain(null, type.value, keycode, 0);
        else
            return Message.obtain(null, type.value, keycode, down ? 1 : 0);
    }

    public enum EventType {
        Click(0), LongPress(1);

        public final int value;

        EventType(int value) {
            this.value = value;
        }
    }
}
