package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.LockSupport;

/**
 * 写模式、读模式、乐观读模式
 * 相比于ReentrantLock新增了一个乐观读模式
 */
public class StampedLock implements java.io.Serializable {

    private static final long serialVersionUID = -6001602636862214147L;

    // 计算空闲CPU
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** Maximum number of retries before enqueuing on acquisition */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 0;

    /** Maximum number of retries before blocking at head on acquisition */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 0;

    /** Maximum number of retries before re-blocking */
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 0;

    /** The period for yielding when waiting for overflow spinlock */
    private static final int OVERFLOW_YIELD_RATE = 7; // must be power 2 - 1

    // 读线程的个数占有低7位
    private static final int LG_READERS = 7;
    // 读线程个数每次增加的单位
    private static final long RUNIT = 1L;
    // 写线程个数所在的位置
    private static final long WBIT  = 1L << LG_READERS;
    // 读线程个数所在的位置
    private static final long RBITS = WBIT - 1L;
    // 最大读线程个数
    private static final long RFULL = RBITS - 1L;
    // 读线程个数和写线程个数的掩码
    private static final long ABITS = RBITS | WBIT;
    // 读线程个数的反数，高25位全部为1
    private static final long SBITS = ~RBITS;
    // state的初始值
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled acquire methods so caller can throw IE
    private static final long INTERRUPTED = 1L;

    // Values for node status; order matters
    private static final int WAITING   = -1;
    private static final int CANCELLED =  1;

    // Modes for nodes (int not boolean to allow arithmetic)
    private static final int RMODE = 0;
    private static final int WMODE = 1;

    /**
     * 队列中的节点，类似于AQS队列中的节点，可以看到它组成了一个双向链表，内部维护着阻塞的线程
     */
    static final class WNode {
        // 前一个节点
        volatile WNode prev;
        // 后一个节点
        volatile WNode next;
        // 读线程所用的链表（实际是一个栈结果）
        volatile WNode cowait;
        // 阻塞的线程
        volatile Thread thread;
        // 状态 -- 等待/取消
        volatile int status;
        // 读模式还是写模式
        final int mode;
        WNode(int m, WNode p) { mode = m; prev = p; }
    }

    // CLH队列的头节点
    private transient volatile WNode whead;

    // CLH队列尾节点
    private transient volatile WNode wtail;

    // views
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;

    // 存储着当前的版本号，类似于AQS的状态变量state
    private transient volatile long state;
    /** extra reader count when state read count saturated */
    private transient int readerOverflow;

    // 默认ORIGIN 256 1 0000 0000
    // 也就是初始版本号
    public StampedLock() {
        state = ORIGIN;
    }

