package ar.ne.mipencustomizer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

public class Settings {
    public static final String TAG = "ar.ne.mipencustomizer.Settings";
    public static final String MIPEN_CUSTOMIZER_VERSION = "0.1";
    private final SharedPreferences prefs;
    private final Context context;
    private int longPressTime;
    private boolean longPressEnabled;

    public Settings(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("mipencustomizer", Context.MODE_PRIVATE);
        this.longPressTime = prefs.getInt("longPressTime", 1000);
        this.longPressEnabled = prefs.getBoolean("enableLongPress", false);
    }

    /**
     * @return the unregister runnable.
     */
    public Runnable registerOnChangeBroadcastReceiver() {
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received broadcast: " + intent.getAction());
                longPressTime = prefs.getInt("longPressTime", 1000);
                longPressEnabled = prefs.getBoolean("enableLongPress", false);
            }
        };
        context.registerReceiver(broadcastReceiver, new IntentFilter("ar.ne.mipencustomizer.Settings.save"));
        return () -> context.unregisterReceiver(broadcastReceiver);
    }

    public int getLongPressTime() {
        return longPressTime;
    }

    public void setLongPressTime(int longPressTime) {
        this.longPressTime = longPressTime;
        save();
    }

    public boolean isLongPressEnabled() {
        return longPressEnabled;
    }

    public void setLongPressEnabled(boolean longPressEnabled) {
        this.longPressEnabled = longPressEnabled;
        save();
    }

    @SuppressLint("ApplySharedPref")
    public void save() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("longPressTime", longPressTime);
        editor.putBoolean("enableLongPress", longPressEnabled);
        editor.commit();
        context.sendBroadcast(new Intent("ar.ne.mipencustomizer.Settings.save"));
    }
}
