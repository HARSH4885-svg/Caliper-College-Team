import sys

def bubble(arr):
    arr = arr[:]
    swapped = True
    while swapped:
        swapped = False
        for k in range(len(arr) - 1):
            if arr[k] > arr[k + 1]:
                arr[k], arr[k + 1] = arr[k + 1], arr[k]
                swapped = True
    return arr

line_items = []
for ln in sys.stdin:
    line_items.extend(int(p) for p in ln.split())
arranged = bubble(line_items)
count = len(arranged)
if count == 0:
    med_value = 0
elif count % 2 == 0:
    med_value = (arranged[count // 2] + arranged[count // 2 - 1]) / 2
else:
    med_value = arranged[(count - 1) // 2]
histogram = dict()
for item in arranged:
    histogram[item] = histogram.setdefault(item, 0) + 1
frequent = None
for item, times in sorted(histogram.items()):
    if frequent is None or times > histogram[frequent]:
        frequent = item
print("sorted:", " ".join(map(str, arranged)))
print("sum:", sum(arranged))
print("median:", med_value)
print("mode:", frequent)
