package com.atomic.unsafe;

import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * 利用反射可以拿到Unsafe来操作
 * Unsafe可以做很多操作
 * 1.实例化一个类
 * 2.修改任何私有字段的值
 * 3.抛出Check异常而不用在方法上签名
 * 4.堆外内存
 * 5.CAS方法
 * 6.unpark/park阻塞方法
 *
 */
public class CreateUnsafe {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException, InstantiationException, InterruptedException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);

        /**
         * 实例化一个类
         */
        User userUnsafe = (User) unsafe.allocateInstance(User.class);
        System.out.println(userUnsafe.age);

        /**
         * 修改任意私有字段的值
         */
        Field age = userUnsafe.getClass().getDeclaredField("age");
        unsafe.putInt(userUnsafe, unsafe.objectFieldOffset(age), 20);
        System.out.println(userUnsafe.age);

        /**
         * 不用在签名上面标注异常
         */
        unsafe.throwException(new IOException());

        /**
         * 使用堆外内存
         */
        OffHeapArray offHeapArray = new OffHeapArray(4);
        offHeapArray.set(0, 0);
        offHeapArray.set(1, 1);
        offHeapArray.set(2, 2);
        offHeapArray.set(3, 3);
        offHeapArray.set(2, 4);
        int sum = 0;
        for(int i=0;i< offHeapArray.size();i++) {
            sum+=offHeapArray.get(i);
        }
        System.out.println(sum);
        // 使用堆外内存一定要free掉
        offHeapArray.freeMemory();

        /**
         * CompareAndSwap操作
         */
        Counter counter = new Counter();
        ExecutorService threadPool = Executors.newFixedThreadPool(100);
        IntStream.range(0, 100)
                .forEach(i->threadPool.submit(()->IntStream.range(0, 10000)
                        .forEach(j->counter.increment())));
        threadPool.shutdown();
        Thread.sleep(2000);
        System.out.println(counter.getCount());

        /**
         * 当一个线程正在等待某个操作时，JVM调用Unsafe的park()方法来阻塞此线程
         * 当阻塞中的线程需要再次运行时，JVM调用Unsafe的unpark()方法来唤醒此线程
         * LockSupport.park()/unpark() 就是调用此方法
         */
    }

    static class User{
        private String name;
        private int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
