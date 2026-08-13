package com.freddy.wintertodthud;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** One movable, user-facing box for points and live coaching. */
public class CoachOverlay extends Overlay
{
    private final WintertodtState state;
    private final PerformanceTracker tracker;
    private final AdvisorEngine advisor;
    private final WintertodtHudConfig config;
    private final EnergyPhaseClock energyClock;
    private final WintertodtStationMonitor stationMonitor;

    @Inject
    CoachOverlay(WintertodtHudPlugin plugin, WintertodtState state, PerformanceTracker tracker,
                 AdvisorEngine advisor, WintertodtHudConfig config,
                 EnergyPhaseClock energyClock, WintertodtStationMonitor stationMonitor)
    {
        super(plugin);
        this.state = state;
        this.tracker = tracker;
        this.advisor = advisor;
        this.config = config;
        this.energyClock = energyClock;
        this.stationMonitor = stationMonitor;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);
        setSnappable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!DisplayRules.showCoach(config)
            || (config.onlyInsideWintertodt() && !state.inWintertodt()))
        {
            return null;
        }

        AdvisorSnapshot snapshot = advisor.snapshot();
        List<SimplePanelRenderer.Line> lines = new ArrayList<>();

        if (!snapshot.active())
        {
            lines.add(new SimplePanelRenderer.Line("Waiting for round", config.panelText()));
            return SimplePanelRenderer.render(graphics, config, "WINTERTODT", lines);
        }

        int points = state.points() >= 0 ? state.points() : tracker.trackedPoints();
        if (config.showCurrentPoints())
        {
            lines.add(new SimplePanelRenderer.Line(
                "Points  " + points + (points >= 500 ? "  ✓" : ""),
                points >= 500 ? config.panelGood() : config.panelText()));
        }

        // Normal mode stops here: points + one instruction + one short countdown.
        if (config.showAdviceText() && snapshot.action() != AdvisorAction.WAITING)
        {
            lines.add(new SimplePanelRenderer.Line(snapshot.advice(), actionColor(snapshot.action())));
            if (!snapshot.detail().isEmpty() && config.showAdviceDetail())
            {
                lines.add(new SimplePanelRenderer.Line(snapshot.detail(), config.panelText()));
            }
        }
        if (!snapshot.goal().isEmpty())
        {
            lines.add(new SimplePanelRenderer.Line(snapshot.goal(), config.panelDanger()));
        }

        // Optional details are deliberately hidden in Recommended/Simple presets.
        if (config.showRootCounts())
        {
            lines.add(new SimplePanelRenderer.Line(
                tracker.roots() + " roots  ·  " + tracker.kindling() + " kindling", config.panelText()));
        }
        if (config.showInventoryPoints())
        {
            lines.add(new SimplePanelRenderer.Line("In bag  +" + tracker.inventoryPoints(), config.panelText()));
        }
        if (DisplayRules.showPotential(config))
        {
            lines.add(new SimplePanelRenderer.Line("If all fletched  +" + tracker.potentialInventoryPoints(), config.panelText()));
        }
        if (config.showProjectedPoints())
        {
            lines.add(new SimplePanelRenderer.Line("After plan  " + snapshot.projectedPoints(), config.panelText()));
        }
        if (DisplayRules.showTime(config) && snapshot.ticksLeft() >= 0)
        {
            lines.add(new SimplePanelRenderer.Line("Round  ~" + seconds(snapshot.ticksLeft()) + " sec", config.panelText()));
        }
        if (DisplayRules.showExtraRoots(config))
        {
            lines.add(new SimplePanelRenderer.Line("Safe raw left  ~" + snapshot.safeRemainingRawRoots(), config.panelText()));
        }
        if (DisplayRules.showAdvanced(config))
        {
            lines.add(new SimplePanelRenderer.Line(
                "Safe / max load  " + Math.max(0, snapshot.safeExtraRoots()) + " / " + Math.max(0, snapshot.maxExtraRoots()),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line(
                "Clock  " + energyClock.status()
                    + (energyClock.hasForecast() ? " · " + energyClock.rangeSummary() + "t" : ""),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line(
                "Phases  " + energyClock.knownPhaseCount() + " / "
                    + (stationMonitor.activeDrainers() < 0 ? "?" : stationMonitor.activeDrainers()),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line(
                "Stations  " + (stationMonitor.reliable()
                    ? stationMonitor.stationSummary() : stationMonitor.diagnostic()),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line(
                "Phases  " + energyClock.phaseSummary(),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line(String.format("Mechanics  cut %.0f · fletch %.0f · burn %.0f",
                tracker.safeCutTicks(), tracker.safeFletchTicks(), tracker.safeFeedTicks()),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line(String.format("Movement  %.1f tick/tile%s",
                tracker.safeMovementTicksPerTile(config.assumeRunning()),
                tracker.fletchingKnifeEquipped() ? " · knife equipped" : ""),
                config.panelText()));
            lines.add(new SimplePanelRenderer.Line("Observed samples  " + tracker.learnedSamples(), config.panelText()));
        }

        return SimplePanelRenderer.render(graphics, config, "WINTERTODT", lines);
    }

    private Color actionColor(AdvisorAction action)
    {
        switch (action.level())
        {
            case GOOD:
                return config.panelGood();
            case WARN:
                return config.panelWarn();
            case DANGER:
                return config.panelDanger();
            case NORMAL:
            default:
                return config.panelText();
        }
    }

    private static int seconds(int ticks)
    {
        return Math.max(0, (int)Math.ceil(ticks * 0.6));
    }
}
