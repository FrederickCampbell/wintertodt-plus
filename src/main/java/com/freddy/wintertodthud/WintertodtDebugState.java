package com.freddy.wintertodthud;

import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Thread boundary between RuneLite/client state and Swing.
 *
 * publish() is called only from RuneLite event handlers. The sidebar EDT never
 * reaches into Client, WintertodtState, tracker, clock, or advisor objects; it
 * renders only the latest immutable snapshot from this AtomicReference.
 */
@Singleton
public class WintertodtDebugState
{
    private final WintertodtState state;
    private final PerformanceTracker tracker;
    private final AdvisorEngine advisor;
    private final EnergyPhaseClock clock;
    private final WintertodtStationMonitor stations;
    private final WintertodtAuditLog audit;
    private final WintertodtHudConfig config;

    private final AtomicReference<WintertodtDebugSnapshot> latest =
        new AtomicReference<>(WintertodtDebugSnapshot.EMPTY);

    @Inject
    WintertodtDebugState(WintertodtState state, PerformanceTracker tracker,
                         AdvisorEngine advisor, EnergyPhaseClock clock,
                         WintertodtStationMonitor stations, WintertodtAuditLog audit,
                         WintertodtHudConfig config)
    {
        this.state = state;
        this.tracker = tracker;
        this.advisor = advisor;
        this.clock = clock;
        this.stations = stations;
        this.audit = audit;
        this.config = config;
    }

    public void publish()
    {
        boolean in = state.inWintertodt();
        int points = state.points() >= 0 ? state.points() : tracker.trackedPoints();
        AdvisorSnapshot a = advisor.snapshot();
        String auditState = !config.plannerTraceEnabled()
            ? "Off"
            : (!audit.debugLoggingAvailable() ? "Needs RUN_DEBUG"
                : (audit.active() ? "Recording" : "Armed"));
        String stationText = stations.reliable() ? stations.stationSummary() : stations.diagnostic();
        int routeTiles = state.routeCalibrated() ? state.rootToBrazierTiles(config.fallbackBrazierTiles()) : -1;

        latest.set(new WintertodtDebugSnapshot(
            in, state.warmth(), state.energy(), points, tracker.roots(), tracker.kindling(),
            a.action(), a.advice(), a.detail(),
            clock.status(), clock.trusted(), clock.safeTicksLeft(), clock.latestTicksLeft(),
            clock.knownPhaseCount(), clock.candidateCount(),
            stationText, stations.activeDrainers(),
            state.hasBrazierAnchor(), state.hasRootAnchor(), routeTiles,
            auditState, tracker.sampleCountSummary()));
    }

    public WintertodtDebugSnapshot snapshot()
    {
        return latest.get();
    }
}
