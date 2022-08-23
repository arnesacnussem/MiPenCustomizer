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

import java.util.concurrent.atomic.AtomicReference;

public class FlexcilHook implements IXposedHookLoadPackage, ServiceConnection {
    private static final String TAG = "ar.ne.mipencustomizer.hooks.FlexcilHook";
    private final AtomicReference<Messenger> mService = new AtomicReference<>();
    private final Flexcil flexcil = new Flexcil();
    private Context applicationContext;
    private Messenger client;
    private int previousPenToolBeforeLongPress = 0;
    private volatile boolean hooked = false;

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
                        flexcil.toggleTriangle();
                        break;
                    case KeyEvent.KEYCODE_PAGE_UP:
                        if (flexcil.getCurrentPenToolIndex() == flexcil.getPenToolCount() - 1)
                            cycleFirst2Pen();
                        else {
                            previousPenToolBeforeLongPress = flexcil.getCurrentPenToolIndex();
                            flexcil.setCurrentPenToolByIndex(flexcil.getPenToolCount() - 1);
                        }
                        break;
                }
                break;
        }

        sendClientReady();
    }

    private void toggleEraser() {
        if (!flexcil.currentToolIsEraser()) {
            flexcil.selectEraser();
        } else {
            flexcil.selectPreviousTool();
        }
    }

    private void cycleFirst2Pen() {
        if (flexcil.getPenToolCount() < 2) return;
        int currentPenTool = flexcil.getCurrentPenToolIndex();
        if (!flexcil.currentIsDrawingPen()) {
            flexcil.setCurrentPenToolByIndex(currentPenTool);
        } else {
            if (currentPenTool > 2)
                flexcil.setCurrentPenToolByIndex(previousPenToolBeforeLongPress);
            else
                flexcil.setCurrentPenToolByIndex(currentPenTool == 0 ? 1 : 0);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (hooked || !"com.flexcil.flexcilnote".equals(lpparam.packageName) || !lpparam.isFirstApplication) return;
        hooked = true;
        Class<?> contextClass = XposedHelpers.findClass("android.content.ContextWrapper", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(contextClass, "getApplicationContext", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                synchronized (mService) {
                    if (applicationContext != null) return;
                    Log.i(TAG, "handleLoadPackage: context=" + param.getResult());
                    applicationContext = (Context) param.getResult();
                    if (!bind()) {
                        Log.w(TAG, "handleLoadPackage: Unable to bind service, register on service start receiver");
                        applicationContext.registerReceiver(
                                new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        synchronized (mService) {
                                            applicationContext.unregisterReceiver(this);
                                            bind();
                                        }
                                    }
                                },
                                new IntentFilter("ar.ne.mipencustomizer.MiPenCustomizerServer.started")
                        );
                        applicationContext.sendBroadcast(new Intent("ar.ne.mipencustomizer.hooks.FrameworkHook.REQUEST_SERVICE_START"));
                    }
                }
            }
        });

        Class<?> toolbarClazz = XposedHelpers.findClass("p6.g", lpparam.classLoader);
        flexcil.toolbarMgr = XposedHelpers.getStaticObjectField(toolbarClazz, "a");
        Log.i(TAG, "handleLoadPackage: found toolbarMgr=" + flexcil.toolbarMgr);

        XposedHelpers.findAndHookConstructor("com.flexcil.flexcilnote.writingView.WritingFragment", lpparam.classLoader, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Log.d(TAG, "handleLoadPackage: found writingFragment=" + param.thisObject);
                flexcil.writingFragment = param.thisObject;
            }
        });
    }

    public synchronized boolean bind() {
        if (applicationContext == null) return false;
        if (mService.get() != null) return false;

        Log.d(TAG, "bind: try bindService service...");
        Log.d(TAG, "bind: applicationContext:" + applicationContext);

        if (client == null) {
            client = MessengerUtil.create(this::handleMessage);
        }

        return applicationContext.bindService(
                new Intent()
                        .setComponent(new ComponentName(
                                "ar.ne.mipencustomizer",
                                "ar.ne.mipencustomizer.MiPenCustomizerServer"))
                        .setAction(this.getClass().getCanonicalName()),
                this,
                Context.BIND_AUTO_CREATE);
    }

    private void sendClientReady() {
        try {
            Message obtain = Message.obtain();
            obtain.replyTo = client;
            mService.get().send(obtain);
        } catch (RemoteException e) {
            Log.e(TAG, "handleMessage: Failed to send client-ready message to server.", e);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected: service bounded.");
        mService.set(new Messenger(service));
        sendClientReady();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected: service disconnected.");
        mService.set(null);
    }

    private static class Flexcil {
        /**
         * 不包含笔工具的管理模块
         */
        private Object toolbarMgr;
        private Object writingFragment;

        public void selectEraser() {
            XposedHelpers.callMethod(toolbarMgr, "w", true);
        }

        public void setCurrentPenToolByIndex(int index) {
            XposedHelpers.callMethod(toolbarMgr, "z", index, true);
        }

        public boolean currentToolIsEraser() {
            return (boolean) XposedHelpers.callMethod(toolbarMgr, "o");
        }

        public int getCurrentPenToolIndex() {
            return XposedHelpers.getIntField(toolbarMgr, "c");
        }

        public int getPenToolCount() {
            return (int) XposedHelpers.callMethod(toolbarMgr, "h");
        }

        private void toggleTriangle() {
            if (writingFragment == null) return;
            XposedHelpers.callMethod(writingFragment, "y3");
        }

        public void selectPreviousTool() {
            XposedHelpers.callMethod(toolbarMgr, "A");
        }

        public boolean currentIsDrawingPen() {
            return (boolean) XposedHelpers.callMethod(toolbarMgr, "r");
        }
    }
}
