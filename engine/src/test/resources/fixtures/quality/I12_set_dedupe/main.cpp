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

#include <set>
#include <algorithm>

int main() {
    int n;
    cin >> n;
    vector<int> raw = readNumbers(n);
    set<int> uniq(raw.begin(), raw.end());
    vector<int> sorted(raw);
    sort(sorted.begin(), sorted.end());
    printNumbers(sorted);
    cout << "distinct values: " << uniq.size() << endl;
    long long product = 1;
    for (size_t i = 0; i < sorted.size() && i < 3; i++) {
        product *= sorted[i];
    }
    cout << "product of smallest three: " << product << endl;
    int needle;
    cin >> needle;
    bool seen = binary_search(sorted.begin(), sorted.end(), needle);
    if (seen) {
        int where = find(sorted.begin(), sorted.end(), needle) - sorted.begin();
        cout << "at " << where << endl;
    } else {
        cout << "absent" << endl;
    }
    return 0;
}
