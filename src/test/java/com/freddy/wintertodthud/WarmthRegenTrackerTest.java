package com.freddy.wintertodthud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WarmthRegenTrackerTest
{
    @Test
    public void userFourPieceWarmSetupIsRecognized()
    {
        assertTrue(WarmthRegenTracker.isKnownWarmName("Clue hunter garb"));
        assertTrue(WarmthRegenTracker.isKnownWarmName("Clue hunter gloves"));
        assertTrue(WarmthRegenTracker.isKnownWarmName("Clue hunter boots"));
        assertTrue(WarmthRegenTracker.isKnownWarmName("Rainbow scarf"));
    }

    @Test
    public void obviousNonWarmItemIsNotRecognized()
    {
        assertFalse(WarmthRegenTracker.isKnownWarmName("Graceful gloves"));
    }

    @Test
    public void regenCadenceMatchesHitpointsStyleCycle()
    {
        assertEquals(100, WarmthRegenTracker.NORMAL_REGEN_TICKS);
        assertEquals(50, WarmthRegenTracker.RAPID_HEAL_REGEN_TICKS);
    }
}
