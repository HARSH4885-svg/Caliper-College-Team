import sys
from collections import Counter

class Stats:
    def __init__(self, data):
        self.data = sorted(data)
        self.counter = Counter(self.data)

    def total(self):
        return sum(self.data)

    def middle(self):
        n = len(self.data)
        if not n:
            return 0
        return self.data[n // 2] if n & 1 else (self.data[n // 2 - 1] + self.data[n // 2]) / 2

    def commonest(self):
        if not self.counter:
            return None
        best_count = max(self.counter.values())
        return min(k for k, c in self.counter.items() if c == best_count)

if __name__ == "__main__":
    raw = sys.stdin.read().split()
    st = Stats([int(t) for t in raw])
    print("sorted:", *st.data)
    print("sum:", st.total())
    print("median:", st.middle())
    print("mode:", st.commonest())
