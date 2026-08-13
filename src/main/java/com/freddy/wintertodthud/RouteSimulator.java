package com.freddy.wintertodthud;

/**
 * Small deterministic route planner used by the advisor.
 *
 * It deliberately knows nothing about RuneLite. AdvisorEngine supplies the live
 * game state and two timing profiles: MAX (no reserve) and SAFE (same hard
 * mechanics plus a route-level reserve).
 */
final class RouteSimulator
{
    private static final int OPEN_ENDED_TICKS = 100000;
    private static final int MAX_SEARCH_ROOTS = 200;

    static final class Profile
    {
        final double cutTicks;
        final double feedTicks;
        final double fletchTicks;
        final double moveTicksPerTile;
        final int movementOverheadTicks;
        final int reserveTicks;
        final boolean overlapFletchWithMovement;

        Profile(double cutTicks, double feedTicks, double fletchTicks, double moveTicksPerTile,
                int movementOverheadTicks, int reserveTicks, boolean overlapFletchWithMovement)
        {
            this.cutTicks = Math.max(0.1, cutTicks);
            this.feedTicks = Math.max(0.1, feedTicks);
            this.fletchTicks = Math.max(0.1, fletchTicks);
            this.moveTicksPerTile = Math.max(0.1, moveTicksPerTile);
            this.movementOverheadTicks = Math.max(0, movementOverheadTicks);
            this.reserveTicks = Math.max(0, reserveTicks);
            this.overlapFletchWithMovement = overlapFletchWithMovement;
        }
    }

    static final class Input
    {
        final int ticksLeft;
        final int points;
        final int roots;
        final int kindling;
        final int freeSlots;
        final int tilesToRoot;
        final int tilesToBrazier;
        final int rootToBrazierTiles;
        final int lightPenaltyTicks;
        final boolean atBrazier;

        Input(int ticksLeft, int points, int roots, int kindling, int freeSlots,
              int tilesToRoot, int tilesToBrazier, int rootToBrazierTiles,
              int lightPenaltyTicks, boolean atBrazier)
        {
            this.ticksLeft = ticksLeft;
            this.points = Math.max(0, points);
            this.roots = Math.max(0, roots);
            this.kindling = Math.max(0, kindling);
            this.freeSlots = Math.max(0, freeSlots);
            this.tilesToRoot = Math.max(0, tilesToRoot);
            this.tilesToBrazier = Math.max(0, tilesToBrazier);
            this.rootToBrazierTiles = Math.max(1, rootToBrazierTiles);
            this.lightPenaltyTicks = Math.max(0, lightPenaltyTicks);
            this.atBrazier = atBrazier;
        }
    }

    static final class Result
    {
        final int extraRootsThisLoad;
        final boolean fullLoadSafe;
        final int additionalRawRootsPossible;
        final int rawFinalPoints;
        final int fletchesFor500;
        final int fletchPlanAdditionalRoots;
        final int plannedPoints;
        final boolean raw500Reachable;
        final boolean fiveHundredReachable;
        final int planTicks;
        final int availableTicks;

        Result(int extraRootsThisLoad, boolean fullLoadSafe, int additionalRawRootsPossible,
               int rawFinalPoints, int fletchesFor500, int fletchPlanAdditionalRoots,
               int plannedPoints, boolean raw500Reachable, boolean fiveHundredReachable,
               int planTicks, int availableTicks)
        {
            this.extraRootsThisLoad = extraRootsThisLoad;
            this.fullLoadSafe = fullLoadSafe;
            this.additionalRawRootsPossible = additionalRawRootsPossible;
            this.rawFinalPoints = rawFinalPoints;
            this.fletchesFor500 = fletchesFor500;
            this.fletchPlanAdditionalRoots = fletchPlanAdditionalRoots;
            this.plannedPoints = plannedPoints;
            this.raw500Reachable = raw500Reachable;
            this.fiveHundredReachable = fiveHundredReachable;
            this.planTicks = planTicks;
            this.availableTicks = availableTicks;
        }
    }

