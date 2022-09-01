package com.atomic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * 基础原子类型示例
 */
public class TestAtomicReference {
    public static void main(String[] args) {
        AtomicInteger atomicInteger = new AtomicInteger(1);
        atomicInteger.incrementAndGet();
        atomicInteger.getAndIncrement();
        atomicInteger.compareAndSet(3, 666);
        System.out.println(atomicInteger.get());

        AtomicStampedReference atomicStampedReference = new AtomicStampedReference(1, 1);
        atomicStampedReference.compareAndSet(1, 2,1,3);
        atomicStampedReference.compareAndSet(2,666,3,5);
        System.out.println(atomicStampedReference.getReference());
        System.out.println(atomicStampedReference.getStamp());
    }
}
