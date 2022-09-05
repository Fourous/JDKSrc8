package com.thread;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class ThreadPoolFuture {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futureList = new ArrayList<>();

        IntStream.range(0, 20).forEach(i -> {
            Future<Integer> future = threadPool.submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("return " + i);
                return i;
            });
            futureList.add(future);
        });
        int sum = 0;
        for (Future<Integer> future : futureList) {
            sum += future.get();
        }
        System.out.println("sum = " + sum);
    }
}
