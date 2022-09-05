package java.util.concurrent;

/**
 * 对ExecutorService做了一些扩展，增加一些定时任务相关的功能，主要包含两大类：执行一次，重复多次执行
 */
public interface ScheduledExecutorService extends ExecutorService {

    /**
     * 在指定延时后执行一次
     */
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    /**
     * 在指定延时后执行一次
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * 在指定延时后开始执行，并在之后以指定时间间隔重复执行（间隔不包含任务执行的时间）
     * 相当于之后的延时以任务开始计算
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * 在指定延时后开始执行，并在之后以指定延时重复执行（间隔包含任务执行的时间）
     * 相当于之后的延时以任务结束计算
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
