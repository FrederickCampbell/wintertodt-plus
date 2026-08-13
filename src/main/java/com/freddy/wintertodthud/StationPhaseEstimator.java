package com.freddy.wintertodthud;

import java.util.Arrays;

/**
 * Pure-Java four-station phase estimator for Wintertodt's independent 14-tick
 * pyromancer drain cycles.
 *
 * Each candidate is one assignment of a 0..13 phase to SW/NW/NE/SE. Aggregate
 * Energy changes eliminate assignments that cannot have produced the observed
 * number of hits on the current residue. When one station changes state, only
 * that station's phase is expanded back to unknown; correlations/knowledge for
 * the other three stations are preserved.
 */
final class StationPhaseEstimator
{
    static final int PERIOD = WintertodtMechanics.PYROMANCER_DRAIN_PERIOD_TICKS;
    static final int STATIONS = 4;
    static final int COMBINATIONS = PERIOD * PERIOD * PERIOD * PERIOD;
    static final int PRECISE_RANGE_TICKS = 2;

    private static final int[] POW = {1, PERIOD, PERIOD * PERIOD, PERIOD * PERIOD * PERIOD};
    private static final String[] NAMES = {"SW", "NW", "NE", "SE"};

    private boolean[] possible = new boolean[COMBINATIONS];
    private boolean[] scratch = new boolean[COMBINATIONS];
    private final int[] lastRevisions = {-1, -1, -1, -1};
    private final boolean[] active = new boolean[STATIONS];

    private long tick;
    private int previousEnergy = -1;
    private int earliestTicks = -1;
    private int latestTicks = -1;
    private int activeCount;
    private int knownPhaseCount;
    private int candidateCount;
    private int observations;
    private int conflicts;
    private int timingRecoveries;
    private boolean stationsReliable;
    private String reason = "calibrating";

    StationPhaseEstimator()
    {
        reset();
    }

    void reset()
    {
        tick = 0;
        previousEnergy = -1;
        earliestTicks = -1;
        latestTicks = -1;
        activeCount = 0;
        knownPhaseCount = 0;
        candidateCount = COMBINATIONS;
        observations = 0;
        conflicts = 0;
        timingRecoveries = 0;
        stationsReliable = false;
        reason = "calibrating";
        Arrays.fill(possible, true);
        Arrays.fill(scratch, false);
        Arrays.fill(active, false);
        Arrays.fill(lastRevisions, -1);
    }

    void onGameTick(boolean roundActive, int energy, boolean reliable,
                    boolean[] contributing, int[] revisions)
    {
        tick++;

        if (contributing == null || contributing.length != STATIONS
            || revisions == null || revisions.length != STATIONS)
        {
            stationsReliable = false;
            previousEnergy = energy;
            suppress("station data invalid");
            return;
        }

        boolean changedThisTick = false;
        for (int i = 0; i < STATIONS; i++)
        {
            boolean wasActive = active[i];
            boolean nowActive = contributing[i];
            if (lastRevisions[i] < 0)
            {
                lastRevisions[i] = revisions[i];
            }
            else if (lastRevisions[i] != revisions[i])
            {
                // Going inactive does NOT erase the old phase: retaining it keeps
                // all correlations that can still identify the unaffected cycle.
                // A resume can have a fresh restart delay, so only then reopen
                // this physical station to all 14 phases.
                if ((!wasActive && nowActive) || (wasActive && nowActive))
                {
                    expandStation(i);
                }
                lastRevisions[i] = revisions[i];
                changedThisTick = true;
            }
            active[i] = nowActive;
        }

        stationsReliable = reliable;
        activeCount = countActive(active);

        if (!roundActive || energy < 0)
        {
            previousEnergy = energy;
            suppress("round inactive");
            return;
        }

        if (!stationsReliable)
        {
            previousEnergy = energy;
            suppress("station widgets unverified");
            return;
        }

        if (activeCount == 0)
        {
            previousEnergy = energy;
            suppress("no active drainers");
            return;
        }

        if (previousEnergy < 0)
        {
            previousEnergy = energy;
            recomputeForecast(energy);
            return;
        }

        if (energy > previousEnergy)
        {
            previousEnergy = energy;
            suppress("energy rising");
            return;
        }

        int drop = Math.max(0, previousEnergy - energy);
        previousEnergy = energy;

        if (energy <= 0)
        {
            earliestTicks = 0;
            latestTicks = 0;
            knownPhaseCount = countKnownActivePhases();
            reason = "round ending";
            return;
        }

        // The exact tick of a station-state transition can be ambiguous relative
        // to the Energy update. Preserve knowledge, but do not use that one tick
        // as a hard phase constraint for the just-changed station.
        if (!changedThisTick)
        {
            boolean accepted = filterObservation(currentResidue(), drop);
            if (!accepted)
            {
                // RuneLite's widget/varbit update can land one client GameTick on
                // either side of the physical drain. First tolerate that bounded
                // observation jitter without changing any station's true phase.
                accepted = filterObservationWithTickJitter(currentResidue(), drop);
                if (accepted)
                {
                    timingRecoveries++;
                }
            }
            if (!accepted)
            {
                conflicts++;
                // Only after bounded timing jitter fails do we infer that one
                // active station may genuinely have restarted without a visible
                // widget revision. Widen one unknown physical identity, not all 4.
                expandAnyOneActiveStation();
                accepted = filterObservation(currentResidue(), drop);
                if (!accepted)
                {
                    accepted = filterObservationWithTickJitter(currentResidue(), drop);
                    if (accepted)
                    {
                        timingRecoveries++;
                    }
                }
                if (!accepted)
                {
                    // Last-resort fail-safe for a truly unmodeled observation.
                    Arrays.fill(possible, true);
                    candidateCount = COMBINATIONS;
                    accepted = filterObservation(currentResidue(), drop);
                    if (!accepted)
                    {
                        accepted = filterObservationWithTickJitter(currentResidue(), drop);
                        if (accepted)
                        {
                            timingRecoveries++;
                        }
                    }
                }
                if (!accepted)
                {
                    suppress("unmodeled energy change");
                    return;
                }
            }
            observations++;
        }

        recomputeForecast(energy);
    }

