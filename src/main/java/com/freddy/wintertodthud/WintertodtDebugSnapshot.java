package com.freddy.wintertodthud;

/** Immutable presentation snapshot published from the RuneLite client thread. */
public final class WintertodtDebugSnapshot
{
    public static final WintertodtDebugSnapshot EMPTY = new WintertodtDebugSnapshot(
        false, 100, -1, 0, 0, 0,
        AdvisorAction.WAITING, "", "",
        "--", false, -1, -1, 0, 0,
        "--", -1, false, false, -1,
        "waiting", "cut=0 fletch=0 feed=0 move=0");

    private final boolean inWintertodt;
    private final int warmth;
    private final int energy;
    private final int points;
    private final int roots;
    private final int kindling;
    private final AdvisorAction action;
    private final String advice;
    private final String adviceDetail;
    private final String clockStatus;
    private final boolean clockExact;
    private final int earliestTicks;
    private final int latestTicks;
    private final int knownPhases;
    private final int candidateCount;
    private final String stationText;
    private final int activeDrainers;
    private final boolean brazierAnchor;
    private final boolean rootAnchor;
    private final int rootToBrazierTiles;
    private final String auditState;
    private final String sampleCounts;

    public WintertodtDebugSnapshot(boolean inWintertodt, int warmth, int energy,
                                   int points, int roots, int kindling,
                                   AdvisorAction action, String advice, String adviceDetail,
                                   String clockStatus, boolean clockExact,
                                   int earliestTicks, int latestTicks,
                                   int knownPhases, int candidateCount,
                                   String stationText, int activeDrainers,
                                   boolean brazierAnchor, boolean rootAnchor,
                                   int rootToBrazierTiles,
                                   String auditState, String sampleCounts)
    {
        this.inWintertodt = inWintertodt;
        this.warmth = warmth;
        this.energy = energy;
        this.points = points;
        this.roots = roots;
        this.kindling = kindling;
        this.action = action == null ? AdvisorAction.WAITING : action;
        this.advice = advice == null ? "" : advice;
        this.adviceDetail = adviceDetail == null ? "" : adviceDetail;
        this.clockStatus = clockStatus == null ? "--" : clockStatus;
        this.clockExact = clockExact;
        this.earliestTicks = earliestTicks;
        this.latestTicks = latestTicks;
        this.knownPhases = knownPhases;
        this.candidateCount = candidateCount;
        this.stationText = stationText == null ? "--" : stationText;
        this.activeDrainers = activeDrainers;
        this.brazierAnchor = brazierAnchor;
        this.rootAnchor = rootAnchor;
        this.rootToBrazierTiles = rootToBrazierTiles;
        this.auditState = auditState == null ? "waiting" : auditState;
        this.sampleCounts = sampleCounts == null ? "" : sampleCounts;
    }

    public boolean inWintertodt(){ return inWintertodt; }
    public int warmth(){ return warmth; }
    public int energy(){ return energy; }
    public int points(){ return points; }
    public int roots(){ return roots; }
    public int kindling(){ return kindling; }
    public AdvisorAction action(){ return action; }
    public String advice(){ return advice; }
    public String adviceDetail(){ return adviceDetail; }
    public String clockStatus(){ return clockStatus; }
    public boolean clockExact(){ return clockExact; }
    public int earliestTicks(){ return earliestTicks; }
    public int latestTicks(){ return latestTicks; }
    public int knownPhases(){ return knownPhases; }
    public int candidateCount(){ return candidateCount; }
    public String stationText(){ return stationText; }
    public int activeDrainers(){ return activeDrainers; }
    public boolean brazierAnchor(){ return brazierAnchor; }
    public boolean rootAnchor(){ return rootAnchor; }
    public int rootToBrazierTiles(){ return rootToBrazierTiles; }
    public String auditState(){ return auditState; }
    public String sampleCounts(){ return sampleCounts; }

}
