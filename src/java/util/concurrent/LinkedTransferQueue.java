package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

public class LinkedTransferQueue<E> extends AbstractQueue<E> implements TransferQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    // 获取CPU
    private static final boolean MP = Runtime.getRuntime().availableProcessors() > 1;

    private static final int FRONT_SPINS   = 1 << 7;

    private static final int CHAINED_SPINS = FRONT_SPINS >>> 1;

    static final int SWEEP_THRESHOLD = 32;

    // 双重队列
    // 典型的单链表结构，内部除了存储元素的值和下一个节点的指针外，还包含了是否为数据节点和持有元素的线程。
    //内部通过isData区分是生产者还是消费者
    static final class Node {
        // 是否是数据节点（也就标识了是生产者还是消费者）
        final boolean isData;
        // 元素值
        volatile Object item;
        // 下一个节点
        volatile Node next;
        // 持有元素的线程
        volatile Thread waiter;

        final boolean casNext(Node cmp, Node val) {
            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        final boolean casItem(Object cmp, Object val) {
            return UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
        }

        /**
         * Constructs a new node.  Uses relaxed write because item can
         * only be seen after publication via casNext.
         */
        Node(Object item, boolean isData) {
            UNSAFE.putObject(this, itemOffset, item); // relaxed write
            this.isData = isData;
        }

        /**
         * Links node to itself to avoid garbage retention.  Called
         * only after CASing head field, so uses relaxed write.
         */
        final void forgetNext() {
            UNSAFE.putObject(this, nextOffset, this);
        }

        /**
         * Sets item to self and waiter to null, to avoid garbage
         * retention after matching or cancelling. Uses relaxed writes
         * because order is already constrained in the only calling
         * contexts: item is forgotten only after volatile/atomic
         * mechanics that extract items.  Similarly, clearing waiter
         * follows either CAS or return from park (if ever parked;
         * else we don't care).
         */
        final void forgetContents() {
            UNSAFE.putObject(this, itemOffset, this);
            UNSAFE.putObject(this, waiterOffset, null);
        }

        /**
         * Returns true if this node has been matched, including the
         * case of artificial matches due to cancellation.
         */
        final boolean isMatched() {
            Object x = item;
            return (x == this) || ((x == null) == isData);
        }

        /**
         * Returns true if this is an unmatched request node.
         */
        final boolean isUnmatchedRequest() {
            return !isData && item == null;
        }

        /**
         * Returns true if a node with the given mode cannot be
         * appended to this node because this node is unmatched and
         * has opposite data mode.
         */
        final boolean cannotPrecede(boolean haveData) {
            boolean d = isData;
            Object x;
            return d != haveData && (x = item) != this && (x != null) == d;
        }

        /**
         * Tries to artificially match a data node -- used by remove.
         */
        final boolean tryMatchData() {
            // assert isData;
            Object x = item;
            if (x != null && x != this && casItem(x, null)) {
                LockSupport.unpark(waiter);
                return true;
            }
            return false;
        }

        private static final long serialVersionUID = -3375979862319811754L;

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long itemOffset;
        private static final long nextOffset;
        private static final long waiterOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = Node.class;
                itemOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("item"));
                nextOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("next"));
                waiterOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiter"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    // 头节点
    transient volatile Node head;

    // 尾节点
    private transient volatile Node tail;

    private transient volatile int sweepVotes;

    // CAS methods for fields
    private boolean casTail(Node cmp, Node val) {
        return UNSAFE.compareAndSwapObject(this, tailOffset, cmp, val);
    }

    private boolean casHead(Node cmp, Node val) {
        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    private boolean casSweepVotes(int cmp, int val) {
        return UNSAFE.compareAndSwapInt(this, sweepVotesOffset, cmp, val);
    }

    /** 放取元素的几种方式**/
    // 立即返回，用于非超时的poll()和tryTransfer()方法中
    private static final int NOW   = 0;
    // 异步，不会阻塞，用于放元素时，因为内部使用无界单链表存储元素，不会阻塞放元素的过程
    private static final int ASYNC = 1;
    // 同步，调用的时候如果没有匹配到会阻塞直到匹配到为止
    private static final int SYNC  = 2;
    // 超时，用于有超时的poll()和tryTransfer()方法中
    private static final int TIMED = 3;

    @SuppressWarnings("unchecked")
    static <E> E cast(Object item) {
        return (E) item;
    }

    // 此方法是入队出队核心方法
    private E xfer(E e, boolean haveData, int how, long nanos) {
        // 不允许放入空元素
        if (haveData && (e == null)) throw new NullPointerException();
        Node s = null;
        // 外层循环，自旋，失败就重试
        retry:
        for (;;) {
            // 下面这个for循环用于控制匹配的过程
            // 同一时刻队列中只会存储一种类型的节点
            // 从头节点开始尝试匹配，如果头节点被其它线程先一步匹配了
            // 就再尝试其下一个，直到匹配到为止，或者到队列中没有元素为止
            for (Node h = head, p = h; p != null;) {
                // p节点的模式
                boolean isData = p.isData;
                // p节点的值
                Object item = p.item;
                // p没有被匹配到
                if (item != p && (item != null) == isData) {
                    // 如果两者模式一样，则不能匹配，跳出循环后尝试入队
                    if (isData == haveData) break;
                    // 如果两者模式不一样，则尝试匹配
                    // 把p的值设置为e（如果是取元素则e是null，如果是放元素则e是元素值）
                    if (p.casItem(item, e)) {
                        // 匹配成功
                        // 逻辑比较复杂，用于控制多线程同时放取元素时出现竞争的情况的
                        for (Node q = p; q != h;) {
                            // 进入到这里可能是头节点已经被匹配，然后p会变成h的下一个节点
                            Node n = q.next;
                            // 如果head还没变，就把它更新成新的节点
                            // 并把它删除（forgetNext()会把它的next设为自己，也就是从单链表中删除了）
                            // 这时为什么要把head设为n呢？因为到这里了，肯定head本身已经被匹配掉了
                            // 而上面的p.casItem()又成功了，说明p也被当前这个元素给匹配掉了
                            // 所以需要把它们俩都出队列，让其它线程可以从真正的头开始，不用重复检查了
                            if (head == h && casHead(h, n == null ? q : n)) {
                                h.forgetNext();
                                break;
                            }
                            // 如果新的头节点为空，或者其next为空，或者其next未匹配，就重试
                            if ((h = head)   == null || (q = h.next) == null || !q.isMatched()) break;
                        }
                        // 唤醒p中等待的线程
                        LockSupport.unpark(p.waiter);
                        // 并返回匹配到的元素
                        return LinkedTransferQueue.<E>cast(item);
                    }
                }
                // p已经被匹配了或者尝试匹配的时候失败了
                // 也就是其它线程先一步匹配了
                // 这时候又分两种情况，p的next还没来得及修改，p的next指向了自己
                // 如果p的next已经指向了自己，就重新取head重试，否则就取其next重试
                Node n = p.next;
                p = (p != n) ? n : (h = head); // Use head if p offlist
            }
            // 到这里肯定是队列中存储的节点类型和自己一样
            // 或者队列中没有元素了
            // 就入队（不管放元素还是取元素都得入队）
            // 入队又分成四种情况：
            // NOW，立即返回，没有匹配到立即返回，不做入队操作
            // ASYNC，异步，元素入队但当前线程不会阻塞（相当于无界LinkedBlockingQueue的元素入队）
            // SYNC，同步，元素入队后当前线程阻塞，等待被匹配到
            // TIMED，有超时，元素入队后等待一段时间被匹配，时间到了还没匹配到就返回元素本身
            // 如果不是立即返回
            if (how != NOW) {
                // 新建s节点
                if (s == null) s = new Node(e, haveData);
                // 尝试入队
                Node pred = tryAppend(s, haveData);
                // 入队失败，重试
                if (pred == null) continue retry;
                // 如果不是异步（同步或者有超时）就等待被匹配
                if (how != ASYNC) return awaitMatch(s, pred, e, (how == TIMED), nanos);
            }
            return e; // not waiting
        }
    }


    private Node tryAppend(Node s, boolean haveData) {
        // 从tail开始遍历，把s放到链表尾端
        for (Node t = tail, p = t;;) {
            Node n, u;
            // 如果首尾都是null，说明链表中还没有元素
            if (p == null && (p = head) == null) {
                // 就让首节点指向s
                // 注意，这里插入第一个元素的时候tail指针并没有指向s
                if (casHead(null, s)) return s;
            }
            // 如果p无法处理，则返回null
            // 这里无法处理的意思是，p和s节点的类型不一样，不允许s入队
            // 比如，其它线程先入队了一个数据节点，这时候要入队一个非数据节点，就不允许队列中所有的元素都要保证是同一种类型的节点
            // 返回null后外面的方法会重新尝试匹配重新入队等
            else if (p.cannotPrecede(haveData)) return null;
            // 如果p的next不为空，说明不是最后一个节点
            // 则让p重新指向最后一个节点
            else if ((n = p.next) != null) p = p != t && t != (u = tail) ? (t = u) : (p != n) ? n : null;
            // 如果CAS更新s为p的next失败
            // 则说明有其它线程先一步更新到p的next了
            // 就让p指向p的next，重新尝试让s入队
            else if (!p.casNext(null, s)) p = p.next;
            else {
                // 到这里说明s成功入队了
                // 如果p不等于t，就更新tail指针
                // 还记得上面插入第一个元素时tail指针并没有指向新元素吗？
                // 这里就是用来更新tail指针的
                if (p != t) {
                    while ((tail != t || !casTail(t, s)) && (t = tail)!= null && (s = t.next) != null &&
                            (s = s.next) != null && s != t);
                }
                // 返回p，即s的前一个元素
                return p;
            }
        }
    }

    private E awaitMatch(Node s, Node pred, E e, boolean timed, long nanos) {
        // 如果是有超时的，计算其超时时间
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        Thread w = Thread.currentThread();
        // 自旋次数
        int spins = -1;
        // 随机数，随机让一些自旋的线程让出CPU
        ThreadLocalRandom randomYields = null;

        for (;;) {
            Object item = s.item;
            // 如果s元素的值不等于e，说明它被匹配到了
            if (item != e) {
                // assert item != s;
                // 把s的item更新为s本身
                // 并把s中的waiter置为空
                s.forgetContents();
                // 返回匹配到的元素
                return LinkedTransferQueue.<E>cast(item);
            }
            // 如果当前线程中断了，或者有超时的到期了
            // 就更新s的元素值指向s本身
            if ((w.isInterrupted() || (timed && nanos <= 0)) && s.casItem(e, s)) {
                // 尝试解除s与其前一个节点的关系
                // 也就是删除s节点
                unsplice(pred, s);
                // 返回元素的值本身，说明没匹配到
                return e;
            }
            // 如果自旋次数小于0，就计算自旋次数
            if (spins < 0) {
                // spinsFor()计算自旋次数
                // 如果前面有节点未被匹配就返回0
                // 如果前面有节点且正在匹配中就返回一定的次数，等待
                if ((spins = spinsFor(pred, s.isData)) > 0)
                    // 初始化随机数
                    randomYields = ThreadLocalRandom.current();
            }
            else if (spins > 0) {
                // 还有自旋次数就减1
                --spins;
                // 并随机让出CPU
                if (randomYields.nextInt(CHAINED_SPINS) == 0) Thread.yield();
            }
            else if (s.waiter == null) {
                // 更新s的waiter为当前线程
                s.waiter = w;
            }
            else if (timed) {
                // 如果有超时，计算超时时间，并阻塞一定时间
                nanos = deadline - System.nanoTime();
                if (nanos > 0L) LockSupport.parkNanos(this, nanos);
            }
            else {
                // 不是超时的，直接阻塞，等待被唤醒
                // 唤醒后进入下一次循环，走第一个if的逻辑就返回匹配的元素了
                LockSupport.park(this);
            }
        }
    }

    /**
     * Returns spin/yield value for a node with given predecessor and
     * data mode. See above for explanation.
     */
    private static int spinsFor(Node pred, boolean haveData) {
        if (MP && pred != null) {
            if (pred.isData != haveData)      // phase change
                return FRONT_SPINS + CHAINED_SPINS;
            if (pred.isMatched())             // probably at front
                return FRONT_SPINS;
            if (pred.waiter == null)          // pred apparently spinning
                return CHAINED_SPINS;
        }
        return 0;
    }

    /* -------------- Traversal methods -------------- */

    /**
     * Returns the successor of p, or the head node if p.next has been
     * linked to self, which will only be true if traversing with a
     * stale pointer that is now off the list.
     */
    final Node succ(Node p) {
        Node next = p.next;
        return (p == next) ? head : next;
    }

    /**
     * Returns the first unmatched node of the given mode, or null if
     * none.  Used by methods isEmpty, hasWaitingConsumer.
     */
    private Node firstOfMode(boolean isData) {
        for (Node p = head; p != null; p = succ(p)) {
            if (!p.isMatched())
                return (p.isData == isData) ? p : null;
        }
        return null;
    }

    /**
     * Version of firstOfMode used by Spliterator. Callers must
     * recheck if the returned node's item field is null or
     * self-linked before using.
     */
    final Node firstDataNode() {
        for (Node p = head; p != null;) {
            Object item = p.item;
            if (p.isData) {
                if (item != null && item != p)
                    return p;
            }
            else if (item == null)
                break;
            if (p == (p = p.next))
                p = head;
        }
        return null;
    }

    /**
     * Returns the item in the first unmatched node with isData; or
     * null if none.  Used by peek.
     */
    private E firstDataItem() {
        for (Node p = head; p != null; p = succ(p)) {
            Object item = p.item;
            if (p.isData) {
                if (item != null && item != p)
                    return LinkedTransferQueue.<E>cast(item);
            }
            else if (item == null)
                return null;
        }
        return null;
    }

    /**
     * Traverses and counts unmatched nodes of the given mode.
     * Used by methods size and getWaitingConsumerCount.
     */
    private int countOfMode(boolean data) {
        int count = 0;
        for (Node p = head; p != null; ) {
            if (!p.isMatched()) {
                if (p.isData != data)
                    return 0;
                if (++count == Integer.MAX_VALUE) // saturated
                    break;
            }
            Node n = p.next;
            if (n != p)
                p = n;
            else {
                count = 0;
                p = head;
            }
        }
        return count;
    }

    final class Itr implements Iterator<E> {
        private Node nextNode;   // next node to return item for
        private E nextItem;      // the corresponding item
        private Node lastRet;    // last returned node, to support remove
        private Node lastPred;   // predecessor to unlink lastRet

        /**
         * Moves to next node after prev, or first node if prev null.
         */
        private void advance(Node prev) {
            /*
             * To track and avoid buildup of deleted nodes in the face
             * of calls to both Queue.remove and Itr.remove, we must
             * include variants of unsplice and sweep upon each
             * advance: Upon Itr.remove, we may need to catch up links
             * from lastPred, and upon other removes, we might need to
             * skip ahead from stale nodes and unsplice deleted ones
             * found while advancing.
             */

            Node r, b; // reset lastPred upon possible deletion of lastRet
            if ((r = lastRet) != null && !r.isMatched())
                lastPred = r;    // next lastPred is old lastRet
            else if ((b = lastPred) == null || b.isMatched())
                lastPred = null; // at start of list
            else {
                Node s, n;       // help with removal of lastPred.next
                while ((s = b.next) != null &&
                       s != b && s.isMatched() &&
                       (n = s.next) != null && n != s)
                    b.casNext(s, n);
            }

            this.lastRet = prev;

            for (Node p = prev, s, n;;) {
                s = (p == null) ? head : p.next;
                if (s == null)
                    break;
                else if (s == p) {
                    p = null;
                    continue;
                }
                Object item = s.item;
                if (s.isData) {
                    if (item != null && item != s) {
                        nextItem = LinkedTransferQueue.<E>cast(item);
                        nextNode = s;
                        return;
                    }
                }
                else if (item == null)
                    break;
                // assert s.isMatched();
                if (p == null)
                    p = s;
                else if ((n = s.next) == null)
                    break;
                else if (s == n)
                    p = null;
                else
                    p.casNext(s, n);
            }
            nextNode = null;
            nextItem = null;
        }

        Itr() {
            advance(null);
        }

        public final boolean hasNext() {
            return nextNode != null;
        }

        public final E next() {
            Node p = nextNode;
            if (p == null) throw new NoSuchElementException();
            E e = nextItem;
            advance(p);
            return e;
        }

        public final void remove() {
            final Node lastRet = this.lastRet;
            if (lastRet == null)
                throw new IllegalStateException();
            this.lastRet = null;
            if (lastRet.tryMatchData())
                unsplice(lastPred, lastRet);
        }
    }

    /** A customized variant of Spliterators.IteratorSpliterator */
    static final class LTQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedTransferQueue<E> queue;
        Node current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        LTQSpliterator(LinkedTransferQueue<E> queue) {
            this.queue = queue;
        }

        public Spliterator<E> trySplit() {
            Node p;
            final LinkedTransferQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                ((p = current) != null || (p = q.firstDataNode()) != null) &&
                p.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                do {
                    Object e = p.item;
                    if (e != p && (a[i] = e) != null)
                        ++i;
                    if (p == (p = p.next))
                        p = q.firstDataNode();
                } while (p != null && i < n && p.isData);
                if ((current = p) == null)
                    exhausted = true;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                        (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                         Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> action) {
            Node p;
            if (action == null) throw new NullPointerException();
            final LinkedTransferQueue<E> q = this.queue;
            if (!exhausted &&
                ((p = current) != null || (p = q.firstDataNode()) != null)) {
                exhausted = true;
                do {
                    Object e = p.item;
                    if (e != null && e != p)
                        action.accept((E)e);
                    if (p == (p = p.next))
                        p = q.firstDataNode();
                } while (p != null && p.isData);
            }
        }

        @SuppressWarnings("unchecked")
        public boolean tryAdvance(Consumer<? super E> action) {
            Node p;
            if (action == null) throw new NullPointerException();
            final LinkedTransferQueue<E> q = this.queue;
            if (!exhausted &&
                ((p = current) != null || (p = q.firstDataNode()) != null)) {
                Object e;
                do {
                    if ((e = p.item) == p)
                        e = null;
                    if (p == (p = p.next))
                        p = q.firstDataNode();
                } while (e == null && p != null && p.isData);
                if ((current = p) == null)
                    exhausted = true;
                if (e != null) {
                    action.accept((E)e);
                    return true;
                }
            }
            return false;
        }

        public long estimateSize() { return Long.MAX_VALUE; }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @implNote
     * The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LTQSpliterator<E>(this);
    }

    /* -------------- Removal methods -------------- */

    /**
     * Unsplices (now or later) the given deleted/cancelled node with
     * the given predecessor.
     *
     * @param pred a node that was at one time known to be the
     * predecessor of s, or null or s itself if s is/was at head
     * @param s the node to be unspliced
     */
    final void unsplice(Node pred, Node s) {
        s.forgetContents(); // forget unneeded fields
        /*
         * See above for rationale. Briefly: if pred still points to
         * s, try to unlink s.  If s cannot be unlinked, because it is
         * trailing node or pred might be unlinked, and neither pred
         * nor s are head or offlist, add to sweepVotes, and if enough
         * votes have accumulated, sweep.
         */
        if (pred != null && pred != s && pred.next == s) {
            Node n = s.next;
            if (n == null ||
                (n != s && pred.casNext(s, n) && pred.isMatched())) {
                for (;;) {               // check if at, or could be, head
                    Node h = head;
                    if (h == pred || h == s || h == null)
                        return;          // at head or list empty
                    if (!h.isMatched())
                        break;
                    Node hn = h.next;
                    if (hn == null)
                        return;          // now empty
                    if (hn != h && casHead(h, hn))
                        h.forgetNext();  // advance head
                }
                if (pred.next != pred && s.next != s) { // recheck if offlist
                    for (;;) {           // sweep now if enough votes
                        int v = sweepVotes;
                        if (v < SWEEP_THRESHOLD) {
                            if (casSweepVotes(v, v + 1))
                                break;
                        }
                        else if (casSweepVotes(v, 0)) {
                            sweep();
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Unlinks matched (typically cancelled) nodes encountered in a
     * traversal from head.
     */
    private void sweep() {
        for (Node p = head, s, n; p != null && (s = p.next) != null; ) {
            if (!s.isMatched())
                // Unmatched nodes are never self-linked
                p = s;
            else if ((n = s.next) == null) // trailing node is pinned
                break;
            else if (s == n)    // stale
                // No need to also check for p == s, since that implies s == n
                p = head;
            else
                p.casNext(s, n);
        }
    }

    /**
     * Main implementation of remove(Object)
     */
    private boolean findAndRemove(Object e) {
        if (e != null) {
            for (Node pred = null, p = head; p != null; ) {
                Object item = p.item;
                if (p.isData) {
                    if (item != null && item != p && e.equals(item) &&
                        p.tryMatchData()) {
                        unsplice(pred, p);
                        return true;
                    }
                }
                else if (item == null)
                    break;
                pred = p;
                if ((p = p.next) == pred) { // stale
                    pred = null;
                    p = head;
                }
            }
        }
        return false;
    }

    /** 构造方法 2个构造方法都是无界的 **/
    public LinkedTransferQueue() {
    }

    public LinkedTransferQueue(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * 四个入队方法
     * 参数都是一模一样，都是异步的
     */
    public void put(E e) {
        // 异步模式，不会阻塞，不会超时
        // 因为是放元素，单链表存储，会一直往后加
        // 元素，  数据节点   异步    不超时
        xfer(e, true, ASYNC, 0);
    }

    public boolean offer(E e, long timeout, TimeUnit unit) {
        xfer(e, true, ASYNC, 0);
        return true;
    }

    public boolean offer(E e) {
        xfer(e, true, ASYNC, 0);
        return true;
    }

    public boolean add(E e) {
        xfer(e, true, ASYNC, 0);
        return true;
    }

    /** 定义了三个Transfer，阻塞，非阻塞，带超时时间 **/
    public boolean tryTransfer(E e) {
        // 立即返回
        return xfer(e, true, NOW, 0) == null;
    }

    public void transfer(E e) throws InterruptedException {
        // 同步模式
        if (xfer(e, true, SYNC, 0) != null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    public boolean tryTransfer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        // 有超时时间
        if (xfer(e, true, TIMED, unit.toNanos(timeout)) == null) return true;
        if (!Thread.interrupted()) return false;
        throw new InterruptedException();
    }

    /** 4个出队方法 上层Queue定义了remove方法
     *
     * **/
    public E take() throws InterruptedException {
        // 同步模式，会阻塞直到取到元素
        E e = xfer(null, false, SYNC, 0);
        if (e != null) return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        // 有超时时间
        E e = xfer(null, false, TIMED, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted()) return e;
        throw new InterruptedException();
    }

    public E poll() {
        // 立即返回，没取到元素返回null
        return xfer(null, false, NOW, 0);
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

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    public E peek() {
        return firstDataItem();
    }

    /**
     * Returns {@code true} if this queue contains no elements.
     *
     * @return {@code true} if this queue contains no elements
     */
    public boolean isEmpty() {
        for (Node p = head; p != null; p = succ(p)) {
            if (!p.isMatched())
                return !p.isData;
        }
        return true;
    }

    // 判断是否有消费者
    public boolean hasWaitingConsumer() {
        return firstOfMode(false) != null;
    }

    /**
     * Returns the number of elements in this queue.  If this queue
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     *
     * @return the number of elements in this queue
     */
    public int size() {
        return countOfMode(true);
    }

    // 查看消费者数量
    public int getWaitingConsumerCount() {
        return countOfMode(false);
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        return findAndRemove(o);
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        if (o == null) return false;
        for (Node p = head; p != null; p = succ(p)) {
            Object item = p.item;
            if (p.isData) {
                if (item != null && item != p && o.equals(item))
                    return true;
            }
            else if (item == null)
                break;
        }
        return false;
    }

    /**
     * Always returns {@code Integer.MAX_VALUE} because a
     * {@code LinkedTransferQueue} is not capacity constrained.
     *
     * @return {@code Integer.MAX_VALUE} (as specified by
     *         {@link java.util.concurrent.BlockingQueue#remainingCapacity()
     *         BlockingQueue.remainingCapacity})
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        for (E e : this)
            s.writeObject(e);
        s.writeObject(null);
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        for (;;) {
            @SuppressWarnings("unchecked")
            E item = (E) s.readObject();
            if (item == null)
                break;
            else
                offer(item);
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long sweepVotesOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = LinkedTransferQueue.class;
            headOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("head"));
            tailOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("tail"));
            sweepVotesOffset = UNSAFE.objectFieldOffset(k.getDeclaredField("sweepVotes"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
