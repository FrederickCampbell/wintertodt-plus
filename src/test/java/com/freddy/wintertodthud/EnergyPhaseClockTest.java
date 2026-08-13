package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class EnergyPhaseClockTest
{
    @Test
    public void fourKnownPhasesPredictSeventhHitExactly()
    {
        int[] hits = new int[EnergyPhaseClock.PERIOD];
        // From current tick residue 0, future hits occur at 11, 12, 13, 14,
        // then 25, 26, 27... Seven Energy remaining therefore ends at 27t.
        hits[11] = 1;
        hits[12] = 1;
        hits[13] = 1;
        hits[0] = 1;
        assertEquals(27, EnergyPhaseClock.predictTicksToZero(7, 0, hits));
    }

    @Test
    public void threeKnownPhasesCanLastLongerThanFourPyroAverage()
    {
        int[] hits = new int[EnergyPhaseClock.PERIOD];
        hits[0] = 1;
        hits[5] = 1;
        hits[10] = 1;
        // Future hits: 5,10,14,19,24,28,33.
        assertEquals(33, EnergyPhaseClock.predictTicksToZero(7, 0, hits));
    }

    @Test
    public void simultaneousPyromancerHitsPreserveMultiplicity()
    {
        int[] hits = new int[EnergyPhaseClock.PERIOD];
        hits[5] = 2;
        // Two Energy at tick 5, then two more at tick 19.
        assertEquals(19, EnergyPhaseClock.predictTicksToZero(3, 0, hits));
    }

    @Test
    public void noActiveDrainScheduleHasNoForecast()
    {
        assertEquals(-1, EnergyPhaseClock.predictTicksToZero(50, 0,
            new int[EnergyPhaseClock.PERIOD]));
    }

    @Test
    public void alreadyDeadIsZeroTicks()
    {
        assertEquals(0, EnergyPhaseClock.predictTicksToZero(0, 123,
            new int[EnergyPhaseClock.PERIOD]));
    }
    @Test
    public void midRoundBootstrapMatchesUnknownFourStationLowerEdge()
    {
        assertEquals(337, EnergyPhaseClock.bootstrapSafeTicksFromEnergy(100));
        assertEquals(113, EnergyPhaseClock.bootstrapSafeTicksFromEnergy(33));
        assertEquals(1, EnergyPhaseClock.bootstrapSafeTicksFromEnergy(4));
        assertEquals(1, EnergyPhaseClock.bootstrapSafeTicksFromEnergy(1));
        assertEquals(0, EnergyPhaseClock.bootstrapSafeTicksFromEnergy(0));
    }

}
