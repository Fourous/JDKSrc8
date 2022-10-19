package com.monitor;

/**
 * -XX:NewSize=10485760   新生代10MB
 * -XX:MaxNewSize=10485760
 * -XX:InitialHeapSize=20971520 堆大小20MB
 * -XX:MaxHeapSize=20971520
 * -XX:SurvivorRatio=8   Eden 8MB S0 1MB S1 1MB
 * -XX:MaxTenuringThreshold=15      年龄15
 * -XX:PretenureSizeThreshold=3145728   最大对象3MB
 * -XX:+UseParNewGC                 ParNew
 * -XX:+UseConcMarkSweepGC          CMS
 * -XX:+PrintGCDetails
 * -XX:+PrintGCTimeStamps
 * -Xloggc:full.log
 *
 * fullGC 场景
 * 1.老年代空间不足
 * 2.触发Young GC之前，可能老年代可用空间小于了历次Young GC后升入老年代的对象的平均大小，就会在 Young GC 之前，提前触发 Full GC
 * 3.老年代被使用率达到了92%的阈值，也会触发 Full GC
 */
public class FullGCDemo {
    public static void main(String[] args) {
        byte[] arr1 = new byte[4 * 1024 * 1024];    // 分配4MB 超过3M进入老年代
        arr1 = null;                                //4MB垃圾老年代 还剩6M
        byte[] arr2 = new byte[2 * 1024 * 1024];    // 2MB
        byte[] arr3 = new byte[2 * 1024 * 1024];    // 2MB
        byte[] arr4 = new byte[2 * 1024 * 1024];    // 2MB
        byte[] arr5 = new byte[128 * 1024];         // 128KB 6M+128KB
        byte[] arr6 = new byte[2 * 1024 * 1024];    // 2MB YGC
    }
}
