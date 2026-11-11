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

void insertionSort(vector<int>& a) {
    for (int i = 1; i < (int)a.size(); i++) {
        int key = a[i];
        int j = i - 1;
        while (j >= 0 && a[j] > key) {
            a[j + 1] = a[j];
            j--;
        }
        a[j + 1] = key;
    }
}

int searchRec(const vector<int>& a, int lo, int hi, int key) {
    if (lo > hi) return -1;
    int mid = (lo + hi) / 2;
    if (a[mid] == key) return mid;
    if (key < a[mid]) return searchRec(a, lo, mid - 1, key);
    return searchRec(a, mid + 1, hi, key);
}

int main() {
    int n, key;
    cin >> n;
    vector<int> nums = readNumbers(n);
    insertionSort(nums);
    printNumbers(nums);
    long long s = 0;
    for (int x : nums) s += x;
    cout << "average " << (double)s / n << endl;
    cin >> key;
    int r = searchRec(nums, 0, n - 1, key);
    if (r >= 0) cout << "index " << r << endl;
    else cout << "missing" << endl;
    return 0;
}
