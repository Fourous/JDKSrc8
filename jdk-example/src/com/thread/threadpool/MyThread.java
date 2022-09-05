package com.thread.threadpool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class MyThread implements Executor {
    /**
     * 线程池名字
     */
    private String name;
    /**
     * 线程序列号
     */
    private AtomicInteger sequence = new AtomicInteger(0);
    /**
     * 核心线程
     */
    private int coreSize;
    /**
     * 最大线程个数
     */
    private int maxSize;
    /**
     * 阻塞队列
     */
    private BlockingQueue<Runnable> taskQueue;
    /**
     * 当前正在运行的线程数
     */
    private AtomicInteger runningCount = new AtomicInteger(0);
    /**
     * 任务队列
     */
    private RejectPolicy rejectPolicy;

    @Override
    public void execute(Runnable task) {
        // 正在运行的线程数
        int count = runningCount.get();
        if (count < coreSize) {
            if (addWorker(task, true)) return;
        }
        // 添加核心线程失败，则进行下面逻辑
        // 如果达到了核心线程数，先尝试让任务入队
        // 这里之所以使用offer()，是因为如果队列满了offer()会立即返回false
        if (taskQueue.offer(task)) {

        } else {
            if (!addWorker(task, false)) {
                // 如果添加非核心线程失败，则执行拒绝策略
                rejectPolicy.reject(task, this);
            }
        }
    }

    // 添加线程进线程池
    private boolean addWorker(Runnable newTask, boolean core) {
        // 自旋判断是否可以创建线程
        for (; ; ) {
            int count = runningCount.get();
            int max = core ? coreSize : maxSize;
            if (count > max) return false;
            if (runningCount.compareAndSet(count, count + 1)) {
                String threadName = (core ? "core_thread" : "thread") + name + sequence.incrementAndGet();
                new Thread(() -> {
                    System.out.println("thread name " + Thread.currentThread().getName());
                    Runnable task = newTask;
                    while (task != null || (task = getTask()) != null) {
                        try {
                            task.run();
                        } finally {
                            // 任务执行完成，置为空
                            task = null;
                        }
                    }
                }, threadName).start();
                break;
            }
        }
        return true;
    }

    // 从阻塞队列获取，如果为null，结束当前线程
    private Runnable getTask() {
        try {
            // take()方法会一直阻塞直到取到任务为止
            return taskQueue.take();
        } catch (InterruptedException e) {
            runningCount.decrementAndGet();
            return null;
        }
    }

    public MyThread(String name, int coreSize, int maxSize, BlockingQueue<Runnable> taskQueue, RejectPolicy rejectPolicy) {
        this.name = name;
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        this.taskQueue = taskQueue;
        this.rejectPolicy = rejectPolicy;
    }

    public static void main(String[] args) {
        Executor threadPool = new MyThread("threadT", 5, 10,
                new ArrayBlockingQueue<>(15), new DiscardRejectPolicy());
        AtomicInteger atomicInteger = new AtomicInteger(0);
        IntStream.range(0, 100).forEach(i -> {
            threadPool.execute(() -> {
                try {
                    Thread.sleep(100);
                    System.out.println("running " + System.currentTimeMillis() + ":" + atomicInteger.incrementAndGet());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });
    }
}
