package com.freddy.wintertodthud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StationPhaseEstimatorTest
{
    @Test
    public void knownAssignmentPredictionMatchesPeriodicHitMath()
    {
        boolean[] active = {true, true, true, true};
        int code = StationPhaseEstimator.encode(11, 12, 13, 0);
        assertEquals(27, StationPhaseEstimator.predictTicksToZero(7, 0, code, active));
    }

    @Test
    public void stationOutagePreservesConstrainedModelInsteadOfGlobalReset()
    {
        StationPhaseEstimator model = new StationPhaseEstimator();
        boolean[] active = {true, true, true, true};
        int[] revisions = {1, 1, 1, 1};
        int[] phases = {2, 5, 9, 12};
        int energy = 80;

        for (int t = 1; t <= 18; t++)
        {
            energy -= hitsAt(t, phases, active);
            model.onGameTick(true, energy, true, active, revisions);
        }

        assertTrue("aggregate schedule should lock after a full observed cycle", model.trusted());
        int calibratedCandidates = model.candidateCount();
        assertTrue(calibratedCandidates > 0);
        assertTrue(calibratedCandidates < StationPhaseEstimator.COMBINATIONS);

        active[0] = false;
        revisions[0]++;
        int t = 19;
        energy -= hitsAt(t, phases, active);
        model.onGameTick(true, energy, true, active, revisions);

        assertTrue("partial knowledge should still produce a conservative forecast", model.hasForecast());
        assertTrue("one outage must not reset to all 14^4 assignments",
            model.candidateCount() < StationPhaseEstimator.COMBINATIONS);

        for (t = 20; t <= 34; t++)
        {
            energy -= hitsAt(t, phases, active);
            model.onGameTick(true, energy, true, active, revisions);
        }
        assertTrue("three-station aggregate schedule should relock without relearning all four", model.trusted());

        active[0] = true;
        phases[0] = 7; // relight can restart this station on a new phase
        revisions[0]++;
        t = 35;
        energy -= hitsAt(t, phases, active);
        model.onGameTick(true, energy, true, active, revisions);

        assertTrue(model.hasForecast());
        assertFalse("resumed station should temporarily widen the forecast", model.trusted());
        assertTrue("resume should expand only that station, not globally",
            model.candidateCount() < StationPhaseEstimator.COMBINATIONS);
    }


    @Test
    public void unexplainedPhaseDriftWidensOneUnknownStationBeforeGlobalReset()
    {
        StationPhaseEstimator model = new StationPhaseEstimator();
        boolean[] active = {true, true, true, true};
        int[] revisions = {1, 1, 1, 1};
        int[] phases = {2, 5, 9, 12};
        int energy = 80;

        for (int t = 1; t <= 18; t++)
        {
            energy -= hitsAt(t, phases, active);
            model.onGameTick(true, energy, true, active, revisions);
        }
        assertTrue(model.trusted());

        // Simulate the pattern seen in the v0.2.7 live audit: one periodic
        // drainer shifts phase without a matching native station-widget revision.
        phases[0] = 7;
        boolean sawConflict = false;
        for (int t = 19; t <= 34; t++)
        {
            energy -= hitsAt(t, phases, active);
            model.onGameTick(true, energy, true, active, revisions);
            sawConflict |= model.conflicts() > 0;
        }

        assertTrue("hidden drift should be detected as a model conflict", sawConflict);
        assertTrue("local widening should recover a usable forecast", model.hasForecast());
        assertTrue("one-station drift recovery should remain narrower than a global reset",
            model.candidateCount() < StationPhaseEstimator.COMBINATIONS);
    }


    @Test
    public void oneOffEnergyObservationLagUsesJitterRecoveryWithoutPhaseReset()
    {
        StationPhaseEstimator model = new StationPhaseEstimator();
        boolean[] active = {true, true, true, true};
        int[] revisions = {1, 1, 1, 1};
        int[] phases = {2, 5, 9, 12};
        int energy = 80;

        for (int t = 1; t <= 18; t++)
        {
            energy -= hitsAt(t, phases, active);
            model.onGameTick(true, energy, true, active, revisions);
        }
        assertTrue(model.trusted());
        int before = model.candidateCount();

        // t=19 is residue 5 and should physically drain. Delay that visible
        // Energy update to t=20 to mimic a one-off client observation shift.
        model.onGameTick(true, energy, true, active, revisions);
        energy -= 1;
        model.onGameTick(true, energy, true, active, revisions);

        assertTrue(model.hasForecast());
        assertTrue("shifted observation should be recognized as timing jitter",
            model.timingRecoveries() >= 2);
        assertEquals("bounded widget timing must not become structural phase conflict",
            0, model.conflicts());
        assertEquals("timing tolerance should retain the constrained station model",
            before, model.candidateCount());
    }

    @Test
    public void safeEdgeIsAlwaysTheEarliestPlausibleFinish()
    {
        StationPhaseEstimator model = new StationPhaseEstimator();
        boolean[] active = {true, true, true, true};
        int[] revisions = {1, 1, 1, 1};
        model.onGameTick(true, 50, true, active, revisions);

        assertTrue(model.hasForecast());
        assertTrue(model.safeTicksLeft() > 0);
        assertTrue(model.latestTicksLeft() >= model.safeTicksLeft());
    }

    private static int hitsAt(int tick, int[] phases, boolean[] active)
    {
        int residue = tick % StationPhaseEstimator.PERIOD;
        int hits = 0;
        for (int i = 0; i < phases.length; i++)
        {
            if (active[i] && phases[i] == residue)
            {
                hits++;
            }
        }
        return hits;
    }
}
