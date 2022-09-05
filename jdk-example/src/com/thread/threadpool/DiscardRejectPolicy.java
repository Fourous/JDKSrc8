package com.thread.threadpool;

public class DiscardRejectPolicy implements RejectPolicy {
    @Override
    public void reject(Runnable task, MyThread myThread) {
        System.out.println("discard current Thread");
    }
}
