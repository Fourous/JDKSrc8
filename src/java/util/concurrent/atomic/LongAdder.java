package java.util.concurrent.atomic;
import java.io.Serializable;

/**
 * LongAdder是java8中新增的原子类，在多线程环境中，它比AtomicLong性能要高出不少，特别是写多的场景
 * LongAdder的原理是，在最初无竞争时，只更新base的值，当有多线程竞争时通过分段的思想，让不同的线程更新不同的段，最后把这些段相加就得到了完整的LongAdder存储的值
 */
public class LongAdder extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    /**
     * 默认构造是给的0
     */
    public LongAdder() {
    }

    /**
     * 添加元素
     * （1）最初无竞争时只更新base；
     * （2）直到更新base失败时，创建cells数组；
     * （3）当多个线程竞争同一个Cell比较激烈时，可能要扩容
     */
    public void add(long x) {
        /**
         * Striped64中的cells属性
         */
        Cell[] as;
        /**
         * B Striped64中的base属性
         * V 当前线程hash到的Cell中存储的值
         */
        long b, v;
        /**
         * cells的长度减1，hash时作为掩码使用
         */
        int m;
        /**
         * 当前线程hash到的Cell
         */
        Cell a;

        // 条件1：cells不为空，说明出现过竞争，cells已经创建
        // 条件2：cas操作base失败，说明其它线程先一步修改了base，正在出现竞争
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            // true表示当前竞争还不激烈
            // false表示竞争激烈，多个线程hash到同一个Cell，可能要扩容
            boolean uncontended = true;
            // 条件1：cells为空，说明正在出现竞争，上面是从条件2过来的
            // 条件2：应该不会出现
            // 条件3：当前线程所在的Cell为空，说明当前线程还没有更新过Cell，应初始化一个Cell
            // 条件4：更新当前线程所在的Cell失败，说明现在竞争很激烈，多个线程hash到了同一个Cell，应扩容
            if (as == null || (m = as.length - 1) < 0 ||
                // getProbe()方法返回的是线程中的threadLocalRandomProbe字段
                // 它是通过随机数生成的一个值，对于一个确定的线程这个值是固定的
                // 除非刻意修改它
                (a = as[getProbe() & m]) == null ||
                !(uncontended = a.cas(v = a.value, v + x)))
                // 调用Striped64中的方法处理
                longAccumulate(x, null, uncontended);
        }
    }

    /**
     * Equivalent to {@code add(1)}.
     */
    public void increment() {
        add(1L);
    }

    /**
     * Equivalent to {@code add(-1)}.
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * sum()方法是获取LongAdder中真正存储的值的大小，通过把base和所有段相加得到
     * 可以看到，这里相加没有任何保护Cell操作，说明就算在计算过程中有修改，也不会加入进去
     * 说明LongAdder不是强一致性的，它是最终一致性
     */
    public long sum() {
        Cell[] as = cells; Cell a;
        // sum初始等于base
        long sum = base;
        // 如果cells不为空
        if (as != null) {
            // 遍历Cell
            for (int i = 0; i < as.length; ++i) {
                // 如果所在的Cell不为空，就把它的value累加到sum中
                if ((a = as[i]) != null)
                    sum += a.value;
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    public void reset() {
        Cell[] as = cells; Cell a;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null)
                    a.value = 0L;
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     *
     * @return the sum
     */
    public long sumThenReset() {
        Cell[] as = cells; Cell a;
        long sum = base;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[i]) != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     * @return the String representation of the {@link #sum}
     */
    public String toString() {
        return Long.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     *
     * @return the sum
     */
    public long longValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a narrowing
     * primitive conversion.
     */
    public int intValue() {
        return (int)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a widening primitive conversion.
     */
    public float floatValue() {
        return (float)sum();
    }

    /**
     * Returns the {@link #sum} as a {@code double} after a widening
     * primitive conversion.
     */
    public double doubleValue() {
        return (double)sum();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     * @serial include
     */
    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Return a {@code LongAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code LongAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     *
     * @return a {@link SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
