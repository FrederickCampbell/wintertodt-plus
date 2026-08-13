package com.freddy.wintertodthud;

/**
 * Pure deterministic solver for the reward-first advisor policy.
 *
 * Objective hierarchy:
 * 1) Reach 500 points by the conservative round-end estimate minus 5 ticks.
 * 2) Prefer a completely raw-root route whenever one exists.
 * 3) If raw-only cannot make 500, use the absolute minimum number of fletches.
 * 4) Within that fletch count, reach 500 as early as possible so the remaining
 *    round can be spent on raw-root Firemaking XP.
 *
 * This class deliberately models only hard mechanics. Future Wintertodt hits,
 * station failures, player reaction time, etc. are represented by elapsed live
 * ticks and by the Energy/station model, not by inflating action durations.
 */
final class RewardRoutePlanner
{
    static final int REWARD_RESERVE_TICKS = 5;
    static final int XP_RESERVE_TICKS = 1;

    private static final int MAX_THRESHOLD_ROOTS = 50;
    private static final int MAX_THRESHOLD_FLETCHES = 20;

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
        final int brazierReadyTicks;
        final boolean atBrazier;

        Input(int ticksLeft, int points, int roots, int kindling, int freeSlots,
              int tilesToRoot, int tilesToBrazier, int rootToBrazierTiles,
              int brazierReadyTicks, boolean atBrazier)
        {
            this.ticksLeft = ticksLeft;
            this.points = Math.max(0, points);
            this.roots = Math.max(0, roots);
            this.kindling = Math.max(0, kindling);
            this.freeSlots = Math.max(0, freeSlots);
            this.tilesToRoot = Math.max(0, tilesToRoot);
            this.tilesToBrazier = Math.max(0, tilesToBrazier);
            this.rootToBrazierTiles = Math.max(1, rootToBrazierTiles);
            this.brazierReadyTicks = Math.max(0, brazierReadyTicks);
            this.atBrazier = atBrazier;
        }
    }

    static final class Mechanics
    {
        final int cutTicks;
        final int feedTicks;
        final int fletchTicks;
        final double moveTicksPerTile;

        Mechanics(double cutTicks, double feedTicks, double fletchTicks, double moveTicksPerTile)
        {
            this.cutTicks = Math.max(1, (int)Math.ceil(cutTicks));
            this.feedTicks = Math.max(1, (int)Math.ceil(feedTicks));
            this.fletchTicks = Math.max(1, (int)Math.ceil(fletchTicks));
            this.moveTicksPerTile = Math.max(0.1, moveTicksPerTile);
        }
    }

    static final class Plan
    {
        static final Plan IMPOSSIBLE = new Plan(false, 0, 0, Integer.MAX_VALUE / 4, 0);

        final boolean reachable;
        final int fletches;
        final int additionalRoots;
        final int ticksTo500;
        final int pointsAtThreshold;

        Plan(boolean reachable, int fletches, int additionalRoots, int ticksTo500, int pointsAtThreshold)
        {
            this.reachable = reachable;
            this.fletches = Math.max(0, fletches);
            this.additionalRoots = Math.max(0, additionalRoots);
            this.ticksTo500 = ticksTo500;
            this.pointsAtThreshold = Math.max(0, pointsAtThreshold);
        }

        boolean rawOnly()
        {
            return reachable && fletches == 0;
        }
    }

    private static final class Outcome
    {
        static final Outcome IMPOSSIBLE = new Outcome(false, Integer.MAX_VALUE / 4, 0, 0);

        final boolean reached;
        final int ticks;
        final int points;
        final int rootsActuallyCut;

        Outcome(boolean reached, int ticks, int points, int rootsActuallyCut)
        {
            this.reached = reached;
            this.ticks = ticks;
            this.points = points;
            this.rootsActuallyCut = rootsActuallyCut;
        }
    }

    private static final class Sim
    {
        int ticks;
        int points;
        int roots;
        int kindling;
        int rootsCut;
        boolean readyPaid;

        Sim(Input in)
        {
            points = in.points;
            roots = in.roots;
            kindling = in.kindling;
        }
    }

    private RewardRoutePlanner()
    {
    }

    static Plan minimumPlanTo500(Input in, Mechanics mechanics)
    {
        if (in.points >= WintertodtMechanics.REWARD_THRESHOLD)
        {
            return new Plan(true, 0, 0, 0, in.points);
        }
        for (int fletches = 0; fletches <= MAX_THRESHOLD_FLETCHES; fletches++)
        {
            Plan exact = exactFletchPlanTo500(in, mechanics, fletches);
            if (exact.reachable)
            {
                return exact;
            }
        }
        return Plan.IMPOSSIBLE;
    }

    static Plan exactFletchPlanTo500(Input in, Mechanics mechanics, int exactFletches)
    {
        if (in.points >= WintertodtMechanics.REWARD_THRESHOLD)
        {
            return exactFletches == 0
                ? new Plan(true, 0, 0, 0, in.points)
                : Plan.IMPOSSIBLE;
        }
        if (in.ticksLeft < 0)
        {
            // No trustworthy end exists yet. Raw-only remains the default; do not
            // manufacture a fletch requirement while the boss timer is open-ended.
            return exactFletches == 0
                ? new Plan(true, 0, 0, 0, in.points)
                : Plan.IMPOSSIBLE;
        }

        int available = Math.max(0, in.ticksLeft - REWARD_RESERVE_TICKS);
        Plan best = Plan.IMPOSSIBLE;

        for (int additionalRoots = 0; additionalRoots <= MAX_THRESHOLD_ROOTS; additionalRoots++)
        {
            if (exactFletches > in.roots + additionalRoots)
            {
                continue;
            }

            Outcome outcome = minimumThresholdTiming(in, mechanics, additionalRoots, exactFletches);
            if (!outcome.reached || outcome.ticks > available)
            {
                continue;
            }

            Plan candidate = new Plan(true, exactFletches, outcome.rootsActuallyCut,
                outcome.ticks, outcome.points);
            if (!best.reachable
                || candidate.ticksTo500 < best.ticksTo500
                || (candidate.ticksTo500 == best.ticksTo500
                    && candidate.additionalRoots < best.additionalRoots)
                || (candidate.ticksTo500 == best.ticksTo500
                    && candidate.additionalRoots == best.additionalRoots
                    && candidate.pointsAtThreshold < best.pointsAtThreshold))
            {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Test the exact next-root decision demanded by the HUD policy. This spends
     * the movement/cut ticks now, then asks whether the complete raw-only chain
     * still reaches 500 by the five-tick reward deadline.
     */
    static boolean oneMoreRawRootStillSecures500(Input in, Mechanics mechanics)
    {
        if (in.freeSlots <= 0 || in.ticksLeft < 0)
        {
            return in.freeSlots > 0 && in.ticksLeft < 0;
        }

        int acquire = moveTicks(in.tilesToRoot, mechanics.moveTicksPerTile) + mechanics.cutTicks;
        if (acquire >= in.ticksLeft)
        {
            return false;
        }

        Input afterCut = new Input(
            in.ticksLeft - acquire,
            in.points,
            in.roots + 1,
            in.kindling,
            in.freeSlots - 1,
            0,
            in.rootToBrazierTiles,
            in.rootToBrazierTiles,
            in.brazierReadyTicks,
            false);

        return exactFletchPlanTo500(afterCut, mechanics, 0).reachable;
    }

    /**
     * Full objective proof for a speculative raw cut before 500: the new root
     * must preserve the five-tick reward guarantee AND the entire resulting
     * load must still be burnable by the one-tick XP deadline. This prevents
     * the coach from cutting roots that mathematically cannot reach the fire.
     */
    static boolean oneMoreRawRootFitsWholeObjective(Input in, Mechanics mechanics)
    {
        if (!oneMoreRawRootStillSecures500(in, mechanics) || in.freeSlots <= 0 || in.ticksLeft < 0)
        {
            return in.ticksLeft < 0 && in.freeSlots > 0;
        }

        int acquire = moveTicks(in.tilesToRoot, mechanics.moveTicksPerTile) + mechanics.cutTicks;
        int afterCutTicks = in.ticksLeft - acquire;
        int moveBack = moveTicks(in.rootToBrazierTiles, mechanics.moveTicksPerTile);
        int totalFuel = in.roots + in.kindling + 1;
        int burnEverything = moveBack + in.brazierReadyTicks + totalFuel * mechanics.feedTicks;
        return burnEverything <= Math.max(0, afterCutTicks - XP_RESERVE_TICKS);
    }


    /**
     * Final-load next-root proof. Another raw root is valid iff the complete
     * cut -> run -> brazier-ready -> feed-all chain still finishes with the
     * hard one-tick end reserve. Used both after 500 and for best-effort salvage
     * when a mid-round login is already too late to reach 500.
     */
    static boolean oneMoreRawRootFitsXpObjective(Input in, Mechanics mechanics)
    {
        if (in.freeSlots <= 0 || in.ticksLeft < 0)
        {
            return in.freeSlots > 0 && in.ticksLeft < 0;
        }

        int acquire = moveTicks(in.tilesToRoot, mechanics.moveTicksPerTile) + mechanics.cutTicks;
        if (acquire >= in.ticksLeft)
        {
            return false;
        }

        int afterCutTicks = in.ticksLeft - acquire;
        int moveBack = moveTicks(in.rootToBrazierTiles, mechanics.moveTicksPerTile);
        int totalFuel = in.roots + in.kindling + 1;
        int burnEverything = moveBack + in.brazierReadyTicks + totalFuel * mechanics.feedTicks;
        return burnEverything <= Math.max(0, afterCutTicks - XP_RESERVE_TICKS);
    }

    /**
     * Max-Points objective proof for one more root. The candidate root and every
     * currently-held raw root must still be fletchable, then the complete load
     * must still reach the brazier and be fed before the active reward/XP reserve.
     */
    static boolean oneMoreFletchedRootFitsXpObjective(Input in, Mechanics mechanics)
    {
        if (in.freeSlots <= 0 || in.ticksLeft < 0)
        {
            return in.freeSlots > 0 && in.ticksLeft < 0;
        }

        int acquire = moveTicks(in.tilesToRoot, mechanics.moveTicksPerTile) + mechanics.cutTicks;
        if (acquire >= in.ticksLeft)
        {
            return false;
        }

        int afterCutTicks = in.ticksLeft - acquire;
        int moveBack = moveTicks(in.rootToBrazierTiles, mechanics.moveTicksPerTile);
        int totalRoots = in.roots + 1;
        int totalFuel = totalRoots + in.kindling;
        int finish = totalRoots * mechanics.fletchTicks
            + moveBack
            + in.brazierReadyTicks
            + totalFuel * mechanics.feedTicks;

        int reserve = in.points < WintertodtMechanics.REWARD_THRESHOLD
            ? REWARD_RESERVE_TICKS : XP_RESERVE_TICKS;
        return finish <= Math.max(0, afterCutTicks - reserve);
    }

    /**
     * Maximum number of currently-held raw roots that can still be fletched
     * without sacrificing the ability to burn the entire current load.
     */
    static int maxCurrentLoadFletchesForPoints(Input in, Mechanics mechanics)
    {
        if (in.roots <= 0)
        {
            return 0;
        }
        if (in.ticksLeft < 0)
        {
            return in.roots;
        }

        int reserve = in.points < WintertodtMechanics.REWARD_THRESHOLD
            ? REWARD_RESERVE_TICKS : XP_RESERVE_TICKS;
        int available = Math.max(0, in.ticksLeft - reserve);
        int move = in.atBrazier ? 0 : moveTicks(in.tilesToBrazier, mechanics.moveTicksPerTile);
        int totalFuel = in.roots + in.kindling;
        int base = move + in.brazierReadyTicks + totalFuel * mechanics.feedTicks;
        int room = available - base;
        if (room < mechanics.fletchTicks)
        {
            return 0;
        }
        return Math.min(in.roots, room / mechanics.fletchTicks);
    }

    static int maxPointsCurrentLoadProjection(Input in, Mechanics mechanics)
    {
        int fletches = maxCurrentLoadFletchesForPoints(in, mechanics);
        return in.points
            + AdvisorMath.inventoryPoints(in.roots, in.kindling)
            + fletches * WintertodtMechanics.FLETCH_BONUS_POINTS;
    }

    /** Same next-root proof, but for a locked rescue fletch count. */
    static boolean oneMoreRootStillFitsLockedRescue(Input in, Mechanics mechanics, int remainingFletches)
    {
        if (in.freeSlots <= 0 || in.ticksLeft < 0)
        {
            return in.freeSlots > 0 && in.ticksLeft < 0;
        }

        int acquire = moveTicks(in.tilesToRoot, mechanics.moveTicksPerTile) + mechanics.cutTicks;
        if (acquire >= in.ticksLeft)
        {
            return false;
        }

        Input afterCut = new Input(
            in.ticksLeft - acquire,
            in.points,
            in.roots + 1,
            in.kindling,
            in.freeSlots - 1,
            0,
            in.rootToBrazierTiles,
            in.rootToBrazierTiles,
            in.brazierReadyTicks,
            false);

        return exactFletchPlanTo500(afterCut, mechanics, remainingFletches).reachable;
    }

    private static Outcome minimumThresholdTiming(Input in, Mechanics m,
                                                   int additionalRoots, int fletches)
    {
        Outcome best = depositFirst(in, m, additionalRoots, fletches);

        int maxFirstRoots = Math.min(additionalRoots, in.freeSlots);
        for (int firstRoots = 1; firstRoots <= maxFirstRoots; firstRoots++)
        {
            Outcome candidate = cutFirst(in, m, additionalRoots, fletches, firstRoots);
            best = better(best, candidate);
        }
        return best;
    }

    private static Outcome depositFirst(Input in, Mechanics m, int additionalRoots, int fletches)
    {
        Sim s = new Sim(in);
        int remainingFletches = fletches;
        int firstFletches = Math.min(remainingFletches, s.roots);
        fletch(s, firstFletches, m);
        remainingFletches -= firstFletches;

        s.ticks += moveTicks(in.tilesToBrazier, m.moveTicksPerTile);
        payReady(s, in);
        if (feedUntilThresholdOrEmpty(s, m))
        {
            return new Outcome(true, s.ticks, s.points, s.rootsCut);
        }

        int capacity = Math.max(1, in.roots + in.kindling + in.freeSlots);
        int remainingRoots = additionalRoots;
        while (remainingRoots > 0)
        {
            int chunk = Math.min(capacity, remainingRoots);
            s.ticks += moveTicks(in.rootToBrazierTiles, m.moveTicksPerTile);
            s.ticks += chunk * m.cutTicks;
            s.roots += chunk;
            s.rootsCut += chunk;

            int makeKindling = Math.min(remainingFletches, s.roots);
            fletch(s, makeKindling, m);
            remainingFletches -= makeKindling;

            s.ticks += moveTicks(in.rootToBrazierTiles, m.moveTicksPerTile);
            if (feedUntilThresholdOrEmpty(s, m))
            {
                return remainingFletches == 0
                    ? new Outcome(true, s.ticks, s.points, s.rootsCut)
                    : Outcome.IMPOSSIBLE;
            }
            remainingRoots -= chunk;
        }

        return Outcome.IMPOSSIBLE;
    }

    private static Outcome cutFirst(Input in, Mechanics m, int additionalRoots,
                                    int fletches, int firstRoots)
    {
        Sim s = new Sim(in);
        int remainingFletches = fletches;
        int remainingRoots = additionalRoots;

        s.ticks += moveTicks(in.tilesToRoot, m.moveTicksPerTile);
        s.ticks += firstRoots * m.cutTicks;
        s.roots += firstRoots;
        s.rootsCut += firstRoots;
        remainingRoots -= firstRoots;

        int firstFletches = Math.min(remainingFletches, s.roots);
        fletch(s, firstFletches, m);
        remainingFletches -= firstFletches;

        s.ticks += moveTicks(in.rootToBrazierTiles, m.moveTicksPerTile);
        payReady(s, in);
        if (feedUntilThresholdOrEmpty(s, m))
        {
            return remainingFletches == 0
                ? new Outcome(true, s.ticks, s.points, s.rootsCut)
                : Outcome.IMPOSSIBLE;
        }

        int capacity = Math.max(1, in.roots + in.kindling + in.freeSlots);
        while (remainingRoots > 0)
        {
            int chunk = Math.min(capacity, remainingRoots);
            s.ticks += moveTicks(in.rootToBrazierTiles, m.moveTicksPerTile);
            s.ticks += chunk * m.cutTicks;
            s.roots += chunk;
            s.rootsCut += chunk;

            int makeKindling = Math.min(remainingFletches, s.roots);
            fletch(s, makeKindling, m);
            remainingFletches -= makeKindling;

            s.ticks += moveTicks(in.rootToBrazierTiles, m.moveTicksPerTile);
            if (feedUntilThresholdOrEmpty(s, m))
            {
                return remainingFletches == 0
                    ? new Outcome(true, s.ticks, s.points, s.rootsCut)
                    : Outcome.IMPOSSIBLE;
            }
            remainingRoots -= chunk;
        }

        return Outcome.IMPOSSIBLE;
    }

    private static void fletch(Sim s, int count, Mechanics m)
    {
        if (count <= 0)
        {
            return;
        }
        s.roots -= count;
        s.kindling += count;
        s.ticks += count * m.fletchTicks;
    }

    /**
     * The live game consumes kindling first when both fuel types are held, which
     * also minimizes the number of feed actions needed to secure the threshold.
     */
    private static boolean feedUntilThresholdOrEmpty(Sim s, Mechanics m)
    {
        while (s.points < WintertodtMechanics.REWARD_THRESHOLD && s.kindling > 0)
        {
            s.kindling--;
            s.points += WintertodtMechanics.KINDLING_POINTS;
            s.ticks += m.feedTicks;
        }
        while (s.points < WintertodtMechanics.REWARD_THRESHOLD && s.roots > 0)
        {
            s.roots--;
            s.points += WintertodtMechanics.ROOT_POINTS;
            s.ticks += m.feedTicks;
        }
        return s.points >= WintertodtMechanics.REWARD_THRESHOLD;
    }

    private static void payReady(Sim s, Input in)
    {
        if (!s.readyPaid)
        {
            s.ticks += in.brazierReadyTicks;
            s.readyPaid = true;
        }
    }

    private static Outcome better(Outcome a, Outcome b)
    {
        if (!a.reached)
        {
            return b;
        }
        if (!b.reached)
        {
            return a;
        }
        if (b.ticks < a.ticks)
        {
            return b;
        }
        if (b.ticks == a.ticks && b.rootsActuallyCut < a.rootsActuallyCut)
        {
            return b;
        }
        if (b.ticks == a.ticks && b.rootsActuallyCut == a.rootsActuallyCut && b.points < a.points)
        {
            return b;
        }
        return a;
    }

    static int moveTicks(int tiles, double ticksPerTile)
    {
        if (tiles <= 0)
        {
            return 0;
        }
        return (int)Math.ceil(tiles * Math.max(0.1, ticksPerTile));
    }
}
