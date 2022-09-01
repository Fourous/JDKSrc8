package com.atomic.unsafe;

import java.util.stream.IntStream;

/**
 * 伪共享，当多线程修改互相独立的变量时，如果这些变量共享同一个缓存行，就会无意中影响彼此的性能，这就是伪共享
 */
public class FalseSharingTest {
    public static void main(String[] args) throws InterruptedException {
        testPointer(new Pointer());
    }

    private static void testPointer(Pointer pointer) throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread threadX = new Thread(()->{
            IntStream.range(0, 100000000).forEach(i->{
                pointer.x++;
            });
        });

        Thread threadY = new Thread(()->{
            IntStream.range(0, 100000000).forEach(i->{
                pointer.y++;
            });
        });
        threadX.start();
        threadY.start();
        threadX.join();
        threadY.join();
        System.out.println(System.currentTimeMillis()-start);
        System.out.println(pointer);
    }

    /**
     * 设置为long变量，每次添加X时候会让Y失效
     */
    static class Pointer{
        volatile long x;
        // 加入这一行和不加这一行，时间上差别5倍左右
        long p1,p2,p3,p4,p5,p6,p7;
        volatile long y;

        @Override
        public String toString() {
            return "Pointer{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }
}
