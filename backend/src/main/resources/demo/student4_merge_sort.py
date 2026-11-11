import sys
from functools import reduce

def merge(left, right):
    merged, i, j = [], 0, 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            merged.append(left[i]); i += 1
        else:
            merged.append(right[j]); j += 1
    return merged + left[i:] + right[j:]

def msort(xs):
    if len(xs) < 2:
        return xs
    m = len(xs) // 2
    return merge(msort(xs[:m]), msort(xs[m:]))

nums = list(map(int, sys.stdin.read().split()))
ys = msort(nums)
s = reduce(lambda a, b: a + b, ys, 0)
med = 0 if not ys else (ys[len(ys) // 2] if len(ys) % 2 else (ys[len(ys) // 2 - 1] + ys[len(ys) // 2]) / 2)
tally = {}
for y in ys:
    tally[y] = tally.get(y, 0) + 1
winner = max(sorted(tally), key=lambda k: tally[k]) if tally else None
print("sorted: " + " ".join(map(str, ys)))
print("sum: %d" % s)
print("median: %s" % med)
print("mode: %s" % winner)
