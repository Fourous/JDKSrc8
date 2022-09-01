package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.Collection;

/**
 * 重入锁，是指一个线程获取锁之后再尝试获取锁时会自动获取锁，Synchronized也是可重入的
 * 定义了三个内部类：
 *
 * abstract Sync extends AbstractQueuedSynchronizer 实现AQS的逻辑，也就是锁的逻辑
 * NonfairSync extends Sync 实现Sync抽象逻辑，非公平锁的获取
 * FairSync extends Sync 实现sync抽象逻辑，公平锁获取 默认采用非公平锁
 *
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;
    /**
     * 在构造函数中初始化，决定采用公平/非公平获取锁
     */
    private final Sync sync;

    /**
     * 实现AQS逻辑
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 抽象方法，后面的公平和非公平都要实现该方法
         */
        abstract void lock();

        /**
         * 非公平的尝试获取
         * 这里为什么写父类里面，公平写在子类，是因为默认就是非公平
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                // 这里也可以看到，直接就开始获取，而不管是否排队
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }

        // AQS 还是会回到这里，来进行释放锁
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            // 如果当前线程不是占有着锁的线程，抛出异常
            if (Thread.currentThread() != getExclusiveOwnerThread()) throw new IllegalMonitorStateException();
            boolean free = false;
            // 如果状态变量的值为0了，说明完全释放了锁
            // 这也就是为什么重入锁调用了多少次lock()就要调用多少次unlock()的原因
            // 如果不这样做，会导致锁不会完全释放，别的线程永远无法获取到锁
            if (c == 0) {
                free = true;
                // 清空占有线程
                setExclusiveOwnerThread(null);
            }
            // 设置状态变量的值
            setState(c);
            return free;
        }

        protected final boolean isHeldExclusively() {
            // 判断是否当前线程是获取锁的线程
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // AQS ConditionObject
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // 获取当前锁的线程对象
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁获取
     * （1）一开始就尝试CAS更新状态变量state的值，如果成功了就获取到锁了；
     * （2）在tryAcquire()的时候没有检查是否前面有排队的线程，直接上去获取锁才不管别人有没有排队
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * Performs lock.  Try immediate barge, backing up to normal
         * acquire on failure.
         */
        final void lock() {
            // 直接尝试CAS更新状态变量
            // 相比于公平，这里是直接就开始获取锁了，省略了排队
            if (compareAndSetState(0, 1))
                // 如果更新成功，说明获取到锁，把当前线程设为独占线程
                setExclusiveOwnerThread(Thread.currentThread());
            else
                // 没有获取到就调用AQS的获取方法
                // AQS acquire
                acquire(1);
        }

        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁获取
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        final void lock() {
            // 这里可以看到，直接调用AQS的acquire方法实现获取锁
            // 并且AQS默认方法是独占模式
            acquire(1);
        }

        /**
         * 实现公平锁的尝试获取锁逻辑
         */
        protected final boolean tryAcquire(int acquires) {
            // 当前线程
            final Thread current = Thread.currentThread();
            // 获取当前状态变量的值
            int c = getState();
            // 如果还没有线程获取到锁
            if (c == 0) {
                // 如果没有其它线程在排队，再次尝试获取锁
                // 如果成功了则证明获取到锁
                if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                    // 当前线程获取了锁，把自己设置到exclusiveOwnerThread变量中
                    // exclusiveOwnerThread是AQS的父类AbstractOwnableSynchronizer中提供的变量
                    setExclusiveOwnerThread(current);
                    // 返回true说明成功获取了锁
                    return true;
                }
            }
            // 如果当前线程本身就占有着锁，现在又尝试获取锁
            // 那么，直接让它获取锁并返回true
            else if (current == getExclusiveOwnerThread()) {
                // 状态变量state的值加1
                int nextc = c + acquires;
                // 如果溢出了，则报错
                if (nextc < 0) throw new Error("Maximum lock count exceeded");
                // 设置到state中，而且这里没有使用CAS更新，因为已经获取到锁其他线程无法进行争抢
                // 单线程不用考虑并发问题
                setState(nextc);
                return true;
            }
            return false;
        }
    }

    /**
     * 默认非公平锁
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * fair在此初始化，决定采用那种方法初始化-公平/非公平
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 默认使用非公平锁
     */
    public void lock() {
        sync.lock();
    }

    /**
     * 可中断获取直接调用AQS的acquireInterruptibly
     */
    public void lockInterruptibly() throws InterruptedException {
        // AQS直接interrupt
        sync.acquireInterruptibly(1);
    }

    /**
     * 非公平锁获取
     */
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     *
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        // AQS的方法
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放锁
     */
    public void unlock() {
        sync.release(1);
    }

    /**
     * AQS的newCondition
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * Queries the number of holds on this lock by the current thread.
     *
     * <p>A thread has a hold on a lock for each lock action that is not
     * matched by an unlock action.
     *
     * <p>The hold count information is typically only used for testing and
     * debugging purposes. For example, if a certain section of code should
     * not be entered with the lock already held then we can assert that
     * fact:
     *
     *  <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *   public void m() {
     *     assert lock.getHoldCount() == 0;
     *     lock.lock();
     *     try {
     *       // ... method body
     *     } finally {
     *       lock.unlock();
     *     }
     *   }
     * }}</pre>
     *
     * @return the number of holds on this lock by the current thread,
     *         or zero if this lock is not held by the current thread
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * Queries if this lock is held by the current thread.
     *
     * <p>Analogous to the {@link Thread#holdsLock(Object)} method for
     * built-in monitor locks, this method is typically used for
     * debugging and testing. For example, a method that should only be
     * called while a lock is held can assert that this is the case:
     *
     *  <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() {
     *       assert lock.isHeldByCurrentThread();
     *       // ... method body
     *   }
     * }}</pre>
     *
     * <p>It can also be used to ensure that a reentrant lock is used
     * in a non-reentrant manner, for example:
     *
     *  <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() {
     *       assert !lock.isHeldByCurrentThread();
     *       lock.lock();
     *       try {
     *           // ... method body
     *       } finally {
     *           lock.unlock();
     *       }
     *   }
     * }}</pre>
     *
     * @return {@code true} if current thread holds this lock and
     *         {@code false} otherwise
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * Queries if this lock is held by any thread. This method is
     * designed for use in monitoring of the system state,
     * not for synchronization control.
     *
     * @return {@code true} if any thread holds this lock and
     *         {@code false} otherwise
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * Returns {@code true} if this lock has fairness set true.
     *
     * @return {@code true} if this lock has fairness set true
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * Returns the thread that currently owns this lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * Queries whether any threads are waiting to acquire this lock. Note that
     * because cancellations may occur at any time, a {@code true}
     * return does not guarantee that any other thread will ever
     * acquire this lock.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire this
     * lock. Note that because cancellations may occur at any time, a
     * {@code true} return does not guarantee that this thread
     * will ever acquire this lock.  This method is designed primarily for use
     * in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire this lock.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire this lock.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this lock. Note that because timeouts and
     * interrupts may occur at any time, a {@code true} return does
     * not guarantee that a future {@code signal} will awaken any
     * threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null) throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this lock. Note that because
     * timeouts and interrupts may occur at any time, the estimate
     * serves only as an upper bound on the actual number of waiters.
     * This method is designed for use in monitoring of the system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null) throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this lock.
     * Because the actual set of threads may change dynamically while
     * constructing this result, the returned collection is only a
     * best-effort estimate. The elements of the returned collection
     * are in no particular order.  This method is designed to
     * facilitate construction of subclasses that provide more
     * extensive condition monitoring facilities.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if this lock is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this lock
     * @throws NullPointerException if the condition is null
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null) throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes either the String {@code "Unlocked"}
     * or the String {@code "Locked by"} followed by the
     * {@linkplain Thread#getName name} of the owning thread.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }
}
