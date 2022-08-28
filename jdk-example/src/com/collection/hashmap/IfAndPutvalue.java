package com.collection.hashmap;

/**
 * 判断里面加入赋值
 * 对于括号里面判断，与和或都是判断前面是否满足即可，只要满足就完成赋值
 * 如果前面满足后面则不判断，则没办法完成赋值
 */
public class IfAndPutvalue {
    public static void main(String[] args) {
        judgeAndOr();

    }

    static void judgeAndOr() {
        int a = 5;
        int b = 0;
        int c = 10;
        if ((a = 10) > 5 && b == 0 && c < 9) {
            System.out.println("a=" + a + "b=" + b + "c=" + c);
        } else if ((c = 0) < 5 || (b = 10) > 5 && a == 10) {
            System.out.println("a=" + a + "b=" + b + "c=" + c);
        }
    }

    static void judgeIf() {
        int a = 5;
        int b = 10;
        if (a > 4 && (b = 6) > 10) {
            System.out.println("a=" + a + "b=" + b);
        } else if ((a = 10) > 5 && (b = 7) > 8) {
            System.out.println("a=" + a + "b=" + b);
        } else if ((a + b) < 15 || (b = 33) > 10) {
            System.out.println("a=" + a + "b=" + b);
        }
//        else if (a > 15 && (b = 9) > 7) {
//            System.out.println("a=" + a + "b=" + b);
//        }
    }
}
