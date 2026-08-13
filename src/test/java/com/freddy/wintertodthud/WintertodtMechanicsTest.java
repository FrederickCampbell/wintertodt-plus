package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WintertodtMechanicsTest
{
    @Test
    public void deterministicActionCyclesStayCanonical()
    {
        assertEquals(3.0, WintertodtMechanics.CUT_TICKS, 0.0);
        assertEquals(3.0, WintertodtMechanics.FEED_TICKS, 0.0);
        assertEquals(4.0, WintertodtMechanics.fletchTicks(false), 0.0);
        assertEquals(3.0, WintertodtMechanics.fletchTicks(true), 0.0);
        assertEquals(0.5, WintertodtMechanics.movementTicksPerTile(true), 0.0);
        assertEquals(1.0, WintertodtMechanics.movementTicksPerTile(false), 0.0);
    }

    @Test
    public void idleCadenceGraceCoversRepeatAnimationGaps()
    {
        assertEquals(4, WintertodtState.actionCadenceGraceTicks(WintertodtActivity.CUTTING));
        assertEquals(5, WintertodtState.actionCadenceGraceTicks(WintertodtActivity.FLETCHING));
        assertEquals(4, WintertodtState.actionCadenceGraceTicks(WintertodtActivity.FEEDING));
    }

}
