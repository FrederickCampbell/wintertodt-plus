package com.freddy.wintertodthud;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Stateful Wintertodt coach with two explicit goals.
 *
 * MAX_FM_XP preserves the validated reward-first policy. MAX_POINTS uses a
 * separate fill -> fletch -> burn policy with the same live clock and route model.
 *
 * The FM-XP solver may recalculate internally every tick, but the policy is monotonic:
 * CUTTING -> MINIMUM_PREP -> BURNING. An emergency salvage state sits outside
 * that normal flow and may recover only after a meaningful station change.
 *
 * In MAX_FM_XP, fletching is never speculative. A completely raw-root route is tested first;
 * only when raw-only cannot reach 500 by the conservative end minus five ticks
 * may the advisor lock the absolute minimum rescue fletch count.
 */
@Singleton
public class AdvisorEngine
{
    enum PlannerStage
    {
        CUTTING,
        MINIMUM_PREP,
        BURNING,
        EMERGENCY,
        MAX_POINTS
    }

    private final WintertodtState state;
    private final PerformanceTracker tracker;
    private final WintertodtHudConfig config;
    private final EnergyPhaseClock energyClock;
    private final WintertodtStationMonitor stations;

    private AdvisorSnapshot snapshot = AdvisorSnapshot.EMPTY;
    private PlannerStage stage = PlannerStage.CUTTING;
    private int lockedFletches;
    private int prepStartObservedFletches;
    private boolean runCommitted;
    private boolean maxPointsFletchCommitted;
    private StrategyMode lastStrategy = StrategyMode.MAX_FM_XP;

    // Always plan from the CURRENT conservative clock. Command hysteresis is
    // handled separately by runCommitted; never throw away newly available time.
    private int planningTicksLeft = -1;
    private int lastStationEpoch = Integer.MIN_VALUE;

    @Inject
    AdvisorEngine(WintertodtState state, PerformanceTracker tracker, WintertodtHudConfig config,
                  EnergyPhaseClock energyClock, WintertodtStationMonitor stations)
    {
        this.state = state;
        this.tracker = tracker;
        this.config = config;
        this.energyClock = energyClock;
        this.stations = stations;
    }

