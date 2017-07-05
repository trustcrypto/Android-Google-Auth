package to.crp.android.oktimeset;

/**
 * Interface for classes wishing to be notified of {@link OnlyKey} events.
 */
public interface OKListener {
    /**
     * OnlyKey has had an error.
     *
     * @param event The event object.
     */
    abstract void okError(OKEvent event);

    /**
     * OnlyKey has a message for the user.
     *
     * @param event The event object.
     */
    abstract void okMessage(OKEvent event);

    /**
     * OnlyKey initialized state has changed.
     *
     * @param event The event object.
     */
    abstract void okSetInitialized(OKEvent event);

    /**
     * OnlyKey has set the device time.
     *
     * @param event The event object.
     */
    abstract void okSetTime(OKEvent event);

    /**
     * OnlyKey locked state has changed.
     *
     * @param event The event object.
     */
    abstract void okSetLocked(OKEvent event);
}
