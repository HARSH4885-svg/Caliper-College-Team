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

void sortNumbers(vector<int>& v) {
    int n = v.size();
    for (int i = 0; i < n - 1; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            if (v[j] > v[j + 1]) {
                int tmp = v[j];
                v[j] = v[j + 1];
                v[j + 1] = tmp;
            }
        }
    }
}

int binarySearch(const vector<int>& v, int target) {
    int lo = 0, hi = (int)v.size() - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (v[mid] == target) return mid;
        if (v[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}

int main() {
    int n;
    cin >> n;
    vector<int> data = readNumbers(n);
    sortNumbers(data);
    printNumbers(data);
    long long sum = 0;
    int best = data[0];
    for (int i = 0; i < n; i++) {
        sum += data[i];
        if (data[i] > best) best = data[i];
    }
    cout << "sum=" << sum << " max=" << best << endl;
    int target;
    cin >> target;
    cout << binarySearch(data, target) << endl;
    return 0;
}
