import sys

def read_numbers(stream):
    values = []
    for line in stream:
        for token in line.split():
            values.append(int(token))
    return values

def insertion_sort(items):
    result = list(items)
    for i in range(1, len(result)):
        key = result[i]
        j = i - 1
        while j >= 0 and result[j] > key:
            result[j + 1] = result[j]
            j -= 1
        result[j + 1] = key
    return result

def median(sorted_values):
    n = len(sorted_values)
    if n == 0:
        return 0
    mid = n // 2
    if n % 2 == 1:
        return sorted_values[mid]
    return (sorted_values[mid - 1] + sorted_values[mid]) / 2

def mode(values):
    counts = {}
    for v in values:
        counts[v] = counts.get(v, 0) + 1
    best = None
    for v in sorted(counts):
        if best is None or counts[v] > counts[best]:
            best = v
    return best

def main():
    numbers = read_numbers(sys.stdin)
    ordered = insertion_sort(numbers)
    total = 0
    for v in ordered:
        total += v
    print("sorted:", " ".join(str(v) for v in ordered))
    print("sum:", total)
    print("median:", median(ordered))
    print("mode:", mode(ordered))

main()