    public void update()
    {
        if (!state.roundActive())
        {
            resetPolicy();
            snapshot = AdvisorSnapshot.EMPTY;
            return;
        }

        StrategyMode strategy = config.strategyMode();
        if (strategy != lastStrategy)
        {
            resetPolicy();
            lastStrategy = strategy;
        }

        int points = state.points() >= 0 ? state.points() : tracker.trackedPoints();
        int rawClockTicks = energyClock.planningTicksLeft();
        int displayTicksLeft = energyClock.displayTicksLeft();
        int stationEpoch = stations.epoch();
        boolean stationChanged = lastStationEpoch != Integer.MIN_VALUE && stationEpoch != lastStationEpoch;

        if (rawClockTicks < 0 || (!state.routeCalibrated() && !state.hasRecentActivity()))
        {
            lastStationEpoch = stationEpoch;
            snapshot = pointsOnly(points, displayTicksLeft);
            return;
        }

        planningTicksLeft = rawClockTicks;
        if (stationChanged)
        {
            // A real brazier/pyromancer change is meaningful new information, so
            // release a prior departure commitment and re-run the full objective.
            runCommitted = false;
        }
        lastStationEpoch = stationEpoch;

        int roots = tracker.roots();
        int kindling = tracker.kindling();
        int fuel = roots + kindling;
        int freeSlots = tracker.freeSlots();
        int fallback = config.fallbackBrazierTiles();
        int toBrazier = nonNegative(state.nearestBrazierTiles(), fallback);
        int toRoot = nonNegative(state.nearestRootTiles(), fallback);
        int rootToBrazier = Math.max(1, state.rootToBrazierTiles(fallback));
        int readyTicks = brazierReadyPenaltyTicks();
        boolean atBrazier = state.atBrazier();

        RewardRoutePlanner.Input rewardInput = new RewardRoutePlanner.Input(
            planningTicksLeft, points, roots, kindling, freeSlots,
            toRoot, toBrazier, rootToBrazier, readyTicks, atBrazier);
        RewardRoutePlanner.Mechanics mechanics = new RewardRoutePlanner.Mechanics(
            tracker.safeCutTicks(), tracker.safeFeedTicks(), tracker.safeFletchTicks(),
            tracker.safeMovementTicksPerTile(config.assumeRunning()));
        int oneFeedChainTicks = (atBrazier ? 0 : RewardRoutePlanner.moveTicks(toBrazier, mechanics.moveTicksPerTile))
            + readyTicks + mechanics.feedTicks;
        boolean canCompleteOneFeedByXpDeadline = fuel > 0
            && oneFeedChainTicks <= Math.max(0, planningTicksLeft - RewardRoutePlanner.XP_RESERVE_TICKS);

        RouteSimulator.Input routeInput = new RouteSimulator.Input(
            planningTicksLeft, points, roots, kindling, freeSlots,
            toRoot, toBrazier, rootToBrazier, readyTicks, atBrazier);
        RouteSimulator.Profile xpProfile = new RouteSimulator.Profile(
            tracker.safeCutTicks(), tracker.safeFeedTicks(), tracker.safeFletchTicks(),
            tracker.safeMovementTicksPerTile(config.assumeRunning()),
            0, RewardRoutePlanner.XP_RESERVE_TICKS, false);
        RouteSimulator.Profile ceilingProfile = new RouteSimulator.Profile(
            tracker.safeCutTicks(), tracker.safeFeedTicks(), tracker.safeFletchTicks(),
            tracker.safeMovementTicksPerTile(config.assumeRunning()),
            0, 0, false);
        RouteSimulator.Result xp = RouteSimulator.simulate(routeInput, xpProfile);
        RouteSimulator.Result ceiling = RouteSimulator.simulate(routeInput, ceilingProfile);

        RewardRoutePlanner.Plan rawRewardPlan = RewardRoutePlanner.exactFletchPlanTo500(
            rewardInput, mechanics, 0);
        RewardRoutePlanner.Plan minimumRewardPlan = rawRewardPlan.reachable
            ? rawRewardPlan
            : RewardRoutePlanner.minimumPlanTo500(rewardInput, mechanics);

        if (strategy == StrategyMode.MAX_POINTS)
        {
            updateMaxPoints(points, displayTicksLeft, rewardInput, mechanics,
                xp, ceiling, minimumRewardPlan, rawRewardPlan, atBrazier,
                roots, fuel, freeSlots, canCompleteOneFeedByXpDeadline);
            return;
        }

        // Actual reward security is a one-way objective transition.
        if (points >= WintertodtMechanics.REWARD_THRESHOLD)
        {
            stage = PlannerStage.BURNING;
            lockedFletches = 0;
            runCommitted = false;
        }

        // Emergency may recover only after a real station-state change creates
        // genuinely new time. Clock jitter alone is never allowed to bounce it.
        if (stage == PlannerStage.EMERGENCY && points < WintertodtMechanics.REWARD_THRESHOLD
            && stationChanged && minimumRewardPlan.reachable)
        {
            if (minimumRewardPlan.rawOnly())
            {
                stage = PlannerStage.CUTTING;
                runCommitted = false;
            }
            else
            {
                enterMinimumPrep(minimumRewardPlan.fletches);
            }
        }

        if (stage == PlannerStage.CUTTING && points < WintertodtMechanics.REWARD_THRESHOLD
            && !rawRewardPlan.reachable)
        {
            if (minimumRewardPlan.reachable && minimumRewardPlan.fletches > 0)
            {
                // RAW-ONLY FAILED. This is the sole gateway into fletching.
                enterMinimumPrep(minimumRewardPlan.fletches);
            }
            else
            {
                stage = PlannerStage.EMERGENCY;
                runCommitted = false;
            }
        }

        if (stage == PlannerStage.MINIMUM_PREP && points < WintertodtMechanics.REWARD_THRESHOLD)
        {
            // A rescue target can be locked during a transient early estimate. If
            // no rescue fletch has actually happened yet and the next live solve
            // proves the raw-only route is safe, drop that stale lock. This is the
            // only planner change from the validated v0.3.0 baseline.
            if (shouldReleaseUnstartedFletchLock(
                state.routeCalibrated(), rawRewardPlan.reachable,
                tracker.totalFletchesObserved(), prepStartObservedFletches))
            {
                stage = PlannerStage.CUTTING;
                lockedFletches = 0;
                runCommitted = false;
            }

            int remaining = remainingLockedFletches();
            RewardRoutePlanner.Plan lockedPlan = RewardRoutePlanner.exactFletchPlanTo500(
                rewardInput, mechanics, remaining);
            if (!lockedPlan.reachable)
            {
                RewardRoutePlanner.Plan newMinimum = RewardRoutePlanner.minimumPlanTo500(rewardInput, mechanics);
                if (!newMinimum.reachable)
                {
                    stage = PlannerStage.EMERGENCY;
                    runCommitted = false;
                }
                else if (newMinimum.fletches > remaining)
                {
                    // A real loss of time may require MORE rescue prep. Never lower
                    // the locked target just because the clock later becomes nicer.
                    lockedFletches += newMinimum.fletches - remaining;
                }
            }
        }

        AdvisorAction action;
        int displayFletches = 0;

        switch (stage)
        {
            case EMERGENCY:
                // 500 is no longer reachable, but that does NOT mean "burn now".
                // Maximize salvage points/XP with the same whole-chain proof used
                // after 500: keep cutting while one more root plus the complete
                // run + feed-all chain still fits before the end reserve.
                if (atBrazier && fuel > 0)
                {
                    action = AdvisorAction.BURN_NOW;
                    runCommitted = false;
                }
                else if (runCommitted && fuel > 0)
                {
                    action = AdvisorAction.RUN_TO_BRAZIER;
                }
                else if (RewardRoutePlanner.oneMoreRawRootFitsXpObjective(rewardInput, mechanics))
                {
                    action = AdvisorAction.KEEP_CUTTING;
                    runCommitted = false;
                }
                else if (fuel > 0)
                {
                    action = committedRun(fuel, atBrazier);
                }
                else
                {
                    action = AdvisorAction.ROUND_ENDING;
                    runCommitted = false;
                }
                break;

            case MINIMUM_PREP:
                if (points >= WintertodtMechanics.REWARD_THRESHOLD)
                {
                    stage = PlannerStage.BURNING;
                    lockedFletches = 0;
                    runCommitted = false;
                    action = burningAction(rewardInput, mechanics, atBrazier, fuel,
                        canCompleteOneFeedByXpDeadline, stationChanged);
                }
                else
                {
                    int remaining = remainingLockedFletches();
                    displayFletches = remaining;

                    if (remaining > 0 && roots >= remaining)
                    {
                        // Once prep begins, N is a commitment. Do not interleave
                        // speculative extra cutting while enough roots already exist.
                        action = AdvisorAction.FLETCH;
                        runCommitted = false;
                    }
                    else if (remaining > 0)
                    {
                        boolean nextRootFits = RewardRoutePlanner.oneMoreRootStillFitsLockedRescue(
                            rewardInput, mechanics, remaining);
                        if (freeSlots > 0 && nextRootFits)
                        {
                            action = AdvisorAction.KEEP_CUTTING;
                            runCommitted = false;
                        }
                        else if (fuel > 0)
                        {
                            action = travelOrBurn(atBrazier);
                        }
                        else
                        {
                            stage = PlannerStage.EMERGENCY;
                            action = AdvisorAction.MAX_POINTS_BURN_NOW;
                            runCommitted = false;
                        }
                    }
                    else
                    {
                        // Minimum prep is complete. From here every extra fuel item
                        // is raw. Burn/cut only as needed until the actual 500 crossing.
                        if (atBrazier && fuel > 0)
                        {
                            action = AdvisorAction.BURN_NOW;
                            runCommitted = false;
                        }
                        else if (runCommitted && fuel > 0)
                        {
                            action = AdvisorAction.RUN_TO_BRAZIER;
                        }
                        else if (RewardRoutePlanner.oneMoreRawRootFitsWholeObjective(rewardInput, mechanics))
                        {
                            // Rescue prep is complete, but do NOT leave merely because
                            // the current bag already crosses 500. Keep adding raw roots
                            // while the 500-by-5 and all-fuel-by-1 deadlines both hold.
                            action = AdvisorAction.KEEP_CUTTING;
                            runCommitted = false;
                        }
                        else if (fuel > 0)
                        {
                            action = committedRun(fuel, atBrazier);
                        }
                        else
                        {
                            stage = PlannerStage.EMERGENCY;
                            action = AdvisorAction.MAX_POINTS_BURN_NOW;
                            runCommitted = false;
                        }
                    }
                }
                break;

            case BURNING:
                action = burningAction(rewardInput, mechanics, atBrazier, fuel,
                    canCompleteOneFeedByXpDeadline, stationChanged);
                break;

            case CUTTING:
            default:
                // Raw-only remains the default route. A root is recommended ONLY
                // when acquiring it and the entire downstream raw run/feed chain
                // still fit before the hard 500-by-end-minus-five deadline.
                if (atBrazier && fuel > 0)
                {
                    action = AdvisorAction.BURN_NOW;
                    runCommitted = false;
                }
                else if (runCommitted && fuel > 0)
                {
                    action = AdvisorAction.RUN_TO_BRAZIER;
                }
                else if (RewardRoutePlanner.oneMoreRawRootFitsWholeObjective(rewardInput, mechanics))
                {
                    action = AdvisorAction.KEEP_CUTTING;
                    runCommitted = false;
                }
                else if (fuel > 0)
                {
                    action = committedRun(fuel, atBrazier);
                }
                else if (rawRewardPlan.reachable)
                {
                    // Open-ended/very early calibration edge: the raw solver says
                    // the reward is safe, so continue collecting rather than inventing prep.
                    action = AdvisorAction.KEEP_CUTTING;
                }
                else
                {
                    stage = PlannerStage.EMERGENCY;
                    action = AdvisorAction.MAX_POINTS_BURN_NOW;
                    runCommitted = false;
                }
                break;
        }

        if (stage == PlannerStage.BURNING && planningTicksLeft <= RewardRoutePlanner.XP_RESERVE_TICKS
            && fuel == 0)
        {
            action = AdvisorAction.ROUND_ENDING;
        }

        boolean fiveHundredSafe = points >= WintertodtMechanics.REWARD_THRESHOLD
            || (stage == PlannerStage.MINIMUM_PREP
                ? RewardRoutePlanner.exactFletchPlanTo500(rewardInput, mechanics, remainingLockedFletches()).reachable
                : minimumRewardPlan.reachable);
        boolean rawFiveHundredSafe = points >= WintertodtMechanics.REWARD_THRESHOLD || rawRewardPlan.reachable;
        int projected;
        if (stage == PlannerStage.EMERGENCY)
        {
            projected = xp.rawFinalPoints;
        }
        else if (points >= WintertodtMechanics.REWARD_THRESHOLD || stage == PlannerStage.BURNING)
        {
            projected = xp.rawFinalPoints;
        }
        else if (minimumRewardPlan.reachable)
        {
            projected = minimumRewardPlan.pointsAtThreshold;
        }
        else
        {
            projected = points + AdvisorMath.inventoryPoints(roots, kindling);
        }

        boolean finalLoad = stage == PlannerStage.MINIMUM_PREP
            || stage == PlannerStage.BURNING
            || stage == PlannerStage.EMERGENCY
            || runCommitted;
        boolean goNow = action == AdvisorAction.RUN_TO_BRAZIER
            || action == AdvisorAction.MAX_POINTS_BURN_NOW;

        snapshot = new AdvisorSnapshot(true, action, "", "",
            displayTicksLeft, -1, -1, finalLoad,
            xp.extraRootsThisLoad, ceiling.extraRootsThisLoad,
            xp.additionalRawRootsPossible, ceiling.additionalRawRootsPossible,
            displayFletches, points, projected, ceiling.rawFinalPoints,
            fiveHundredSafe, rawFiveHundredSafe, goNow);
    }

