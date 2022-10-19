package com.dealock;

import com.oom.OOMTest;

/**
 * 利用stack查看高CPU
 */
public class HighCpuStack {
    public static final int initData = 666;

    public static int compute() {
        int a = 10;
        int b = 20;
        return (a + b) / 3 * 3 / 3 * 3 / 3 * 3;
    }

    public static int compute(int a) {
        return a * 10;
    }

    public static void main(String[] args) {
        while (true) {
            compute();
        }

    }
}
