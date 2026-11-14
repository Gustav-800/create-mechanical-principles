package dev.cmp.config;

/**
 * Device-level settings. These live in the server config because they affect
 * world behaviour and should be reloadable per save.
 */
public record ComponentSettings(
        boolean clutchSoftStart,
        int clutchEngageDelayTicks,
        float syncEpsilon,
        boolean autoMergeOnSync,
        float reverseAccelMultiplier,
        int reverseTransitionTicks,
        boolean refuseReverseWithForeignSource,
        float sequencerAcceleration,
        int sequencerStartupTicks,
        float overloadReplanThreshold,
        boolean maintainRatioAfterStep,
        float chainMaxRatioDelta,
        boolean speedControllerSoftThrottle,
        int hotPlugSpeedThreshold,
        boolean enableOnContraptions
) {
    public ComponentSettings {
        clutchEngageDelayTicks = Math.max(0, clutchEngageDelayTicks);
        syncEpsilon = Math.max(0.0001F, syncEpsilon);
        reverseAccelMultiplier = Math.max(1.0F, Math.min(3.0F, reverseAccelMultiplier));
        reverseTransitionTicks = Math.max(0, reverseTransitionTicks);
        sequencerAcceleration = Math.max(0.001F, sequencerAcceleration);
        sequencerStartupTicks = Math.max(0, sequencerStartupTicks);
        overloadReplanThreshold = Math.max(0.0F, Math.min(1.0F, overloadReplanThreshold));
        chainMaxRatioDelta = Math.max(0.0001F, chainMaxRatioDelta);
        hotPlugSpeedThreshold = Math.max(0, Math.min(256, hotPlugSpeedThreshold));
    }

    public boolean hotPlugUnlimited() {
        return hotPlugSpeedThreshold >= 256;
    }

    public boolean hotPlugDisabled() {
        return hotPlugSpeedThreshold <= 0;
    }
}
