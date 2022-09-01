package com.atomic;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * 原子更新数组中的元素，可以更新数组中指定索引位置的元素
 */
public class TestAtomicReferenceArray {
    public static void main(String[] args) {
        AtomicIntegerArray atomicIntegerArray = new AtomicIntegerArray(10);
        atomicIntegerArray.getAndIncrement(0);
        atomicIntegerArray.getAndAdd(1, 666);
        atomicIntegerArray.incrementAndGet(2);
        atomicIntegerArray.addAndGet(3, 666);
        atomicIntegerArray.compareAndSet(4, 0, 666);

        System.out.println(atomicIntegerArray.get(0));
        System.out.println(atomicIntegerArray.get(1));
        System.out.println(atomicIntegerArray.get(2));
        System.out.println(atomicIntegerArray.get(3));
        System.out.println(atomicIntegerArray.get(4));
        System.out.println(atomicIntegerArray.get(5));

    }
}
