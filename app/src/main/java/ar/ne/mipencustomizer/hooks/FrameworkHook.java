package ar.ne.mipencustomizer.hooks;

import android.annotation.SuppressLint;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import ar.ne.mipencustomizer.MPCKeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.concurrent.atomic.AtomicReference;

public class FrameworkHook implements IXposedHookZygoteInit, IXposedHookLoadPackage, ServiceConnection {
    public static final String TAG = "ar.ne.mipencustomizer.hooks.FrameworkHook";
    private final AtomicReference<Messenger> mMessenger = new AtomicReference<>();
    private Context mContext;
    private volatile boolean hooked = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (hooked || !"android".equals(lpparam.packageName)) return;
        hooked = true;

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
                        synchronized (mMessenger) {
                            if (FrameworkHook.this.mContext == null) {
                                FrameworkHook.this.mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                                BroadcastReceiver onBootCompletedReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        FrameworkHook.this.mContext.unregisterReceiver(this);
                                        if (FrameworkHook.this.mMessenger.get() == null) {
                                            bindService();
                                        }
                                    }
                                };
                                BroadcastReceiver onRequestServiceStartReceiver = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        if (FrameworkHook.this.mMessenger.get() == null) {
                                            bindService();
                                        }
                                    }
                                };
                                FrameworkHook.this.mContext.registerReceiver(onBootCompletedReceiver, new IntentFilter("android.intent.action.BOOT_COMPLETED"));
                                FrameworkHook.this.mContext.registerReceiver(onRequestServiceStartReceiver, new IntentFilter("ar.ne.mipencustomizer.hooks.FrameworkHook.REQUEST_SERVICE_START"));
                            }
                        }
                    }
                });
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean bindService() {
        if (mContext == null) return false;
        boolean b = this.mContext.bindServiceAsUser(
                new Intent()
                        .setComponent(new ComponentName("ar.ne.mipencustomizer", "ar.ne.mipencustomizer.MiPenCustomizerServer"))
                        .setAction(this.getClass().getCanonicalName()),
                this, Context.BIND_AUTO_CREATE, UserHandle.getUserHandleForUid(0));
        if (b) {
            Log.d(TAG, "MiPenCustomizer: Service bound");
            this.mContext.sendBroadcast(new Intent("ar.ne.mipencustomizer.MiPenCustomizerServer.started"));
        } else Log.e(TAG, "MiPenCustomizer: Failed to bindService service");
        return b;
    }

    public boolean enabled() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "__mpc_enabled", 0) == 1;
    }

    public void sendKeyEvent(boolean down, int keycode) {
        Message msg = new MPCKeyEvent(keycode, down).getMsg();
        if (mMessenger.get() != null) {
            try {
                mMessenger.get().send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "sendKeyEvent: Failed to send message", e);
            }
        } else if (bindService()) {
            try {
                mMessenger.get().send(Message.obtain(msg));
            } catch (RemoteException ignored) {
            }
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mMessenger.set(new Messenger(service));
        Log.d(TAG, "onServiceConnected: Service connected, messenger=" + mMessenger.get());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mMessenger.set(null);
        Log.d(TAG, "onServiceDisconnected: mMessenger cleared");
        bindService();
    }
}
