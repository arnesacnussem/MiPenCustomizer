package ar.ne.mipencustomizer.hooks;

import android.content.*;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import ar.ne.mipencustomizer.MPCKeyEvent;
import ar.ne.mipencustomizer.MessengerUtil;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FlexcilHook implements IXposedHookLoadPackage {
    private static final String TAG = "ar.ne.mipencustomizer.hooks.FlexcilHook";
    private Messenger mService = null;
    private Context applicationContext;
    private Messenger client;
    private boolean binding;
    private int previousPenToolBeforeLongPress = 0;
    /**
     * 不包含笔工具的管理模块
     */
    private Object toolbarMgr;
    private Object writingFragment;

    public void handleMessage(Message msg) {
        MPCKeyEvent event = MPCKeyEvent.parse(msg);
        if (event == null) return;
        Log.d(TAG, "handleMessage: event=" + event);

        switch (event.type) {
            case Click:
                if (!event.down) {
                    switch (event.keycode) {
                        case KeyEvent.KEYCODE_PAGE_DOWN:
                            toggleEraser();
                            break;
                        case KeyEvent.KEYCODE_PAGE_UP:
                            cycleFirst2Pen();
                            break;
                    }
                }
                break;
            case LongPress:
                switch (event.keycode) {
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                        toggleTriangle();
                        break;
                    case KeyEvent.KEYCODE_PAGE_UP:
                        previousPenToolBeforeLongPress = getCurrentPenToolIndex();
                        setCurrentPenToolByIndex(getPenToolCount() - 1);
                        break;
                }
                break;
        }

        sendClientReady();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.flexcil.flexcilnote".equals(lpparam.packageName)) return;

        tryBindService(lpparam);

        Class<?> toolbarClazz = XposedHelpers.findClass("l6.g", lpparam.classLoader);
        toolbarMgr = XposedHelpers.getStaticObjectField(toolbarClazz, "a");
        Log.i(TAG, "handleLoadPackage: found toolbarMgr=" + toolbarMgr);

        XposedHelpers.findAndHookMethod("androidx.fragment.app.FragmentManager", lpparam.classLoader, "H", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (writingFragment == null && ((int) param.args[0]) == 0x7f09046b && param.getResult() != null) {
                    writingFragment = param.getResult();
                    Log.i(TAG, "handleLoadPackage: found writingFragment=" + param.getResult());
                }
            }
        });
        XposedHelpers.findAndHookMethod("p5.q", lpparam.classLoader, "a",
                "com.flexcil.flexcilnote.writingView.WritingFragment", int.class, android.widget.ImageButton.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (writingFragment == null && ((int) param.args[1]) == 15 && param.args[0] != null) {
                            writingFragment = param.args[0];
                            Log.i(TAG, "handleLoadPackage: found writingFragment=" + param.args[0]);
                        }
                    }
                });
    }

    private void setCurrentPenToolByIndex(int index) {
        XposedHelpers.callMethod(toolbarMgr, "x", index, true);
    }

    private boolean currentToolIsEraser() {
        return (boolean) XposedHelpers.callMethod(toolbarMgr, "o");
    }

    private int getCurrentPenToolIndex() {
        return XposedHelpers.getIntField(toolbarMgr, "c");
    }

    private int getPenToolCount() {
        return (int) XposedHelpers.callMethod(toolbarMgr, "h");
    }

    private void tryBindService(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> ContextClass = XposedHelpers.findClass("android.content.ContextWrapper", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(ContextClass, "getApplicationContext", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (applicationContext != null && mService != null) return;
                applicationContext = (Context) param.getResult();
                boolean b = bindServiceSync();
                if (!b) {
                    applicationContext.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            applicationContext.unregisterReceiver(this);
                            bindServiceSync();
                        }
                    }, new IntentFilter("ar.ne.mipencustomizer.MiPenCustomizerServer.started"));
                    applicationContext.sendBroadcast(new Intent("ar.ne.mipencustomizer.hooks.REQUEST_SERVICE_START"));
                }
            }
        });
    }


    public boolean bindServiceSync() {
        synchronized (TAG) {
            if (!binding) {
                binding = true;
                return bind();
            }
        }
        return false;
    }

    private void toggleEraser() {
        if (!currentToolIsEraser()) {
            //select eraser
            XposedHelpers.callMethod(toolbarMgr, "v", true);
        } else {
            selectPreviousTool();
        }
    }

    private void cycleFirst2Pen() {
        if (getPenToolCount() < 2) return;
        int currentPenTool = getCurrentPenToolIndex();
        if (!currentIsDrawingPen()) {
            setCurrentPenToolByIndex(currentPenTool);
        } else {
            if (currentPenTool > 2)
                setCurrentPenToolByIndex(previousPenToolBeforeLongPress);
            else
                setCurrentPenToolByIndex(currentPenTool == 0 ? 1 : 0);
        }
    }

    private void selectPreviousTool() {
        XposedHelpers.callMethod(toolbarMgr, "y");
    }

    private boolean currentIsDrawingPen() {
        return (boolean) XposedHelpers.callMethod(toolbarMgr, "q");
    }

    private void toggleTriangle() {
        if (writingFragment == null) return;
        XposedHelpers.callMethod(writingFragment, "H3");
    }

    public boolean bind() {
        if (applicationContext == null) return false;
        if (mService != null) return false;

        Log.d(TAG, "bindService: try bindService service...");
        Log.d(TAG, "bindService: applicationContext:" + applicationContext);

        if (client == null) {
            client = MessengerUtil.create(this::handleMessage);
        }

        return applicationContext.bindService(new Intent().setComponent(new ComponentName("ar.ne.mipencustomizer", "ar.ne.mipencustomizer.MiPenCustomizerServer")).setAction(this.getClass().getCanonicalName()), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected: service bounded.");
                mService = new Messenger(service);
                binding = false;
                sendClientReady();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected: service disconnected.");
                mService = null;
                binding = false;
            }
        }, Context.BIND_AUTO_CREATE);
    }

    private void sendClientReady() {
        try {
            Message obtain = Message.obtain();
            obtain.replyTo = client;
            mService.send(obtain);
        } catch (RemoteException e) {
            Log.e(TAG, "handleMessage: Failed to send client-ready message to server.", e);
        }
    }


}