    private void updateMaxPoints(int points, int displayTicksLeft,
                                 RewardRoutePlanner.Input input,
                                 RewardRoutePlanner.Mechanics mechanics,
                                 RouteSimulator.Result xp,
                                 RouteSimulator.Result ceiling,
                                 RewardRoutePlanner.Plan minimumRewardPlan,
                                 RewardRoutePlanner.Plan rawRewardPlan,
                                 boolean atBrazier,
                                 int roots,
                                 int fuel,
                                 int freeSlots,
                                 boolean canCompleteOneFeed)
    {
        stage = PlannerStage.MAX_POINTS;
        lockedFletches = 0;

        int fletchesFit = RewardRoutePlanner.maxCurrentLoadFletchesForPoints(input, mechanics);
        boolean oneMoreFletchedRootFits =
            RewardRoutePlanner.oneMoreFletchedRootFitsXpObjective(input, mechanics);

        AdvisorAction action;
        int displayFletches = 0;

        if (roots == 0)
        {
            maxPointsFletchCommitted = false;
        }

        if (atBrazier && fuel > 0)
        {
            if (roots > 0 && fletchesFit > 0)
            {
                maxPointsFletchCommitted = true;
                action = AdvisorAction.FLETCH;
                displayFletches = fletchesFit;
            }
            else
            {
                maxPointsFletchCommitted = false;
                action = canCompleteOneFeed ? AdvisorAction.BURN_NOW : AdvisorAction.ROUND_ENDING;
            }
            runCommitted = false;
        }
        else if (maxPointsFletchCommitted && roots > 0)
        {
            // Once a load enters its fletching phase, finish that phase instead
            // of bouncing FLETCH <-> KEEP CUTTING as the live clock jitters.
            if (fletchesFit > 0)
            {
                action = AdvisorAction.FLETCH;
                displayFletches = fletchesFit;
                runCommitted = false;
            }
            else if (fuel > 0 && canCompleteOneFeed)
            {
                maxPointsFletchCommitted = false;
                action = committedRun(fuel, atBrazier);
            }
            else
            {
                maxPointsFletchCommitted = false;
                action = AdvisorAction.ROUND_ENDING;
                runCommitted = false;
            }
        }
        else if (oneMoreFletchedRootFits && freeSlots > 0)
        {
            action = AdvisorAction.KEEP_CUTTING;
            runCommitted = false;
        }
        else if (roots > 0 && fletchesFit > 0)
        {
            maxPointsFletchCommitted = true;
            action = AdvisorAction.FLETCH;
            displayFletches = fletchesFit;
            runCommitted = false;
        }
        else if (fuel > 0 && canCompleteOneFeed)
        {
            maxPointsFletchCommitted = false;
            action = committedRun(fuel, atBrazier);
        }
        else
        {
            maxPointsFletchCommitted = false;
            action = AdvisorAction.ROUND_ENDING;
            runCommitted = false;
        }

        int projected = RewardRoutePlanner.maxPointsCurrentLoadProjection(input, mechanics);
        boolean fiveHundredSafe = points >= WintertodtMechanics.REWARD_THRESHOLD
            || minimumRewardPlan.reachable;
        boolean rawFiveHundredSafe = points >= WintertodtMechanics.REWARD_THRESHOLD
            || rawRewardPlan.reachable;
        boolean finalLoad = action == AdvisorAction.FLETCH
            || action == AdvisorAction.RUN_TO_BRAZIER
            || action == AdvisorAction.BURN_NOW
            || action == AdvisorAction.ROUND_ENDING;
        boolean goNow = action == AdvisorAction.RUN_TO_BRAZIER;

        snapshot = new AdvisorSnapshot(true, action, "", "",
            displayTicksLeft, -1, -1, finalLoad,
            xp.extraRootsThisLoad, ceiling.extraRootsThisLoad,
            xp.additionalRawRootsPossible, ceiling.additionalRawRootsPossible,
            displayFletches, points, projected, ceiling.rawFinalPoints,
            fiveHundredSafe, rawFiveHundredSafe, goNow);
    }

