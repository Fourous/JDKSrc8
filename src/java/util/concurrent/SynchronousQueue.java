package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;
import java.util.Spliterator;
import java.util.Spliterators;

/**
 * java并发包下无缓冲阻塞队列，它用来在两个线程之间移交元素
 */
public class SynchronousQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /**
     * Transferer抽象类，主要定义了一个transfer方法用来传输元素
     */
    abstract static class Transferer<E> {
        abstract E transfer(E e, boolean timed, long nanos);
    }

    // CPU数量
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    // 有超时的情况自旋多少次，当CPU数量小于2的时候不自旋
    static final int maxTimedSpins = (NCPUS < 2) ? 0 : 32;

    // 没有超时的情况自旋多少次
    static final int maxUntimedSpins = maxTimedSpins * 16;

    // 针对有超时的情况，自旋了多少次后，如果剩余时间大于1000纳秒就使用带时间的LockSupport.parkNanos()这个方法
    static final long spinForTimeoutThreshold = 1000L;

    // 以栈方式实现的Transferer
    static final class TransferStack<E> extends Transferer<E> {
        // 栈中节点的几种类型：
        // 1. 消费者（请求数据的）
        static final int REQUEST    = 0;
        // 2. 生产者（提供数据的）
        static final int DATA       = 1;
        // 3. 二者正在匹配中
        static final int FULFILLING = 2;

        static boolean isFulfilling(int m) { return (m & FULFILLING) != 0; }

        // 栈中的节点
        static final class SNode {
            // 栈下一个节点
            volatile SNode next;
            // 匹配者
            volatile SNode match;
            // 等待着的线程
            volatile Thread waiter;
            // 元素
            Object item;
            // 模式，也就是节点的类型，是消费者，是生产者，还是正在匹配中
            int mode;

            SNode(Object item) {
                this.item = item;
            }

            boolean casNext(SNode cmp, SNode val) {
                return cmp == next && UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }
            // SNode里面的方向，调用者m是s的下一个节点
            // 这时候m节点的线程应该是阻塞状态的
            boolean tryMatch(SNode s) {
                // 如果m还没有匹配者，就把s作为它的匹配者
                if (match == null && UNSAFE.compareAndSwapObject(this, matchOffset, null, s)) {
                    Thread w = waiter;
                    if (w != null) {
                        waiter = null;
                        // 唤醒m中的线程，两者匹配完毕
                        LockSupport.unpark(w);
                    }
                    // 匹配到了返回true
                    return true;
                }
                // 可能其它线程先一步匹配了m，返回其是否是s
                return match == s;
            }

            void tryCancel() {
                UNSAFE.compareAndSwapObject(this, matchOffset, null, this);
            }

            boolean isCancelled() {
                return match == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long matchOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = SNode.class;
                    matchOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("match"));
                    nextOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }
        // 栈的头节点
        volatile SNode head;

        boolean casHead(SNode h, SNode nh) {
            return h == head && UNSAFE.compareAndSwapObject(this, headOffset, h, nh);
        }

        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null) s = new SNode(e);
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         * （1）如果栈中没有元素，或者栈顶元素跟将要入栈的元素模式一样，就入栈；
         * （2）入栈后自旋等待一会看有没有其它线程匹配到它，自旋完了还没匹配到元素就阻塞等待；
         * （3）阻塞等待被唤醒了说明其它线程匹配到了当前的元素，就返回匹配到的元素；
         * （4）如果两者模式不一样，且头节点没有在匹配中，就拿当前节点跟它匹配，匹配成功了就返回匹配到的元素；
         * （5）如果两者模式不一样，且头节点正在匹配中，当前线程就协助去匹配，匹配完成了再让当前节点重新入栈重新匹配
         */
        // 传递元素
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            // 根据e是否为null决定是生产者还是消费者
            SNode s = null;
            int mode = (e == null) ? REQUEST : DATA;
            for (;;) {
                // 栈顶元素
                SNode h = head;
                // 栈顶没有元素，或者栈顶元素跟当前元素是一个模式的
                // 也就是都是生产者节点或者都是消费者节点
                if (h == null || h.mode == mode) {
                    // 如果有超时而且已到期
                    if (timed && nanos <= 0) {
                        // 如果头节点不为空且是取消状态
                        // 就把头节点弹出，并进入下一次循环
                        if (h != null && h.isCancelled()) casHead(h, h.next);
                        // 否则，直接返回null（超时返回null）
                        else return null;
                    } else if (casHead(h, s = snode(s, e, h, mode))) {
                        // 入栈成功（因为是模式相同的，所以只能入栈）
                        // 调用awaitFulfill()方法自旋+阻塞当前入栈的线程并等待被匹配到
                        SNode m = awaitFulfill(s, timed, nanos);
                        // 如果m等于s，说明取消了，那么就把它清除掉，并返回null
                        if (m == s) {
                            clean(s);
                            // 被取消了返回null
                            return null;
                        }
                        // 到这里说明匹配到元素了
                        // 因为从awaitFulfill()里面出来要不被取消了要不就匹配到了
                        // 如果头节点不为空，并且头节点的下一个节点是s
                        // 就把头节点换成s的下一个节点
                        // 也就是把h和s都弹出了
                        // 也就是把栈顶两个元素都弹出了
                        if ((h = head) != null && h.next == s) casHead(h, s.next);
                        // 根据当前节点的模式判断返回m还是s中的值
                        return (E) ((mode == REQUEST) ? m.item : s.item);
                    }
                    // 到这里说明头节点和当前节点模式不一样
                    // 如果头节点不是正在匹配中
                } else if (!isFulfilling(h.mode)) {
                    // 如果头节点已经取消了，就把它弹出栈
                    if (h.isCancelled()) casHead(h, h.next);
                    else if (casHead(h, s=snode(s, e, h, FULFILLING|mode))) {
                        // 头节点没有在匹配中，就让当前节点先入队，再让他们尝试匹配
                        // 且s成为了新的头节点，它的状态是正在匹配中
                        for (;;) {
                            SNode m = s.next;
                            // 如果m为null，说明除了s节点外的节点都被其它线程先一步匹配掉了
                            // 就清空栈并跳出内部循环，到外部循环再重新入栈判断
                            if (m == null) {
                                casHead(s, null);
                                s = null;
                                break;
                            }
                            SNode mn = m.next;
                            // 如果m和s尝试匹配成功，就弹出栈顶的两个元素m和s
                            if (m.tryMatch(s)) {
                                casHead(s, mn);
                                // 返回匹配结果
                                return (E) ((mode == REQUEST) ? m.item : s.item);
                                // 尝试匹配失败，说明m已经先一步被其它线程匹配了
                                // 就协助清除它
                            } else s.casNext(m, mn);
                        }
                    }
                // 到这里说明当前节点和头节点模式不一样
                // 且头节点是正在匹配中
                } else {
                    SNode m = h.next;
                    // 如果m为null，说明m已经被其它线程先一步匹配了
                    if (m == null) casHead(h, null);
                    else {
                        SNode mn = m.next;
                        // 协助匹配，如果m和s尝试匹配成功，就弹出栈顶的两个元素m和s
                        if (m.tryMatch(h)) casHead(h, mn);
                        // 尝试匹配失败，说明m已经先一步被其它线程匹配了
                        // 就协助清除它
                        else h.casNext(m, mn);
                    }
                }
            }
        }
        // 三个参数：需要等待的节点，是否需要超时，超时时间
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            // 到期时间
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            // 自旋次数
            int spins = (shouldSpin(s) ? (timed ? maxTimedSpins : maxUntimedSpins) : 0);
            for (;;) {
                // 当前线程中断了，尝试清除s
                if (w.isInterrupted()) s.tryCancel();
                // 检查s是否匹配到了元素m（有可能是其它线程的m匹配到当前线程的s）
                SNode m = s.match;
                // 检查s是否匹配到了元素m（有可能是其它线程的m匹配到当前线程的s）
                if (m != null) return m;
                // 如果需要超时
                if (timed) {
                    // 检查超时时间如果小于0了，尝试清除s
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel();
                        continue;
                    }
                }
                // 如果还有自旋次数，自旋次数减一，并进入下一次自旋
                if (spins > 0) spins = shouldSpin(s) ? (spins-1) : 0;
                // 如果s的waiter为null，把当前线程注入进去，并进入下一次自旋
                else if (s.waiter == null) s.waiter = w;
                // 如果不允许超时，直接阻塞，并等待被其它线程唤醒，唤醒后继续自旋并查看是否匹配到了元素
                else if (!timed) LockSupport.park(this);
                // 如果允许超时且还有剩余时间，就阻塞相应时间
                else if (nanos > spinForTimeoutThreshold) LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Returns true if node s is at head or there is an active
         * fulfiller.
         */
        boolean shouldSpin(SNode s) {
            SNode h = head;
            return (h == s || h == null || isFulfilling(h.mode));
        }

        /**
         * Unlinks s from the stack.
         */
        void clean(SNode s) {
            s.item = null;   // forget item
            s.waiter = null; // forget thread
            SNode past = s.next;
            if (past != null && past.isCancelled())
                past = past.next;

            // Absorb cancelled nodes at head
            SNode p;
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);

            // Unsplice embedded nodes
            while (p != null && p != past) {
                SNode n = p.next;
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferStack.class;
                headOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("head"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    // 以队列方式实现的Transferer
    static final class TransferQueue<E> extends Transferer<E> {
        // 队列中的节点
        static final class QNode {
            // 下一个节点
            volatile QNode next;
            // 存储的元素
            volatile Object item;
            // 等待着的线程
            volatile Thread waiter;
            // 是否是数据节点
            final boolean isData;

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            boolean casNext(QNode cmp, QNode val) {
                return next == cmp && UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            boolean casItem(Object cmp, Object val) {
                return item == cmp && UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
            }

            /**
             * Tries to cancel by CAS'ing ref to this as item.
             */
            void tryCancel(Object cmp) {
                UNSAFE.compareAndSwapObject(this, itemOffset, cmp, this);
            }

            boolean isCancelled() {
                return item == this;
            }

            boolean isOffList() {
                return next == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long itemOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = QNode.class;
                    itemOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("item"));
                    nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        // 队列的头节点
        transient volatile QNode head;
        // 队列尾节点
        transient volatile QNode tail;
        // 没有没打断但是已经被取消的节点，标记一下
        transient volatile QNode cleanMe;

        TransferQueue() {
            QNode h = new QNode(null, false); // initialize to dummy node.
            head = h;
            tail = h;
        }

        void advanceHead(QNode h, QNode nh) {
            if (h == head &&
                UNSAFE.compareAndSwapObject(this, headOffset, h, nh))
                h.next = h; // forget old next
        }

        void advanceTail(QNode t, QNode nt) {
            if (tail == t)
                UNSAFE.compareAndSwapObject(this, tailOffset, t, nt);
        }

        boolean casCleanMe(QNode cmp, QNode val) {
            return cleanMe == cmp && UNSAFE.compareAndSwapObject(this, cleanMeOffset, cmp, val);
        }

        /**
         * Puts or takes an item.
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            QNode s = null; // constructed/reused as needed
            boolean isData = (e != null);

            for (;;) {
                QNode t = tail;
                QNode h = head;
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin

                if (h == t || t.isData == isData) { // empty or same-mode
                    QNode tn = t.next;
                    if (t != tail)                  // inconsistent read
                        continue;
                    if (tn != null) {               // lagging tail
                        advanceTail(t, tn);
                        continue;
                    }
                    if (timed && nanos <= 0)        // can't wait
                        return null;
                    if (s == null)
                        s = new QNode(e, isData);
                    if (!t.casNext(null, s))        // failed to link in
                        continue;

                    advanceTail(t, s);              // swing tail and wait
                    Object x = awaitFulfill(s, e, timed, nanos);
                    if (x == s) {                   // wait was cancelled
                        clean(t, s);
                        return null;
                    }

                    if (!s.isOffList()) {           // not already unlinked
                        advanceHead(t, s);          // unlink if head
                        if (x != null)              // and forget fields
                            s.item = s;
                        s.waiter = null;
                    }
                    return (x != null) ? (E)x : e;

                } else {                            // complementary-mode
                    QNode m = h.next;               // node to fulfill
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read

                    Object x = m.item;
                    if (isData == (x != null) ||    // m already fulfilled
                        x == m ||                   // m cancelled
                        !m.casItem(x, e)) {         // lost CAS
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }

                    advanceHead(h, m);              // successfully fulfilled
                    LockSupport.unpark(m.waiter);
                    return (x != null) ? (E)x : e;
                }
            }
        }

        /**
         * Spins/blocks until node s is fulfilled.
         *
         * @param s the waiting node
         * @param e the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         */
        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            int spins = ((head.next == s) ?
                         (timed ? maxTimedSpins : maxUntimedSpins) : 0);
            for (;;) {
                if (w.isInterrupted())
                    s.tryCancel(e);
                Object x = s.item;
                if (x != e)
                    return x;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel(e);
                        continue;
                    }
                }
                if (spins > 0)
                    --spins;
                else if (s.waiter == null)
                    s.waiter = w;
                else if (!timed)
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Gets rid of cancelled node s with original predecessor pred.
         */
        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head
                if (hn != null && hn.isCancelled()) {
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail
                if (t == h)
                    return;
                QNode tn = t.next;
                if (t != tail)
                    continue;
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }
                QNode dp = cleanMe;
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;
                    if (d == null ||               // d is gone or
                        d == dp ||                 // d is off list or
                        !d.isCancelled() ||        // d not cancelled or
                        (d != t &&                 // d not tail and
                         (dn = d.next) != null &&  //   has successor
                         dn != d &&                //   that is on list
                         dp.casNext(d, dn)))       // d unspliced
                        casCleanMe(dp, null);
                    if (dp == pred)
                        return;      // s is already saved node
                } else if (casCleanMe(null, pred))
                    return;          // Postpone cleaning s
            }
        }

        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        private static final long tailOffset;
        private static final long cleanMeOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferQueue.class;
                headOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("head"));
                tailOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("tail"));
                cleanMeOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("cleanMe"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    // 传输器，即两个线程交换元素使用的东西
    private transient volatile Transferer<E> transferer;

    /*** 构造函数 一个默认false，也就是栈模式***/
    public SynchronousQueue() {
        this(false);
    }
    // 构造不同的结构，栈 VS 队列
    public SynchronousQueue(boolean fair) {
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }

    /** 放入元素 3个方法**/
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        // 传输的元素，是否需要超时，超时的时间
        // 第一个字段有元素证明是生产者
        if (transferer.transfer(e, false, 0) == null) {
            // 如果传输失败，直接让线程中断并抛出中断异常
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, true, unit.toNanos(timeout)) != null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }

    /** 取出元素 3个方法
     *
     * **/
    public E take() throws InterruptedException {
        // 第一个元素为null，证明是消费者
        E e = transferer.transfer(null, false, 0);
        if (e != null) return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted()) return e;
        throw new InterruptedException();
    }

    public E poll() {
        return transferer.transfer(null, true, 0);
    }

   /*** API 基本下面的API方法都给给空值，因为本身就是传递方法 ***/
    public boolean isEmpty() {
        return true;
    }

    public int size() {
        return 0;
    }

    public int remainingCapacity() {
        return 0;
    }

    // clear是空的，实际上
    public void clear() {
    }

    public boolean contains(Object o) {
        return false;
    }

    public boolean remove(Object o) {
        return false;
    }

    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    public boolean removeAll(Collection<?> c) {
        return false;
    }

    public boolean retainAll(Collection<?> c) {
        return false;
    }

    public E peek() {
        return null;
    }

    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    public Object[] toArray() {
        return new Object[0];
    }

    public <T> T[] toArray(T[] a) {
        if (a.length > 0) a[0] = null;
        return a;
    }

    public int drainTo(Collection<? super E> c) {
        if (c == null) throw new NullPointerException();
        if (c == this) throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    @SuppressWarnings("serial")
    static class WaitQueue implements java.io.Serializable { }
    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }
    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }
    private ReentrantLock qlock;
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        }
        else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue)
            transferer = new TransferQueue<E>();
        else
            transferer = new TransferStack<E>();
    }

    // Unsafe mechanics
    static long objectFieldOffset(sun.misc.Unsafe UNSAFE, String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

}
