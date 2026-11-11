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

int findMinIndex(const vector<int>& v, int from) {
    int best = from;
    for (int i = from + 1; i < (int)v.size(); i++) {
        if (v[i] < v[best]) best = i;
    }
    return best;
}

void selectionSort(vector<int>& v) {
    for (int i = 0; i < (int)v.size(); i++) {
        int m = findMinIndex(v, i);
        int t = v[i];
        v[i] = v[m];
        v[m] = t;
    }
}

int linearSearch(const vector<int>& v, int x) {
    for (int i = 0; i < (int)v.size(); i++) {
        if (v[i] == x) return i;
    }
    return -1;
}

int main() {
    int n;
    cin >> n;
    vector<int> v = readNumbers(n);
    selectionSort(v);
    printNumbers(v);
    cout << "min " << v.front() << " max " << v.back() << endl;
    long long sum = 0;
    for (int i = 0; i < n; i++) sum += v[i];
    cout << "sum " << sum << endl;
    int x;
    cin >> x;
    cout << linearSearch(v, x) << endl;
    return 0;
}