    private final Input in;
    private final Profile p;
    private final int currentFuel;
    private final int fuelCapacity;
    private final int availableTicks;

    private RouteSimulator(Input in, Profile p)
    {
        this.in = in;
        this.p = p;
        this.currentFuel = in.roots + in.kindling;
        this.fuelCapacity = Math.max(1, currentFuel + in.freeSlots);
        this.availableTicks = in.ticksLeft < 0
            ? OPEN_ENDED_TICKS
            : Math.max(0, in.ticksLeft - p.reserveTicks);
    }

    static Result simulate(Input in, Profile profile)
    {
        return new RouteSimulator(in, profile).run();
    }

    private Result run()
    {
        int load = calculateThisLoad();
        boolean full = in.freeSlots > 0 && load >= in.freeSlots;

        int rawExtra;
        if (in.ticksLeft < 0)
        {
            // Energy is currently rising/paused. Do not invent an end timer; simply
            // treat raw-only 500 as reachable and keep the visible load full.
            int neededFor500 = Math.max(0,
                (int)Math.ceil((WintertodtMechanics.REWARD_THRESHOLD - (in.points + AdvisorMath.inventoryPoints(in.roots, in.kindling))) / (double)WintertodtMechanics.ROOT_POINTS));
            rawExtra = Math.min(MAX_SEARCH_ROOTS, Math.max(in.freeSlots, neededFor500 + fuelCapacity));
        }
        else
        {
            rawExtra = Math.max(additionalRawDepositFirst(), additionalRawCutFirst());
        }

        int rawFinal = in.points + AdvisorMath.inventoryPoints(in.roots, in.kindling) + rawExtra * WintertodtMechanics.ROOT_POINTS;
        boolean raw500 = rawFinal >= WintertodtMechanics.REWARD_THRESHOLD;

        int fletches = 0;
        int fletchRoots = 0;
        int plannedPoints = rawFinal;
        int planTicks = 0;
        boolean reachable = in.points >= WintertodtMechanics.REWARD_THRESHOLD || raw500;

        if (!reachable)
        {
            FletchPlan plan = findFletchPlan(rawExtra);
            if (plan != null)
            {
                fletches = plan.fletches;
                fletchRoots = plan.additionalRoots;
                plannedPoints = plan.points;
                planTicks = plan.ticks;
                reachable = true;
            }
        }

        return new Result(load, full, rawExtra, rawFinal, fletches, fletchRoots,
            plannedPoints, raw500, reachable, planTicks, availableTicks);
    }

    /**
     * How many MORE roots can be cut on the current/next load and still get all
     * currently-held fuel plus those roots into a brazier in time.
     */
    private int calculateThisLoad()
    {
        if (in.freeSlots <= 0)
        {
            return 0;
        }
        if (in.ticksLeft < 0)
        {
            return in.freeSlots;
        }

        // If we are already at the brazier with fuel, the visible advice will say
        // BURN NOW. Calculate the next fresh load after that deposit.
        if (in.atBrazier && currentFuel > 0)
        {
            Timing deposit = depositCurrentTiming();
            if (deposit.totalTicks > availableTicks)
            {
                return 0;
            }
            return maxFreshLoad(availableTicks - deposit.totalTicks);
        }

        int best = 0;
        for (int extra = 1; extra <= in.freeSlots; extra++)
        {
            Timing t = cutFirstTiming(extra);
            if (t.totalTicks <= availableTicks)
            {
                best = extra;
            }
            else
            {
                break;
            }
        }
        return best;
    }

    private int maxFreshLoad(int ticks)
    {
        int max = Math.min(fuelCapacity, in.freeSlots + currentFuel);
        int best = 0;
        for (int roots = 1; roots <= max; roots++)
        {
            Timing t = freshTripTiming(roots);
            if (t.totalTicks <= ticks)
            {
                best = roots;
            }
            else
            {
                break;
            }
        }
        return Math.min(in.freeSlots, best);
    }

