package com.freddy.wintertodthud;

/**
 * Hard Wintertodt/game-cycle constants used by the planner.
 *
 * These are mechanics, not player performance estimates. Real interruptions
 * are handled by elapsed game ticks and the route-level safety reserve rather
 * than by inflating every action duration.
 */
final class WintertodtMechanics
{
    static final int ROOT_POINTS = 10;
    static final int KINDLING_POINTS = 25;
    static final int FLETCH_BONUS_POINTS = KINDLING_POINTS - ROOT_POINTS;
    static final int LIGHT_POINTS = 25;
    static final int REPAIR_POINTS = 25;
    static final int HEAL_PYROMANCER_POINTS = 75;
    static final int REWARD_THRESHOLD = 500;

    static final double CUT_TICKS = 3.0;
    static final double FEED_TICKS = 3.0;
    static final double FLETCH_TICKS = 4.0;
    static final double FLETCHING_KNIFE_TICKS = 3.0;
    static final double RUN_TICKS_PER_TILE = 0.5;
    static final double WALK_TICKS_PER_TILE = 1.0;

    static final int LIGHT_BRAZIER_TICKS = 4;
    static final int REPAIR_BRAZIER_TICKS = 4;
    static final int PYROMANCER_DRAIN_PERIOD_TICKS = 14;

    private WintertodtMechanics()
    {
    }

    static double fletchTicks(boolean fletchingKnifeEquipped)
    {
        return fletchingKnifeEquipped ? FLETCHING_KNIFE_TICKS : FLETCH_TICKS;
    }

    static double movementTicksPerTile(boolean assumeRunning)
    {
        return assumeRunning ? RUN_TICKS_PER_TILE : WALK_TICKS_PER_TILE;
    }
}
