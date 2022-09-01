package com.sync;

import java.util.concurrent.locks.StampedLock;

/**
 * StampedLock的使用用例
 * 相比于读写锁，主要针对读锁尽心乐观锁控制，新增了一个模式乐观读
 * 乐观读时假定没有其它线程修改数据，读取完成后再检查下版本号有没有变化，没有变化就读取成功了
 * 读多写少的场景非常适合
 */
public class StampedLockPointer {
    private double x, y;
    private final StampedLock lock = new StampedLock();

    public void move(double _x, double _y) {
        //  加锁返沪一个版本号
        long stamp = lock.writeLock();
        try {
            x += _x;
            y += _y;
        } finally {
            lock.unlock(stamp);
        }
    }

    public double getDistance(int x, int y) {
        // 乐观读
        long stamp = lock.tryOptimisticRead();
        double currentX = x, currentY = y;
        // 验证版本号是否有变化
        if (!lock.validate(stamp)) {
            try {
                // 验证不通过，证明版本号变化了
                stamp = lock.readLock();
                currentX = x;
                currentY = y;
            } finally {
                // 读锁解除
                lock.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }

    void maveifOrigin(double newx, double newy) {
        // 获取悲观读锁
        long stamp = lock.readLock();
        try {
            while (x == 0.0 && y == 0.0) {
                // 转为写锁
                long ws = lock.tryConvertToWriteLock(stamp);
                // 转换成功
                if (ws != 0L) {
                    stamp = ws;
                    x = newx;
                    y=newy;
                    break;
                } else {
                    // 转换失败
                    lock.unlockRead(stamp);
                    // 获取写锁
                    stamp = lock.writeLock();
                }
            }
        } finally {
            lock.unlock(stamp);
        }
    }

}
