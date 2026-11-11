# Caliper — Plagiarism Detection Benchmark & Quality Evaluation

This document details the architecture, experimental methodology, ground-truth dataset, mathematical formulation, and actual measured results of Caliper's plagiarism and code-similarity detection engine.

---

## 1. Architectural Overview & Rationale

Caliper employs a **hybrid dual-engine architecture** combining:
1. **Lexical Tokenization & Winnowing Fingerprinting** (based on Schleimer et al. Winnowing and Karp–Rabin substring hashing).
2. **Abstract Syntax Tree (AST) Structural Comparison** (powered by native Tree-sitter parsers).

### Why Dual Analysis?
- **Lexical Winnowing** excels at identifying exact code overlap, variable renaming, and formatting changes with minimal computational overhead ($O(N)$ fingerprinting, inverted index candidate pruning).
- **AST Structural Comparison** abstracts away variable identifiers and formatting entirely, constructing normalized node sequences and subtree hash profiles to detect structural clones, control-flow rearrangement, and functional equivalence.
- **Explainability**: Similarity scores in Caliper are **investigation signals, not accusations**. The engine produces granular evidence (token ranges, AST subtree highlights, and common starter-code suppression) for faculty review.

---

## 2. Benchmark Corpus Construction

To ensure deterministic, reproducible evaluation without compromising private student data, Caliper maintains a synthetic benchmark corpus located in `engine/src/test/resources/fixtures/quality/`.

### 2.1 Composition
The dataset consists of **17 complete C/C++ program submissions** for an algorithmic assignment:
- **12 Independent Implementations** ($I_{01}$ through $I_{12}$): Independently authored solutions utilizing distinct algorithmic strategies (e.g., recursive vs. iterative, bubble sort, selection sort, merge sort, quicksort, heap sort, binary search, map counting, and struct aggregates).
- **5 Deliberate Copies** ($C_{01}$ through $C_{05}$): Plagiarism cases engineered with realistic adversarial obfuscations against specific original submissions.
- **1 Starter Code Template** (`starter/`): Common boilerplate provided to all students containing header includes, input reading loops, and function signatures.

### 2.2 Attack Vectors in Benchmark Dataset
| Copy ID | Target Submission | Applied Transformation / Attack Technique |
| :--- | :--- | :--- |
| **`C01_exact_copy_of_I03`** | `I03_insertion_recursive` | **Verbatim Copy**: 100% identical source code with zero edits. |
| **`C02_renamed_commented_of_I04`** | `I04_selection_linear` | **Identifier Renaming & Comments**: Systematically renamed variables, parameters, and injected extensive synthetic comment blocks. |
| **`C03_reformatted_of_I06`** | `I06_merge_binary` | **Reformatting & Whitespace**: Radically altered indentation, brace styles (K&R vs Allman), tab sizes, and merged lines. |
| **`C04_padded_of_I07`** | `I07_quick_binary` | **Statement Padding / Dead Code**: Inserted redundant computations, unused local variables, and benign statement noise. |
| **`C05_reordered_renamed_of_I01`** | `I01_bubble_binary` | **Statement Reordering & Renaming**: Swapped independent statements, flipped condition expressions, and renamed functions. |

### 2.3 Ground-Truth Matrix
Ground truth is codified in `engine/src/test/resources/fixtures/quality/ground_truth.csv`. Out of $\binom{17}{2} = 136$ possible pairwise comparisons:
- **5 pairs** are ground-truth copies (True Positives).
- **131 pairs** are genuinely independent solutions (True Negatives).

---

## 3. Mathematical Metrics & Formulas

For any given threshold and normalization mode, pairwise comparisons are categorized into a standard confusion matrix:
- **True Positive ($TP$)**: Planted copy correctly surfaced with a review flag.
- **False Positive ($FP$)**: Independent pair incorrectly surfaced with a review flag.
- **True Negative ($TN$)**: Independent pair correctly ignored below cutoff.
- **False Negative ($FN$)**: Planted copy erroneously missed below cutoff.

### Formulas
$$\text{Precision} = \frac{TP}{TP + FP}$$

$$\text{Recall} = \frac{TP}{TP + FN}$$

$$\text{F1 Score} = 2 \cdot \frac{\text{Precision} \cdot \text{Recall}}{\text{Precision} + \text{Recall}}$$

$$\text{False Positive Rate (FPR)} = \frac{FP}{FP + TN}$$

---

## 4. Actual Measured Quality Results

Measured on Linux amd64 (`Java 25.0.4.1`, 12 CPU cores) via `QualityMeasurementTest`:

### 4.1 Normalization Mode & Parameter Exploration

