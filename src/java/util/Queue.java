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

package java.util;

/**
 * Queue是一种叫做队列的数据结构，队列是遵循着一定原则的入队出队操作的集合
 * 一般来说，入队是在队列尾添加元素，出队是在队列头删除元素，但是，也不一定，比如优先级队列的原则就稍微有些不同
 * 跟存储结构有关，优先级用堆存储，出队入队都是在头
 */
public interface Queue<E> extends Collection<E> {
    /**
     *
     */
    boolean add(E e);

    /**
     *
     */
    boolean offer(E e);

    /**
     *
     */
    E remove();

    /**
     *
     */
    E poll();

    /**
     *
     */
    E element();

    /**
     *
     */
    E peek();
}
