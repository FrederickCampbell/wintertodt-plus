package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AdvisorMathTest
{
    @Test
    public void doesNotFletchWhenRawRootsAlreadyReach500()
    {
        assertEquals(0, AdvisorMath.minimumFletchesFor500(420, 8, 0));
    }

    @Test
    public void fletchesOnlyWhatIsNeeded()
    {
        assertEquals(2, AdvisorMath.minimumFletchesFor500(390, 8, 0));
    }

    @Test
    public void countsRootsAndKindlingLikeRuneLite()
    {
        assertEquals(95, AdvisorMath.inventoryPoints(2, 3));
        assertEquals(125, AdvisorMath.potentialInventoryPoints(2, 3));
    }
}
