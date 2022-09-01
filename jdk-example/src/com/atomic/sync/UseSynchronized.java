package com.atomic.sync;

/**
 * synchronized使用的五种方式
 *
 * 在方法上使用synchronized的时候要注意，会隐式传参，分为静态方法和非静态方法
 * 静态方法上的隐式参数为当前类对象，非静态方法上的隐式参数为当前实例this
 *
 * synchronized只有锁同一个对象他们之间的代码才是同步的
 */
public class UseSynchronized {
    public static final Object lock = new Object();

    // 锁的是UseSynchronized.class
    public static synchronized void sync1() {}

    public static void sync2() {
        // 锁的是UseSynchronized.class
        synchronized (UseSynchronized.class) {
        }
    }

    // 锁的是当前实例this
    public synchronized void sync3() {}

    // 锁的是当前实例this
    public void sync4() {
        synchronized (this) {}
    }

    // 锁的是指定对象lock
    public void sync5() {
        synchronized (lock) {}
    }

}
