package com.thread;

import java.sql.Time;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

// 定时任务的执行
public class ThreadPoolScheduled {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ScheduledThreadPoolExecutor threadPoolScheduled = new ScheduledThreadPoolExecutor(5);
        System.out.println("start: " + System.currentTimeMillis());

        // 执行一个无返回值任务，5秒后执行，只执行一次
        threadPoolScheduled.schedule(()->{
            System.out.println("schedule1: " + System.currentTimeMillis());
        }, 5, TimeUnit.SECONDS);

        // 执行一个有返回值任务，5秒后执行，只执行一次
        ScheduledFuture<String> scheduledFuture = threadPoolScheduled.schedule(()->{
            System.out.println("schedule2: " + System.currentTimeMillis());
            return "schedule2";
        },5, TimeUnit.SECONDS);
        System.out.println(scheduledFuture.get() + System.currentTimeMillis());

        // 按固定频率执行一个任务，每2秒执行一次，1秒后执行
        // 任务开始时的2秒后
        threadPoolScheduled.scheduleAtFixedRate(()->{
            System.out.println("schedule3: " + System.currentTimeMillis());
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        }, 1, 2, TimeUnit.SECONDS);

        // 按固定延时执行一个任务，每延时2秒执行一次，1秒执行
        // 任务结束时的2秒后
        threadPoolScheduled.scheduleWithFixedDelay(()->{
            System.out.println("schedule4: " + System.currentTimeMillis());
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        }, 1, 2, TimeUnit.SECONDS);
    }
}
