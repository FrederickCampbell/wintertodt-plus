package com.freddy.wintertodthud;

import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small, privacy-safe session audit intended for calculator validation.
 *
 * This is deliberately event-driven rather than a per-tick dump. A normal round
 * should produce roughly one line per Energy change plus meaningful state changes,
 * which is enough to reconstruct the calculator without generating a giant log.
 */
@Singleton
public class WintertodtAuditLog
{
    private static final Logger log = LoggerFactory.getLogger(WintertodtAuditLog.class);
    private static final String VERSION = "0.3.10";

    // Current live OSRS NPC IDs. Kept local because the audit is instrumentation,
    // not gameplay logic, and this makes an unexpected transformed NPC obvious.
    private static final int PYROMANCER = 7371;
    private static final int INCAPACITATED_PYROMANCER = 7372;

    private final WintertodtState state;
    private final WintertodtStationMonitor stations;
    private final EnergyPhaseClock clock;
    private final PerformanceTracker tracker;
    private final AdvisorEngine advisor;
    private final WintertodtHudConfig config;

    private boolean active;
    private long sessionTick;
    private int lastEnergy = Integer.MIN_VALUE;
    private int lastPoints = Integer.MIN_VALUE;
    private int lastStationEpoch = Integer.MIN_VALUE;
    private String lastClockSignature = "";
    private boolean lastRoundActive;
    private boolean lastBrazierAnchor;
    private boolean lastRootAnchor;
    private int lastLearnedSamples = -1;
    private Boolean lastKnifeEquipped;
    private String lastAdviceKey = "";

    @Inject
    WintertodtAuditLog(WintertodtState state, WintertodtStationMonitor stations,
                       EnergyPhaseClock clock, PerformanceTracker tracker, AdvisorEngine advisor,
                       WintertodtHudConfig config)
    {
        this.state = state;
        this.stations = stations;
        this.clock = clock;
        this.tracker = tracker;
        this.advisor = advisor;
        this.config = config;
    }

    public void enterWintertodt()
    {
        close();
        if (!config.plannerTraceEnabled() || !log.isDebugEnabled())
        {
            return;
        }
        active = true;
        sessionTick = 0;
        resetObservedState();

        writeRaw("# Wintertodt+ audit v" + VERSION);
        writeRaw("# Privacy: no account name, email, credentials, chat text, or world number is recorded.");
        writeRaw("# Structured trace is emitted through RuneLite's normal debug logger; no custom files are written.");
        writeRaw("# Station state: L=lit U=unlit B=broken; +=pyro alive -=incapacitated.");
        line("ENTER", "energy=" + state.energy() + " points=" + state.points());
    }

    public void leaveWintertodt()
    {
        if (active)
        {
            line("EXIT", "energy=" + state.energy() + " points=" + state.points());
        }
        close();
    }

    /** Call after state/clock/tracker/advisor have all updated for the game tick. */
    public void onGameTick()
    {
        if (!active)
        {
            return;
        }
        sessionTick++;

        boolean round = state.roundActive();
        if (round != lastRoundActive)
        {
            line(round ? "ROUND_START" : "ROUND_END",
                "energy=" + state.energy() + " points=" + currentPoints()
                    + " stations=" + safeStations());
            lastRoundActive = round;
        }

        if (stations.epoch() != lastStationEpoch)
        {
            line("STATIONS", "active=" + printable(stations.activeDrainers())
                + " state=" + safeStations() + " raw=" + stations.rawSummary());
            lastStationEpoch = stations.epoch();
        }

        int energy = state.energy();
        if (lastEnergy != Integer.MIN_VALUE && energy >= 0 && energy != lastEnergy)
        {
            int delta = lastEnergy - energy;
            AdvisorSnapshot s = advisor.snapshot();
            line("ENERGY", lastEnergy + "->" + energy
                + " delta=" + signed(delta)
                + " residue=" + clock.currentResidue()
                + " active=" + printable(stations.activeDrainers())
                + " phases=" + clock.phaseSummary()
                + " clock=" + clockField()
                + " advice=" + compactAdvice(s));
        }
        if (energy >= 0)
        {
            lastEnergy = energy;
        }

        int points = currentPoints();
        if (lastPoints != Integer.MIN_VALUE && points != lastPoints)
        {
            line("POINTS", lastPoints + "->" + points
                + " bag=" + tracker.roots() + "R/" + tracker.kindling() + "K");
        }
        lastPoints = points;

        String clockStatus = clock.status();
        String clockSignature = clockStatus + "|" + stations.activeDrainers()
            + "|" + clock.knownPhaseCount() + "|" + clock.phaseSummary()
            + "|" + clock.rangeSummary() + "|" + clock.candidateCount()
            + "|" + clock.conflictCount() + "|" + clock.timingRecoveryCount();
        if (!clockSignature.equals(lastClockSignature))
        {
            line("CLOCK", clockStatus
                + " active=" + printable(stations.activeDrainers())
                + " known=" + clock.knownPhaseCount()
                + " phases=" + clock.phaseSummary()
                + " range=" + clock.rangeSummary()
                + " candidates=" + clock.candidateCount()
                + " conflicts=" + clock.conflictCount()
                + " jitter=" + clock.timingRecoveryCount());
            lastClockSignature = clockSignature;
        }

        boolean brazierAnchor = state.hasBrazierAnchor();
        boolean rootAnchor = state.hasRootAnchor();
        if (brazierAnchor != lastBrazierAnchor || rootAnchor != lastRootAnchor)
        {
            line("ROUTE", "brazier=" + anchor(state.brazierAnchor())
                + " roots=" + anchor(state.rootAnchor())
                + " rootToBrazier=" + (state.routeCalibrated() ? state.rootToBrazierTiles(8) : -1));
            lastBrazierAnchor = brazierAnchor;
            lastRootAnchor = rootAnchor;
        }

        boolean knifeEquipped = tracker.fletchingKnifeEquipped();
        if (lastKnifeEquipped == null || lastKnifeEquipped != knifeEquipped)
        {
            line("MECHANICS", rateSummary()
                + " knife=" + (knifeEquipped ? "equipped" : "no"));
            lastKnifeEquipped = knifeEquipped;
        }

        int learned = tracker.learnedSamples();
        if (learned != lastLearnedSamples)
        {
            line("OBSERVED", "samples=" + learned
                + " counts=" + tracker.sampleCountSummary()
                + " mechanics=" + rateSummary()
                + " knife=" + (tracker.fletchingKnifeEquipped() ? "equipped" : "no"));
            lastLearnedSamples = learned;
        }

        AdvisorSnapshot snapshot = advisor.snapshot();
        String adviceKey = adviceKey(snapshot);
        if (!adviceKey.equals(lastAdviceKey))
        {
            line("ADVICE", compactAdvice(snapshot)
                + " stage=" + advisor.stageName()
                + " lockedN=" + advisor.lockedFletchCount()
                + " planT=" + advisor.planningTicksLeft()
                + " points=" + snapshot.points()
                + " projected=" + snapshot.projectedPoints()
                + " raw500=" + snapshot.rawFiveHundredSafe()
                + " safe500=" + snapshot.fiveHundredSafe()
                + " finalLoad=" + snapshot.finalLoad());
            lastAdviceKey = adviceKey;
        }
    }

