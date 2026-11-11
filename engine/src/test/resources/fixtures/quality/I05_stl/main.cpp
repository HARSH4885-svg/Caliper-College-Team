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
#include <numeric>

int main() {
    int n;
    cin >> n;
    vector<int> items = readNumbers(n);
    sort(items.begin(), items.end());
    printNumbers(items);
    long long total = accumulate(items.begin(), items.end(), 0LL);
    cout << "sum: " << total << "\n";
    cout << "max: " << *max_element(items.begin(), items.end()) << "\n";
    cout << "min: " << *min_element(items.begin(), items.end()) << "\n";
    double mean = static_cast<double>(total) / items.size();
    cout << "mean: " << mean << "\n";
    int wanted;
    cin >> wanted;
    auto it = lower_bound(items.begin(), items.end(), wanted);
    if (it != items.end() && *it == wanted) {
        cout << (it - items.begin()) << "\n";
    } else {
        cout << -1 << "\n";
    }
    return 0;
}
