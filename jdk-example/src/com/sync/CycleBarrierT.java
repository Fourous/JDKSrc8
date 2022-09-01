package com.sync;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;

/**
 * 回环栅栏使用
 * 使用一个CyclicBarrier使得三个线程保持同步，当三个线程同时到达 cyclicBarrier.await();处大家再一起往下运行
 */
public class CycleBarrierT {
    public static void main(String[] args) {
        CyclicBarrier cyclicBarrier = new CyclicBarrier(3);
        IntStream.range(0,3).forEach(i->{
            new Thread(()->{
                try {
                    System.out.println("Thread--" + i + " logic before");
//                    cyclicBarrier.reset();
                    cyclicBarrier.await();
                    System.out.println("Thread--" + i + " logic after");
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }
}
