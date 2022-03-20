package ar.ne.mipencustomizer.hooks;

import android.annotation.SuppressLint;
import android.content.*;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import ar.ne.mipencustomizer.MPCKeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FrameworkHook implements IXposedHookZygoteInit, IXposedHookLoadPackage, ServiceConnection {
    public static final String TAG = "ar.ne.mipencustomizer.hooks.FrameworkHook";
    private Context mContext;
    private Messenger mMessenger;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName)) {
            XposedHelpers.findAndHookMethod("com.miui.server.stylus.MiuiStylusPageKeyListener", lpparam.classLoader,
                    "shouldInterceptKey", KeyEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            KeyEvent event = (KeyEvent) param.args[0];
                            boolean isPageKeyEnable = (boolean) XposedHelpers.callMethod(param.thisObject, "isPageKeyEnable", event);
                            if (!isPageKeyEnable) {
                                param.setResult(false);
                            } else if (enabled()) {
                                sendKeyEvent(event.getAction() == 0, event.getKeyCode());
                                param.setResult(true);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod("com.miui.server.stylus.MiuiStylusPageKeyListener", lpparam.classLoader,
                    "initView", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (FrameworkHook.this.mContext == null) {
                                FrameworkHook.this.mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                                BroadcastReceiver onBootCompletedReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        FrameworkHook.this.mContext.unregisterReceiver(this);
                                        bindService();
                                    }
                                };
                                BroadcastReceiver onRequestServiceStartReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        bindService();
                                    }
                                };
                                FrameworkHook.this.mContext.registerReceiver(onBootCompletedReceiver, new IntentFilter("android.intent.action.BOOT_COMPLETED"));
                                FrameworkHook.this.mContext.registerReceiver(onRequestServiceStartReceiver, new IntentFilter("ar.ne.mipencustomizer.hooks.FrameworkHook.REQUEST_SERVICE_START"));
                            }
                        }
                    });
        }
    }

    @SuppressLint("MissingPermission")
    public void bindService() {
        if (mContext == null) return;
        if (
                this.mContext.bindServiceAsUser(
                        new Intent()
                                .setComponent(new ComponentName("ar.ne.mipencustomizer", "ar.ne.mipencustomizer.MiPenCustomizerServer"))
                                .setAction(this.getClass().getCanonicalName()),
                        this, Context.BIND_AUTO_CREATE, UserHandle.getUserHandleForUid(0))
        )
            Log.d(TAG, "MiPenCustomizer: Bind service");
        else Log.e(TAG, "MiPenCustomizer: Failed to bindService service");
    }

    public boolean enabled() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "__mpc_enabled", 0) == 1;
    }

    public void sendKeyEvent(boolean down, int keycode) {
        if (mMessenger != null) {
            try {
                mMessenger.send(new MPCKeyEvent(keycode, down).getMsg());
            } catch (RemoteException e) {
                Log.e(TAG, "sendKeyEvent: Failed to send message", e);
            }
        } else {
            bindService();
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mMessenger = new Messenger(service);
        Log.d(TAG, "onServiceConnected: mMessenger set");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mMessenger = null;
        Log.d(TAG, "onServiceDisconnected: mMessenger cleared");
        bindService();
    }
}
