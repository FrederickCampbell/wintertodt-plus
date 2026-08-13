package com.freddy.wintertodthud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardRoutePlannerTest
{
    private static final RewardRoutePlanner.Mechanics NORMAL =
        new RewardRoutePlanner.Mechanics(3.0, 3.0, 4.0, 0.5);

    @Test
    public void rawOnlyAlwaysWinsWhenItCanMeetFiveTickRewardDeadline()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            58, 435, 9, 0, 15, 0, 10, 10, 0, false);

        RewardRoutePlanner.Plan plan = RewardRoutePlanner.minimumPlanTo500(input, NORMAL);

        assertTrue(plan.reachable);
        assertTrue(plan.rawOnly());
        assertEquals(0, plan.fletches);
    }

    @Test
    public void extraRootMustFitBothRewardAndOneTickXpDeadline()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            58, 435, 9, 0, 15, 0, 10, 10, 0, false);

        int accepted = 0;
        while (RewardRoutePlanner.oneMoreRawRootFitsWholeObjective(input, NORMAL))
        {
            int acquire = RewardRoutePlanner.moveTicks(input.tilesToRoot, NORMAL.moveTicksPerTile)
                + NORMAL.cutTicks;
            input = new RewardRoutePlanner.Input(
                input.ticksLeft - acquire, input.points,
                input.roots + 1, input.kindling, input.freeSlots - 1,
                0, input.rootToBrazierTiles, input.rootToBrazierTiles,
                input.brazierReadyTicks, false);
            accepted++;
        }

        // Regression for the live repaired-brazier case. Four more roots can
        // actually be cut AND burned; a fifth would become dead inventory.
        assertEquals(4, accepted);
    }

    @Test
    public void fletchingIsUsedOnlyAfterRawOnlyFails()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            47, 390, 5, 0, 19, 0, 9, 9, 0, false);

        RewardRoutePlanner.Plan raw = RewardRoutePlanner.exactFletchPlanTo500(input, NORMAL, 0);
        RewardRoutePlanner.Plan minimum = RewardRoutePlanner.minimumPlanTo500(input, NORMAL);

        assertFalse(raw.reachable);
        assertTrue(minimum.reachable);
        assertEquals(4, minimum.fletches);
    }

    @Test
    public void fletchingKnifeCanLowerTheAbsoluteMinimumN()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            47, 390, 5, 0, 19, 0, 9, 9, 0, false);
        RewardRoutePlanner.Mechanics knife =
            new RewardRoutePlanner.Mechanics(3.0, 3.0, 3.0, 0.5);

        RewardRoutePlanner.Plan normal = RewardRoutePlanner.minimumPlanTo500(input, NORMAL);
        RewardRoutePlanner.Plan equipped = RewardRoutePlanner.minimumPlanTo500(input, knife);

        assertEquals(4, normal.fletches);
        assertEquals(3, equipped.fletches);
    }

    @Test
    public void almostFinalLoadKeepsCuttingWhenMoreRawRootsStillFit()
    {
        // v0.2.10 live regression: ~168 ticks left, 360 points, 13 raw roots.
        // The old state shortcut said RUN because the bag already covered 500.
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            168, 360, 13, 0, 11, 0, 10, 10, 0, false);

        int accepted = 0;
        while (RewardRoutePlanner.oneMoreRawRootFitsWholeObjective(input, NORMAL))
        {
            int acquire = RewardRoutePlanner.moveTicks(input.tilesToRoot, NORMAL.moveTicksPerTile)
                + NORMAL.cutTicks;
            input = new RewardRoutePlanner.Input(
                input.ticksLeft - acquire, input.points, input.roots + 1, input.kindling,
                input.freeSlots - 1, 0, input.rootToBrazierTiles, input.rootToBrazierTiles,
                input.brazierReadyTicks, false);
            accepted++;
        }

        // The live player correctly ignored the old RUN prompt and filled to 24.
        assertEquals(11, accepted);
        assertEquals(24, input.roots);
    }

    @Test
    public void post500FinalLoadAllowsOneMoreRootAtNineteenTicks()
    {
        // v0.2.10 live regression: with one raw root already held and 19 ticks
        // remaining, another 3t cut + 5t run + 6t feeding = 14t, leaving 5t.
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            19, 610, 1, 0, 22, 0, 10, 10, 0, false);

        assertTrue(RewardRoutePlanner.oneMoreRawRootFitsXpObjective(input, NORMAL));

        RewardRoutePlanner.Input afterOne = new RewardRoutePlanner.Input(
            16, 610, 2, 0, 21, 0, 10, 10, 0, false);
        assertFalse(RewardRoutePlanner.oneMoreRawRootFitsXpObjective(afterOne, NORMAL));
    }

    @Test
    public void impossibleRewardPlanReturnsEmergencySignal()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            20, 100, 1, 0, 20, 0, 10, 10, 0, false);

        RewardRoutePlanner.Plan minimum = RewardRoutePlanner.minimumPlanTo500(input, NORMAL);
        assertFalse(minimum.reachable);
    }
    @Test
    public void impossible500StillHasTimeToCutMoreBeforeSalvageBurn()
    {
        // v0.2.16 mid-round login regression: ~19 Energy, essentially no
        // reward path, but plenty of time to cut more than the one held root.
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            71, 0, 1, 0, 20, 0, 10, 10, 0, false);

        assertFalse(RewardRoutePlanner.minimumPlanTo500(input, NORMAL).reachable);
        assertTrue(RewardRoutePlanner.oneMoreRawRootFitsXpObjective(input, NORMAL));
    }

    @Test
    public void maxPointsNextRootIncludesFletchingTime()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            19, 610, 1, 0, 22, 0, 10, 10, 0, false);

        // Raw-XP mode can squeeze in another root here; Max Points cannot,
        // because both held roots would also need to be fletched before feeding.
        assertTrue(RewardRoutePlanner.oneMoreRawRootFitsXpObjective(input, NORMAL));
        assertFalse(RewardRoutePlanner.oneMoreFletchedRootFitsXpObjective(input, NORMAL));
    }

    @Test
    public void maxPointsFletchesEntireLoadWhenTimeAllows()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            200, 100, 20, 0, 4, 0, 10, 10, 0, false);

        assertEquals(20, RewardRoutePlanner.maxCurrentLoadFletchesForPoints(input, NORMAL));
        assertEquals(600, RewardRoutePlanner.maxPointsCurrentLoadProjection(input, NORMAL));
    }

    @Test
    public void maxPointsStopsFletchingBeforeCreatingDeadInventory()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            20, 600, 3, 0, 20, 0, 0, 10, 0, true);

        // Post-500 uses the one-tick XP reserve: 19 usable ticks. Feeding all 3
        // costs 9, leaving 10, so only two 4t fletches safely fit.
        assertEquals(2, RewardRoutePlanner.maxCurrentLoadFletchesForPoints(input, NORMAL));
        assertEquals(660, RewardRoutePlanner.maxPointsCurrentLoadProjection(input, NORMAL));
    }

    @Test
    public void maxPointsOpenEndedClockAllowsAllCurrentFletches()
    {
        RewardRoutePlanner.Input input = new RewardRoutePlanner.Input(
            -1, 0, 7, 2, 15, 0, 10, 10, 0, false);

        assertEquals(7, RewardRoutePlanner.maxCurrentLoadFletchesForPoints(input, NORMAL));
        assertTrue(RewardRoutePlanner.oneMoreFletchedRootFitsXpObjective(input, NORMAL));
    }

}
