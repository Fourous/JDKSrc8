package com.sync;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.stream.IntStream;

public class MyAQSImp {
    private static class Sync extends AbstractQueuedSynchronizer {
        @Override
        protected boolean tryAcquire(int acquire) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int release) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }
    }

    private final Sync sync = new Sync();

    public void lock() {
        sync.tryAcquire(1);
    }

    public void unlock() {
        sync.release(1);
    }

    private static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        MyAQSImp myAQSImp = new MyAQSImp();
        CountDownLatch countDownLatch = new CountDownLatch(100);
        IntStream.range(0, 100).forEach(i -> {
            new Thread(() -> {
                myAQSImp.lock();
                IntStream.range(0, 100).forEach(j -> {
                    count++;
                });
                myAQSImp.unlock();
                countDownLatch.countDown();
            }).start();
        });
        countDownLatch.await();
        System.out.println(count);
    }
}
