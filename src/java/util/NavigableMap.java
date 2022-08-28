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
 * Written by Doug Lea and Josh Bloch with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util;

/**
 * 对SortMap的增强，定义了一些返回离目标key最近的元素的方法
 */
public interface NavigableMap<K,V> extends SortedMap<K,V> {
    /**
     * 小于给定Key的最大节点
     */
    Map.Entry<K,V> lowerEntry(K key);

    /**
     * 小于给定Key的最大Key
     */
    K lowerKey(K key);

    /**
     * 小于等于Key的最大节点
     */
    Map.Entry<K,V> floorEntry(K key);

    /**
     * 小于等于给定key的最大key
     */
    K floorKey(K key);

    /**
     * 大于等于给定key的最小节点
     */
    Map.Entry<K,V> ceilingEntry(K key);

    /**
     * 大于等于给定key的最小key
     */
    K ceilingKey(K key);

    /**
     * 大于给定key的最小节点
     */
    Map.Entry<K,V> higherEntry(K key);

    /**
     * 大于给定key的最小key
     */
    K higherKey(K key);

    /**
     * 最小的节点
     */
    Map.Entry<K,V> firstEntry();

    /**
     * 最大的节点
     */
    Map.Entry<K,V> lastEntry();

    /**
     * 弹出最小的节点
     */
    Map.Entry<K,V> pollFirstEntry();

    /**
     * 弹出最大的节点
     */
    Map.Entry<K,V> pollLastEntry();

    /**
     * 返回倒序的map
     */
    NavigableMap<K,V> descendingMap();

    /**
     * 返回有序的key集合
     */
    NavigableSet<K> navigableKeySet();

    /**
     * 返回倒序的key集合
     */
    NavigableSet<K> descendingKeySet();

    /**
     * 返回从fromKey到toKey的子map，是否包含起止元素可以自己决定
     */
    NavigableMap<K,V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive);

    /**
     * 返回小于toKey的子map，是否包含toKey自己决定
     */
    NavigableMap<K,V> headMap(K toKey, boolean inclusive);

    /**
     * 返回大于fromKey的子map，是否包含fromKey自己决定
     */
    NavigableMap<K,V> tailMap(K fromKey, boolean inclusive);

    /**
     * 等价于subMap(fromKey, true, toKey, false)
     */
    SortedMap<K,V> subMap(K fromKey, K toKey);

    /**
     * 等价于headMap(toKey, false)
     */
    SortedMap<K,V> headMap(K toKey);

    /**
     * 等价于tailMap(fromKey, true)
     */
    SortedMap<K,V> tailMap(K fromKey);
}