    private AdvisorAction burningAction(RewardRoutePlanner.Input input, RewardRoutePlanner.Mechanics mechanics,
                                       boolean atBrazier, int fuel, boolean canCompleteOneFeed,
                                       boolean stationChanged)
    {
        if (fuel > 0 && !canCompleteOneFeed)
        {
            runCommitted = false;
            return AdvisorAction.ROUND_ENDING;
        }
        if (atBrazier && fuel > 0)
        {
            runCommitted = false;
            return AdvisorAction.BURN_NOW;
        }
        if (runCommitted && !stationChanged && fuel > 0)
        {
            return AdvisorAction.RUN_TO_BRAZIER;
        }
        if (RewardRoutePlanner.oneMoreRawRootFitsXpObjective(input, mechanics))
        {
            runCommitted = false;
            return AdvisorAction.KEEP_CUTTING;
        }
        if (fuel > 0)
        {
            return committedRun(fuel, atBrazier);
        }
        runCommitted = false;
        return AdvisorAction.ROUND_ENDING;
    }

    private AdvisorAction travelOrBurn(boolean atBrazier)
    {
        if (atBrazier)
        {
            runCommitted = false;
            return AdvisorAction.BURN_NOW;
        }
        runCommitted = true;
        return AdvisorAction.RUN_TO_BRAZIER;
    }

