package java.util.concurrent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 回环栅栏使用
 */
public class CyclicBarrier {
    // 代，用于控制CyclicBarrier的循环使用
    private static class Generation {
        boolean broken = false;
    }
    // 重入锁
    private final ReentrantLock lock = new ReentrantLock();
    // 条件锁，名称为trip，绊倒的意思，可能是指线程来了先绊倒，等达到一定数量了再唤醒
    private final Condition trip = lock.newCondition();
    // 需要等待的线程数量
    private final int parties;
    // 当唤醒的时候执行的命令
    private final Runnable barrierCommand;
    // 代
    private Generation generation = new Generation();
    // 当前这一代还需要等待的线程数
    private int count;

    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    private void nextGeneration() {
        // 调用condition的signalAll()将其队列中的等待者全部转移到AQS的队列中
        trip.signalAll();
        // 重置count
        count = parties;
        // 进入下一代
        generation = new Generation();
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     */
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        trip.signalAll();
    }

    /**
     * 核心方法
     */
    private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
        final ReentrantLock lock = this.lock;
        // 加锁
        lock.lock();
        try {
            // 当前代
            final Generation g = generation;
            // 检查
            if (g.broken) throw new BrokenBarrierException();
            // 中断检查
            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }
            // count的值减1
            int index = --count;
            // 如果数量减到0了，走这段逻辑（最后一个线程走这里）
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    // 如果初始化的时候传了命令，这里执行
                    if (command != null) command.run();
                    ranAction = true;
                    // 调用下一代方法
                    nextGeneration();
                    return 0;
                } finally {
                    // 防止传入执行动作报错
                    if (!ranAction) breakBarrier();
                }
            }

            // 这个循环只有非最后一个线程可以走到
            for (;;) {
                try {
                    // 调用condition的await()方法
                    if (!timed) trip.await();
                    // 超时等待方法
                    else if (nanos > 0L) nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        Thread.currentThread().interrupt();
                    }
                }
                // 检查
                if (g.broken) throw new BrokenBarrierException();
                // 正常来说这里肯定不相等
                // 因为上面打破栅栏的时候调用nextGeneration()方法时generation的引用已经变化了
                if (g != generation) return index;
                // 超时检查
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // 构造方法
    // 传入Party，传入一个线程，这个线程应该不是本身线程
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        // 初始化parties
        this.parties = parties;
        // 初始化count等于parties
        this.count = parties;
        // 初始化都到达栅栏处执行的命令
        this.barrierCommand = barrierAction;
    }

    // 只传入数量
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    public int getParties() {
        return parties;
    }

    /**
     * 每个需要在栅栏处等待的线程都需要显式地调用await()方法等待其它线程的到来
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            // 调用dowait方法，不需要超时
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    // 传入超时，而且可以控制粒度
    public int await(long timeout, TimeUnit unit) throws InterruptedException, BrokenBarrierException, TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    //
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    // 调用此方法会直接打破屏障，并且直接进入下一代
    // 会直接开始显示调用
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    //
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
