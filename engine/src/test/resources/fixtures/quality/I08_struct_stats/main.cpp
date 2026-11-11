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

struct Stats {
    long long sum;
    int smallest;
    int largest;
};

Stats summarize(const vector<int>& v) {
    Stats s;
    s.sum = 0;
    s.smallest = v[0];
    s.largest = v[0];
    for (int x : v) {
        s.sum += x;
        if (x < s.smallest) s.smallest = x;
        if (x > s.largest) s.largest = x;
    }
    return s;
}

bool byValue(int a, int b) {
    return a < b;
}

int main() {
    int n;
    cin >> n;
    vector<int> v = readNumbers(n);
    sort(v.begin(), v.end(), byValue);
    printNumbers(v);
    Stats st = summarize(v);
    cout << "min=" << st.smallest << " max=" << st.largest << " sum=" << st.sum << endl;
    int target;
    cin >> target;
    int found = -1;
    for (int i = 0; i < n && found < 0; i++) {
        if (v[i] == target) found = i;
    }
    cout << found << endl;
    return 0;
}
