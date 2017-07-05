package to.crp.oktimeset;

/**
 * Created by zbrowning on 5/20/2017.
 */
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
