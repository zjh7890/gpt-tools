package com.github.zjh7890.gpttools;

/**
 * @Date: 2024/12/10 21:43
 */
public class BinaryTest {
    public static void main(String[] args) {
        // 测试用例1：有序数组 [1,3,5,7,9]
        int[] arr1 = {1, 3, 5, 7, 9};
        System.out.println("测试用例1：");
        System.out.println("查找3的下标：" + binarySearch(arr1, 3)); // 期望输出：1
        System.out.println("查找9的下标：" + binarySearch(arr1, 9)); // 期望输出：4
        System.out.println("查找2的下标：" + binarySearch(arr1, 2)); // 期望输出：-1

        // 测试用例2：有序数组 [2,4,6,8,10,12]
        int[] arr2 = {2,4,6,8,10,12};
        System.out.println("测试用例2：");
        System.out.println("查找8的下标：" + binarySearch(arr2, 8));   // 期望输出：3
        System.out.println("查找12的下标：" + binarySearch(arr2, 12)); // 期望输出：5
        System.out.println("查找1的下标：" + binarySearch(arr2, 1));   // 期望输出：-1

        // 测试用例3：有序数组 [100]
        int[] arr3 = {100};
        System.out.println("测试例3：");
        System.out.println("查找100的下标：" + binarySearch(arr3, 100)); // 期望输出：0
        System.out.println("查找99的下标：" + binarySearch(arr3, 99));   // 期望输出：-1
    }

    private static int binarySearch(int[] arr, int target) {
        int left = 0;
        int right = arr.length - 1;
        while (left <= right) {
            int mid = left + ((right - left) >> 1); // 避免整数溢出
            if (arr[mid] == target) {
                return mid;
            } else if (arr[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return -1; // 未找到返回-1
    }
}