    static boolean shouldReleaseUnstartedFletchLock(boolean routeCalibrated,
                                                       boolean rawRewardReachable,
                                                       int observedFletches,
                                                       int prepStartObservedFletches)
    {
        return routeCalibrated
            && rawRewardReachable
            && observedFletches == prepStartObservedFletches;
    }

    private AdvisorAction committedRun(int fuel, boolean atBrazier)
    {
        if (fuel <= 0)
        {
            runCommitted = false;
            return AdvisorAction.ROUND_ENDING;
        }
        if (atBrazier)
        {
            runCommitted = false;
            return AdvisorAction.BURN_NOW;
        }
        runCommitted = true;
        return AdvisorAction.RUN_TO_BRAZIER;
    }

    private void enterMinimumPrep(int minimumFletches)
    {
        stage = PlannerStage.MINIMUM_PREP;
        lockedFletches = Math.max(1, minimumFletches);
        prepStartObservedFletches = tracker.totalFletchesObserved();
        runCommitted = false;
    }

    private int remainingLockedFletches()
    {
        int completed = Math.max(0, tracker.totalFletchesObserved() - prepStartObservedFletches);
        return Math.max(0, lockedFletches - completed);
    }

    private void resetPolicy()
    {
        stage = PlannerStage.CUTTING;
        lockedFletches = 0;
        prepStartObservedFletches = 0;
        runCommitted = false;
        maxPointsFletchCommitted = false;
        planningTicksLeft = -1;
        lastStationEpoch = Integer.MIN_VALUE;
    }

