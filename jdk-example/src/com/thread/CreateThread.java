package com.thread;

/**
 * 本质上，创建线程只有一种方式，就是实现Runnable方式
 */
public class CreateThread {
    public static void main(String[] args) {
        new Thread(()->{
            System.out.println("Runnable " + Thread.currentThread().getName());
        }){
            @Override
            public void run() {
                System.out.println("Thread " + getName());
            }
        }.start();
    }
}
