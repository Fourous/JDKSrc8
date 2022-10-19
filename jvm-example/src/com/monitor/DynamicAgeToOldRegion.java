package com.monitor;

/**
 * 模拟对象进入老年代，动态年龄规则
 * 1.躲过 15 次 gc，达到 15 岁高龄之后进入老年代
 * 2.如果 Survivor 区域内年龄1+年龄2+年龄3+年龄n的对象总和大于 Survivor 区的 50%，此时年龄n以上的对象会进入老年代，不一定要达到 15 岁
 * 3.如果一次 Young GC 后存活对象太多无法放入Survivor 区，此时直接计入老年代
 * 4.大对象直接进入老年代
 *
 * eden 区 8m、s1 1m、s2 1m、老年代 10m，堆总大小 20m，大对象 10m 进老年代
 * -XX:NewSize=10485760         新生代10MB
 * -XX:MaxNewSize=10485760
 * -XX:InitialHeapSize=20971520  初始堆大小20MB
 * -XX:MaxHeapSize=20971520      最大堆大小
 * -XX:SurvivorRatio=8            Eden 8MB S0 1MB S1 1MB
 * -XX:MaxTenuringThreshold=15         对象 15 岁进老年代
 * -XX:PretenureSizeThreshold=10485760  最大对象直接老年代 10MB
 * -XX:+UseParNewGC                     ParNew
 * -XX:+UseConcMarkSweepGC              CMS
 * -XX:+PrintGCDetails
 * -XX:+PrintGCTimeStamps
 * -Xloggc:ogc.log
 *
 */
public class DynamicAgeToOldRegion {
    public static void main(String[] args) {
        byte[] arr = new byte[2 * 1024 * 1024]; // 分配2MB
        arr = new byte[2 * 1024 * 1024];     // 分配2MB
        arr = new byte[2 * 1024 * 1024];    // 分配2MB
        arr = null;                         // 6MB垃圾

        byte[] arr2 = new byte[700 * 1024];     // 128KB

        byte[] arr3 = new byte[2 * 1024 * 1024];    // 2MB YGC

        arr3 = new byte[2 * 1024 * 1024]; // 2MB
        arr3 = new byte[2 * 1024 * 1024]; // 2MB

        arr3 = new byte[128 * 1024];  //128KB
        arr3 = null;                // 6MB+128KB垃圾
        byte[] arr4 = new byte[2 * 1024 * 1024]; // 触发第二次YGC
//        // 第二次 gc，满足动态年龄判断规则，全部进老年代，总共 790k
    }
}