    void resetRoundPolicy()
    {
        resetPolicy();
        snapshot = AdvisorSnapshot.EMPTY;
    }

    public AdvisorSnapshot snapshot()
    {
        return snapshot;
    }

    public String stageName()
    {
        return stage.name();
    }

    public int lockedFletchCount()
    {
        return stage == PlannerStage.MINIMUM_PREP ? remainingLockedFletches() : 0;
    }

    public int planningTicksLeft()
    {
        return planningTicksLeft;
    }

    private static AdvisorSnapshot pointsOnly(int points, int ticksLeft)
    {
        return new AdvisorSnapshot(true, AdvisorAction.WAITING, "", "",
            ticksLeft, -1, -1, false,
            -1, -1, -1, -1, 0,
            points, points, points,
            points >= WintertodtMechanics.REWARD_THRESHOLD,
            points >= WintertodtMechanics.REWARD_THRESHOLD, false);
    }

    private int brazierReadyPenaltyTicks()
    {
        WintertodtStationMonitor.Quadrant quadrant = state.brazierQuadrant();
        WintertodtStationMonitor.BrazierState brazier = stations.brazierState(quadrant);
        switch (brazier)
        {
            case LIT:
                return 0;
            case UNLIT:
                return WintertodtMechanics.LIGHT_BRAZIER_TICKS;
            case BROKEN:
                return WintertodtMechanics.REPAIR_BRAZIER_TICKS
                    + WintertodtMechanics.LIGHT_BRAZIER_TICKS;
            case UNKNOWN:
            default:
                return state.brazierOutLikely() ? WintertodtMechanics.LIGHT_BRAZIER_TICKS : 0;
        }
    }

    private static int nonNegative(int value, int fallback)
    {
        return value < 0 ? Math.max(1, fallback) : value;
    }
}
