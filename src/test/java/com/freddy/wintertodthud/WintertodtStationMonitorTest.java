package com.freddy.wintertodthud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WintertodtStationMonitorTest
{
    @Test
    public void jagexPyromancerSpriteSemanticsAreDecodedCorrectly()
    {
        assertEquals(Boolean.TRUE, WintertodtStationMonitor.decodePyromancer(-1));
        assertEquals(Boolean.FALSE, WintertodtStationMonitor.decodePyromancer(1400));
        assertNull(WintertodtStationMonitor.decodePyromancer(0));
    }

    @Test
    public void jagexBrazierSpritesAreDecodedCorrectly()
    {
        assertEquals(WintertodtStationMonitor.BrazierState.BROKEN,
            WintertodtStationMonitor.decodeBrazier(1397));
        assertEquals(WintertodtStationMonitor.BrazierState.UNLIT,
            WintertodtStationMonitor.decodeBrazier(1398));
        assertEquals(WintertodtStationMonitor.BrazierState.LIT,
            WintertodtStationMonitor.decodeBrazier(1399));
        assertEquals(WintertodtStationMonitor.BrazierState.UNKNOWN,
            WintertodtStationMonitor.decodeBrazier(-1));
    }
}
