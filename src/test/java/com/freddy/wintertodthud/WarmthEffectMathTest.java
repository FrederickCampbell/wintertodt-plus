package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.*;

public class WarmthEffectMathTest
{
    @Test public void cakeBiteQualifies(){ assertEquals(35, WarmthEffectMath.warmthGain("Cake", 4)); }
    @Test public void tinyHealDoesNotQualify(){ assertEquals(0, WarmthEffectMath.warmthGain("Shrimps", 3)); }
    @Test public void bigFoodStillRestoresFixedWarmth(){ assertEquals(35, WarmthEffectMath.warmthGain("Shark", 20)); }
    @Test public void rejuvenationOverridesHpRule(){ assertEquals(30, WarmthEffectMath.warmthGain("Rejuvenation potion (4)", 0)); }
    @Test public void healingPotionDoseQualifies(){ assertEquals(35, WarmthEffectMath.warmthGain("Saradomin brew(4)", 16)); }
}
