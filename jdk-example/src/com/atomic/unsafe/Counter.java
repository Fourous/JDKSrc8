package com.atomic.unsafe;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * JUC下面大量使用了CAS操作，它们的底层是调用的Unsafe的CompareAndSwapXXX()方法
 * 这种方式广泛运用于无锁算法，与java中标准的悲观锁机制相比，它可以利用CAS处理器指令提供极大的加速
 */
public class Counter {
    /**
     * 定义volatile的字段count，以便对它的修改所有线程都可见
     */
    private volatile int count = 0;

    private static long offset;
    private static Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            // 在类加载的时候获取count在类中的偏移地址
            offset = unsafe.objectFieldOffset(Counter.class.getDeclaredField("count"));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void increment() {
        int before = count;
        // 重试直到成功
        while (!unsafe.compareAndSwapInt(this, offset, before, before + 1)) {
            // 尝试更新之前获取到的count的值，如果它没有被其它线程更新过，则更新成功，否则不断重试直到成功为止
            before = count;
        }
    }

    public int getCount() {
        return count;
    }
}
