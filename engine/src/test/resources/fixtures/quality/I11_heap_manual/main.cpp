#include <iostream>
#include <vector>
using namespace std;

// ---- Provided by instructor: do not change ----
vector<int> readNumbers(int n) {
    vector<int> v(n);
    for (int i = 0; i < n; i++) {
        cin >> v[i];
    }
    return v;
}

void printNumbers(const vector<int>& v) {
    for (size_t i = 0; i < v.size(); i++) {
        if (i > 0) cout << " ";
        cout << v[i];
    }
    cout << endl;
}
// ---- Student part below ----

void siftDown(vector<int>& h, int start, int end) {
    int root = start;
    while (2 * root + 1 <= end) {
        int child = 2 * root + 1;
        int swapIdx = root;
        if (h[swapIdx] < h[child]) swapIdx = child;
        if (child + 1 <= end && h[swapIdx] < h[child + 1]) swapIdx = child + 1;
        if (swapIdx == root) return;
        swap(h[root], h[swapIdx]);
        root = swapIdx;
    }
}

void heapSort(vector<int>& h) {
    int n = h.size();
    for (int s = (n - 2) / 2; s >= 0; s--) siftDown(h, s, n - 1);
    for (int e = n - 1; e > 0; e--) {
        swap(h[0], h[e]);
        siftDown(h, 0, e - 1);
    }
}

int main() {
    int n;
    cin >> n;
    vector<int> h = readNumbers(n);
    heapSort(h);
    printNumbers(h);
    int evens = 0, odds = 0;
    for (int v : h) {
        if (v % 2 == 0) evens++;
        else odds++;
    }
    cout << "even:" << evens << " odd:" << odds << endl;
    int want;
    cin >> want;
    int lo = 0, hi = n - 1, ans = -1;
    while (lo <= hi && ans == -1) {
        int mid = (lo + hi) / 2;
        if (h[mid] == want) ans = mid;
        else if (h[mid] > want) hi = mid - 1;
        else lo = mid + 1;
    }
    cout << ans << endl;
    return 0;
}
