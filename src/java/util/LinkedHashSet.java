/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

/**
 * LinkedHashSet继承自HashSet
 * LinkedHashSet是有序的，按照插入顺序排序
 * 其实这个dummy没有用到，创建时候直接是按照默认方式创建的 new LinkedHashMap存储
 * dummy应该就是为了实现多态
 */
public class LinkedHashSet<E> extends HashSet<E> implements Set<E>, Cloneable, java.io.Serializable {

    private static final long serialVersionUID = -2851667679971038690L;

    /**
     * 传入容量和装载因子
     */
    public LinkedHashSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }

    /**
     * 默认装载因子为0。75
     */
    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity, .75f, true);
    }

    /**
     * 默认容量默认装载因子
     */
    public LinkedHashSet() {
        super(16, .75f, true);
    }

    /**
     * 传入Collection
     * 不过这里计算方式变了，目前不清楚原因
     */
    public LinkedHashSet(Collection<? extends E> c) {
        super(Math.max(2*c.size(), 11), .75f, true);
        addAll(c);
    }

    /**
     * 这个也是JDK8新增功能
     * 可以分割的迭代器，多线程并发迭代
     */
    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
    }
}
