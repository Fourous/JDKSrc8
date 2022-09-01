package com.sync;

/**
 * Synchronized是非公平的
 */
public class SynchronizedUnfair {
    public static void sync(String s) {
        synchronized (SynchronizedUnfair.class) {
            System.out.println(s);
            try {
                // 这里设置为100
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new Thread(()->sync("Thread_1")).start();
        Thread.sleep(100);
        new Thread(()->sync("Thread_2")).start();
        Thread.sleep(100);
        new Thread(()->sync("Thread_3")).start();
        Thread.sleep(100);
        new Thread(()->sync("Thread_4")).start();
    }
}
