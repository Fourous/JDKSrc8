package com.atomic.unsafe;

import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;

/**
 * 对象的6种实例化方法
 * 构造方法
 * Class newInstance
 * 反射
 * 克隆
 * 反序列化
 * Unsafe
 */
public class UserInstance {
    private static Unsafe unsafe;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. 构造函数
        User user1 = new User();
        // 2. Class，里面实际也是反射
        User user2 = User.class.newInstance();
        // 3. 反射
        User user3 = User.class.getConstructor().newInstance();
        // 4. 克隆
        User user4 = (User) user1.clone();
        // 5. 反序列化
        User user5 = unSerializable(user1);
        // 6. Unsafe
        User user6 = (User) unsafe.allocateInstance(User.class);

        System.out.println(user1.age);
        System.out.println(user2.age);
        System.out.println(user3.age);
        System.out.println(user4.age);
        System.out.println(user5.age);
        System.out.println(user6.age);

    }

    private static User unSerializable(User user) throws Exception {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("/tmp/user.txt"));
        oos.writeObject(user);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("/tmp/user.txt"));
        User userO = (User) ois.readObject();
        ois.close();
        return userO;
    }

    static class User implements Cloneable, Serializable {
        private int age;

        public User() {
            this.age = 10;
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }
}
