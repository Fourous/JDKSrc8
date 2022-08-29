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
 * Written by Doug Lea and Josh Bloch with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util;

/**
 * Deque是对Queue的增强,例如增加Stack Collection方法
 * @param <E>
 */
public interface Deque<E> extends Queue<E> {
    /**
     * 添加元素到队列头
     */
    void addFirst(E e);

    /**
     * 添加元素到队列尾
     */
    void addLast(E e);

    /**
     * 添加元素到队列头
     */
    boolean offerFirst(E e);

    /**
     * 添加元素到队列尾
     */
    boolean offerLast(E e);

    /**
     * 从队列头移除元素
     */
    E removeFirst();

    /**
     * 从队列尾移除元素
     */
    E removeLast();

    /**
     * 从队列头移除元素
     */
    E pollFirst();

    /**
     * 从队列尾移除元素
     */
    E pollLast();

    /**
     * 查看队列头元素
     */
    E getFirst();

    /**
     * 查看队列尾元素
     */
    E getLast();

    /**
     * 查看队列头元素
     */
    E peekFirst();

    /**
     * 查看队列尾元素
     */
    E peekLast();

    /**
     * 从队列头向后遍历移除指定元素
     */
    boolean removeFirstOccurrence(Object o);

    /**
     * 从队列尾向前遍历移除指定元素
     */
    boolean removeLastOccurrence(Object o);

    // *** Queue methods ***
    // 队列中的方法

    /**
     * 添加元素，等于addLast(e)
     */
    boolean add(E e);

    /**
     * 添加元素，等于offerLast(e)
     */
    boolean offer(E e);

    /**
     * 移除元素，等于removeFirst()
     */
    E remove();

    /**
     * 移除元素，等于pollFirst()
     */
    E poll();

    /**
     * 查看元素，等于getFirst()
     */
    E element();

    /**
     * 查看元素，等于peekFirst()
     */
    E peek();


    // *** Stack methods ***
    // 堆栈方法

    /**
     * 入栈，等于addFirst(e)
     */
    void push(E e);

    /**
     * 出栈，等于removeFirst()
     */
    E pop();


    // *** Collection methods ***
    // Collection 方法

    /**
     * 删除指定元素，等于removeFirstOccurrence(o)
     */
    boolean remove(Object o);

    /**
     * 检查是否包含某个元素
     */
    boolean contains(Object o);

    /**
     * 元素个数
     */
    public int size();

    /**
     * 迭代器
     */
    Iterator<E> iterator();

    /**
     * 反向迭代器
     */
    Iterator<E> descendingIterator();

}
