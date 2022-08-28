package com.collection.hashmap;

/**
 * 计算扩容采用最近N的2次方
 */
public class TableSizefor {
    public static final int MAXIMUM_CAPACITY = Integer.MAX_VALUE;
    public static void main(String[] args) {
        int num = 2<<2;
        System.out.println(num);
        num = 2<<3;
        System.out.println(num);
        num = 2<<4;
        System.out.println(num);
        num = 2<<5;
        System.out.println(num);
        num = 2<<6;
        System.out.println(num);
        num = 2<<7;
        System.out.println(num);
        num = 2<<8;
        System.out.println(num);
        num = 2<<9;
        System.out.println(num);

        System.out.println(tableSizeUP(998));
    }

    // 传入的初始容量往上取最近的2的n次方
    static final int tableSizeUP(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    // 传入的初始容量往下取最近的2的n次方
    static final int tableSizeDown(int cap) {
        return 0;
    }
}
