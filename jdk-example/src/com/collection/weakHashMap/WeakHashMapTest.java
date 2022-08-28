package com.collection.weakHashMap;

import java.util.WeakHashMap;

public class WeakHashMapTest {
    public static void main(String[] args) {
        WeakHashMap<String, Integer> weakHashMap = new WeakHashMap<>(3);
        // new String()声明的变量才是弱引用，使用"6"这种声明方式会一直存在于常量池中，不会被清理
        weakHashMap.put(new String("1"), 1);
        weakHashMap.put(new String("2"), 2);
        weakHashMap.put(new String("3"), 3);

        weakHashMap.put("6", 6);

        // 使用key强引用3
        String key = "";
        for(String s: weakHashMap.keySet()) {
            if(s.equals("3")) {
                key = s;
            }
        }
        // 可以看到，新建元素是在最前面的
        System.out.println(weakHashMap);
        System.gc();

        weakHashMap.put(new String("4"), 4);
        System.out.println(weakHashMap);

        key = null;
        System.gc();
        System.out.println(weakHashMap);
    }
}
