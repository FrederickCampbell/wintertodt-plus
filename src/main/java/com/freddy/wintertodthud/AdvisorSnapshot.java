package com.freddy.wintertodthud;

public final class AdvisorSnapshot
{
    public static final AdvisorSnapshot EMPTY = new AdvisorSnapshot(
        false, AdvisorAction.WAITING, "", "", -1, -1, -1, false,
        -1, -1, -1, -1, 0, 0, 0, 0, false, false, false);

    private final boolean active;
    private final AdvisorAction action;
    private final String detail;
    private final String goal;
    private final int ticksLeft;
    private final int runInTicks;
    private final int fletchInTicks;
    private final boolean finalLoad;
    private final int safeExtraRoots;
    private final int maxExtraRoots;
    private final int safeRemainingRawRoots;
    private final int maxRemainingRawRoots;
    private final int fletchCount;
    private final int points;
    private final int projectedPoints;
    private final int maxProjectedPoints;
    private final boolean fiveHundredSafe;
    private final boolean rawFiveHundredSafe;
    private final boolean goNow;

    public AdvisorSnapshot(boolean active, AdvisorAction action, String detail, String goal,
                           int ticksLeft, int runInTicks, int fletchInTicks, boolean finalLoad,
                           int safeExtraRoots, int maxExtraRoots,
                           int safeRemainingRawRoots, int maxRemainingRawRoots, int fletchCount,
                           int points, int projectedPoints, int maxProjectedPoints,
                           boolean fiveHundredSafe, boolean rawFiveHundredSafe, boolean goNow)
    {
        this.active = active;
        this.action = action;
        this.detail = detail;
        this.goal = goal;
        this.ticksLeft = ticksLeft;
        this.runInTicks = runInTicks;
        this.fletchInTicks = fletchInTicks;
        this.finalLoad = finalLoad;
        this.safeExtraRoots = safeExtraRoots;
        this.maxExtraRoots = maxExtraRoots;
        this.safeRemainingRawRoots = safeRemainingRawRoots;
        this.maxRemainingRawRoots = maxRemainingRawRoots;
        this.fletchCount = fletchCount;
        this.points = points;
        this.projectedPoints = projectedPoints;
        this.maxProjectedPoints = maxProjectedPoints;
        this.fiveHundredSafe = fiveHundredSafe;
        this.rawFiveHundredSafe = rawFiveHundredSafe;
        this.goNow = goNow;
    }

    public boolean active(){ return active; }
    public AdvisorAction action(){ return action; }
    public String advice(){ return action == AdvisorAction.FLETCH && fletchCount > 0 ? action.label() + " " + fletchCount : action.label(); }
    public String detail(){ return detail; }
    public String goal(){ return goal; }
    public int ticksLeft(){ return ticksLeft; }
    public int runInTicks(){ return runInTicks; }
    public int fletchInTicks(){ return fletchInTicks; }
    public boolean finalLoad(){ return finalLoad; }
    public int safeExtraRoots(){ return safeExtraRoots; }
    public int maxExtraRoots(){ return maxExtraRoots; }
    public int safeRemainingRawRoots(){ return safeRemainingRawRoots; }
    public int maxRemainingRawRoots(){ return maxRemainingRawRoots; }
    public int fletchCount(){ return fletchCount; }
    public int points(){ return points; }
    public int projectedPoints(){ return projectedPoints; }
    public int maxProjectedPoints(){ return maxProjectedPoints; }
    public boolean fiveHundredSafe(){ return fiveHundredSafe; }
    public boolean rawFiveHundredSafe(){ return rawFiveHundredSafe; }
    public boolean goNow(){ return goNow; }
}
