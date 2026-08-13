package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CoachMathTest
{
    @Test
    public void finalRunCountdownUsesOneReserveAndMechanicalTiming()
    {
        // 35 ticks left, 6 reserved, 9-tile run = 5 ticks,
        // 3 roots at 3 ticks each => leave in 15 ticks.
        assertEquals(15, CoachMath.runInTicks(35, 6, 9, 3, 0.5, 0, 3.0, 0));
    }

    @Test
    public void fletchDeadlineWorksBackwardsFromRunDeadline()
    {
        assertEquals(7, CoachMath.fletchInTicks(15, 2, 4.0));
        assertEquals(9, CoachMath.fletchInTicks(15, 2, 3.0));
    }

    @Test
    public void risingEnergyHasNoFakeCountdown()
    {
        assertEquals(-1, CoachMath.runInTicks(-1, 6, 9, 3, 0.5, 0, 3.0, 0));
    }
}