    private boolean filterObservation(int residue, int drop)
    {
        if (drop < 0 || drop > activeCount)
        {
            return false;
        }

        Arrays.fill(scratch, false);
        int kept = 0;
        for (int code = 0; code < COMBINATIONS; code++)
        {
            if (!possible[code])
            {
                continue;
            }
            int hits = 0;
            for (int station = 0; station < STATIONS; station++)
            {
                if (active[station] && phase(code, station) == residue)
                {
                    hits++;
                }
            }
            if (hits == drop)
            {
                scratch[code] = true;
                kept++;
            }
        }

        if (kept == 0)
        {
            return false;
        }

        boolean[] tmp = possible;
        possible = scratch;
        scratch = tmp;
        candidateCount = kept;
        return true;
    }


    /**
     * Accept an observation whose visible Energy change arrived one GameTick
     * early/late relative to the underlying 14-tick station phase. This is an
     * observation-timing tolerance only: candidate station phases are retained.
     */
    private boolean filterObservationWithTickJitter(int residue, int drop)
    {
        if (drop < 0 || drop > activeCount)
        {
            return false;
        }

        int previousResidue = floorMod(residue - 1, PERIOD);
        int nextResidue = floorMod(residue + 1, PERIOD);
        Arrays.fill(scratch, false);
        int kept = 0;
        for (int code = 0; code < COMBINATIONS; code++)
        {
            if (!possible[code])
            {
                continue;
            }
            if (hitsForCandidate(code, previousResidue) == drop
                || hitsForCandidate(code, nextResidue) == drop)
            {
                scratch[code] = true;
                kept++;
            }
        }

        if (kept == 0)
        {
            return false;
        }

        boolean[] tmp = possible;
        possible = scratch;
        scratch = tmp;
        candidateCount = kept;
        return true;
    }

    private int hitsForCandidate(int code, int residue)
    {
        int hits = 0;
        for (int station = 0; station < STATIONS; station++)
        {
            if (active[station] && phase(code, station) == residue)
            {
                hits++;
            }
        }
        return hits;
    }

    private void expandStation(int station)
    {
        Arrays.fill(scratch, false);
        for (int code = 0; code < COMBINATIONS; code++)
        {
            if (!possible[code])
            {
                continue;
            }
            int current = phase(code, station);
            int base = code - current * POW[station];
            for (int p = 0; p < PERIOD; p++)
            {
                scratch[base + p * POW[station]] = true;
            }
        }

        boolean[] tmp = possible;
        possible = scratch;
        scratch = tmp;
        candidateCount = countCandidates();
    }

    private void recomputeForecast(int energy)
    {
        if (!stationsReliable || activeCount <= 0 || energy < 0)
        {
            suppress(stationsReliable ? "no active drainers" : "station widgets unverified");
            return;
        }
        if (energy <= 0)
        {
            earliestTicks = latestTicks = 0;
            knownPhaseCount = countKnownActivePhases();
            reason = "round ending";
            return;
        }

        int min = Integer.MAX_VALUE;
        int max = -1;
        int candidates = 0;
        int residue = currentResidue();
        for (int code = 0; code < COMBINATIONS; code++)
        {
            if (!possible[code])
            {
                continue;
            }
            int predicted = predictTicksToZero(energy, residue, code, active);
            if (predicted >= 0)
            {
                min = Math.min(min, predicted);
                max = Math.max(max, predicted);
                candidates++;
            }
        }

        candidateCount = candidates;
        knownPhaseCount = countKnownActivePhases();
        if (candidates == 0 || min == Integer.MAX_VALUE)
        {
            suppress("no drain forecast");
            return;
        }

        earliestTicks = min;
        latestTicks = max;
        if (min == max)
        {
            reason = "locked";
        }
        else
        {
            reason = "tracking " + knownPhaseCount + "/" + activeCount + " phases";
        }
    }

