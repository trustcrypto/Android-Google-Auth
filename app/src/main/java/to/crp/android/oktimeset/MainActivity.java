package to.crp.android.oktimeset;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App to set the time on an OnlyKey when it is inserted into the Android device.
 */
public class MainActivity extends Activity implements OKListener {

    protected static final String TAG = "onlykey";

    private static final String ACTION_USB_ATTACHED =
            "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String ACTION_USB_DETACHED =
            "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final String ACTION_USB_PERMISSION = "to.crp.android.oktimeset.USB_PERMISSION";

    /**
     * References to connected OnlyKeys by UsbDevice.
     */
    private final Map<UsbDevice, OnlyKey> keys = new ConcurrentHashMap<>();

    private UsbManager manager;

    private TextSwitcher textSwitcher;

    private ViewSwitcher.ViewFactory factory = new ViewSwitcher.ViewFactory() {
        @Override
        public View makeView() {
            TextView t = new TextView(MainActivity.this);
            t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            t.setTextAppearance(android.R.style.TextAppearance_Large);
            return t;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // only want the app opening oncee
        if (!isTaskRoot()) {
            Log.d(TAG, "App already open.");
            finishAndRemoveTask();
        }

        manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        textSwitcher = (TextSwitcher) findViewById(R.id.switcher);
        textSwitcher.setFactory(factory);

        final Animation in = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        final Animation out = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);

        textSwitcher.setInAnimation(in);
        textSwitcher.setOutAnimation(out);


        Log.d(TAG, "onCreate(), action: " + getIntent().getAction());

        if (getIntent().getAction().equals("android.intent.action.MAIN")) {
            toastLong(getString(R.string.launch_err));
            finishAndRemoveTask();
        }

        handleAttachDetachIntent(getIntent());

        // need to listen for permission requests
        final BroadcastReceiver bReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "broadcast, action: " + intent);

                final UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (ACTION_USB_PERMISSION.equalsIgnoreCase(intent.getAction())) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(TAG, "Permission granted.");

                        try {
                            addOnlyKey(dev);
                        } catch (IOException ioe) {
                            handleError(ioe);
                            Log.e(TAG, ioe.getMessage(), ioe);
                        }
                    } else {
                        setMessage(getString(R.string.msg_perm_denied));
                        Log.d(TAG, "Permission denied.");
                    }
                }

                handleAttachDetachIntent(intent);
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_ATTACHED);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(bReceiver, filter);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent(), action: " + intent.getAction());
        handleAttachDetachIntent(intent);
    }

    /**
     * Handle a USB attach/detach intent.
     *
     * @param intent The USB attach/detach intent to handle.
     */
    private void handleAttachDetachIntent(final Intent intent) {
        final UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (ACTION_USB_ATTACHED.equalsIgnoreCase(intent.getAction())) {
            Log.d(TAG, "OnlyKey attached.");
            setMessage(getString(R.string.msg_ok_attached));
            permissionsCheck(dev);
        } else if (ACTION_USB_DETACHED.equalsIgnoreCase(intent.getAction())) {
            synchronized (keys) {
                if (keys.containsKey(dev)) {
                    final OnlyKey k = keys.remove(dev);
                    k.cancel();
                }
            }
            Log.d(TAG, "OnlyKey detached.");
            setMessage(getString(R.string.msg_ok_detached));

            doFinish();
        } else {
            Log.e(TAG, "Unhandled intent action: " + intent.getAction());
        }
    }

    /**
     * Set the message displayed to the user.
     *
     * @param msg The message to display.
     */
    private void setMessage(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textSwitcher.setText(msg);
            }
        });
    }

    /**
     * Display a long toast.
     *
     * @param msg The message to display.
     */
    private void toastLong(final String msg) {
        toast(msg, false);
    }

    /**
     * Display a toast.
     *
     * @param msg     The message to display.
     * @param isShort Whether the delay should be short.
     */
    private void toast(final String msg, final boolean isShort) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg,
                        isShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addOnlyKey(final UsbDevice device) throws IOException {
        final OnlyKey k = OnlyKey.getOnlyKey(device, manager);
        k.addListener(this);
        keys.put(device, k);
        new Thread(k, "onlyKey").start();
    }

    /**
     * Check USB device permissions.
     *
     * @param dev The USB device.
     */
    private void permissionsCheck(final UsbDevice dev) {
        if (manager.hasPermission(dev)) {
            Log.d(TAG, "Already have permission.");
            try {
                addOnlyKey(dev);
            } catch (IOException ioe) {
                handleError(ioe);
            }
        } else {
            Log.d(TAG, "Requesting permission for attached key.");
            final PendingIntent mPermissionIntent =
                    PendingIntent.getBroadcast(getApplicationContext(), 0,
                            new Intent(ACTION_USB_PERMISSION), 0);
            manager.requestPermission(dev, mPermissionIntent);
        }
    }

    /**
     * Set the displayed message, toast it, log it
     *
     * @param e The exception to handle.
     */
    private void handleError(final Exception e) {
        setMessage("Error!");
        toastLong("Error: " + e.getMessage());
        Log.e(TAG, e.getMessage(), e);
    }

    @Override
    public void okError(final OKEvent event) {
        handleError(event.getException());
    }

    private void doFinish() {
        for (final OnlyKey k : keys.values()) {
            k.cancel();
        }
        Log.d(TAG, "Closing.");
        finishAndRemoveTask();
    }

    @Override
    public void okSetTime(OKEvent event) {
        setMessage(getString(R.string.msg_set_time));
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                doFinish();
            }
        }, 1500);
    }

    @Override
    public void okMessage(final OKEvent event) {
        setMessage(event.getStringVal());
    }

    @Override
    public void okSetInitialized(final OKEvent event) {
        final boolean initialized = event.getBoolVal();
        final String msg = initialized ?
                getString(R.string.msg_waiting_for_unlock) : getString(R.string.msg_setup_required);

        Log.d(TAG, msg);
        setMessage(msg);
    }

    @Override
    public void okSetLocked(final OKEvent event) {
        final boolean locked = event.getBoolVal();
        final String msg = locked ?
                getString(R.string.msg_dev_locked) : getString(R.string.msg_dev_unlocked);

        Log.d(TAG, msg);
        setMessage(msg);

        // kick off time set on unlock
        if (!locked) {
            try {
                event.getKey().setTime();
            } catch (IOException ioe) {
                handleError(ioe);
            }
        }
    }
}
