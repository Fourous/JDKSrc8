package com.collection.linkedHashMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUStrategy {
    public static void main(String[] args) {
        LRU<Integer,Integer> lru = new LRU<>(5, 0.75f);
        lru.put(1,1);
        lru.put(2,2);
        lru.put(3,3);
        lru.put(4,4);
        lru.put(5,5);
        lru.put(6,6);
        lru.put(7,7);
        System.out.println(lru.get(3));
        lru.put(8,888);
        System.out.println(lru);
    }

    static class LRU<K,V> extends LinkedHashMap<K,V> {
        // 保存缓存容量
        private int capacity;

        public LRU(int capacity, float factor) {
            super(capacity, factor, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            // 当元素个数大于容量时候，移除元素
            return size() > capacity;
        }
    }
}