    private int additionalRawDepositFirst()
    {
        int ticks = availableTicks;
        if (currentFuel > 0 || !in.atBrazier)
        {
            Timing first = depositCurrentTiming();
            if (first.totalTicks > ticks)
            {
                return 0;
            }
            ticks -= first.totalTicks;
        }
        return rootsFromFreshTrips(ticks);
    }

    private int additionalRawCutFirst()
    {
        if (in.freeSlots <= 0)
        {
            return 0;
        }

        int best = 0;
        for (int firstRoots = 1; firstRoots <= in.freeSlots; firstRoots++)
        {
            Timing first = cutFirstTiming(firstRoots);
            if (first.totalTicks > availableTicks)
            {
                break;
            }
            int total = firstRoots + rootsFromFreshTrips(availableTicks - first.totalTicks);
            best = Math.max(best, total);
        }
        return best;
    }

    private int rootsFromFreshTrips(int ticks)
    {
        int total = 0;
        int guard = 0;
        while (ticks > 0 && guard++ < 50 && total < MAX_SEARCH_ROOTS)
        {
            int best = 0;
            int bestTicks = 0;
            for (int roots = 1; roots <= fuelCapacity && total + roots <= MAX_SEARCH_ROOTS; roots++)
            {
                Timing t = freshTripTiming(roots);
                if (t.totalTicks <= ticks)
                {
                    best = roots;
                    bestTicks = t.totalTicks;
                }
                else
                {
                    break;
                }
            }
            if (best == 0)
            {
                break;
            }
            total += best;
            ticks -= bestTicks;
            if (best < fuelCapacity)
            {
                break;
            }
        }
        return total;
    }

    /** Minimum time to obtain and burn an exact number of additional raw roots. */
    private Timing ticksForAdditionalRoots(int additionalRoots)
    {
        if (additionalRoots < 0)
        {
            return Timing.IMPOSSIBLE;
        }

        Timing best = Timing.IMPOSSIBLE;

        // Plan A: dump current fuel first, then make fresh trips.
        Timing deposit = depositCurrentTiming();
        if (deposit.possible())
        {
            Timing cycles = exactFreshTripsTiming(additionalRoots);
            best = Timing.min(best, deposit.plus(cycles));
        }

        // Plan B: use free inventory space before the first deposit.
        if (additionalRoots > 0 && in.freeSlots > 0)
        {
            int firstRoots = Math.min(in.freeSlots, additionalRoots);
            Timing first = cutFirstTiming(firstRoots);
            Timing rest = exactFreshTripsTiming(additionalRoots - firstRoots);
            best = Timing.min(best, first.plus(rest));
        }
        else if (additionalRoots == 0)
        {
            best = Timing.min(best, deposit);
        }

        return best;
    }

    private Timing exactFreshTripsTiming(int roots)
    {
        Timing total = Timing.ZERO;
        int remaining = roots;
        while (remaining > 0)
        {
            int chunk = Math.min(fuelCapacity, remaining);
            total = total.plus(freshTripTiming(chunk));
            remaining -= chunk;
        }
        return total;
    }

