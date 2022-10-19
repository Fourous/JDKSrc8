package com.oom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模拟OOM
 * -XX:NewSize=5242880
 * -XX:MaxNewSize=5242880
 * -XX:SurvivorRatio=8
 * -XX:InitialHeapSize=10485760
 * -XX:MaxHeapSize=10485760
 * -XX:+UseParNewGC
 * -XX:+UseConcMarkSweepGC
 * ‐XX:+HeapDumpOnOutOfMemoryError
 * ‐XX:HeapDumpPath=jvm.dump
 * ‐XX:+PrintGCDetails
 */
public class OOMTest {
    public static List<Object> list = new ArrayList<>();
    public static void main(String[] args) {
        List<Object> list = new ArrayList<>();
        int i=0;
        int j=0;
        while (true) {
            list.add(new User(UUID.randomUUID().toString(),i++));
            new User(UUID.randomUUID().toString(), j--);
        }
    }

    public static class User {
        String name;
        int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
