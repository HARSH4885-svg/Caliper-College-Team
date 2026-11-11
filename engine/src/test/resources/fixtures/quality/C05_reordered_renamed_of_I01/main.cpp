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

int bisect(const vector<int>& v, int goal) {
    int left = 0, right = (int)v.size() - 1;
    while (left <= right) {
        int centre = left + (right - left) / 2;
        if (v[centre] == goal) return centre;
        if (v[centre] < goal) left = centre + 1;
        else right = centre - 1;
    }
    return -1;
}

void arrange(vector<int>& v) {
    int n = v.size();
    for (int i = 0; i < n - 1; i++) {
        for (int j = 0; j < n - i - 1; j++) {
            if (v[j] > v[j + 1]) {
                int spare = v[j];
                v[j] = v[j + 1];
                v[j + 1] = spare;
            }
        }
    }
}

int main() {
    int n;
    cin >> n;
    vector<int> values = readNumbers(n);
    arrange(values);
    printNumbers(values);
    long long acc = 0;
    int peak = values[0];
    for (int i = 0; i < n; i++) {
        acc += values[i];
        if (values[i] > peak) peak = values[i];
    }
    cout << "acc=" << acc << " max=" << peak << endl;
    int goal;
    cin >> goal;
    cout << bisect(values, goal) << endl;
    return 0;
}
