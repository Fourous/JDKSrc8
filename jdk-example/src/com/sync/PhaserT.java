package com.sync;

import java.util.concurrent.Phaser;
import java.util.stream.IntStream;

/**
 * 大任务可以分为多个阶段完成，且每个阶段的任务可以多个线程并发执行，但是必须上一个阶段的任务都完成了才可以执行下一个阶段的任务
 */
public class PhaserT {
    // 定义3个阶段的任务
    private static final int PHASER = 4;
    // 同时分三块跑，也就是多线程执行三个内容
    private static final int PARTIES = 3;

    public static void main(String[] args) {
        Phaser phaser = new Phaser(PARTIES) {
            @Override
            protected boolean onAdvance(int phase, int registeredParties) {
                System.out.println("============ phase " + phase + " finished ================");
                return super.onAdvance(phase, registeredParties);
            }
        };

        IntStream.range(0, PARTIES).forEach(i -> {
            new Thread(() -> {
                //
                IntStream.range(0, PHASER).forEach(j -> {
                    System.out.println(String.format("%s: phase: %d", Thread.currentThread().getName(), j));
                    phaser.arriveAndAwaitAdvance();
                });
            }, "Thread " + i).start();
        });
    }
}