    private void suppress(String why)
    {
        earliestTicks = -1;
        latestTicks = -1;
        knownPhaseCount = countKnownActivePhases();
        reason = why;
    }

    boolean hasForecast()
    {
        return earliestTicks >= 0 && latestTicks >= earliestTicks;
    }

    /** Conservative edge used by the planner. */
    int safeTicksLeft()
    {
        return hasForecast() ? earliestTicks : -1;
    }

    int latestTicksLeft()
    {
        return hasForecast() ? latestTicks : -1;
    }

    /** Exact phase/finish lock, retained as a deliberately strict semantic. */
    boolean trusted()
    {
        return hasForecast() && earliestTicks == latestTicks;
    }

    /**
     * A single display countdown is allowed only when the uncertainty band is
     * very narrow. The planner still uses safeTicksLeft() even when this is -1.
     */
    int displayTicksLeft()
    {
        if (!hasForecast() || latestTicks - earliestTicks > PRECISE_RANGE_TICKS)
        {
            return -1;
        }
        return (earliestTicks + latestTicks) / 2;
    }

    int knownPhaseCount()
    {
        return knownPhaseCount;
    }

    int activeCount()
    {
        return activeCount;
    }

    int candidateCount()
    {
        return candidateCount;
    }

    int observations()
    {
        return observations;
    }

    int conflicts()
    {
        return conflicts;
    }

    int timingRecoveries()
    {
        return timingRecoveries;
    }

    String status()
    {
        return reason;
    }

    int currentResidue()
    {
        return floorMod(tick, PERIOD);
    }

    int phaseForStation(int station)
    {
        if (station < 0 || station >= STATIONS || !active[station])
        {
            return -1;
        }
        int found = -1;
        for (int code = 0; code < COMBINATIONS; code++)
        {
            if (!possible[code])
            {
                continue;
            }
            int p = phase(code, station);
            if (found < 0)
            {
                found = p;
            }
            else if (found != p)
            {
                return -1;
            }
        }
        return found;
    }

    String phaseSummary()
    {
        StringBuilder out = new StringBuilder();
        for (int station = 0; station < STATIONS; station++)
        {
            if (station > 0)
            {
                out.append(" · ");
            }
            out.append(NAMES[station]).append(' ');
            if (!active[station])
            {
                out.append("off");
            }
            else
            {
                int p = phaseForStation(station);
                out.append(p < 0 ? "?" : Integer.toString(p));
            }
        }
        return out.toString();
    }

    String rangeSummary()
    {
        if (!hasForecast())
        {
            return "--";
        }
        return earliestTicks == latestTicks
            ? Integer.toString(earliestTicks)
            : earliestTicks + "-" + latestTicks;
    }

    private int countKnownActivePhases()
    {
        int count = 0;
        for (int station = 0; station < STATIONS; station++)
        {
            if (active[station] && phaseForStation(station) >= 0)
            {
                count++;
            }
        }
        return count;
    }

    private void expandAnyOneActiveStation()
    {
        Arrays.fill(scratch, false);
        for (int code = 0; code < COMBINATIONS; code++)
        {
            if (!possible[code])
            {
                continue;
            }
            for (int station = 0; station < STATIONS; station++)
            {
                if (!active[station])
                {
                    continue;
                }
                int current = phase(code, station);
                int base = code - current * POW[station];
                for (int p = 0; p < PERIOD; p++)
                {
                    scratch[base + p * POW[station]] = true;
                }
            }
        }

        boolean[] tmp = possible;
        possible = scratch;
        scratch = tmp;
        candidateCount = countCandidates();
    }

    private int countCandidates()
    {
        int count = 0;
        for (boolean b : possible)
        {
            if (b)
            {
                count++;
            }
        }
        return count;
    }

    private static int countActive(boolean[] active)
    {
        int count = 0;
        for (boolean b : active)
        {
            if (b)
            {
                count++;
            }
        }
        return count;
    }

    static int predictTicksToZero(int energy, int currentResidue, int code, boolean[] active)
    {
        if (energy <= 0)
        {
            return 0;
        }

        int[] offsets = new int[STATIONS];
        int n = 0;
        for (int station = 0; station < STATIONS; station++)
        {
            if (!active[station])
            {
                continue;
            }
            int delta = phase(code, station) - currentResidue;
            if (delta <= 0)
            {
                delta += PERIOD;
            }
            offsets[n++] = delta;
        }
        if (n == 0)
        {
            return -1;
        }

        Arrays.sort(offsets, 0, n);
        int zeroBasedHit = energy - 1;
        int fullCycles = zeroBasedHit / n;
        int index = zeroBasedHit % n;
        return fullCycles * PERIOD + offsets[index];
    }

    static int encode(int sw, int nw, int ne, int se)
    {
        return sw + nw * POW[1] + ne * POW[2] + se * POW[3];
    }

    static int phase(int code, int station)
    {
        return (code / POW[station]) % PERIOD;
    }

    private static int floorMod(long value, int modulus)
    {
        int r = (int)(value % modulus);
        return r < 0 ? r + modulus : r;
    }
}