| Identifier Mode | Starter Suppression | Candidate Cutoff | Any Flag (TP/FP/TN/FN) | Precision | Recall | FPR | High Priority Only (TP/FP/FN) | Candidates |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `FIRST_OCCURRENCE_UNIT` *(Default)* | **ON** | **0.30** | **5 / 0 / 131 / 0** | **1.000** | **1.000** | **0.000** | **4 / 0 / 1** | **5** |
| `FIRST_OCCURRENCE_UNIT` | **ON** | 0.50 | 5 / 0 / 131 / 0 | 1.000 | 1.000 | 0.000 | 4 / 0 / 1 | 5 |
| `FIRST_OCCURRENCE_UNIT` | OFF | 0.30 | 5 / 0 / 131 / 0 | 1.000 | 1.000 | 0.000 | 3 / 0 / 2 | 5 |
| `FIRST_OCCURRENCE_UNIT` | OFF | 0.50 | 5 / 0 / 131 / 0 | 1.000 | 1.000 | 0.000 | 3 / 0 / 2 | 5 |
| `FIRST_OCCURRENCE_FILE` | ON | 0.30 | 3 / 0 / 131 / 2 | 1.000 | 0.600 | 0.000 | 3 / 0 / 2 | 3 |
| `FIRST_OCCURRENCE_FILE` | OFF | 0.30 | 2 / 0 / 131 / 3 | 1.000 | 0.400 | 0.000 | 2 / 0 / 3 | 4 |
| `BLIND` | ON | 0.30 | 5 / 0 / 131 / 0 | 1.000 | 1.000 | 0.000 | 5 / 0 / 0 | 6 |
| `BLIND` | OFF | 0.30 | 5 / 0 / 131 / 0 | 1.000 | 1.000 | 0.000 | 3 / 0 / 2 | 12 |

### 4.2 Key Findings
1. **Zero False Positives Across All Modes**: At no point did independent solutions implementing textbook algorithms (e.g. bubble sort or binary search) falsely trigger similarity flags.
2. **Impact of Starter Code Suppression**: Without starter code suppression, candidate generation produced up to 12 candidate pairs due to common header includes and I/O loops. With starter code suppression enabled, candidate generation precisely isolated the true 5 plagiarized pairs.
3. **Identifier Normalization Superiority**: `FIRST_OCCURRENCE_UNIT` achieves **100% Precision and 100% Recall (F1 = 1.000)** while preserving structural differentiation, outperforming `FIRST_OCCURRENCE_FILE`.

### 4.3 Default Configuration Surfaced Pairs Breakdown
Configuration: `norm-v1; k=10; w=5; literals=SMART; dropIncludes=true; identifiers=FIRST_OCCURRENCE_UNIT`, candidate cutoff `0.30`, review threshold `0.40`, high priority threshold `0.80`.

| State | Submission A | Submission B | Dice | Weighted Dice | Distinctive Score | Tokens | Ground Truth |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `HIGH_PRIORITY_REVIEW` | `C02_renamed_commented_of_I04` | `I04_selection_linear` | 1.00 | 1.00 | 1.00 | 239 | **COPY** |
| `HIGH_PRIORITY_REVIEW` | `C03_reformatted_of_I06` | `I06_merge_binary` | 1.00 | 1.00 | 1.00 | 426 | **COPY** |
| `HIGH_PRIORITY_REVIEW` | `C01_exact_copy_of_I03` | `I03_insertion_recursive` | 1.00 | 1.00 | 0.99 | 268 | **COPY** |
| `HIGH_PRIORITY_REVIEW` | `C05_reordered_renamed_of_I01` | `I01_bubble_binary` | 0.93 | 0.91 | 0.81 | 226 | **COPY** |
| `NEEDS_REVIEW` | `C04_padded_of_I07` | `I07_quick_binary` | 0.59 | 0.49 | 0.68 | 270 | **COPY** |

---

## 5. Performance & Scalability Benchmarks

Performance is measured under two synthetic workloads using `BenchmarkTest`:
- **100 submissions**, ~314 lines each.
- Total pairwise combinations evaluated: $\binom{100}{2} = 4,950$ pairs.

| Workload Scenario | Characteristics | Possible Pairs | Candidate Filter Output | Flagged Pairs | Wall Time (ms) | Target |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Diverse (Realistic)** | Varied algorithmic implementations, 3 planted copies | 4,950 | 3 candidates | 3 pairs | **2,320 ms** | < 5,000 ms |
| **Homogeneous (Worst-Case)** | Identical function templates and loop structures | 4,950 | 4,948 candidates | 0 pairs | **3,627 ms** | < 5,000 ms |

---

## 6. Known Limitations

1. **Synthetic vs. Real-World Domain**: These results reflect a synthetic benchmark corpus with controlled transformations. Real student submissions may exhibit additional noise (e.g. LLM-assisted rephrasing, partial snippet borrowing).
2. **Investigation Signal Rule**: Similarity scores must **never** be used to automatically assign academic penalties. They serve exclusively to highlight candidate regions for qualified instructor review.

---

## 7. How to Reproduce

Execute the test suites directly via Maven:

```bash
# Run detection quality evaluation and regenerate target/quality-report.md
mvn test -pl engine -Dtest=QualityMeasurementTest

# Run performance and scalability benchmarks and regenerate target/benchmark.txt
mvn test -pl engine -Dtest=BenchmarkTest
```
