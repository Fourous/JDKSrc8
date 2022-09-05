package java.util.concurrent;

/**
 * 定义线程池队列处理策略
 * ThreadPoolExecutor 内部类实现
 */
public interface RejectedExecutionHandler {

    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
