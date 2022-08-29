/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * TreeSet实现了NavigableSet接口，所以它是有序的
 * （1）TreeSet底层使用NavigableMap存储元素；
 * （2）TreeSet是有序的；
 * （3）TreeSet是非线程安全的；
 * （4）TreeSet实现了NavigableSet接口，而NavigableSet继承自SortedSet接口；
 * （5）TreeSet实现了SortedSet接口；
 *
 *  TreeSet有序性是实现了NavigableMap也就继承了SortMap，是传进来的comparable，自然排序的
 */
public class TreeSet<E> extends AbstractSet<E> implements NavigableSet<E>, Cloneable, java.io.Serializable {
    /**
     * 元素存储在NavigableMap中
     * 这个NavigableMap不一定都是TreeMap
     * 例如在TreeMap中有定义了一个AscendingSubMap，这个类是不是TreeMap的，可以说有关系但不都是TreeMap
     */
    private transient NavigableMap<E,Object> m;

    /**
     * 虚拟元素, 用来作为value存储在map中
     */
    private static final Object PRESENT = new Object();

    /**
     * 传入NavigableMap
     * 这里不是深拷贝,如果外面的map有增删元素也会反映到这里
     * 缺省构造方法，说明有同包会调用这里
     */
    TreeSet(NavigableMap<E,Object> m) {
        this.m = m;
    }

    /**
     * 这里又采用TreeMap初始化
     */
    public TreeSet() {
        this(new TreeMap<E,Object>());
    }

    /**
     * 使用带comparator的TreeMap初始化
     */
    public TreeSet(Comparator<? super E> comparator) {
        this(new TreeMap<>(comparator));
    }

    /**
     * 将集合c中的所有元素添加的TreeSet中
     */
    public TreeSet(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * 将SortedSet中的所有元素添加到TreeSet中
     */
    public TreeSet(SortedSet<E> s) {
        this(s.comparator());
        addAll(s);
    }

    /**
     * 迭代器
     */
    public Iterator<E> iterator() {
        return m.navigableKeySet().iterator();
    }

    /**
     * JDK6 逆序迭代器
     */
    public Iterator<E> descendingIterator() {
        return m.descendingKeySet().iterator();
    }

    /**
     * JDK6 以逆序返回一个新的TreeSet
     */
    public NavigableSet<E> descendingSet() {
        return new TreeSet<>(m.descendingMap());
    }

    /**
     * 元素个数
     */
    public int size() {
        return m.size();
    }

    /**
     * 判空
     */
    public boolean isEmpty() {
        return m.isEmpty();
    }

    /**
     * 判包含
     */
    public boolean contains(Object o) {
        return m.containsKey(o);
    }

    /**
     * 添加元素, 调用map的put()方法, value为PRESENT
     */
    public boolean add(E e) {
        return m.put(e, PRESENT)==null;
    }

    /**
     * 删除元素
     * Map remove删除元素会返回元素的Value
     */
    public boolean remove(Object o) {
        return m.remove(o)==PRESENT;
    }

    /**
     * 清空元素
     */
    public void clear() {
        m.clear();
    }

    /**
     * 添加集合元素
     */
    public  boolean addAll(Collection<? extends E> c) {
        // 可以看到，在满足一定条件情况可以用TreeMap里面添加元素的方法
        if (m.size()==0 && c.size() > 0 && c instanceof SortedSet && m instanceof TreeMap) {
            SortedSet<? extends E> set = (SortedSet<? extends E>) c;
            TreeMap<E,Object> map = (TreeMap<E, Object>) m;
            Comparator<?> cc = set.comparator();
            Comparator<? super E> mc = map.comparator();
            if (cc==mc || (cc != null && cc.equals(mc))) {
                map.addAllForTreeSet(set, PRESENT);
                return true;
            }
        }
        // 如果不满足此判断，则调用父类的添加方法
        return super.addAll(c);
    }

    /**
     * 子set（NavigableSet中的方法）
     */
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return new TreeSet<>(m.subMap(fromElement, fromInclusive, toElement, toInclusive));
    }

    /**
     * 头set（NavigableSet中的方法）
     */
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return new TreeSet<>(m.headMap(toElement, inclusive));
    }

    /**
     * 尾set（NavigableSet中的方法）
     */
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return new TreeSet<>(m.tailMap(fromElement, inclusive));
    }

    /**
     * 子set（SortedSet接口中的方法）
     * 下面几个方法都是SortSet返回，实现了SortSet接口
     */
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    /**
     * 头set（SortedSet接口中的方法）
     */
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    /**
     * 尾set（SortedSet接口中的方法）
     */
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    /**
     * 比较器
     */
    public Comparator<? super E> comparator() {
        return m.comparator();
    }

    /**
     * 返回最小的元素
     */
    public E first() {
        return m.firstKey();
    }

    /**
     * 返回最大的元素
     */
    public E last() {
        return m.lastKey();
    }

    // NavigableSet API methods

    /**
     * 返回小于e的最大的元素
     */
    public E lower(E e) {
        return m.lowerKey(e);
    }

    /**
     * 返回小于等于e的最大的元素
     */
    public E floor(E e) {
        return m.floorKey(e);
    }

    /**
     * 返回大于等于e的最小的元素
     */
    public E ceiling(E e) {
        return m.ceilingKey(e);
    }

    /**
     * 返回大于e的最小的元素
     */
    public E higher(E e) {
        return m.higherKey(e);
    }

    /**
     * 弹出最小的元素
     */
    public E pollFirst() {
        Map.Entry<E,?> e = m.pollFirstEntry();
        return (e == null) ? null : e.getKey();
    }

    /**
     * 弹出最大的元素
     */
    public E pollLast() {
        Map.Entry<E,?> e = m.pollLastEntry();
        return (e == null) ? null : e.getKey();
    }

    /**
     * 克隆方法
     */
    @SuppressWarnings("unchecked")
    public Object clone() {
        TreeSet<E> clone;
        try {
            clone = (TreeSet<E>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }

        clone.m = new TreeMap<>(m);
        return clone;
    }

    /**
     * TreeSet序列化方法
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out Comparator
        s.writeObject(m.comparator());

        // Write out size
        s.writeInt(m.size());

        // Write out all elements in the proper order.
        for (E e : m.keySet())
            s.writeObject(e);
    }

    /**
     * 序列化写出方法
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden stuff
        s.defaultReadObject();

        // Read in Comparator
        @SuppressWarnings("unchecked")
        Comparator<? super E> c = (Comparator<? super E>) s.readObject();

        // Create backing TreeMap
        TreeMap<E,Object> tm = new TreeMap<>(c);
        m = tm;

        // Read in size
        int size = s.readInt();

        tm.readTreeSet(size, s, PRESENT);
    }

    /**
     * JDK8 可分割的迭代器
     */
    public Spliterator<E> spliterator() {
        return TreeMap.keySpliteratorFor(m);
    }

    private static final long serialVersionUID = -2479143000061671589L;
}
