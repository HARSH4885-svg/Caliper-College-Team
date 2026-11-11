import heapq
import sys

def drain(values):
    heap = list(values)
    heapq.heapify(heap)
    while heap:
        yield heapq.heappop(heap)

def parse(text):
    return [int(w) for w in text.split()]

def summarize(ordered):
    size = len(ordered)
    running = 0
    best_val, best_run, cur_val, cur_run = None, 0, None, 0
    for v in ordered:
        running += v
        if v == cur_val:
            cur_run += 1
        else:
            cur_val, cur_run = v, 1
        if cur_run > best_run:
            best_val, best_run = cur_val, cur_run
    if size == 0:
        mid = 0
    elif size % 2:
        mid = ordered[size // 2]
    else:
        mid = (ordered[size // 2 - 1] + ordered[size // 2]) / 2
    return running, mid, best_val

data = list(drain(parse(sys.stdin.read())))
total, mid, common = summarize(data)
print("sorted:", " ".join(str(d) for d in data))
print("sum:", total)
print("median:", mid)
print("mode:", common)
