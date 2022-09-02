package java.util.concurrent;

/**
 * timeQueue delayQueue
 * 继承自Comparable的接口，并且定义了一个getDelay()方法，用于表示还有多少时间到期，到期了应返回小于等于0的数值
 */
public interface Delayed extends Comparable<Delayed> {

    /**
     * 还有多少时间到期，到期了应返回小于等于0的数值
     * 目前JDK内部实现都是采用内部类实现
     */
    long getDelay(TimeUnit unit);
}
