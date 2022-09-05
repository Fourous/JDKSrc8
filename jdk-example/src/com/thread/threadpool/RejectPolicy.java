package com.thread.threadpool;

public interface RejectPolicy {
    public void reject(Runnable task, MyThread myThread);
}
