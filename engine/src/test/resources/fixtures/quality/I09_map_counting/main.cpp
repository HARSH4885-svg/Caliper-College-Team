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

#include <map>

vector<int> sortWithMap(const vector<int>& raw) {
    map<int, int> freq;
    for (int x : raw) freq[x]++;
    vector<int> out;
    for (map<int, int>::iterator it = freq.begin(); it != freq.end(); ++it) {
        for (int c = 0; c < it->second; c++) out.push_back(it->first);
    }
    return out;
}

int countLess(const vector<int>& v, int x) {
    int c = 0;
    for (size_t i = 0; i < v.size(); i++) {
        if (v[i] < x) c++;
    }
    return c;
}

int main() {
    int n;
    cin >> n;
    vector<int> raw = readNumbers(n);
    vector<int> ordered = sortWithMap(raw);
    printNumbers(ordered);
    long long acc = 0;
    for (size_t i = 0; i < ordered.size(); ++i) acc += ordered[i];
    cout << "sum is " << acc << endl;
    int probe;
    cin >> probe;
    int idx = countLess(ordered, probe);
    if (idx < n && ordered[idx] == probe) cout << idx << endl;
    else cout << "none" << endl;
    return 0;
}
