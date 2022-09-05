package com.thread;

import java.util.concurrent.*;

// 利用forkJoinPool实现相加
public class ForkJoinPoolSum {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        int length = 10000;
        long addr[] = new long[length];
        for (int i = 0; i < length; i++) {
            addr[i] = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        }
        long start = System.currentTimeMillis();
        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
        ForkJoinTask<Long> forkJoinTask = forkJoinPool.submit(new SumTask(addr, 0, length));
        Long sum = forkJoinTask.get();
        System.out.println("cost time: " + (System.currentTimeMillis() - start));
        System.out.println(sum);
    }

    private static class SumTask extends RecursiveTask<Long> {
        private long[] addr;
        private int from;
        private int to;

        public SumTask(long[] addr, int from, int to) {
            this.addr = addr;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Long compute() {
            long result = 0;
            if (to - from < 1000) {
                for (int i = from; i < to; i++) {
                    result += (addr[i] / 3 * 3 / 3 * 3 / 3 * 3);
                }
                return result;
            }
            int middle = (from + to) / 2;
            SumTask left = new SumTask(addr, from, middle);
            SumTask right = new SumTask(addr, middle, to);
            left.fork();
            Long rightResult = right.compute();
            Long leftResult = left.join();
            return leftResult + rightResult;
        }
    }
}
