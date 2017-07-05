package to.crp.android.oktimeset;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class representing an attached OnlyKey.
 */
class OnlyKey extends RunnableImpl {

    private static final String TAG = "onlykeykey";

    private static final int OK_HID_INTERFACE = 1;

    private static final int OK_INT_IN = 0;
    private static final int OK_INT_OUT = 1;

    /**
     * OnlyKey message header.
     */
    private static final byte[] header = new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255};

    private static final byte MSG_SET_TIME = (byte) 228;

    private List<OKListener> listeners = new CopyOnWriteArrayList<>();

    private final UsbDeviceConnection conn;
    private final UsbEndpoint epIn;
    private final UsbEndpoint epOut;

    private
    @Nullable
    Boolean initialized = null;
    private
    @Nullable
    Boolean locked = null;

    /**
     * Create a new OnlyKey.
     *
     * @param conn  The USB device connection.
     * @param epIn  The IN USB endpoint.
     * @param epOut The OUT USB endpoint.
     */
    public OnlyKey(final UsbDeviceConnection conn, final UsbEndpoint epIn, final UsbEndpoint epOut) {
        this.conn = conn;
        this.epIn = epIn;
        this.epOut = epOut;
    }

    /**
     * Add a listener to be notified of OnlyKey events.
     *
     * @param listener The listener to add.
     */
    public void addListener(final OKListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify listeners on OnlyKey event.
     *
     * @param event The event object.
     */
    private void notifyListeners(final OKEvent event) {
        for (final OKListener l : listeners) {
            switch (event.getType()) {
                case ERROR:
                    l.okError(event);
                    break;
                case SET_INITIALIZED:
                    l.okSetInitialized(event);
                    break;
                case SET_LOCKED:
                    l.okSetLocked(event);
                    break;
                case SET_TIME:
                    l.okSetTime(event);
                    break;
                case MSG:
                    l.okMessage(event);
                    break;
                default:
                    throw new RuntimeException("Unknown event type!");
            }
        }
    }

    /**
     * Get a new {@link OnlyKey}.
     *
     * @param device  The OnlyKey USB device.
     * @param manager The USBManager.
     * @throws IOException Thrown on error configuring the OnlyKey USB device.
     */
    public static OnlyKey getOnlyKey(final UsbDevice device, final UsbManager manager)
            throws IOException {
        // get interface
        if (device.getInterfaceCount() < OnlyKey.OK_HID_INTERFACE) {
            throw new IOException("USB device does not have any interfaces!");
        }
        final UsbInterface intf = device.getInterface(OnlyKey.OK_HID_INTERFACE);

        // get input endpoint
        if (intf.getEndpointCount() < Math.max(OnlyKey.OK_INT_IN, OnlyKey.OK_INT_OUT)) {
            throw new IOException("Interface doesn't have two endpoints!");
        }
        final UsbEndpoint epIn = intf.getEndpoint(OnlyKey.OK_INT_IN);
        if (epIn.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            throw new IOException("Endpoint is not type INTERRUPT!");
        }
        //Log.d(TAG, "IN Endpoint: " + epIn.getEndpointNumber() + ", address: " + epIn.getAddress() +
        //        " Direction: " + (epIn.getDirection() == UsbConstants.USB_DIR_IN ? "In" : "Out"));

        // get output endpoint
        final UsbEndpoint epOut = intf.getEndpoint(OnlyKey.OK_INT_OUT);
        if (epOut.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            throw new IOException("Endpoint is not type INTERRUPT");
        }
        //Log.d(TAG, "OUT Endpoint: " + epOut.getEndpointNumber() + ", address: " + epOut.getAddress() +
        //        " Direction: " + (epOut.getDirection() == UsbConstants.USB_DIR_IN ? "In" : "Out"));

        // get connection
        final UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            throw new IOException("Could not open connection to USB device!");
        }
        Log.d(TAG, "Opened USB connection.");
        connection.claimInterface(intf, true);

        return new OnlyKey(connection, epIn, epOut);
    }

    /**
     * Watch for messages sent from the OnlyKey.
     */
    @Override
    public void run() {
        try {
            while (!isCancelledOrInterrupted()) {
                final UsbRequest in = new UsbRequest();
                in.initialize(conn, epIn);
                final ByteBuffer buffer = ByteBuffer.allocate(epIn.getMaxPacketSize());

                if (!in.queue(buffer, epIn.getMaxPacketSize()) && !isCancelledOrInterrupted()) {
                    throw new IOException("Error queuing request!");
                }

                final UsbRequest r = conn.requestWait(); // blocking
                if (r.equals(null)) {
                    throw new IOException("Error receiving data!");
                } else if (!r.equals(in)) {
                    throw new IOException("Received response not queued?");
                }

                processReceived(buffer.array(), r);
            }

            conn.close();

            Log.d(TAG, "Done.");
        } catch (IOException ioe) {
            notifyListeners(new OKEvent(this, OKEvent.OKEType.ERROR, ioe));
        }
    }

    /**
     * Process received data and notify as appropriate.
     *
     * @param data
     * @param request
     */
    private void processReceived(final byte[] data, final UsbRequest request) {
        final String inString = new String(data, 0, data.length, StandardCharsets.UTF_8);

        final boolean hasUninitialized = inString.contains("UNINITIALIZED");
        final boolean hasInitialized = inString.contains("INITIALIZED");
        final boolean hasUnlocked = inString.contains("UNLOCKED");
        final boolean hasLocked = inString.contains("LOCKED");

        if (hasUninitialized) {
            setInitialized(false);
        } else if (hasInitialized) {
            setInitialized(true);
        } else if (hasUnlocked) {
            setLocked(false);
        } else if (hasLocked) {
            setLocked(true);
        } else {
            //Log.d(TAG, "Received: " + new String(data));
            //Log.d(TAG, "Received: " + bytesToHex(data));
        }

        request.close();
    }

    private void setInitialized(final boolean value) {
        if (initialized == null || !initialized.equals(value)) {
            initialized = value;
            notifyListeners(new OKEvent(this, OKEvent.OKEType.SET_INITIALIZED, value));
            //Log.d(TAG, "Initialized? " + Boolean.toString(value));
        }
    }

    private void setLocked(final boolean value) {
        if (locked == null || !locked.equals(value)) {
            locked = value;
            notifyListeners(new OKEvent(this, OKEvent.OKEType.SET_LOCKED, value));
            //Log.d(TAG, "Locked? " + Boolean.toString(value));
        }
    }

    /**
     * Send bytes to the OnlyKey.
     *
     * @param toSend Byte sequence to send.
     * @throws IOException Thrown on error sending the byte array.
     */
    private void sendMessage(final byte[] toSend) throws IOException {
        final UsbRequest out = new UsbRequest();
        if (!out.initialize(conn, epOut)) {
            throw new IOException("Request could not initialize out request!");
        }
        final ByteBuffer b = ByteBuffer.allocate(epOut.getMaxPacketSize());

        b.put(toSend);

        if (!out.queue(b, epOut.getMaxPacketSize())) {
            throw new IOException("Error queuing request!");
        }

        final UsbRequest r = conn.requestWait();
        if (r.equals(null)) {
            throw new IOException("Error sending data!");
        } else if (!r.equals(out)) {
            throw new IOException("Send request != original?");
        }

        //Log.d(TAG, "Sent: "+bytesToHex(b.array()));
    }

    /**
     * Set the current time on the OnlyKey to the current system time.
     *
     * @throws IOException
     */
    public void setTime() throws IOException {
        // create packet
        final byte[] toSend = new byte[9];
        // add header
        System.arraycopy(header, 0, toSend, 0, header.length);
        // set message
        toSend[4] = MSG_SET_TIME;
        // copy in time
        System.arraycopy(getTime(), 0, toSend, 5, 4);

        sendMessage(toSend);

        //XXX: Figure out why the resourced doesn't work here.
        notifyListeners(new OKEvent(this, OKEvent.OKEType.SET_TIME));

        //Log.d(TAG, "Set time with " + bytesToHex(toSend));
    }

    /**
     * @return Current epoch type as 4 bytes big endian
     */
    private byte[] getTime() {
        final int unixTime = (int) (System.currentTimeMillis() / 1000);
        byte[] currTime = new byte[]{
                (byte) (unixTime >> 24),
                (byte) (unixTime >> 16),
                (byte) (unixTime >> 8),
                (byte) unixTime};

        return currTime;
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
