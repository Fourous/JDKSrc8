package com.dealock;

public class DeadLockT {
    private static Object lock1 = new Object();
    private static Object lock2 = new Object();

    public static void main(String[] args) {
        new Thread(() -> {
            synchronized (lock1) {
                try {
                    System.out.println("Thread1 start");
                    Thread.sleep(50000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            synchronized (lock2) {
                System.out.println("Thread1 end");
            }
        }).start();

        new Thread(() -> {
            synchronized (lock2) {
                try {
                    System.out.println("Thread2 start");
                    Thread.sleep(50000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            synchronized (lock1) {
                System.out.println("Thread2 end");
            }
        }).start();

    }
}
