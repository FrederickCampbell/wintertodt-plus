package com.freddy.wintertodthud;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * RuneLite-facing wrapper around the pure four-station phase estimator.
 *
 * Numerical planning uses the conservative earliest plausible finish. A single
 * user-facing countdown is exposed only when the uncertainty band is very
 * narrow; exact "trusted" lock means earliest and latest are identical.
 */
@Singleton
public class EnergyPhaseClock
{
    static final int PERIOD = StationPhaseEstimator.PERIOD;

    private final WintertodtStationMonitor stations;
    private final StationPhaseEstimator estimator = new StationPhaseEstimator();
    private int bootstrapSafeTicks = -1;

    @Inject
    EnergyPhaseClock(WintertodtStationMonitor stations)
    {
        this.stations = stations;
    }

    public void reset()
    {
        estimator.reset();
        bootstrapSafeTicks = -1;
    }

    public void onGameTick(boolean roundActive, int energy)
    {
        boolean[] active = new boolean[StationPhaseEstimator.STATIONS];
        int[] revisions = new int[StationPhaseEstimator.STATIONS];
        for (WintertodtStationMonitor.Quadrant quadrant : WintertodtStationMonitor.Quadrant.values())
        {
            int i = quadrant.ordinal();
            active[i] = stations.contributing(quadrant);
            revisions[i] = stations.stationRevision(quadrant);
        }
        estimator.onGameTick(roundActive, energy, stations.reliable(), active, revisions);

        // A fresh login/world hop can begin in the middle of a round before the
        // native station widgets/phases are fully observed. Do not leave the
        // advisor blind: four active drainers is the fastest physically possible
        // Energy loss, so its unknown-phase earliest finish is a conservative
        // temporary planning budget. The exact estimator replaces it as soon as
        // normal observations become available.
        bootstrapSafeTicks = roundActive && energy > 0 && !estimator.hasForecast()
            ? bootstrapSafeTicksFromEnergy(energy) : -1;
    }

    /** Exact phase/finish lock. */
    public boolean trusted()
    {
        return estimator.trusted();
    }

    /** Exact countdown only; retained for existing debug/UI callers. */
    public int ticksLeft()
    {
        return trusted() ? estimator.safeTicksLeft() : -1;
    }

    /** Conservative earliest plausible finish from the calibrated phase solver only. */
    public int safeTicksLeft()
    {
        return estimator.safeTicksLeft();
    }

    /**
     * Planner budget. Uses the exact/uncertain station solver when available,
     * otherwise a deliberately conservative four-drainer mid-round bootstrap.
     */
    public int planningTicksLeft()
    {
        int solved = estimator.safeTicksLeft();
        return solved >= 0 ? solved : bootstrapSafeTicks;
    }

    public boolean usingBootstrap()
    {
        return estimator.safeTicksLeft() < 0 && bootstrapSafeTicks >= 0;
    }

    /**
     * Fastest possible finish with four unknown independent 14-tick drainers.
     * This exactly matches the lower edge of a fully unknown four-station phase
     * search (100 Energy -> 337 ticks, 33 -> 113 ticks).
     */
    static int bootstrapSafeTicksFromEnergy(int energy)
    {
        if (energy <= 0)
        {
            return Math.max(0, energy);
        }
        int bursts = (energy + StationPhaseEstimator.STATIONS - 1) / StationPhaseEstimator.STATIONS;
        return 1 + (bursts - 1) * PERIOD;
    }

    public int latestTicksLeft()
    {
        return estimator.latestTicksLeft();
    }

    /** Narrow-band countdown suitable for a single ~N display, otherwise -1. */
    public int displayTicksLeft()
    {
        return estimator.displayTicksLeft();
    }

    public boolean hasForecast()
    {
        return estimator.hasForecast();
    }

    public int knownPhaseCount()
    {
        return estimator.knownPhaseCount();
    }

    public int candidateCount()
    {
        return estimator.candidateCount();
    }

    public int hitsPerCycle()
    {
        return estimator.activeCount();
    }

    public int seenCount()
    {
        return estimator.observations();
    }

    public int conflictCount()
    {
        return estimator.conflicts();
    }

    public int timingRecoveryCount()
    {
        return estimator.timingRecoveries();
    }

    public String status()
    {
        return usingBootstrap() ? "bootstrap" : estimator.status();
    }

    public int currentResidue()
    {
        return estimator.currentResidue();
    }

    public String phaseSummary()
    {
        return estimator.phaseSummary();
    }

    public String rangeSummary()
    {
        return estimator.rangeSummary();
    }

    /** Kept for the existing pure math regression tests. */
    static int predictTicksToZero(int energy, long currentTick, int[] hitsByResidue)
    {
        if (energy <= 0)
        {
            return 0;
        }

        int hitsPerCycle = 0;
        for (int hits : hitsByResidue)
        {
            hitsPerCycle += Math.max(0, hits);
        }
        if (hitsPerCycle <= 0)
        {
            return -1;
        }

        int remaining = energy;
        int maxTicks = PERIOD * (energy + PERIOD);
        for (int dt = 1; dt <= maxTicks; dt++)
        {
            int residue = floorMod(currentTick + dt, PERIOD);
            remaining -= Math.max(0, hitsByResidue[residue]);
            if (remaining <= 0)
            {
                return dt;
            }
        }
        return -1;
    }

    private static int floorMod(long value, int modulus)
    {
        int r = (int)(value % modulus);
        return r < 0 ? r + modulus : r;
    }
}
