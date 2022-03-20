package ar.ne.mipencustomizer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import ar.ne.mipencustomizer.handlers.AbstractHandler;
import ar.ne.mipencustomizer.handlers.FrameworkEventHandler;
import ar.ne.mipencustomizer.handlers.Relay;

import static ar.ne.mipencustomizer.MessengerUtil.createBinder;

public class MiPenCustomizerServer extends Service {
    public static final String TAG = "ar.ne.mipencustomizer.MiPenCustomizerServer";
    Runnable unregister;
    private AbstractHandler frameworkEventHandler;
    private Relay flexcilHookHandler;
    private Settings settings;

    public MiPenCustomizerServer() {
    }

    @Override
    public void onCreate() {
        settings = new Settings(this);
        unregister = settings.registerOnChangeBroadcastReceiver();
        flexcilHookHandler = new Relay(settings, "FlexcilHook");
        frameworkEventHandler = new FrameworkEventHandler(settings, flexcilHookHandler::forwardEvent);
        this.sendBroadcast(new Intent("ar.ne.mipencustomizer.MiPenCustomizerServer.started"));
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: client=" + intent.getAction());
        switch (intent.getAction()) {
            case "ar.ne.mipencustomizer.hooks.FrameworkHook":
                return createBinder(frameworkEventHandler);
            case "ar.ne.mipencustomizer.hooks.FlexcilHook":
                return createBinder(flexcilHookHandler);
            case "ar.ne.mipencustomizer.MainActivity":
//                return createBinder();
            default:
                throw new IllegalArgumentException("Unknown client: " + intent.getAction());
        }
    }

    @Override
    public void onDestroy() {
        unregister.run();
    }
}