    private FletchPlan findFletchPlan(int rawExtraLimit)
    {
        int currentInventoryPoints = AdvisorMath.inventoryPoints(in.roots, in.kindling);
        int maxExtra = Math.min(MAX_SEARCH_ROOTS, Math.max(0, rawExtraLimit));
        int maxFletches = Math.min(60, in.roots + maxExtra);

        // Fletching is a rescue tool, so minimize the number of fletches first.
        for (int fletches = 1; fletches <= maxFletches; fletches++)
        {
            FletchPlan bestForCount = null;
            for (int extraRoots = 0; extraRoots <= maxExtra; extraRoots++)
            {
                if (fletches > in.roots + extraRoots)
                {
                    continue;
                }
                int points = in.points + currentInventoryPoints + extraRoots * WintertodtMechanics.ROOT_POINTS + fletches * WintertodtMechanics.FLETCH_BONUS_POINTS;
                if (points < WintertodtMechanics.REWARD_THRESHOLD)
                {
                    continue;
                }

                Timing base = ticksForAdditionalRoots(extraRoots);
                if (!base.possible())
                {
                    continue;
                }
                int fletchTicks = (int)Math.ceil(fletches * p.fletchTicks);
                int extraFletchTicks = p.overlapFletchWithMovement
                    ? Math.max(0, fletchTicks - base.movementTicks)
                    : fletchTicks;
                int totalTicks = base.totalTicks + extraFletchTicks;
                if (totalTicks <= availableTicks)
                {
                    FletchPlan candidate = new FletchPlan(fletches, extraRoots, points, totalTicks);
                    // Fletch count is already minimized by the outer loop.
                    // Within that count, prefer the plan that safely burns the
                    // most raw roots (Firemaking XP), then the faster plan.
                    if (bestForCount == null || candidate.additionalRoots > bestForCount.additionalRoots
                        || (candidate.additionalRoots == bestForCount.additionalRoots && candidate.ticks < bestForCount.ticks)
                        || (candidate.additionalRoots == bestForCount.additionalRoots && candidate.ticks == bestForCount.ticks
                            && candidate.points < bestForCount.points))
                    {
                        bestForCount = candidate;
                    }
                }
            }
            if (bestForCount != null)
            {
                return bestForCount;
            }
        }
        return null;
    }

    private Timing depositCurrentTiming()
    {
        int move = moveTicks(in.tilesToBrazier);
        int feed = (int)Math.ceil(currentFuel * p.feedTicks);
        return new Timing(move + in.lightPenaltyTicks + feed, move);
    }

    private Timing cutFirstTiming(int extraRoots)
    {
        int toRoot = moveTicks(in.tilesToRoot);
        int back = moveTicks(in.rootToBrazierTiles);
        int cut = (int)Math.ceil(extraRoots * p.cutTicks);
        int feed = (int)Math.ceil((currentFuel + extraRoots) * p.feedTicks);
        return new Timing(toRoot + cut + back + in.lightPenaltyTicks + feed, toRoot + back);
    }

    private Timing freshTripTiming(int roots)
    {
        int out = moveTicks(in.rootToBrazierTiles);
        int back = moveTicks(in.rootToBrazierTiles);
        int cut = (int)Math.ceil(roots * p.cutTicks);
        int feed = (int)Math.ceil(roots * p.feedTicks);
        return new Timing(out + cut + back + feed, out + back);
    }

    private int moveTicks(int tiles)
    {
        if (tiles <= 0)
        {
            return 0;
        }
        return (int)Math.ceil(tiles * p.moveTicksPerTile) + p.movementOverheadTicks;
    }

    private static final class FletchPlan
    {
        final int fletches;
        final int additionalRoots;
        final int points;
        final int ticks;

        private FletchPlan(int fletches, int additionalRoots, int points, int ticks)
        {
            this.fletches = fletches;
            this.additionalRoots = additionalRoots;
            this.points = points;
            this.ticks = ticks;
        }
    }

    private static final class Timing
    {
        static final Timing ZERO = new Timing(0, 0);
        static final Timing IMPOSSIBLE = new Timing(Integer.MAX_VALUE / 4, 0);

        final int totalTicks;
        final int movementTicks;

        private Timing(int totalTicks, int movementTicks)
        {
            this.totalTicks = totalTicks;
            this.movementTicks = movementTicks;
        }

        boolean possible()
        {
            return totalTicks < Integer.MAX_VALUE / 8;
        }

        Timing plus(Timing other)
        {
            if (!possible() || !other.possible())
            {
                return IMPOSSIBLE;
            }
            return new Timing(totalTicks + other.totalTicks, movementTicks + other.movementTicks);
        }

        static Timing min(Timing a, Timing b)
        {
            return a.totalTicks <= b.totalTicks ? a : b;
        }
    }
}
