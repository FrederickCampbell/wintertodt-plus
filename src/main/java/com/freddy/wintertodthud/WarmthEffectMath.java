package com.freddy.wintertodthud;

/** Pure Wintertodt Warmth restoration rules used by hover-preview UI. */
public final class WarmthEffectMath
{
    public static final int QUALIFYING_CONSUMABLE_WARMTH = 35;
    public static final int REJUVENATION_WARMTH = 30;

    private WarmthEffectMath() {}

    public static int warmthGain(String itemName, int theoreticalHpRestore)
    {
        if (itemName != null && itemName.toLowerCase().startsWith("rejuvenation potion"))
        {
            return REJUVENATION_WARMTH;
        }
        return theoreticalHpRestore >= 4 ? QUALIFYING_CONSUMABLE_WARMTH : 0;
    }
}
