package ar.ne.mipencustomizer;

import android.os.*;
import androidx.annotation.NonNull;

import java.util.function.Consumer;

public class MessengerUtil {
    public static Messenger create(Consumer<Message> messageHandler) {
        return create(createHandler(messageHandler));
    }

    public static Handler createHandler(Consumer<Message> handler) {
        return new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                handler.accept(msg);
            }
        };
    }

    private static Messenger create(Handler handler) {
        return new Messenger(handler);
    }

    public static <T extends Handler> IBinder createBinder(T handler) {
        return create(handler).getBinder();
    }
}
