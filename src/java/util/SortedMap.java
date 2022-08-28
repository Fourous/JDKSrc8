/*
 * Copyright (c) 1998, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * 规定了元素可以按key的大小来遍历，它定义了一些返回部分map的方法
 */
public interface SortedMap<K,V> extends Map<K,V> {
    /**
     * key的比较器
     */
    Comparator<? super K> comparator();

    /**
     * 返回fromKey（包含）到toKey（不包含）之间的元素组成的子map
     */
    SortedMap<K,V> subMap(K fromKey, K toKey);

    /**
     * 返回小于toKey（不包含）的子map
     */
    SortedMap<K,V> headMap(K toKey);

    /**
     * 返回大于等于fromKey（包含）的子map
     */
    SortedMap<K,V> tailMap(K fromKey);

    /**
     * 返回最小的key
     */
    K firstKey();

    /**
     * 返回最大的key
     */
    K lastKey();

    /**
     * 返回key集合
     */
    Set<K> keySet();

    /**
     * 返回value集合
     */
    Collection<V> values();

    /**
     * 返回节点集合
     */
    Set<Map.Entry<K, V>> entrySet();
}