    /**
     * 获取写锁
     */
    public long writeLock() {
        // ABITS = 255 = 1111 1111
        // WBITS = 128 = 1000 0000
        // state与ABITS如果等于0，尝试原子更新state的值加WBITS
        // 如果成功则返回更新的值，如果失败调用acquireWrite()方法
        long s, next;
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : acquireWrite(false, 0L));
    }

    /**
     * Exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryWriteLock() {
        long s, next;
        return ((((s = state) & ABITS) == 0L &&
                 U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ?
                next : 0L);
    }

    /**
     *
     */
    public long tryWriteLock(long time, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            if ((next = tryWriteLock()) != 0L) return next;
            if (nanos <= 0L) return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L) deadline = 1L;
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED) return next;
        }
        throw new InterruptedException();
    }

    /**
     * Exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * 获取读锁
     * 获取读锁的时候先看看现在有没有其它线程占用着写锁，如果没有的话再检测读锁被获取的次数有没有达到最大
     * 如果没有的话直接尝试获取一次读锁，如果成功了直接返回版本号，如果没成功就调用acquireRead()排队
     */
    public long readLock() {
        long s = state, next;  // bypass acquireRead on common uncontended case
        // 没有写锁占用，并且读锁被获取的次数未达到最大值
        // 尝试原子更新读锁被获取的次数加1
        // 如果成功直接返回，如果失败调用acquireRead()方法
        return ((whead == wtail && (s & ABITS) < RFULL &&
                 U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) ?
                next : acquireRead(false, 0L));
    }

    /**
     * Non-exclusively acquires the lock if it is immediately available.
     *
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    public long tryReadLock() {
        for (;;) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == WBIT)
                return 0L;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                    return next;
            }
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
    }

    /**
     * Non-exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            if ((m = (s = state) & ABITS) != WBIT) {
                if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * Non-exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long readLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() &&
            (next = acquireRead(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * 乐观锁尝试
     * 如果没有写锁，就返回state的高25位，这里把写所在位置一起返回了，是为了后面检测数据有没有被写过
     */
    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * 检查版本是否一致
     */
    public boolean validate(long stamp) {
        // 强制加入内存屏障，刷新数据
        U.loadFence();
        // 检测两者的版本号是否一致，与SBITS与操作保证不受读操作的影响
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * 写锁的释放
     */
    public void unlockWrite(long stamp) {
        WNode h;
        // 检查版本号对不对
        if (state != stamp || (stamp & WBIT) == 0L) throw new IllegalMonitorStateException();
        // 这行代码实际有两个作用：
        // 1. 更新版本号加1
        // 2. 释放写锁
        // stamp + WBIT实际会把state的第8位置为0，也就相当于释放了写锁
        // 同时会进1，也就是高24位整体加1了
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;
        // 如果头节点不为空，并且状态不为0，调用release方法唤醒它的下一个节点
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    /**
     * 读锁的释放
     * 将state的低7位减1，当减为0的时候说明完全释放了读锁，就唤醒下一个排队的线程
     */
    public void unlockRead(long stamp) {
        long s, m; WNode h;
        for (;;) {
            // 检查版本号
            if (((s = state) & SBITS) != (stamp & SBITS) || (stamp & ABITS) == 0L || (m = s & ABITS) == 0L || m == WBIT)
                throw new IllegalMonitorStateException();
            // 读线程个数正常
            if (m < RFULL) {
                // 释放一次读锁
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    // 如果读锁全部都释放了，且头节点不为空且状态不为0，唤醒它的下一个节点
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                // 读线程个数溢出检测
                break;
        }
    }

    /**
     * If the lock state matches the given stamp, releases the
     * corresponding mode of the lock.
     *
     * @param stamp a stamp returned by a lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    public void unlock(long stamp) {
        long a = stamp & ABITS, m, s; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) break;
            else if (m == WBIT) {
                if (a != m) break;
                state = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return;
            }
            else if (a == 0L || a >= WBIT) break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L) return;
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, returns it.  Or, if a read lock, if the write lock is
     * available, releases the read lock and returns a write stamp.
     * Or, if an optimistic read, returns a write stamp only if
     * immediately available. This method returns zero in all other
     * cases.
     *
     * @param stamp a stamp
     * @return a valid write stamp, or zero on failure
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT))
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                return stamp;
            }
            else if (m == RUNIT && a != 0L) {
                if (U.compareAndSwapLong(this, STATE, s,
                                         next = s - RUNIT + WBIT))
                    return next;
            }
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp, performs one of
     * the following actions. If the stamp represents holding a write
     * lock, releases it and obtains a read lock.  Or, if a read lock,
     * returns it. Or, if an optimistic read, acquires a read lock and
     * returns a read stamp only if immediately available. This method
     * returns zero in all other cases.
     *
     * @param stamp a stamp
     * @return a valid read stamp, or zero on failure
     */
    public long tryConvertToReadLock(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT))
                        return next;
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = next = s + (WBIT + RUNIT);
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a != 0L && a < WBIT)
                return stamp;
            else
                break;
        }
        return 0L;
    }

    /**
     * If the lock state matches the given stamp then, if the stamp
     * represents holding a lock, releases it and returns an
     * observation stamp.  Or, if an optimistic read, returns it if
     * validated. This method returns zero in all other cases, and so
     * may be useful as a form of "tryUnlock".
     *
     * @param stamp a stamp
     * @return a valid optimistic read stamp, or zero on failure
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a = stamp & ABITS, m, s, next; WNode h;
        U.loadFence();
        for (;;) {
            if (((s = state) & SBITS) != (stamp & SBITS))
                break;
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                return s;
            }
            else if (m == WBIT) {
                if (a != m)
                    break;
                state = next = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            }
            else if (a == 0L || a >= WBIT)
                break;
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return next & SBITS;
                }
            }
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     *
     * @return {@code true} if the lock was held, else false
     */
    public boolean tryUnlockWrite() {
        long s; WNode h;
        if (((s = state) & WBIT) != 0L) {
            state = (s += WBIT) == 0L ? ORIGIN : s;
            if ((h = whead) != null && h.status != 0)
                release(h);
            return true;
        }
        return false;
    }

    /**
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     *
     * @return {@code true} if the read lock was held, else false
     */
    public boolean tryUnlockRead() {
        long s, m; WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return true;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     * Returns {@code true} if the lock is currently held exclusively.
     *
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns {@code true} if the lock is currently held non-exclusively.
     *
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    public String toString() {
        long s = state;
        return super.toString() + ((s & ABITS) == 0L ? "[Unlocked]" : (s & WBIT) != 0L ? "[Write-locked]" : "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #readLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asReadLock() {
        ReadLockView v;
        return ((v = readLockView) != null ? v :
                (readLockView = new ReadLockView()));
    }

    /**
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #writeLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link
     * Lock#newCondition()} throws {@code
     * UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asWriteLock() {
        WriteLockView v;
        return ((v = writeLockView) != null ? v :
                (writeLockView = new WriteLockView()));
    }

    /**
     * Returns a {@link ReadWriteLock} view of this StampedLock in
     * which the {@link ReadWriteLock#readLock()} method is mapped to
     * {@link #asReadLock()}, and {@link ReadWriteLock#writeLock()} to
     * {@link #asWriteLock()}.
     *
     * @return the lock
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        return ((v = readWriteLockView) != null ? v :
                (readWriteLockView = new ReadWriteLockView()));
    }

    // view classes

    final class ReadLockView implements Lock {
        public void lock() { readLock(); }
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }
        public boolean tryLock() { return tryReadLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockRead(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {
        public void lock() { writeLock(); }
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }
        public boolean tryLock() { return tryWriteLock() != 0L; }
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }
        public void unlock() { unstampedUnlockWrite(); }
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() { return asReadLock(); }
        public Lock writeLock() { return asWriteLock(); }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    final void unstampedUnlockWrite() {
        WNode h; long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        state = (s += WBIT) == 0L ? ORIGIN : s;
        if ((h = whead) != null && h.status != 0)
            release(h);
    }

    final void unstampedUnlockRead() {
        for (;;) {
            long s, m; WNode h;
            if ((m = (s = state) & ABITS) == 0L || m >= WBIT)
                throw new IllegalMonitorStateException();
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L)
                break;
        }
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    // internals

    /**
     * Tries to increment readerOverflow by first setting state
     * access bits value to RBITS, indicating hold of spinlock,
     * then updating, then releasing.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                ++readerOverflow;
                state = s;
                return s;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * Tries to decrement readerOverflow.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r; long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                }
                else
                    next = s - RUNIT;
                 state = next;
                 return next;
            }
        }
        else if ((LockSupport.nextSecondarySeed() &
                  OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        return 0L;
    }

    /**
     * （1）更改state的值，释放写锁；
     * （2）版本号加1；
     * （3）唤醒下一个等待着的节点
     */
    private void release(WNode h) {
        if (h != null) {
            WNode q; Thread w;
            // 将其状态改为0
            U.compareAndSwapInt(h, WSTATUS, WAITING, 0);
            // 如果头节点的下一个节点为空或者其状态为已取消
            if ((q = h.next) == null || q.status == CANCELLED) {
                // 从尾节点向前遍历找到一个可用的节点
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            // 唤醒q节点所在的线程
            if (q != null && (w = q.thread) != null)
                U.unpark(w);
        }
    }

    /**
     * 获取写锁
     * 第一段自旋—— 入队：
     * （1）如果头节点等于尾节点，说明没有其它线程排队，那就多自旋一会，看能不能尝试获取到写锁
     * （2）否则，自旋次数为0，直接让其入队
     *
     * 第二段自旋—— 阻塞并等待被唤醒 + 第三段自旋——不断尝试获取写锁：
     * （1）第三段自旋在第二段自旋内部
     * （2）如果头节点等于前置节点，那就进入第三段自旋，不断尝试获取写锁
     * （3）否则，尝试唤醒头节点中等待着的读线程
     * （4）最后，如果当前线程一直都没有获取到写锁，就阻塞当前线程并等待被唤醒
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        // node为新增节点，p为尾节点（即将成为node的前置节点）
        WNode node = null, p;
        // 第一次自旋——入队
        for (int spins = -1;;) { // spin while enqueuing
            long m, s, ns;
            // 再次尝试获取写锁
            if ((m = (s = state) & ABITS) == 0L) {
                if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT))
                    return ns;
            }
            else if (spins < 0)
                // 如果自旋次数小于0，则计算自旋的次数
                // 如果当前有写锁独占且队列无元素，说明快轮到自己了
                // 就自旋就行了，如果自旋完了还没轮到自己才入队
                // 则自旋次数为SPINS常量
                // 否则自旋次数为0
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            else if (spins > 0) {
                // 当自旋次数大于0时，当前这次自旋随机减一次自旋次数
                if (LockSupport.nextSecondarySeed() >= 0)
                    --spins;
            }
            else if ((p = wtail) == null) {
                // 如果队列未初始化，新建一个空节点并初始化头节点和尾节点
                WNode hd = new WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            else if (node == null)
                // 如果新增节点还未初始化，则新建之，并赋值其前置节点为尾节点
                node = new WNode(WMODE, p);
            else if (node.prev != p)
                // 如果尾节点有变化，则更新新增节点的前置节点为新的尾节点
                node.prev = p;
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                // 尝试更新新增节点为新的尾节点成功，则退出循环
                p.next = node;
                break;
            }
        }

        // 第二次自旋——阻塞并等待唤醒
        for (int spins = -1;;) {
            // h为头节点，np为新增节点的前置节点，pp为前前置节点，ps为前置节点的状态
            WNode h, np, pp; int ps;
            // 如果头节点等于前置节点，说明快轮到自己了
            if ((h = whead) == p) {
                if (spins < 0)
                    // 初始化自旋次数
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    // 增加自旋次数
                    spins <<= 1;
                // 第三次自旋，不断尝试获取写锁
                for (int k = spins;;) {
                    long s, ns;
                    if (((s = state) & ABITS) == 0L) {
                        if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT)) {
                            // 尝试获取写锁成功，将node设置为新头节点并清除其前置节点(gc)
                            whead = node;
                            node.prev = null;
                            return ns;
                        }
                    }
                    // 随机立减自旋次数，当自旋次数减为0时跳出循环再重试
                    else if (LockSupport.nextSecondarySeed() >= 0 && --k <= 0)
                        break;
                }
            }
            else if (h != null) { // help release stale waiters
                // 这段代码很难进来，是用于协助唤醒读节点的
                // 我是这么调试进来的：
                // 起三个写线程，两个读线程
                // 写线程1获取锁不要释放
                // 读线程1获取锁，读线程2获取锁（会阻塞）
                // 写线程2获取锁（会阻塞）
                // 写线程1释放锁，此时会唤醒读线程1
                // 在读线程1里面先不要唤醒读线程2
                // 写线程3获取锁，此时就会走到这里来了
                WNode c; Thread w;
                // 如果头节点的cowait链表（栈）不为空，唤醒里面的所有节点
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null)
                        U.unpark(w);
                }
            }
            // 如果头节点没有变化
            if (whead == h) {
                // 如果尾节点有变化，则更新
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                // 如果尾节点状态为0，则更新成WAITING
                else if ((ps = p.status) == 0) U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                // 如果尾节点状态为取消，则把它从链表中删除
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    // 有超时时间的处理 可以设置为0
                    long time;
                    if (deadline == 0L) time = 0L;
                    // 已超时，剔除当前节点
                    else if ((time = deadline - System.nanoTime()) <= 0L) return cancelWaiter(node, node, false);
                    // 当前线程
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    // 把node的线程指向当前线程
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) && whead == h && node.prev == p)
                        // 阻塞当前线程 直接调用park，等同于LockSupport.park()
                        U.park(false, time);
                    //当前节点被唤醒后，清除线程
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    // 如果中断了，取消当前节点
                    if (interruptible && Thread.interrupted()) return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * 获取读锁 6段自旋
     * （1）读节点进来都是先判断是头节点如果等于尾节点，说明快轮到自己了，就不断地尝试获取读锁，如果成功了就返回
     * （2）如果头节点不等于尾节点，这里就会让当前节点入队，这里入队又分成了两种
     * （3）一种是首个读节点入队，它是会排队到整个队列的尾部，然后跳出第一段自旋
     * （4）另一种是非第一个读节点入队，它是进入到首个读节点的cowait栈中，所以更确切地说应该是入栈
     * （5）不管是入队还入栈后，都会再次检测头节点是不是等于尾节点了，如果相等，则会再次不断尝试获取读锁
     * （6）如果头节点不等于尾节点，那么才会真正地阻塞当前线程并等待被唤醒
     * （7）上面说的首个读节点其实是连续的读线程中的首个，如果是两个读线程中间夹了一个写线程，还是老老实实的排队
     */
    private long acquireRead(boolean interruptible, long deadline) {
        // node为新增节点，p为尾节点
        WNode node = null, p;
        // 第一段自旋——入队
        for (int spins = -1;;) {
            // 头节点
            WNode h;
            // 如果头节点等于尾节点
            // 说明没有排队的线程了，快轮到自己了，直接自旋不断尝试获取读锁
            if ((h = whead) == (p = wtail)) {
                // 第二段自旋——不断尝试获取读锁
                for (long m, s, ns;;) {
                    // 尝试获取读锁，如果成功了直接返回版本号
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                        // 如果读线程个数达到了最大值，会溢出，返回的是0
                        return ns;
                    else if (m >= WBIT) {
                        // m >= WBIT表示有其它线程先一步获取了写锁
                        if (spins > 0) {
                            // 随机立减自旋次数
                            if (LockSupport.nextSecondarySeed() >= 0) --spins;
                        }
                        else {
                            // 如果自旋次数为0了，看看是否要跳出循环
                            if (spins == 0) {
                                WNode nh = whead, np = wtail;
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            // 设置自旋次数
                            spins = SPINS;
                        }
                    }
                }
            }
            // 如果尾节点为空，初始化头节点和尾节点
            if (p == null) {
                WNode hd = new WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd))
                    wtail = hd;
            }
            else if (node == null)
                // 如果新增节点为空，初始化
                node = new WNode(RMODE, p);
            else if (h == p || p.mode != RMODE) {
                // 如果头节点等于尾节点或者尾节点不是读模式
                // 当前节点入队
                if (node.prev != p)
                    node.prev = p;
                else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                    p.next = node;
                    break;
                }
            }
            else if (!U.compareAndSwapObject(p, WCOWAIT, node.cowait = p.cowait, node))
                // 接着上一个else if，这里肯定是尾节点为读模式了
                // 将当前节点加入到尾节点的cowait中，这是一个栈
                // 上面的CAS成功了是不会进入到这里来的
                node.cowait = null;
            else {
                // 第三段自旋——阻塞当前线程并等待被唤醒
                for (;;) {
                    // 如果头节点不为空且其cowait不为空，协助唤醒其中等待的读线程
                    WNode pp, c; Thread w;
                    // 如果头节点等待前前置节点或者等于前置节点或者前前置节点为空
                    // 这同样说明快轮到自己了
                    if ((h = whead) != null && (c = h.cowait) != null && U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null)
                        U.unpark(w);
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        // 第四段自旋——又是不断尝试获取锁
                        do {
                            if ((m = (s = state) & ABITS) < RFULL ?
                                    U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L))
                                return ns;
                        } while (m < WBIT);
                        // 只有当前时刻没有其它线程占有写锁就不断尝试
                    }
                    // 如果头节点未曾改变且前前置节点也未曾改 则阻塞当前线程
                    if (whead == h && p.prev == pp) {
                        long time;
                        // 如果前前置节点为空，或者头节点等于前置节点，或者前置节点已取消
                        // 从第一个for自旋开始重试
                        if (pp == null || h == p || p.status > 0) {
                            node = null; // throw away
                            break;
                        }
                        // 超时检测
                        if (deadline == 0L) time = 0L;
                        // 如果超时了，取消当前节点
                        else if ((time = deadline - System.nanoTime()) <= 0L) return cancelWaiter(node, p, false);
                        // 当前线程
                        Thread wt = Thread.currentThread();
                        U.putObject(wt, PARKBLOCKER, this);
                        // 设置进node中
                        node.thread = wt;
                        // 检测之前的条件未曾改变，阻塞当前线程并等待被唤醒
                        if ((h != pp || (state & ABITS) == WBIT) && whead == h && p.prev == pp) U.park(false, time);
                        // 唤醒之后清除线程
                        node.thread = null;
                        U.putObject(wt, PARKBLOCKER, null);
                        // 如果中断了，取消当前节点
                        if (interruptible && Thread.interrupted()) return cancelWaiter(node, p, true);
                    }
                }
            }
        }
        // 只有第一个读线程会走到下面的for循环处，参考上面第一段自旋中有一个break，当第一个读线程入队的时候break出来的
        // 第五段自旋——跟上面的逻辑差不多，只不过这里单独搞一个自旋针对第一个读线程
        for (int spins = -1;;) {
            WNode h, np, pp; int ps;
            // 如果头节点等于尾节点，说明快轮到自己了
            // 不断尝试获取读锁
            if ((h = whead) == p) {
                // 设置自旋次数
                if (spins < 0) spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS) spins <<= 1;
                // 第六段自旋——不断尝试获取读锁
                for (int k = spins;;) {
                    long m, s, ns;
                    // 不断尝试获取读锁
                    if ((m = (s = state) & ABITS) < RFULL ?
                        U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        // 获取到了读锁
                        WNode c; Thread w;
                        whead = node;
                        node.prev = null;
                        // 唤醒当前节点中所有等待着的读线程
                        // 因为当前节点是第一个读节点，所以它是在队列中的，其它读节点都是挂这个节点的cowait栈中的
                        while ((c = node.cowait) != null) {
                            if (U.compareAndSwapObject(node, WCOWAIT, c, c.cowait) && (w = c.thread) != null)
                                U.unpark(w);
                        }
                        // 返回版本号
                        return ns;
                    }
                    // 如果当前有其它线程占有着写锁，并且没有自旋次数了，跳出当前循环
                    else if (m >= WBIT && LockSupport.nextSecondarySeed() >= 0 && --k <= 0) break;
                }
            }
            else if (h != null) {
                // 如果头节点不等待尾节点且不为空且其为读模式，协助唤醒里面的读线程
                WNode c; Thread w;
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null) U.unpark(w);
                }
            }
            // 如果头节点未曾变化
            if (whead == h) {
                // 更新前置节点及其状态等
                if ((np = node.prev) != p) {
                    if (np != null) (p = np).next = node;
                }
                else if ((ps = p.status) == 0) U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    // 第一个读节点即将进入阻塞
                    long time;
                    // 超时设置
                    if (deadline == 0L) time = 0L;
                    // 如果超时了取消当前节点
                    else if ((time = deadline - System.nanoTime()) <= 0L) return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) == WBIT) && whead == h && node.prev == p)
                        // 阻塞第一个读节点并等待被唤醒
                        U.park(false, time);
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted()) return cancelWaiter(node, node, true);
                }
            }
        }
    }

    /**
     * 取消当前节点
     */
    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            node.status = CANCELLED;
            // unsplice cancelled nodes from group
            for (WNode p = group, q; (q = p.cowait) != null;) {
                if (q.status == CANCELLED) {
                    U.compareAndSwapObject(p, WCOWAIT, q, q.cowait);
                    p = group; // restart
                }
                else
                    p = q;
            }
            if (group == node) {
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        U.unpark(w);       // wake up uncancelled co-waiters
                }
                for (WNode pred = node.prev; pred != null; ) { // unsplice
                    WNode succ, pp;        // find valid successor
                    while ((succ = node.next) == null ||
                           succ.status == CANCELLED) {
                        WNode q = null;    // find successor the slow way
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            if (t.status != CANCELLED)
                                q = t;     // don't link if succ cancelled
                        if (succ == q ||   // ensure accurate successor
                            U.compareAndSwapObject(node, WNEXT,
                                                   succ, succ = q)) {
                            if (succ == null && node == wtail)
                                U.compareAndSwapObject(this, WTAIL, node, pred);
                            break;
                        }
                    }
                    if (pred.next == node) // unsplice pred link
                        U.compareAndSwapObject(pred, WNEXT, node, succ);
                    if (succ != null && (w = succ.thread) != null) {
                        succ.thread = null;
                        U.unpark(w);       // wake up succ to observe new pred
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        break;
                    node.prev = pp;        // repeat if new pred wrong/cancelled
                    U.compareAndSwapObject(pp, WNEXT, pred, succ);
                    pred = pp;
                }
            }
        }
        WNode h; // Possibly release first waiter
        while ((h = whead) != null) {
            long s; WNode q; // similar to release() but check eligibility
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (h == whead) {
                if (q != null && h.status == 0 &&
                    ((s = state) & ABITS) != WBIT && // waiter is eligible
                    (s == 0L || q.mode == RMODE))
                    release(h);
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long STATE;
    private static final long WHEAD;
    private static final long WTAIL;
    private static final long WNEXT;
    private static final long WSTATUS;
    private static final long WCOWAIT;
    private static final long PARKBLOCKER;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> wk = WNode.class;
            STATE = U.objectFieldOffset(k.getDeclaredField("state"));
            WHEAD = U.objectFieldOffset(k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset(k.getDeclaredField("wtail"));
            WSTATUS = U.objectFieldOffset(wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset(wk.getDeclaredField("next"));
            WCOWAIT = U.objectFieldOffset(wk.getDeclaredField("cowait"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset(tk.getDeclaredField("parkBlocker"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
