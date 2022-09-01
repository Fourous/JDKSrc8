package com.atomic;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * 高性能原子类，是java8中增加的原子类，它们使用分段的思想，把不同的线程hash到不同的段上去更新，最后再把这些段的值相加得到最终的值
 */
public class TestNewAtomic {
    public static void main(String[] args) {
        LongAdder longAdder = new LongAdder();
        longAdder.increment();
        longAdder.add(666);
        System.out.println(longAdder.sum());

        LongAccumulator longAccumulator = new LongAccumulator((left, right) -> left + right * 2, 666);
        longAccumulator.accumulate(1);
        longAccumulator.accumulate(3);
        longAccumulator.accumulate(-4);
        System.out.println(longAccumulator.get());
    }
}
