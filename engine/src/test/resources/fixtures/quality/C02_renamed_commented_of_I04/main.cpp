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

int locateSmallest(const vector<int>& numbers, int startAt) {
    int champion = startAt;
    for (int idx = startAt + 1; idx < (int)numbers.size(); idx++) {
        if (numbers[idx] < numbers[champion]) champion = idx;
    }
    return champion;
}

void orderAscending(vector<int>& numbers) {
    for (int idx = 0; idx < (int)numbers.size(); idx++) {
        int minPos = locateSmallest(numbers, idx);
        int holder = numbers[idx];
        numbers[idx] = numbers[minPos];
        numbers[minPos] = holder;
    }
}

int scanFor(const vector<int>& numbers, int needle) {
    for (int idx = 0; idx < (int)numbers.size(); idx++) {
        if (numbers[idx] == needle) return idx;
    }
    return -1;
}

// entry point of the program
int main() {
    int n;
    cin >> n;
    vector<int> numbers = readNumbers(n);
    // sort first
    orderAscending(numbers);
    printNumbers(numbers);
    cout << "min " << numbers.front() << " max " << numbers.back() << endl;
    long long running = 0;
    for (int idx = 0; idx < n; idx++) running += numbers[idx];
    cout << "running " << running << endl;
    int needle;
    cin >> needle;
    cout << scanFor(numbers, needle) << endl;
    return 0;
}
