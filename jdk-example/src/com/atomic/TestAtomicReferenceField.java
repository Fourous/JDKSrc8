package com.atomic;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 原子更新对象中的字段，可以更新对象中指定字段名称的字段
 */
public class TestAtomicReferenceField {
    public static void main(String[] args) {
        AtomicReferenceFieldUpdater<User, String> updateName = AtomicReferenceFieldUpdater
                .newUpdater(User.class, String.class, "name");
        AtomicIntegerFieldUpdater<User> updateAge = AtomicIntegerFieldUpdater
                .newUpdater(User.class, "age");

        User user = new User("1", 1);
        updateName.compareAndSet(user, "1", "read");
        updateAge.compareAndSet(user,1,666);
        updateAge.incrementAndGet(user);
        System.out.println(user);
    }

    /**
     * 这里的字段必须是volatile类型的，不然会直接报错
     */
    public static class User{
        volatile String name;
        volatile int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", age='" + age + '\'' +
                    '}';
        }
    }
}
