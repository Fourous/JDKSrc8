package com.atomic.sync;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * 流量控制-用semaphore控制
 */
public class SemaphoreSec {
    public static final Semaphore semaphore = new Semaphore(100);
    public static final AtomicInteger failCount = new AtomicInteger();
    public static final AtomicInteger successCount = new AtomicInteger();

    public static void main(String[] args) {
        IntStream.range(0, 1000).forEach(i -> {
            new Thread(() -> sec()).start();
        });
    }

    public static boolean sec() {
        if (!semaphore.tryAcquire()) {
            System.out.println("failCount: " + failCount.incrementAndGet());
            return false;
        }
        try {
            Thread.sleep(100);
            System.out.println("successCount: " + successCount.incrementAndGet());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
        System.out.println(failCount.get() + successCount.get());
        System.out.println(successCount.get());
        return true;
    }
}
