package com.collection.arrayList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * ArrayList中的BatchRemove采用双指针原地进行删除
 */
public class BatchRemove {
    static Object[] elementData = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    static int size = elementData.length;

    public static void main(String[] args) {
        List removeList = Arrays.asList(1, 2, 3, 4);
        System.out.println(Arrays.toString(elementData));
        batchRemove(removeList, false);
        //batchRemove(removeList, true);
        System.out.println(Arrays.toString(elementData));
    }

    private static boolean batchRemove(Collection<?> c, boolean complement) {
        /**
         * 双指针原地删除
         * 读指针自增1，写指针放入元素才加一
         */
        int r = 0, w = 0;
        boolean modified = false;
        try {
            for (; r < size; r++)
                // 遍历整个数组，如果C包含元素并且为true，写指针放在此位置
                if (c.contains(elementData[r]) == complement) elementData[w++] = elementData[r];
        } finally {
            // 正常来说相等，但是如果contains异常，可能造成不相等
            if (r != size) {
                // 异常就将未读元素拷贝在写指针以后
                System.arraycopy(elementData, r, elementData, w, size - r);
                w += size - r;
            }
            if (w != size) {
                // 将写指针以后的元素置空，帮助GC
                for (int i = w; i < size; i++)
                    elementData[i] = null;
                // 新大小就是写指针位置，每次写指针加一，新大小就是写指针位置
                size = w;
                modified = true;
            }
        }
        return modified;
    }
}
