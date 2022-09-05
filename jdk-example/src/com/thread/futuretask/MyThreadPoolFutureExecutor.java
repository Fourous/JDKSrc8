package com.thread.futuretask;

import com.thread.threadpool.MyThread;
import com.thread.threadpool.RejectPolicy;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class MyThreadPoolFutureExecutor extends MyThread implements FutureExecutor, Executor {
    public MyThreadPoolFutureExecutor(String name, int coreSize, int maxSize, BlockingQueue<Runnable> taskQueue, RejectPolicy rejectPolicy) {
        super(name, coreSize, maxSize, taskQueue, rejectPolicy);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        // 包装成将来获取返回值的任务
        FutureTask<T> futureTask = new FutureTask(task);
        // 还是使用原来的执行能力
        execute(futureTask);
        // 返回将来的任务，只需要返回其get返回值的能力即可
        // 所以这里返回的是Future而不是FutureTask类型
        return futureTask;
    }
}
