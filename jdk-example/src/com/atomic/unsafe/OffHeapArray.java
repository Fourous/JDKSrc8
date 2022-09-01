package com.atomic.unsafe;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * 使用堆外内存
 * 如果进程在运行过程中JVM上的内存不足了，会导致频繁的进行GC。理想情况下，我们可以考虑使用堆外内存，这是一块不受JVM管理的内存
 * 使用Unsafe的allocateMemory()我们可以直接在堆外分配内存，但是这个内存不受JVM管理，因此我们要调用freeMemory()方法手动释放它
 */
public class OffHeapArray {
    private static final int INT = 4;
    private long size;
    private long address;

    private static Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public OffHeapArray(long size) {
        this.size = size;
        // 参数字节数
        address = unsafe.allocateMemory(size * INT);
    }

    public int get(long i) {
        return unsafe.getInt(address + i * INT);
    }

    public void set(long i, int value) {
        unsafe.putInt(address + i * INT, value);
    }

    public long size() {
        return size;
    }

    public void freeMemory() {
        unsafe.freeMemory(address);
    }
}
