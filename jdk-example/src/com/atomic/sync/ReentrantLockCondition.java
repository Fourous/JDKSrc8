package com.atomic.sync;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ReentrantLock的条件锁
 */
public class ReentrantLockCondition {
    public static void main(String[] args) throws InterruptedException {
        // 默认非公平
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();

        new Thread(() -> {
            try {
                lock.lock();
                System.out.println("before await");
                condition.await();
                // 指定A线程的业务
                Thread.sleep(1000);
                System.out.println("after await");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }).start();

        // 先让第一个线程获取到锁
        Thread.sleep(1000);

        new Thread(() -> {
            try {
                lock.lock();
                System.out.println("condition before");
                // 执行B线程的业务
                Thread.sleep(2000);
                condition.signal();
                System.out.println("condition after");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }).start();
    }
}
