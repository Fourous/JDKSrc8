package com.monitor;

/**
 * 模拟YGC: eden 区 4mb、s1 0.5mb、s2 0.5mb、老年代 5mb
 * -XX:NewSize=5242880          初始新生代和最大新生代5MB
 * -XX:MaxNewSize=5242880
 * -XX:InitialHeapSize=10485760 初始堆和最大堆都是10MB
 * -XX:MaxHeapSize=10485760
 * -XX:SurvivorRatio=8                  SurvivorRatio Eden:S0:S1
 * -XX:PretenureSizeThreshold=10485760  大对象阈值10MB
 * -XX:+UseParNewGC                 ParNewGC
 * -XX:+UseConcMarkSweepGC
 * -XX:+PrintGCDetails              打印详细的 gc 日志
 * -XX:+PrintGCTimeStamps           打印每次 GC 发生的时间
 * -Xloggc:ygc.log
 */
public class YGCDemo {
    public static void main(String[] args) {
        byte[] arr = new byte[1024 * 1024];     // 分配了 1mb 到 eden
        arr = new byte[1024 * 1024];            // 分配了 1mb 到 eden
        arr = new byte[1024 * 1024];            // 分配了 1mb 到 eden
        arr = null;                             // 三个对象都成垃圾
        byte[] arr2 = new byte[2*1024 * 1024]; // 此时Eden还剩1MB，arr2放不下，触发YGC
    }
}