    /**
     * Instrumentation only: records animations emitted by the four pyromancer NPCs
     * so the live client can prove which animation corresponds to the 14-tick cast.
     */
    public void onNpcAnimation(NPC npc)
    {
        if (!active || npc == null)
        {
            return;
        }
        int id = npc.getId();
        if (id != PYROMANCER && id != INCAPACITATED_PYROMANCER)
        {
            return;
        }
        int animation = npc.getAnimation();
        if (animation < 0)
        {
            return;
        }
        WorldPoint p = npc.getWorldLocation();
        line("PYRO_ANIM", "q=" + quadrant(p)
            + " npc=" + id
            + " anim=" + animation
            + " pos=" + (p == null ? "?" : p.getX() + "," + p.getY()));
    }

    public boolean debugLoggingAvailable()
    {
        return log.isDebugEnabled();
    }

    public boolean active()
    {
        return active;
    }

    public void close()
    {
        active = false;
    }

    private void resetObservedState()
    {
        lastEnergy = Integer.MIN_VALUE;
        lastPoints = Integer.MIN_VALUE;
        lastStationEpoch = Integer.MIN_VALUE;
        lastClockSignature = "";
        lastRoundActive = false;
        lastBrazierAnchor = false;
        lastRootAnchor = false;
        lastLearnedSamples = -1;
        lastKnifeEquipped = null;
        lastAdviceKey = "";
    }

    private int currentPoints()
    {
        return state.points() >= 0 ? state.points() : tracker.trackedPoints();
    }

    private String safeStations()
    {
        return stations.reliable() ? stations.stationSummary() : stations.diagnostic();
    }

    private String clockField()
    {
        if (clock.trusted())
        {
            return "LOCKED(" + clock.ticksLeft() + "t)";
        }
        return clock.hasForecast() ? "RANGE(" + clock.rangeSummary() + "t)" : compact(clock.status());
    }

    private String compactAdvice(AdvisorSnapshot s)
    {
        if (s == null || !s.active())
        {
            return "--";
        }
        String out = s.advice();
        if (s.runInTicks() >= 0)
        {
            out += " runIn=" + s.runInTicks() + "t";
        }
        if (s.fletchInTicks() >= 0)
        {
            out += " fletchIn=" + s.fletchInTicks() + "t";
        }
        if (!s.goal().isEmpty())
        {
            out += " goal=" + compact(s.goal());
        }
        return compact(out);
    }

    private String adviceKey(AdvisorSnapshot s)
    {
        if (s == null || !s.active())
        {
            return "inactive";
        }
        // Deliberately exclude the continuously changing countdown numbers.
        return s.action().name() + '|' + s.fletchCount() + '|' + s.goal()
            + '|' + advisor.stageName() + '|' + advisor.lockedFletchCount()
            + '|' + (s.runInTicks() >= 0) + '|' + (s.fletchInTicks() >= 0);
    }

    private String rateSummary()
    {
        return String.format(Locale.ROOT, "cut=%.1f feed=%.1f fletch=%.1f move=%.2f",
            tracker.safeCutTicks(), tracker.safeFeedTicks(), tracker.safeFletchTicks(),
            tracker.safeMovementTicksPerTile(true));
    }

    private static String anchor(WorldPoint p)
    {
        return p == null ? "--" : p.getX() + "," + p.getY();
    }

    private static String quadrant(WorldPoint p)
    {
        if (p == null)
        {
            return "?";
        }
        boolean west = p.getX() < 1630;
        boolean south = p.getY() < 4007;
        if (west)
        {
            return south ? "SW" : "NW";
        }
        return south ? "SE" : "NE";
    }

    private static String printable(int value)
    {
        return value < 0 ? "?" : Integer.toString(value);
    }

    private static String signed(int value)
    {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static String compact(String value)
    {
        return value == null ? "" : value.replaceAll("\\s+", "_");
    }

    private void line(String type, String data)
    {
        writeRaw(String.format(Locale.ROOT, "t=%04d %-11s %s", sessionTick, type, data));
    }

    private void writeRaw(String text)
    {
        if (!active)
        {
            return;
        }
        log.debug("[WT+AUDIT] {}", text);
    }
}
