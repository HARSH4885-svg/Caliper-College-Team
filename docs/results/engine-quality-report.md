# Plagiarism engine quality measurement (SYNTHETIC data)

Dataset: 17 submissions to one assignment = 12 independent solutions (several implement the same textbook algorithms) + 5 deliberate copies. 136 pairs, 5 ground-truth copies, 131 independent pairs. Environment: Java 25.0.4.1, Linux amd64, 12 CPU(s).

Predicted positive = `NEEDS_REVIEW` or `HIGH_PRIORITY_REVIEW` ("any flag"); the second column counts only `HIGH_PRIORITY_REVIEW`. Undefined metrics are shown as `undefined`.

| identifiers | starter suppression | candidate cutoff | any flag: TP/FP/TN/FN | precision | recall | FPR | high only: TP/FP/FN | candidates |
|---|---|---|---|---|---|---|---|---|
| FIRST_OCCURRENCE_FILE | on | 0.50 | 3/0/131/2 | 1.000 | 0.600 | 0.000 | 3/0/2 | 3 |
| FIRST_OCCURRENCE_FILE | on | 0.30 | 3/0/131/2 | 1.000 | 0.600 | 0.000 | 3/0/2 | 3 |
| FIRST_OCCURRENCE_FILE | off | 0.50 | 2/0/131/3 | 1.000 | 0.400 | 0.000 | 2/0/3 | 2 |
| FIRST_OCCURRENCE_FILE | off | 0.30 | 2/0/131/3 | 1.000 | 0.400 | 0.000 | 2/0/3 | 4 |
| FIRST_OCCURRENCE_UNIT | on | 0.50 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 4/0/1 | 5 |
| FIRST_OCCURRENCE_UNIT | on | 0.30 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 4/0/1 | 5 |
| FIRST_OCCURRENCE_UNIT | off | 0.50 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 3/0/2 | 5 |
| FIRST_OCCURRENCE_UNIT | off | 0.30 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 3/0/2 | 5 |
| BLIND | on | 0.50 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 5/0/0 | 5 |
| BLIND | on | 0.30 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 5/0/0 | 6 |
| BLIND | off | 0.50 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 3/0/2 | 5 |
| BLIND | off | 0.30 | 5/0/131/0 | 1.000 | 1.000 | 0.000 | 3/0/2 | 12 |

## Default configuration, per surfaced pair

Config: `norm-v1;k=10;w=5;literals=SMART;dropIncludes=true;identifiers=FIRST_OCCURRENCE_UNIT`, cutoff 0.3, review 0.4, high 0.8

| state | A | B | Dice | weighted Dice | distinctive score | distinctive tokens | ground truth |
|---|---|---|---|---|---|---|---|
| HIGH_PRIORITY_REVIEW | C02_renamed_commented_of_I04 | I04_selection_linear | 1.00 | 1.00 | 1.00 | 239 | COPY |
| HIGH_PRIORITY_REVIEW | C03_reformatted_of_I06 | I06_merge_binary | 1.00 | 1.00 | 1.00 | 426 | COPY |
| HIGH_PRIORITY_REVIEW | C01_exact_copy_of_I03 | I03_insertion_recursive | 1.00 | 1.00 | 0.99 | 268 | COPY |
| HIGH_PRIORITY_REVIEW | C05_reordered_renamed_of_I01 | I01_bubble_binary | 0.93 | 0.91 | 0.81 | 226 | COPY |
| NEEDS_REVIEW | C04_padded_of_I07 | I07_quick_binary | 0.59 | 0.49 | 0.68 | 270 | COPY |
