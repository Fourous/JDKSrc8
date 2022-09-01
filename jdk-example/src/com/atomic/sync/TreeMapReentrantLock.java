package com.atomic.sync;

import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 用读写锁实现线程安全的TreeMap
 */
public class TreeMapReentrantLock {
    private final TreeMap<String, Object> treeMap = new TreeMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();
    private final Lock readLock = lock.readLock();

    public Object get(String s) {
        try {
            readLock.lock();
            return treeMap.get(s);
        } finally {
            readLock.unlock();
        }
    }

    public Object put(String s, Object o) {
        try {
            writeLock.lock();
            return treeMap.put(s, o);
        } finally {
            writeLock.unlock();
        }
    }
}
