import sys
# helper utilities for the lab
def parse_input(src):
    collected = []
    for row in src:
        for piece in row.split():
            collected.append(int(piece))
    return collected

def find_mode(data):
    freq = {}
    for x in data:
        freq[x] = freq.get(x, 0) + 1
    top = None
    for x in sorted(freq):
        if top is None or freq[x] > freq[top]:
            top = x
    return top

def central(seq):
    length = len(seq)
    if length == 0:
        return 0
    half = length // 2
    if length % 2 == 1:
        return seq[half]
    return (seq[half - 1] + seq[half]) / 2

def order_values(vals):
    out = list(vals)
    for a in range(1, len(out)):
        cur = out[a]
        b = a - 1
        while b >= 0 and out[b] > cur:
            out[b + 1] = out[b]
            b -= 1
        out[b + 1] = cur
    return out

def run():
    nums = parse_input(sys.stdin)
    srt = order_values(nums)
    acc = 0
    for x in srt:
        acc += x
    print("sorted:", " ".join(str(x) for x in srt))
    print("sum:", acc)
    print("median:", central(srt))
    print("mode:", find_mode(srt))

run()
