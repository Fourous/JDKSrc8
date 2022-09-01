package com.sync;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

/**
 * 如果自己实现锁需要准备
 * 共享变量: 也可以一个原子类，只要保证线程的可控更新即可，不过既然用了Unsafe，不如直接CAS
 * Unsafe: 实现CAS更新 阻塞线程，唤醒线程
 * 阻塞队列: 未获取到锁的线程添加进队列
 */
public class MyLock {
    /**
     * 共享变量
     */
    private volatile int state;

    /**
     * 利用Unsafe方法实现CAS park unpark
     */
    private static Unsafe unsafe;

    /**
     * 队尾tail偏移量
     */
    private static volatile long tailOffset;

    /**
     * state偏移量
     */
    private static volatile long stateOffset;

    /**
     * 队列头：因为只有一个线程会获取到锁，所以头部更新不需要并发
     */
    private volatile Node head;

    /**
     * 队列尾：未拿到锁的线程从尾部添加
     */
    private volatile Node tail;

    static final Node EMPTY = new Node();

    public MyLock(){
        head = tail = EMPTY;
    }
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            tailOffset = unsafe.objectFieldOffset(MyLock.class.getDeclaredField("tail"));
            stateOffset = unsafe.objectFieldOffset(MyLock.class.getDeclaredField("state"));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void lock() {
        if (compareAndSetState(0, 1)) {
            return;
        }
        Node node = enqueue();
        Node prev = node.prev;
        while (node.prev != null || compareAndSetState(0, 1)) {
            unsafe.park(false, 0L);
        }
        head = node;
        // 直接置空GC
        node.thread = null;
        node.prev = null;
        node.next = null;
    }

    public void unlock() {
        state = 0;
        Node next = head.next;
        if (next != null) {
            unsafe.unpark(next.thread);
        }
    }

    /**
     * 定义等待队列,采用链表实现
     */
    private static class Node {
        private Thread thread;
        Node prev;
        Node next;

        public Node() {
        }

        public Node(Thread thread, Node prev) {
            this.thread = thread;
            this.prev = prev;
        }
    }

    /**
     * 定义阻塞队列过程中，
     */
    public boolean compareAndSwapTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS更新State
     */
    public boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /**
     * 将线程添加至尾节点
     */
    private Node enqueue() {
        while (true) {
            // 获取尾节点
            Node t = tail;
            // 构造新节点
            Node node = new Node(Thread.currentThread(), t);
            if (compareAndSwapTail(t, node)) {
                // 并发抢占尾节点并更新尾节点
                t.next = node;
                return node;
            }
        }
    }

    private static int count = 0;
    public static void main(String[] args) throws InterruptedException {
        MyLock lock = new MyLock();
        CountDownLatch countDownLatch = new CountDownLatch(10);
        IntStream.range(0, 10).forEach(i -> new Thread(() -> {
            lock.lock();
            try {
                IntStream.range(0, 10).forEach(j -> {
                    count++;
                });
            } finally {
                lock.unlock();
            }
            countDownLatch.countDown();
        }, "tt-" + i).start());
        countDownLatch.await();
        System.out.println(count);
    }
}
