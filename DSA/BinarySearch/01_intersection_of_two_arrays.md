# Intersection of Two Arrays
**LeetCode #349** | Difficulty: Easy | Topic: Binary Search / Hashing

---

## 📋 Problem Statement

Given two integer arrays `nums1` and `nums2`, return an array of their **intersection**.

Each element in the result must be **unique** and you may return the result **in any order**.

**Constraints:**
- `1 <= nums1.length, nums2.length <= 1000`
- `0 <= nums1[i], nums2[i] <= 1000`

**Examples:**
```
Input:  nums1 = [1, 2, 2, 1],  nums2 = [2, 2]
Output: [2]

Input:  nums1 = [4, 9, 5],     nums2 = [9, 4, 9, 8, 4]
Output: [9, 4]   (order doesn't matter)
```

---

## 🐢 Approach 1 — Brute Force

### Idea
For every element in `nums1`, scan **all elements** of `nums2` to check if it exists there.
Use a result list and avoid duplicates by checking if the element is already in the result.

### Algorithm
1. For each element `x` in `nums1`:
   - For each element `y` in `nums2`:
     - If `x == y` and `x` is not already in result → add to result
2. Return result

### Java Code
```java
import java.util.*;

public class BruteForce {
    public int[] intersection(int[] nums1, int[] nums2) {
        List<Integer> result = new ArrayList<>();

        for (int x : nums1) {
            for (int y : nums2) {
                if (x == y && !result.contains(x)) {
                    result.add(x);
                    break; // no need to keep scanning nums2 for this x
                }
            }
        }

        // Convert List to int[]
        int[] ans = new int[result.size()];
        for (int i = 0; i < result.size(); i++) ans[i] = result.get(i);
        return ans;
    }
}
```

### ⏱️ Time & Space Complexity
| | Complexity | Reason |
|---|---|---|
| **Time** | O(m × n × r) | m = nums1 length, n = nums2 length, r = result size (for `contains` check) |
| **Space** | O(r) | result list |

> **Simplified:** ~O(m × n) in most practical cases — very slow for large inputs.

### 🧪 Dry Run
```
nums1 = [1, 2, 2, 1],  nums2 = [2, 2]

x=1: scan nums2 → 2≠1, 2≠1 → no match
x=2: scan nums2 → 2==2, result doesn't have 2 → add 2, break
x=2: scan nums2 → 2==2, result already has 2 → skip
x=1: scan nums2 → 2≠1, 2≠1 → no match

result = [2]  ✅
```

---

## 🚀 Approach 2 — Better (HashSet)

### Idea
Use a **HashSet** to store all elements of `nums1`. Then iterate through `nums2`: if an element exists in the set, add it to a result set (sets auto-handle duplicates).

### Algorithm
1. Add all elements of `nums1` into `Set1`
2. For each element `y` in `nums2`:
   - If `Set1` contains `y` → add `y` to `ResultSet`
3. Convert `ResultSet` to array and return

### Java Code
```java
import java.util.*;

public class BetterHashSet {
    public int[] intersection(int[] nums1, int[] nums2) {
        Set<Integer> set1 = new HashSet<>();
        for (int x : nums1) set1.add(x);

        Set<Integer> resultSet = new HashSet<>();
        for (int y : nums2) {
            if (set1.contains(y)) {
                resultSet.add(y);
            }
        }

        int[] ans = new int[resultSet.size()];
        int i = 0;
        for (int val : resultSet) ans[i++] = val;
        return ans;
    }
}
```

### ⏱️ Time & Space Complexity
| | Complexity | Reason |
|---|---|---|
| **Time** | O(m + n) | One pass to build set1, one pass through nums2 |
| **Space** | O(m + r) | set1 stores nums1 elements, resultSet stores unique intersections |

> Much better than brute force! HashSet lookup is O(1) average.

### 🧪 Dry Run
```
nums1 = [4, 9, 5],  nums2 = [9, 4, 9, 8, 4]

Step 1 — Build set1:
  set1 = {4, 9, 5}

Step 2 — Scan nums2:
  y=9: set1 has 9? YES → resultSet = {9}
  y=4: set1 has 4? YES → resultSet = {9, 4}
  y=9: set1 has 9? YES → resultSet = {9, 4}  (already there, no change)
  y=8: set1 has 8? NO  → skip
  y=4: set1 has 4? YES → resultSet = {9, 4}  (already there, no change)

Result = [9, 4]  ✅
```

---

## ⚡ Approach 3 — Optimal (Sort + Binary Search)

### Idea
Since the problem is under the **Binary Search** topic category, the optimal approach is:
1. **Sort `nums2`**
2. For each **unique** element in `nums1`, do a **binary search** in sorted `nums2`
3. If found, add to result

Binary search gives O(log n) lookup per element (vs O(1) for HashSet, but this is the intended binary search practice).

### Algorithm
1. Sort `nums2`
2. Add all elements of `nums1` into a `Set` to get unique values
3. For each unique element in `nums1Set`:
   - Binary search in `nums2`
   - If found → add to result
4. Return result

