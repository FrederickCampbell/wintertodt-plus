package com.freddy.wintertodthud;

/** Pure timing helpers for the user-facing coach countdowns. */
final class CoachMath
{
    private CoachMath()
    {
    }

    static int moveTicks(int tiles, double ticksPerTile, int overheadTicks)
    {
        if (tiles <= 0)
        {
            return 0;
        }
        return (int)Math.ceil(tiles * Math.max(0.1, ticksPerTile)) + Math.max(0, overheadTicks);
    }

    static int feedTicks(int fuel, double ticksPerFuel)
    {
        return (int)Math.ceil(Math.max(0, fuel) * Math.max(0.1, ticksPerFuel));
    }

    static int fletchTicks(int count, double ticksPerFletch)
    {
        return (int)Math.ceil(Math.max(0, count) * Math.max(0.1, ticksPerFletch));
    }

    /**
     * Latest safe departure from the current cutting area. Returns -1 when the
     * round has no meaningful end timer (for example while Energy is rising).
     */
    static int runInTicks(int ticksLeft, int reserveTicks, int tilesToBrazier,
                          int plannedFuel, double moveTicksPerTile, int moveOverheadTicks,
                          double feedTicksPerFuel, int lightPenaltyTicks)
    {
        if (ticksLeft < 0)
        {
            return -1;
        }

        int available = Math.max(0, ticksLeft - Math.max(0, reserveTicks));
        int finish = moveTicks(tilesToBrazier, moveTicksPerTile, moveOverheadTicks)
            + Math.max(0, lightPenaltyTicks)
            + feedTicks(plannedFuel, feedTicksPerFuel);
        return available - finish;
    }

    /** Latest safe time to begin the required fletching before the final run. */
    static int fletchInTicks(int runInTicks, int fletchCount, double ticksPerFletch)
    {
        if (runInTicks < 0)
        {
            return -1;
        }
        return runInTicks - fletchTicks(fletchCount, ticksPerFletch);
    }
}
