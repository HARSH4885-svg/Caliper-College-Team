#!/usr/bin/env python3
"""
Derives the five SYNTHETIC copied submissions of the quality dataset from independently written ones, and writes the
ground-truth label file. Run from the repo root:  python3 -I scripts/make-quality-fixtures.py
The twelve I* submissions are hand-written independent solutions; everything C* is a deliberate copy (ground truth).
"""
import os, re, shutil

BASE = "engine/src/test/resources/fixtures/quality"
STARTER_END = "// ---- Student part below ----"

def read(d):
    return open(f"{BASE}/{d}/main.cpp").read()

def write(d, text):
    os.makedirs(f"{BASE}/{d}", exist_ok=True)
    open(f"{BASE}/{d}/main.cpp", "w").write(text)

def split(text):
    head, tail = text.split(STARTER_END, 1)
    return head + STARTER_END, tail

def rename(tail, mapping):
    for old, new in mapping.items():
        tail = re.sub(r"\b%s\b" % re.escape(old), new, tail)
    return tail

# C01: exact copy
write("C01_exact_copy_of_I03", read("I03_insertion_recursive"))

# C02: identifiers renamed, comments added, a comment-only line removed
h, t = split(read("I04_selection_linear"))
t = rename(t, {"findMinIndex": "locateSmallest", "selectionSort": "orderAscending", "linearSearch": "scanFor",
               "best": "champion", "from": "startAt", "v": "numbers", "m": "minPos", "t": "holder", "x": "needle",
               "sum": "running", "i": "idx"})
t = t.replace("int main() {", "// entry point of the program\nint main() {")
t = t.replace("    selectionSort", "    // sort first\n    selectionSort").replace("    orderAscending", "    // sort first\n    orderAscending")
write("C02_renamed_commented_of_I04", h + t)

# C03: reformatted (Allman braces, tabs) with extra comments
h, t = split(read("I06_merge_binary"))
t = re.sub(r"\) \{\n", ")\n{\n", t)
t = re.sub(r"(?m)^( {4})+", lambda m: "\t" * (len(m.group(0)) // 4), t)
t = "/* merge sort followed by a binary search */\n" + t
write("C03_reformatted_of_I06", h + t)

# C04: copied with redundant statements inserted after simple declarations
h, t = split(read("I07_quick_binary"))
count = [0]
def pad(m):
    count[0] += 1
    k = count[0]
    ind = m.group(1)
    return f"{m.group(0)}\n{ind}int pad{k} = {k}; pad{k} = pad{k} + 0;"
t = re.sub(r"(?m)^( +)(?:int|long long) \w+ = [^;(]*;$", pad, t)
write("C04_padded_of_I07", h + t)

# C05: functions reordered and identifiers renamed
h, t = split(read("I01_bubble_binary"))
chunks = t.strip("\n").split("\n\n")
# chunks: sortNumbers, binarySearch, main -> swap the first two
chunks[0], chunks[1] = chunks[1], chunks[0]
t = "\n\n" + "\n\n".join(chunks) + "\n"
t = rename(t, {"sortNumbers": "arrange", "binarySearch": "bisect", "data": "values", "best": "peak", "tmp": "spare",
               "lo": "left", "hi": "right", "mid": "centre", "target": "goal", "sum": "acc"})
write("C05_reordered_renamed_of_I01", h + t)

truth = [("I03_insertion_recursive", "C01_exact_copy_of_I03"), ("I04_selection_linear", "C02_renamed_commented_of_I04"),
         ("I06_merge_binary", "C03_reformatted_of_I06"), ("I07_quick_binary", "C04_padded_of_I07"),
         ("I01_bubble_binary", "C05_reordered_renamed_of_I01")]
with open(f"{BASE}/ground_truth.csv", "w") as f:
    f.write("# SYNTHETIC test data. Each row is an intentionally copied pair (ground truth = copy). Every other pair is independent.\n")
    for a, b in truth:
        f.write(f"{a},{b}\n")
print("generated", len(truth), "copied submissions")
