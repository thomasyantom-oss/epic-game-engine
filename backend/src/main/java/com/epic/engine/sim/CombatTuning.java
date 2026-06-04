package com.epic.engine.sim;

import java.util.Random;

/** Simulator-controlled combat knobs. */
public class CombatTuning {
    public static final int DEFAULT_ARMOR_K = 1;

    private double variance = 0.0;
    private Random rng = new Random(0L);
    private boolean armorCurve = true;
    private int armorK = DEFAULT_ARMOR_K;
    private int resistCap = 75;
    private int resistFloor = -50;

    public void setVariance(double variance, long seed) {
        this.variance = variance;
        this.rng = new Random(seed);
    }

    public double rollVariance() {
        if (variance <= 0.0) return 1.0;
        return 1.0 + (rng.nextDouble() * 2.0 - 1.0) * variance;
    }

    public void setArmorCurve(int armorK) {
        this.armorCurve = true;
        this.armorK = armorK;
    }

    public void setArmorCurveForLevel(int baseK, int perLevel, int level) {
        setArmorCurve(baseK + perLevel * Math.max(0, level - 1));
    }

    public void setArmorFlat() {
        this.armorCurve = false;
    }

    public void resetArmorDefault() {
        this.armorCurve = true;
        this.armorK = DEFAULT_ARMOR_K;
    }

    public void setResistBounds(int resistCap, int resistFloor) {
        this.resistCap = resistCap;
        this.resistFloor = resistFloor;
    }

    public boolean armorModelCurve() { return armorCurve; }
    public int armorK() { return armorK; }
    public int resistCap() { return resistCap; }
    public int resistFloor() { return resistFloor; }
}
