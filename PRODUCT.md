# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack
Existing: React 18 + TypeScript + Vite, plain CSS (frontend/src/styles.css), served by the Spring Boot backend in production. Keep this stack; no UI framework dependency is required.

## Users
Instructors who run programming courses (create courses and assignments with sample and hidden tests, read verdicts, review flagged similarity) and students who submit code and read feedback. Both roles matter equally for the presentation. A university project demo at IIIT Allahabad is the near-term evaluation audience.

## Product Purpose
CodeSentinel is a local-first platform for programming assignments: sandboxed automatic judging in Docker and an explainable, human-reviewed similarity check within one assignment. Success: instructors save grading time and review only the pairs worth a human look; students get fast, clear feedback.

## Positioning
Similarity output is an investigation signal, never an accusation: no automatic penalties, no "guilty" wording, no red for unreviewed similarity, evidence shown side by side with reasons, a human decides. Hidden tests stay hidden.

## Operating Context
Course -> assignment -> submissions -> verdict per test; instructor runs a comparison on an assignment, sees ranked pairs with matched code fragments, records CONFIRMED_CONCERN / DISMISSED / INCONCLUSIVE with a note, can export an evidence report. Demo data is synthetic and labelled.

## Capabilities and Constraints
Roles: instructor, student. Verdicts: AC, WA, CE, TLE, MLE, RE, IE. Similarity states: NO_MEANINGFUL_MATCH, LOW_SIMILARITY, NEEDS_REVIEW, HIGH_PRIORITY_REVIEW, INSUFFICIENT_EVIDENCE. Existing routes and API must keep working; vitest suite (10) must keep passing or be updated honestly. UI strings must not use "guilty", "cheat", "plagiarised". Google Drive storage is not implemented and must not be advertised.

## Brand Commitments
Name stays CodeSentinel. The user wants a fresh tagline and voice (not specified; chosen by the designer and flagged for approval). Fairness-first stance is the core promise.

## Evidence on Hand
Real measured facts only: demo copied pair scores 0.783; engine quality set TP5/FP0/TN131/FN0 on SYNTHETIC data; 150+ tests. No customers, testimonials or institutional endorsements exist; do not invent any.

## Product Principles
1. Evidence before judgement: show why, never just a score.
2. A human decides; the product only prioritises.
3. Students and instructors each get a home built for their job.
4. Honest about limits (synthetic data, local-first).

## Accessibility & Inclusion
Keyboard operable, visible focus, sufficient contrast, no meaning by colour alone, responsive down to 360px.
