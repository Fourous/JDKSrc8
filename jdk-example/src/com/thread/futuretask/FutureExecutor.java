package com.thread.futuretask;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * 我们需要给现有的线程池增加一种新的能力，根据单一职责原则，我们定义一个新的接口来承载这种能力
 */
public interface FutureExecutor extends Executor {
    <T> Future<T> submit(Callable<T> command);
}
