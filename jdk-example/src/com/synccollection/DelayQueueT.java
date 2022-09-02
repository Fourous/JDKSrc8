package com.synccollection;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 *
 */
public class DelayQueueT {
    public static void main(String[] args) {
        DelayQueue<Message> delayQueue = new DelayQueue();
        long current = System.currentTimeMillis();

        IntStream.range(0, 5).forEach(i -> {
            new Thread(() -> {
                while (true) {
                    try {
                        System.out.println(delayQueue.take().deadline - current);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        });

        delayQueue.add(new Message(current + 5000));
        delayQueue.add(new Message(current + 8000));
        delayQueue.add(new Message(current + 2000));
        delayQueue.add(new Message(current + 1000));
        delayQueue.add(new Message(current + 7000));
    }

    static class Message implements Delayed {
        long deadline;

        public Message(long deadline) {
            this.deadline = deadline;
        }

        /**
         * 还有多少时间到期，到期返回小于0的long值
         */
        @Override
        public long getDelay(TimeUnit unit) {
            return deadline - System.currentTimeMillis();
        }

        @Override
        public int compareTo(Delayed o) {
            return (int) (getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public String toString() {
            return "Message{" +
                    "deadline=" + deadline +
                    '}';
        }
    }
}
