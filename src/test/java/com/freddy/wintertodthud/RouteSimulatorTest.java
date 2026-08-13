package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteSimulatorTest
{
    // v0.2.9: action speeds are hard mechanics. SAFE differs only by one
    // route-level reserve; MAX removes that reserve for the internal ceiling.
    private static final RouteSimulator.Profile SAFE =
        new RouteSimulator.Profile(3.0, 3.0, 4.0, 0.5, 0, 6, true);
    private static final RouteSimulator.Profile MAX =
        new RouteSimulator.Profile(3.0, 3.0, 4.0, 0.5, 0, 0, true);

    @Test
    public void thirtyFiveTickBudgetUsesMechanicalCyclesWithoutPerActionInflation()
    {
        RouteSimulator.Input input = new RouteSimulator.Input(
            35, 0, 0, 0, 24, 9, 0, 9, 0, true);

        RouteSimulator.Result safe = RouteSimulator.simulate(input, SAFE);
        RouteSimulator.Result max = RouteSimulator.simulate(input, MAX);

        assertEquals(3, safe.extraRootsThisLoad);
        assertEquals(4, max.extraRootsThisLoad);
    }

    @Test
    public void earlyRoundAllowsFullInventory()
    {
        RouteSimulator.Input input = new RouteSimulator.Input(
            350, 0, 0, 0, 24, 9, 0, 9, 0, true);
        RouteSimulator.Result result = RouteSimulator.simulate(input, SAFE);

        assertTrue(result.fullLoadSafe);
        assertEquals(24, result.extraRootsThisLoad);
    }

    @Test
    public void fletchingAppearsOnlyAfterRaw500IsNoLongerReachable()
    {
        RouteSimulator.Input input = new RouteSimulator.Input(
            47, 390, 5, 0, 19, 0, 9, 9, 0, false);
        RouteSimulator.Result result = RouteSimulator.simulate(input, SAFE);

        assertFalse(result.raw500Reachable);
        assertTrue(result.fiveHundredReachable);
        assertEquals(2, result.fletchesFor500);
    }

    @Test
    public void impossible500StaysImpossibleInsteadOfGivingBadFletchAdvice()
    {
        RouteSimulator.Input input = new RouteSimulator.Input(
            36, 390, 5, 0, 19, 0, 9, 9, 0, false);
        RouteSimulator.Result result = RouteSimulator.simulate(input, SAFE);

        assertFalse(result.raw500Reachable);
        assertFalse(result.fiveHundredReachable);
        assertEquals(0, result.fletchesFor500);
    }

    @Test
    public void minimumFletchRescuePrefersMoreSafeRawBurning()
    {
        RouteSimulator.Input input = new RouteSimulator.Input(
            53, 390, 5, 0, 19, 0, 9, 9, 0, false);
        RouteSimulator.Result result = RouteSimulator.simulate(input, SAFE);

        assertTrue(result.fiveHundredReachable);
        assertEquals(2, result.fletchesFor500);
        assertEquals(4, result.fletchPlanAdditionalRoots);
    }

    @Test
    public void repairedBrazierDoesNotForcePrematureRunWhenMoreRootsStillFit()
    {
        // Regression for the v0.2.8 live audit around t=0267-0269: after SE
        // returned, roughly 58 conservative ticks remained with 435 points and
        // nine raw roots already held. The old 5/4 SAFE rates said GO; the real
        // 3/3 mechanics still fit three more roots before the final run.
        RouteSimulator.Input input = new RouteSimulator.Input(
            58, 435, 9, 0, 15, 0, 10, 10, 0, false);
        RouteSimulator.Result result = RouteSimulator.simulate(input, SAFE);

        assertEquals(3, result.extraRootsThisLoad);
        assertTrue(result.raw500Reachable);
        assertEquals(555, result.rawFinalPoints);
    }

    @Test
    public void equippedFletchingKnifeCanMakeATighterRescuePossible()
    {
        RouteSimulator.Input input = new RouteSimulator.Input(
            35, 390, 5, 0, 19, 0, 9, 9, 0, false);
        RouteSimulator.Profile knife =
            new RouteSimulator.Profile(3.0, 3.0, 3.0, 0.5, 0, 6, true);

        RouteSimulator.Result normal = RouteSimulator.simulate(input, SAFE);
        RouteSimulator.Result equipped = RouteSimulator.simulate(input, knife);

        assertFalse(normal.fiveHundredReachable);
        assertTrue(equipped.fiveHundredReachable);
        assertEquals(4, equipped.fletchesFor500);
    }
}
