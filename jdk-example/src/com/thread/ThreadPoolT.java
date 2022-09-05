package com.thread;

import java.util.concurrent.*;
import java.util.stream.IntStream;

public class ThreadPoolT {
    public static void main(String[] args) {
        ExecutorService executorService = new ThreadPoolExecutor(5, 10, 1,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(5), Executors.defaultThreadFactory(),
                (r, executor) -> System.out.println(Thread.currentThread().getName()+ " discard task"));
        IntStream.range(0, 20).forEach(i->{
            executorService.execute(()->{
                try {
                    System.out.println(Thread.currentThread().getName() + "," + i + " running" + System.currentTimeMillis());
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        });
    }
}
