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
// ---- Student part below ----/* merge sort followed by a binary search */


void mergeParts(vector<int>& a, int l, int m, int r)
{
	vector<int> left(a.begin() + l, a.begin() + m + 1);
	vector<int> right(a.begin() + m + 1, a.begin() + r + 1);
	size_t i = 0, j = 0;
	int k = l;
	while (i < left.size() && j < right.size())
{
		if (left[i] <= right[j]) a[k++] = left[i++];
		else a[k++] = right[j++];
	}
	while (i < left.size()) a[k++] = left[i++];
	while (j < right.size()) a[k++] = right[j++];
}

void mergeSort(vector<int>& a, int l, int r)
{
	if (l >= r) return;
	int m = (l + r) / 2;
	mergeSort(a, l, m);
	mergeSort(a, m + 1, r);
	mergeParts(a, l, m, r);
}

int search(const vector<int>& a, int target)
{
	int low = 0, high = a.size() - 1;
	while (low < high)
{
		int mid = (low + high) / 2;
		if (a[mid] < target) low = mid + 1;
		else high = mid;
	}
	return (!a.empty() && a[low] == target) ? low : -1;
}

int main()
{
	int n;
	cin >> n;
	vector<int> a = readNumbers(n);
	mergeSort(a, 0, n - 1);
	printNumbers(a);
	int total = 0;
	for (int i = 0; i < n; i++) total += a[i];
	cout << "total=" << total << " mean=" << total / (double)n << endl;
	int t;
	cin >> t;
	cout << search(a, t) << endl;
	return 0;
}
