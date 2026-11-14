package dev.cmp.config;

/**
 * Ready-made tuning profiles. CUSTOM takes every value from the config file.
 */
public enum DynamicsPreset {
    REALISTIC(0.03F, 0.10F, 0.20F, 0.005F),
    BALANCED(0.05F, 0.20F, 0.30F, 0.01F),
    ARCADE(0.10F, 0.40F, 0.60F, 0.02F),
    CUSTOM(0.05F, 0.20F, 0.30F, 0.01F);

    private final float alpha;
    private final float accelCeilingUp;
    private final float accelCeilingDown;
    private final float accelFloor;

    DynamicsPreset(float alpha, float accelCeilingUp, float accelCeilingDown, float accelFloor) {
        this.alpha = alpha;
        this.accelCeilingUp = accelCeilingUp;
        this.accelCeilingDown = accelCeilingDown;
        this.accelFloor = accelFloor;
    }

    public float alpha() {
        return alpha;
    }

    public float accelCeilingUp() {
        return accelCeilingUp;
    }

    public float accelCeilingDown() {
        return accelCeilingDown;
    }

    public float accelFloor() {
        return accelFloor;
    }
}