### Java Code
```java
import java.util.*;

public class OptimalBinarySearch {

    public int[] intersection(int[] nums1, int[] nums2) {
        Arrays.sort(nums2); // Sort nums2 for binary search

        Set<Integer> seen = new HashSet<>();
        List<Integer> result = new ArrayList<>();

        for (int x : nums1) {
            if (!seen.contains(x)) {           // process each unique element of nums1
                seen.add(x);
                if (binarySearch(nums2, x)) {  // check if x exists in nums2
                    result.add(x);
                }
            }
        }

        int[] ans = new int[result.size()];
        for (int i = 0; i < result.size(); i++) ans[i] = result.get(i);
        return ans;
    }

    // Standard binary search — returns true if target found in sorted array
    private boolean binarySearch(int[] arr, int target) {
        int lo = 0, hi = arr.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;   // avoids integer overflow
            if (arr[mid] == target) return true;
            else if (arr[mid] < target) lo = mid + 1;
            else hi = mid - 1;
        }
        return false;
    }
}
```

### ⏱️ Time & Space Complexity
| | Complexity | Reason |
|---|---|---|
| **Time** | O(m + n log n) | Sorting nums2 = O(n log n), binary search for each unique nums1 element = O(m log n) |
| **Space** | O(m + r) | `seen` set + result list |

> If both arrays were already sorted, this becomes the most space-efficient approach (no extra HashSet needed — see bonus below).

### 🧪 Dry Run
```
nums1 = [1, 2, 2, 1],  nums2 = [2, 2]

Step 1 — Sort nums2:
  nums2 = [2, 2]

Step 2 — Process each unique element of nums1:

  x=1, seen={}, not in seen:
    seen = {1}
    Binary search for 1 in [2, 2]:
      lo=0, hi=1, mid=0 → arr[0]=2 > 1 → hi = -1
      lo=0 > hi=-1 → return false
    1 NOT found → skip

  x=2, seen={1}, not in seen:
    seen = {1, 2}
    Binary search for 2 in [2, 2]:
      lo=0, hi=1, mid=0 → arr[0]=2 == 2 → return TRUE ✅
    2 FOUND → result = [2]

  x=2, already in seen → skip
  x=1, already in seen → skip

Result = [2]  ✅
```

---

## 🎁 Bonus — Optimal (Two Pointers on Sorted Arrays)

If **both arrays are sorted**, you can use **two pointers** with O(1) extra space (excluding output):

### Java Code
```java
import java.util.*;

public class TwoPointers {
    public int[] intersection(int[] nums1, int[] nums2) {
        Arrays.sort(nums1);
        Arrays.sort(nums2);

        List<Integer> result = new ArrayList<>();
        int i = 0, j = 0;
        int prev = -1; // track last added to avoid duplicates

        while (i < nums1.length && j < nums2.length) {
            if (nums1[i] == nums2[j]) {
                if (nums1[i] != prev) { // avoid duplicates
                    result.add(nums1[i]);
                    prev = nums1[i];
                }
                i++; j++;
            } else if (nums1[i] < nums2[j]) {
                i++;
            } else {
                j++;
            }
        }

        int[] ans = new int[result.size()];
        for (int k = 0; k < result.size(); k++) ans[k] = result.get(k);
        return ans;
    }
}
```

### ⏱️ Time & Space Complexity
| | Complexity | Reason |
|---|---|---|
| **Time** | O(m log m + n log n) | Sorting both arrays |
| **Space** | O(1) extra | Only pointers used (output not counted) |

### 🧪 Dry Run
```
nums1 = [4, 9, 5]  →  sorted: [4, 5, 9]
nums2 = [9, 4, 9, 8, 4]  →  sorted: [4, 4, 8, 9, 9]

i=0, j=0: nums1[0]=4 == nums2[0]=4, prev=-1 → add 4, prev=4, i=1, j=1
i=1, j=1: nums1[1]=5 > nums2[1]=4 → j=2
i=1, j=2: nums1[1]=5 < nums2[2]=8 → i=2
i=2, j=2: nums1[2]=9 > nums2[2]=8 → j=3
i=2, j=3: nums1[2]=9 == nums2[3]=9, prev=4 → add 9, prev=9, i=3, j=4
i=3: out of bounds → stop

Result = [4, 9]  ✅
```

---

## 📊 Comparison Summary

| Approach | Time Complexity | Space Complexity | Notes |
|---|---|---|---|
| **Brute Force** | O(m × n) | O(r) | Nested loops, very slow |
| **HashSet** | O(m + n) | O(m + r) | Fast and simple, recommended in interviews |
| **Sort + Binary Search** | O(m + n log n) | O(m + r) | Binary search practice, good if nums2 is pre-sorted |
| **Two Pointers** | O(m log m + n log n) | O(1) extra | Best space efficiency, both arrays need sorting |

> 💡 **Interview Tip:** The **HashSet** approach is the most commonly expected. Mention the **Binary Search** and **Two Pointer** alternatives to show depth of knowledge.

---

## 🔑 Key Takeaways

1. **Binary Search requires sorted input** — always sort first before applying it.
2. Use `lo + (hi - lo) / 2` instead of `(lo + hi) / 2` to **prevent integer overflow**.
3. **Sets handle uniqueness** — use them to avoid manual duplicate checks.
4. Two Pointers on sorted arrays is a powerful, space-efficient technique.
