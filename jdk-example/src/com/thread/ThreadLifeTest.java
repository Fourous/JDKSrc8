package com.thread;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * IDEA用RUN模式jstack跑，Debug会导致都是waiting
 */
public class ThreadLifeTest {
    public static void main(String[] args) {
        Object object = new Object();
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        new Thread(() -> {
            synchronized (object) {
                try {
                    System.out.println("thread1 waiting");
                    object.wait();
//                    object.wait(50000);
                    System.out.println("thread1 after waiting");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Thread1").start();

        new Thread(() -> {
            synchronized (object) {
                try {
                    System.out.println("thread2 notify");
                    // notify之后当前线程并不会释放锁，只是被notify的线程从等待队列进入同步队列
                    object.notify();
                    Thread.sleep(50000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Thread2").start();

        new Thread(() -> {
            lock.lock();
            System.out.println("thread3 waiting");
            try {
                condition.await();
                System.out.println("thread3 after waiting");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Thread3").start();

        new Thread(() -> {
            lock.lock();
            System.out.println("thread4");
            // signal之后当前线程并不会释放锁，只是被signal的线程从等待队列进入同步队列
            condition.signal();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }, "Thread4").start();
    }
}
