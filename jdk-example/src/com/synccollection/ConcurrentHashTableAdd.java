package com.synccollection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * ConcurrenthashMap
 */
public class ConcurrentHashTableAdd {
    private static int count = 0;
    private static ConcurrentHashMap concurrentHashMap = new ConcurrentHashMap();

    public static void main(String[] args) {
        IntStream.range(0, 100).forEach(i->{
            new Thread(()->{

            }, "Thread" + i).start();
        });
    }
}
