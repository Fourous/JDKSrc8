package com.collection.treemap;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 二叉树的遍历：前序，中序，后序
 * （1）前序遍历，先遍历我，再遍历我的左子节点，最后遍历我的右子节点；
 * （2）中序遍历，先遍历我的左子节点，再遍历我，最后遍历我的右子节点；
 * （3）后序遍历，先遍历我的左子节点，再遍历我的右子节点，最后遍历我
 */
public class TreeMapSearch {
    public static void main(String[] args) {
        TreeNode<Integer> node = new TreeNode<>(1, null)
                .insert(2)
                .insert(6)
                .insert(3)
                .insert(5)
                .insert(9)
                .insert(7)
                .insert(8)
                .insert(4)
                .insert(10);
        node.root().inOrderTraverse();
        System.out.println("------- TreeMap ---------");
        node.treeMapTraverse(i -> System.out.println("TreeMap中序遍历: " + i));
    }


    static class TreeNode<T extends Comparable<T>> {
        T value;
        TreeNode<T> parent;
        TreeNode<T> left, right;

        public TreeNode(T value, TreeNode parent) {
            this.value = value;
            this.parent = parent;
        }

        /**
         * TreeMap实现的中序查找
         */
        public void treeMapTraverse(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            for (TreeNode<T> e = getFirstEntry(); e != null; e = successor(e)) {
                action.accept(e.value);
            }
        }

        /**
         *  如果有右子树，取右子树的最小节点，向左不停遍历
         *  如果没有右子树，先找到其父节点
         *      如果此节点是父节点的左子节点，直接返回父节点
         *      如果此节点是父节点的右子节点，向上找到一个祖先节点是其父节点的左子节点为止，返回这祖先节点的父节点
         * @param t
         * @return
         */
        static TreeNode successor(TreeNode t) {
            // 如果当前节点为空，返回空
            if (t == null) return null;
            else if (t.right != null) {
                // 如果当前节点有右子树，取右子树中最小的节点
                // 也就是右子树的最左边的节点
                TreeNode p = t.right;
                while (p.left != null)
                    p = p.left;
                return p;
            } else {
                // 如果当前节点没有右子树
                // 如果当前节点是父节点的左子节点，直接返回父节点
                // 如果当前节点是父节点的右子节点，一直往上找，直到找到一个祖先节点是其父节点的左子节点为止，返回这个祖先节点的父节点
                TreeNode p = t.parent;
                TreeNode ch = t;
                while (p != null && ch == p.right) {
                    ch = p;
                    p = p.parent;
                }
                return p;
            }
        }

        /**
         * 寻找最左边节点也就是最小节点
         */
        public TreeNode<T> getFirstEntry() {
            TreeNode<T> root = root();
            if (root != null) {
                while (root.left != null) {
                    root = root.left;
                }
            }
            return root;
        }

        /**
         * 获取根节点
         *
         * @return
         */
        TreeNode root() {
            TreeNode<T> cur = this;
            while (cur.parent != null) {
                cur = cur.parent;
            }
            return cur;
        }

        /**
         * 中序遍历
         */
        void inOrderTraverse() {
            if (this.left != null) this.left.inOrderTraverse();
            System.out.println("中序遍历：" + this.value);
            if (this.right != null) this.right.inOrderTraverse();
        }

        TreeNode<T> insert(T value) {
            TreeNode<T> root = root();
            TreeNode<T> p;
            int dir;
            do {
                p = root;
                if ((dir = value.compareTo(p.value)) < 0) {
                    root = root.left;
                } else {
                    root = root.right;
                }
            } while (root != null);
            if (dir < 0) {
                p.left = new TreeNode(value, p);
                return p.left;
            } else {
                p.right = new TreeNode(value, p);
                return p.right;
            }
        }
    }
}
