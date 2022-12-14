/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;
import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 原子类Accumulator和Addr分段抽象类
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /**
     * Contend消除伪共享
     */
    @sun.misc.Contended static final class Cell {
        // volatile修饰保证可见
        volatile long value;
        Cell(long x) { value = x; }
        // CAS更新值，直接调用Unsafe
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }
        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long valueOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset(ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * n>=NCPU就不会走到扩容逻辑，N一直是2的倍数，cells数组最大只能达到大于等于NCPU的最小2次方
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * 最初无竞争或有其它线程在创建cells数组时使用base更新值，有过竞争时使用cells更新值。
     * 最初无竞争是指一开始没有线程之间的竞争，但也有可能是多线程在操作，只是这些线程没有同时去更新base的值。
     * 有过竞争是指只要出现过竞争不管后面有没有竞争都使用cells更新值，规则是不同的线程hash到不同的cell上去更新，减少竞争
     */

    /**
     * cells数组，存储各个段的值
     */
    transient volatile Cell[] cells;

    /**
     * 最初无竞争时使用的，也算一个特殊的段
     */
    transient volatile long base;

    /**
     * 标记当前是否有线程在创建或扩容cells，或者在创建Cell
     * 通过CAS更新该值，相当于是一个锁
     */
    transient volatile int cellsBusy;

    /**
     * 只有本包使用
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     */
    final boolean casCellsBusy() {
        return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
    }

    /**
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int getProbe() {
        return UNSAFE.getInt(Thread.currentThread(), PROBE);
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * （1）如果cells数组未初始化，当前线程会尝试占有cellsBusy锁并创建cells数组；
     * （2）如果当前线程尝试创建cells数组时，发现有其它线程已经在创建了，就尝试更新base，如果成功就返回；
     * （3）通过线程的probe值找到当前线程应该更新cells数组中的哪个Cell；
     * （4）如果当前线程所在的Cell未初始化，就占有占有cellsBusy锁并在相应的位置创建一个Cell；
     * （5）尝试CAS更新当前线程所在的Cell，如果成功就返回，如果失败说明出现冲突；
     * （5）当前线程更新Cell失败后并不是立即扩容，而是尝试更新probe值后再重试一次；
     * （6）如果在重试的时候还是更新失败，就扩容；
     * （7）扩容时当前线程占有cellsBusy锁，并把数组容量扩大到两倍，再迁移原cells数组中元素到新数组中；
     * （8）cellsBusy在创建cells数组、创建Cell、扩容cells数组三个地方用到--加锁
     */
    final void longAccumulate(long x, LongBinaryOperator fn, boolean wasUncontended) {
        // 存储线程的probe值
        int h;
        // 获取probe 判断条件是为0说明未初始化
        if ((h = getProbe()) == 0) {
            //直接获取
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            // 都未初始化，肯定还不存在竞争激烈
            wasUncontended = true;
        }
        // 是否发生碰撞
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            // cells已经初始化过
            if ((as = cells) != null && (n = as.length) > 0) {
                // 当前线程所在的Cell未初始化
                if ((a = as[(n - 1) & h]) == null) {
                    // 当前无其它线程在创建或扩容cells，也没有线程在创建Cell
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 新建一个Cell，值为当前需要增加的值
                        Cell r = new Cell(x);   // Optimistically create
                        // 再次检测cellsBusy，并尝试更新它为1，相当于当前线程加锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            // 是否创建成功
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                // 重新获取cells，并找到当前线程hash到cells数组中的位置
                                // 这里一定要重新获取cells，因为as并不在锁定范围内
                                // 有可能已经扩容了，这里要重新获取
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    // 把上面新建的Cell放在cells的j位置处
                                    rs[j] = r;
                                    // 创建成功
                                    created = true;
                                }
                            } finally {
                                // 相当于释放锁
                                cellsBusy = 0;
                            }
                            // 创建成功了就返回
                            // 值已经放在新建的Cell里面了
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    // 标记当前未出现冲突
                    collide = false;
                }
                // 当前线程所在的Cell不为空，且更新失败了
                // 这里简单地设为true，相当于简单地自旋一次
                // 通过下面的语句修改线程的probe再重新尝试
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                // 再次尝试CAS更新当前线程所在Cell的值，如果成功了就返回
                else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                    break;
                // 如果cells数组的长度达到了CPU核心数，或者cells扩容了
                // 设置collide为false并通过下面的语句修改线程的probe再重新尝试
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                // 上上个elseif都更新失败了，且上个条件不成立，说明出现冲突了
                else if (!collide)
                    collide = true;
                // 明确出现冲突了，尝试占有锁，并扩容
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        // 检查是否有其它线程已经扩容过了
                        if (cells == as) {      // Expand table unless stale
                            // 新数组为原数组的两倍
                            Cell[] rs = new Cell[n << 1];
                            // 把旧数组元素拷贝到新数组中
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            // 重新赋值cells为新数组
                            cells = rs;
                        }
                    } finally {
                        // 释放锁
                        cellsBusy = 0;
                    }
                    // 已解决冲突
                    collide = false;
                    // 使用扩容后的新数组重新尝试
                    continue;                   // Retry with expanded table
                }
                // 更新失败或者达到了CPU核心数，重新生成probe，并重试
                h = advanceProbe(h);
            }
            // 未初始化过cells数组，尝试占有锁并初始化cells数组
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                // 是否初始化成功
                boolean init = false;
                try {                           // Initialize table
                    // 检测是否有其它线程初始化过
                    if (cells == as) {
                        // 新建一个大小为2的Cell数组
                        Cell[] rs = new Cell[2];
                        // 找到当前线程hash到数组中的位置并创建其对应的Cell
                        rs[h & 1] = new Cell(x);
                        // 赋值给cells数组
                        cells = rs;
                        // 初始化成功
                        init = true;
                    }
                } finally {
                    // 释放锁
                    cellsBusy = 0;
                }
                // 初始化成功直接返回
                // 因为增加的值已经同时创建到Cell中了
                if (init)
                    break;
            }
            // 如果有其它线程在初始化cells数组中，就尝试更新base
            // 如果成功了就返回
            else if (casBase(v = base, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                break;                          // Fall back on using base
        }
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn, boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (a.cas(v = a.value,
                               ((fn == null) ?
                                Double.doubleToRawLongBits
                                (Double.longBitsToDouble(v) + x) :
                                Double.doubleToRawLongBits
                                (fn.applyAsDouble
                                 (Double.longBitsToDouble(v), x)))))
                    break;
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            }
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            else if (casBase(v = base,
                             ((fn == null) ?
                              Double.doubleToRawLongBits
                              (Double.longBitsToDouble(v) + x) :
                              Double.doubleToRawLongBits
                              (fn.applyAsDouble
                               (Double.longBitsToDouble(v), x)))))
                break;                          // Fall back on using base
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long BASE;
    private static final long CELLSBUSY;
    private static final long PROBE;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            BASE = UNSAFE.objectFieldOffset(sk.getDeclaredField("base"));
            CELLSBUSY = UNSAFE.objectFieldOffset(sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            PROBE = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
