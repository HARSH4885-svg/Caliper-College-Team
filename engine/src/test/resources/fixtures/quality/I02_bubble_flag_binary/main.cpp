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

void bubbleSort(vector<int>& arr) {
    bool swapped = true;
    for (int pass = 0; pass < (int)arr.size() && swapped; ++pass) {
        swapped = false;
        for (size_t k = 0; k + 1 < arr.size() - pass; ++k) {
            if (arr[k] > arr[k + 1]) {
                swap(arr[k], arr[k + 1]);
                swapped = true;
            }
        }
    }
}

int find(const vector<int>& arr, int x) {
    int left = 0;
    int right = arr.size() - 1;
    while (left <= right) {
        int middle = (left + right) / 2;
        if (arr[middle] == x) {
            return middle;
        } else if (arr[middle] < x) {
            left = middle + 1;
        } else {
            right = middle - 1;
        }
    }
    return -1;
}

int main() {
    int count;
    cin >> count;
    vector<int> arr = readNumbers(count);
    bubbleSort(arr);
    printNumbers(arr);
    long long total = 0;
    int largest = arr[0];
    int smallest = arr[0];
    for (int value : arr) {
        total += value;
        largest = max(largest, value);
        smallest = min(smallest, value);
    }
    cout << "Total: " << total << endl;
    cout << "Range: " << smallest << " to " << largest << endl;
    int q;
    cin >> q;
    int pos = find(arr, q);
    if (pos == -1) cout << "not found" << endl;
    else cout << "found at " << pos << endl;
}
