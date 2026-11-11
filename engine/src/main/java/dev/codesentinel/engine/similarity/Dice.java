package dev.codesentinel.engine.similarity;

/** Sorensen-Dice coefficient on fingerprint sets (FR-PLAG-16): 2|A n B| / (|A| + |B|). */
public final class Dice {
    private Dice() {
    }

    public static double coefficient(int intersection, int sizeA, int sizeB) {
        int denom = sizeA + sizeB;
        return denom == 0 ? 0.0 : (2.0 * intersection) / denom;
    }
}
