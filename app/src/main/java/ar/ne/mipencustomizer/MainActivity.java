package ar.ne.mipencustomizer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;

public class MainActivity extends Activity {
    public static volatile Handler uiHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //init ui
        setPermission();
        initSettingsUI();


        startService(new Intent(this, MiPenCustomizerServer.class));

    }

    @SuppressLint("DefaultLocale")
    private void initSettingsUI() {
        {//setting: Enable
            int enable = Settings.Global.getInt(this.getContentResolver(), "__mpc_enabled", 0);
            SwitchMaterial sw = findViewById(R.id.switch2);
            sw.setChecked(enable == 1);
            sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                    Settings.Global.putInt(MainActivity.this.getContentResolver(), "__mpc_enabled", isChecked ? 1 : 0));
        }

        ar.ne.mipencustomizer.Settings settings = new ar.ne.mipencustomizer.Settings(this);
        {//setting: long press delay
            SeekBar seekBar = findViewById(R.id.long_press_time);
            TextView textView = findViewById(R.id.long_press_time_text);
            textView.setText(String.format("LongPressTime: %d ms", settings.getLongPressTime()));
            seekBar.setMax(3000);
            seekBar.setProgress(settings.getLongPressTime());
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    textView.setText(String.format("LongPressTime: %d ms", progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    settings.setLongPressTime(seekBar.getProgress());
                    textView.setText(String.format("LongPressTime: %d ms", seekBar.getProgress()));
                }
            });
        }
        {//setting: long press enable
            SwitchMaterial sw = findViewById(R.id.long_press_enabled);
            sw.setChecked(settings.isLongPressEnabled());
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> settings.setLongPressEnabled(isChecked));
        }
    }

    private void setPermission() {
        int i = checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS");
        if (i == PackageManager.PERMISSION_DENIED) {
            try {
                Runtime.getRuntime().exec("/system/bin/su -c pm grant " + this.getPackageName() + " " + "android.permission.WRITE_SECURE_SETTINGS");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler = null;
    }
}
