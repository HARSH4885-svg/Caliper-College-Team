package dev.codesentinel.engine.reporting;

/**
 * Triage states (section 22). None of these is a verdict; the instructor alone records an academic decision.
 */
public enum ReportState {
    /** Nothing beyond trivial overlap. */
    NO_MEANINGFUL_MATCH,
    /** Overlap is ordinary/shared patterns (common idioms, boilerplate). No flag. */
    LOW_SIMILARITY,
    /** Some distinctive overlap; the teacher may inspect. */
    NEEDS_REVIEW,
    /** Multiple strong independent indicators; teacher review required. */
    HIGH_PRIORITY_REVIEW,
    /** Too short, unsupported, or mostly boilerplate to score reliably. */
    INSUFFICIENT_EVIDENCE
}
