package java.util.concurrent;

/**
 * 线程池顶级接口，只定义了一个执行无返回值任务的方法。
 */
public interface Executor {
    // 执行无返回值任务
    void execute(Runnable command);
}
