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

int partitionRange(vector<int>& v, int lo, int hi) {
    int pivot = v[hi];
    int i = lo - 1;
    for (int j = lo; j < hi; j++) {
        if (v[j] <= pivot) {
            i++;
            swap(v[i], v[j]);
        }
    }
    swap(v[i + 1], v[hi]);
    return i + 1;
}

void quickSort(vector<int>& v, int lo, int hi) {
    if (lo < hi) {
        int p = partitionRange(v, lo, hi);
        quickSort(v, lo, p - 1);
        quickSort(v, p + 1, hi);
    }
}

bool contains(const vector<int>& v, int x, int& where) {
    int lo = 0, hi = v.size();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (v[mid] == x) {
            where = mid;
            return true;
        }
        if (v[mid] < x) lo = mid + 1;
        else hi = mid;
    }
    return false;
}

int main() {
    int n;
    cin >> n;
    vector<int> v = readNumbers(n);
    quickSort(v, 0, n - 1);
    printNumbers(v);
    int sum = 0;
    int mx = v[0];
    for (int i = 0; i < n; i++) {
        sum += v[i];
        mx = max(mx, v[i]);
    }
    cout << sum << " " << mx << endl;
    int q, where = -1;
    cin >> q;
    if (contains(v, q, where)) cout << where << endl;
    else cout << -1 << endl;
    return 0;
}
