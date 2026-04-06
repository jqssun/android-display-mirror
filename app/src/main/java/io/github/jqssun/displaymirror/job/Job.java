package io.github.jqssun.displaymirror.job;

public interface Job {
    void start() throws YieldException;
}
