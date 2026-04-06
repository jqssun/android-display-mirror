package io.github.jqssun.displaymirror.job;

public class BindAllExternalInputToDisplay implements Job {
    private final AcquireShizuku acquireShizuku = new AcquireShizuku();
    private final int displayId;

    public BindAllExternalInputToDisplay(int displayId) {
        this.displayId = displayId;
    }

    @Override
    public void start() throws YieldException {
        acquireShizuku.start();
        if (!acquireShizuku.acquired) {
            return;
        }
        InputRouting.bindAllExternalInputToDisplay(displayId);
    }
}
