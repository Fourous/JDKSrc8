package com.sync;

import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

/**
 * Volatile是不具备原子性的，除非操作本身就是原子的，最典型就是count++不是原子操作
 */
public class VolatileNoAtomic {
    private static volatile int count = 0;

    public static void increase() {
        count++;
    }

    /**
     * 这里是达不到1000 * 100 的目标的，因为非原子操作数据一定会不统一
     *
     * @param args
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(100);
        IntStream.range(0, 100).forEach(i -> {
            new Thread(() -> {
                IntStream.range(0, 1000).forEach(j -> increase());
                countDownLatch.countDown();
            }).start();
        });
        countDownLatch.await();
        System.out.println(count);
    }
}
