package com.freddy.wintertodthud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AlertEngineTest
{
    private static WintertodtHudConfig defaults()
    {
        return new WintertodtHudConfig() { };
    }

    @Test
    public void brazierOutIsQuietByDefault()
    {
        assertFalse(defaults().brazierOutAlert());
        assertFalse(defaults().brazierOutNotify());
    }

    @Test
    public void onlyHighestPriorityTextAlertWins()
    {
        AlertEngine alerts = new AlertEngine(defaults(), null);
        alerts.setPersistent(AlertKind.IDLE, true, AlertVisual.SCREEN, false);
        alerts.fire(AlertKind.GO_NOW, AlertVisual.BANNER, false, 3);
        alerts.fire(AlertKind.POINTS_NOT_SAFE, AlertVisual.BANNER, false, 3);

        assertEquals(AlertKind.POINTS_NOT_SAFE, alerts.primaryOverlayKind());

        alerts.dismiss(AlertKind.POINTS_NOT_SAFE);
        assertEquals(AlertKind.GO_NOW, alerts.primaryOverlayKind());

        alerts.dismiss(AlertKind.GO_NOW);
        assertEquals(AlertKind.IDLE, alerts.primaryOverlayKind());
    }

    @Test
    public void specificInterruptionBeatsGenericIdle()
    {
        AlertEngine alerts = new AlertEngine(defaults(), null);
        alerts.setPersistent(AlertKind.IDLE, true, AlertVisual.SCREEN, false);
        alerts.fire(AlertKind.BRAZIER_OUT, AlertVisual.BANNER, false, 3);
        assertEquals(AlertKind.BRAZIER_OUT, alerts.primaryOverlayKind());
    }
}
