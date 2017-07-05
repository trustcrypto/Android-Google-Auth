package to.crp.android.oktimeset;

public abstract class RunnableImpl implements Runnable {

    private boolean cancelled = false;

    public void cancel() {
        this.cancelled = true;
    }

    public void interrupt() {
        Thread.currentThread().interrupt();
    }

    public boolean isCancelledOrInterrupted() {
        return Thread.currentThread().isInterrupted() || cancelled;
    }
}
