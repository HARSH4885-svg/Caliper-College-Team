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

#include <algorithm>

void shellSort(vector<int>& a) {
    int n = a.size();
    for (int gap = n / 2; gap > 0; gap /= 2) {
        for (int i = gap; i < n; i++) {
            int temp = a[i];
            int j;
            for (j = i; j >= gap && a[j - gap] > temp; j -= gap) {
                a[j] = a[j - gap];
            }
            a[j] = temp;
        }
    }
}

int main() {
    int n;
    cin >> n;
    vector<int> a = readNumbers(n);
    shellSort(a);
    printNumbers(a);
    long long s = 0;
    int mn = a[0], mx = a[0];
    for (int i = 0; i < n; i++) {
        s += a[i];
        if (a[i] < mn) mn = a[i];
        if (a[i] > mx) mx = a[i];
    }
    cout << "spread " << mx - mn << " total " << s << endl;
    int k;
    cin >> k;
    vector<int>::iterator p = lower_bound(a.begin(), a.end(), k);
    if (p != a.end() && *p == k) cout << p - a.begin() << endl;
    else cout << -1 << endl;
    return 0;
}
