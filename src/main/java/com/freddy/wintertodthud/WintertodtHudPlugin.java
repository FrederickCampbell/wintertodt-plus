/*
 * Portions of Wintertodt activity/interruption behavior are adapted from RuneLite core.
 * Copyright (c) 2018, terminatusx <jbfleischman@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020, loldudester <HannahRyanster@gmail.com>
 * RuneLite portions are BSD 2-Clause licensed; see THIRD_PARTY_NOTICES.md.
 */
package com.freddy.wintertodthud;

import com.google.inject.Provider;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.UIManager;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "Wintertodt+",
    description = "Movable Wintertodt HUD, clean alerts, and Max FM XP or Max Points live advice",
    tags = {"wintertodt", "warmth", "energy", "points", "hud", "idle", "alerts"}
)
public class WintertodtHudPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(WintertodtHudPlugin.class);
    private static final String CORE_WT_GROUP = "wintertodt";
    private static final String CORE_WT_OVERLAY_KEY = "showOverlay";

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private ClientThread clientThread;
    @Inject private OverlayManager overlays;
    @Inject private ClientToolbar clientToolbar;
    @Inject private WintertodtHudConfig config;
    @Inject private WintertodtState state;
    @Inject private PerformanceTracker tracker;
    @Inject private AdvisorEngine advisor;
    @Inject private WintertodtStationMonitor stationMonitor;
    @Inject private EnergyPhaseClock energyClock;
    @Inject private WintertodtAuditLog auditLog;
    @Inject private WintertodtDebugState debugState;
    @Inject private AlertEngine alerts;
    @Inject private WarmthRegenTracker warmthRegen;
    @Inject private WarmthOverlay warmthOverlay;
    @Inject private EnergyOverlay energyOverlay;
    @Inject private CoachOverlay coachOverlay;
    @Inject private AlertOverlay alertOverlay;
    @Inject private Provider<WintertodtSidebarPanel> sidebarPanelProvider;

    private WintertodtSidebarPanel sidebarPanel;

    private boolean wasInWintertodt;
    private int previousTimer = -1;
    private Boolean originalRuneLiteOverlay;
    private boolean runeLiteOverlayForced;
    private boolean wasGoNow;
    private boolean wasNotSafe;
    private NavigationButton navButton;

    @Provides
    WintertodtHudConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(WintertodtHudConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlays.add(warmthOverlay);
        overlays.add(energyOverlay);
        overlays.add(coachOverlay);
        overlays.add(alertOverlay);

        // Construct Swing UI only now, after ClientUI.init() has installed RuneLiteLAF.
        // Core RuneLite sidebar plugins defer panel construction until startUp() for
        // the same reason; eagerly injecting the panel gives controls stale pre-LAF
        // UI delegates on Windows.
        sidebarPanel = sidebarPanelProvider.get();
        log.debug("Wintertodt+ sidebar LookAndFeel: {} ({})",
            UIManager.getLookAndFeel().getName(), UIManager.getLookAndFeel().getClass().getName());
        log.debug("Wintertodt+ sidebar width: {}px (RuneLite standard)", sidebarPanel.getPreferredSize().width);

        navButton = NavigationButton.builder()
            .tooltip("Wintertodt+")
            .icon(WintertodtSidebarPanel.createIcon())
            .priority(7)
            .panel(sidebarPanel)
            .build();
        clientToolbar.addNavigation(navButton);

        state.resetActivity();
        state.clearRouteCalibration();
        tracker.resetRound();
        stationMonitor.reset();
        energyClock.reset();
        warmthRegen.reset();
        advisor.resetRoundPolicy();
        migrateUxDefaults();
        sidebarPanel.refreshAsync();
        log.debug("Wintertodt+ started");
    }

    @Override
    protected void shutDown()
    {
        overlays.remove(warmthOverlay);
        overlays.remove(energyOverlay);
        overlays.remove(coachOverlay);
        overlays.remove(alertOverlay);
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        sidebarPanel = null;
        auditLog.close();
        alerts.clear();
        restoreNative();
        restoreRuneLiteOverlay();
    }


    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState gameState = event.getGameState();
        if (gameState == GameState.HOPPING || gameState == GameState.LOGIN_SCREEN
            || gameState == GameState.CONNECTION_LOST)
        {
            // Never carry a previous world's station phases or route anchors into
            // the next world. The first logged-in action will immediately use the
            // conservative bootstrap until fresh station/route observations arrive.
            wasInWintertodt = false;
            previousTimer = -1;
            state.resetActivity();
            state.clearRouteCalibration();
            tracker.resetRound();
            stationMonitor.reset();
            energyClock.reset();
            alerts.clear();
            auditLog.close();
            advisor.resetRoundPolicy();
            wasGoNow = false;
            wasNotSafe = false;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        state.tick();
        warmthRegen.onGameTick();
        boolean in = state.inWintertodt();

        if (in && !wasInWintertodt)
        {
            state.resetActivity();
            state.clearRouteCalibration();
            tracker.resetRound();
            stationMonitor.reset();
            energyClock.reset();
            alerts.clear();
            auditLog.enterWintertodt();
            wasGoNow = false;
            wasNotSafe = false;
            previousTimer = client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY);
        }
        else if (!in && wasInWintertodt)
        {
            auditLog.leaveWintertodt();
            alerts.clear();
            wasGoNow = false;
            wasNotSafe = false;
            restoreNative();
            state.resetActivity();
            state.clearRouteCalibration();
            stationMonitor.reset();
            energyClock.reset();
        }

        wasInWintertodt = in;

        // Read station widgets BEFORE hiding any native HUD components.
        stationMonitor.tick(in);
        energyClock.onGameTick(state.roundActive(), state.energy());
        tracker.onGameTick();
        applyRuneLiteOverlayPreference();

        if (!in)
        {
            advisor.update();
            debugState.publish();
            sidebarPanel.refreshAsync();
            return;
        }

        applyNativeVisibility();
        advisor.update();
        evaluatePersistent();
        checkRoundStart();
        auditLog.onGameTick();
        debugState.publish();
        sidebarPanel.refreshAsync();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!state.inWintertodt())
        {
            return;
        }

        String target = event.getMenuTarget();
        if (target != null && target.toLowerCase().contains("brazier"))
        {
            state.anchorNearestBrazier();
            debugState.publish();
            sidebarPanel.refreshAsync();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!state.inWintertodt())
        {
            return;
        }
        tracker.onInventoryChanged(event);
        advisor.update();
        debugState.publish();
        sidebarPanel.refreshAsync();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!WintertodtHudConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        final String key = event.getKey();
        if ("hudPreset".equals(key))
        {
            applyHudPreset(config.hudPreset());
        }

        // ConfigChanged may be delivered from Swing's AWT event thread when a
        // sidebar/config control is edited. Client/local-player/widget state must
        // only be touched on RuneLite's client thread. v0.3.9 violated that by
        // calling state.inWintertodt() directly here.
        applyRuneLiteOverlayPreference();
        if (sidebarPanel != null)
        {
            sidebarPanel.refreshConfigAsync();
        }

        clientThread.invokeLater(() ->
        {
            if ("plannerTraceEnabled".equals(key))
            {
                if (config.plannerTraceEnabled() && state.inWintertodt())
                {
                    auditLog.enterWintertodt();
                }
                else
                {
                    auditLog.close();
                }
            }

            if (state.inWintertodt())
            {
                applyNativeVisibility();
                advisor.update();
            }
            debugState.publish();
        });
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (state.inWintertodt() && event.getActor() instanceof NPC)
        {
            auditLog.onNpcAnimation((NPC) event.getActor());
        }

        if (!state.inWintertodt() || client.getLocalPlayer() == null || event.getActor() != client.getLocalPlayer())
        {
            return;
        }

        int animation = client.getLocalPlayer().getAnimation();
        switch (animation)
        {
            case AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE:
            case AnimationID.HUMAN_WOODCUTTING_IRON_AXE:
            case AnimationID.HUMAN_WOODCUTTING_STEEL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_BLACK_AXE:
            case AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE:
            case AnimationID.HUMAN_WOODCUTTING_RUNE_AXE:
            case AnimationID.HUMAN_WOODCUTTING_GILDED_AXE:
            case AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL:
            case AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE:
            case AnimationID.HUMAN_WOODCUTTING_3A_AXE:
            case AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE:
            case AnimationID.HUMAN_OPENHEAVYCHEST:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE:
            case AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE:
            case AnimationID.FORESTRY_2H_AXE_CHOPPING_3A:
                state.setActivity(WintertodtActivity.CUTTING);
                break;
            case AnimationID.HUMAN_FLETCHING:
                state.setActivity(WintertodtActivity.FLETCHING);
                break;
            case AnimationID.HUMAN_PICKUPTABLE:
                state.setActivity(WintertodtActivity.FEEDING);
                break;
            case AnimationID.HUMAN_CREATEFIRE:
                state.setActivity(WintertodtActivity.LIGHTING);
                break;
            case AnimationID.HUMAN_POH_BUILD:
            case AnimationID.HUMAN_POH_BUILD_IMCANDO_HAMMER:
                state.setActivity(WintertodtActivity.REPAIRING);
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!state.inWintertodt() || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
        {
            return;
        }
        MessageNode node = event.getMessageNode();
        if (node == null || node.getValue() == null)
        {
            return;
        }
        String message = node.getValue();

        if (message.startsWith("You carefully fletch the root"))
        {
            state.setActivity(WintertodtActivity.FLETCHING);
            return;
        }
        if (message.startsWith("You fix the brazier"))
        {
            state.setActivity(WintertodtActivity.REPAIRING);
            tracker.addTrackedPoints(WintertodtMechanics.REPAIR_POINTS);
            return;
        }
        if (message.startsWith("You light the brazier"))
        {
            state.setActivity(WintertodtActivity.LIGHTING);
            state.noteBrazierLit();
            tracker.addTrackedPoints(WintertodtMechanics.LIGHT_POINTS);
            return;
        }
        if (message.toLowerCase().contains("heal") && message.toLowerCase().contains("pyromancer"))
        {
            tracker.addTrackedPoints(WintertodtMechanics.HEAL_PYROMANCER_POINTS);
        }

        if (config.alertsEnabled() && config.inventoryFullAlert() && message.startsWith("Your inventory is too full"))
        {
            alerts.fire(AlertKind.INVENTORY_FULL, config.inventoryFullVisual(), notify(config.inventoryFullNotify()));
        }
        else if (config.alertsEnabled() && config.outOfRootsAlert() && message.startsWith("You have run out of bruma roots"))
        {
            alerts.fire(AlertKind.OUT_OF_ROOTS, config.outOfRootsVisual(), notify(config.outOfRootsNotify()));
        }
        else if (message.startsWith("The brazier has gone out"))
        {
            WintertodtActivity current = state.activity();
            state.noteBrazierOut();
            if (current == WintertodtActivity.FEEDING)
            {
                markInterruptedIdle();
            }
            if (config.alertsEnabled() && config.brazierOutAlert())
            {
                alerts.dismiss(AlertKind.IDLE);
                alerts.fire(AlertKind.BRAZIER_OUT, config.brazierOutVisual(), notify(config.brazierOutNotify()));
            }
        }
        else if (message.startsWith("The cold of") || message.startsWith("The freezing cold attack") || message.startsWith("The brazier is broken and shrapnel"))
        {
            WintertodtActivity current = state.activity();
            // Current Wintertodt mechanics explicitly interrupt feeding and
            // fletching. Chopping continues through the normal cold attack.
            boolean interrupted = current == WintertodtActivity.FEEDING || current == WintertodtActivity.FLETCHING;
            if (interrupted)
            {
                markInterruptedIdle();
            }
            if (config.alertsEnabled() && config.interruptAlert() && interrupted)
            {
                alerts.dismiss(AlertKind.IDLE);
                alerts.fire(AlertKind.INTERRUPTED, config.interruptVisual(), notify(config.interruptNotify()));
            }
        }
    }

    private void markInterruptedIdle()
    {
        // Record the interruption immediately. The persistent evaluator owns the
        // generic Idle alert, allowing a more-specific short event to suppress it.
        state.interruptActivity();
    }

    private void evaluatePersistent()
    {
        if (!config.alertsEnabled())
        {
            alerts.clear();
            wasGoNow = false;
            wasNotSafe = false;
            return;
        }

        boolean activeRound = state.roundActive();
        boolean critical = activeRound && config.criticalWarmthAlert()
            && state.warmth() <= config.criticalWarmthThreshold();

        alerts.setPersistent(AlertKind.CRITICAL_WARMTH, critical,
            config.criticalWarmthVisual(), notify(config.criticalWarmthNotify()));
        alerts.setPersistent(AlertKind.LOW_WARMTH,
            activeRound && !critical && config.lowWarmthAlert()
                && state.warmth() <= config.lowWarmthThreshold(),
            config.lowWarmthVisual(), notify(config.lowWarmthNotify()));
        alerts.setPersistent(AlertKind.LOW_ENERGY,
            activeRound && config.lowEnergyAlert() && state.energy() >= 0
                && state.energy() <= config.lowEnergyThreshold(),
            config.lowEnergyVisual(), notify(config.lowEnergyNotify()));

        boolean specificInterruption = alerts.isActive(AlertKind.BRAZIER_OUT)
            || alerts.isActive(AlertKind.INTERRUPTED);
        alerts.setPersistent(AlertKind.IDLE,
            !specificInterruption && config.idleAlert() && state.idleFor(config.idleDelay()),
            config.idleVisual(), notify(config.idleNotify()));

        AdvisorSnapshot snapshot = advisor.snapshot();
        boolean notSafe = activeRound && config.notSafeAlert() && snapshot.active()
            && state.energy() >= 0 && state.energy() <= 20
            && snapshot.points() < 500 && !snapshot.fiveHundredSafe();
        if (notSafe)
        {
            alerts.dismiss(AlertKind.GO_NOW);
        }
        if (notSafe && !wasNotSafe)
        {
            alerts.fire(AlertKind.POINTS_NOT_SAFE, config.notSafeVisual(), notify(config.notSafeNotify()), 3);
        }
        wasNotSafe = notSafe;

        boolean goNow = !notSafe && activeRound && config.goNowAlert() && snapshot.active()
            && snapshot.goNow() && (tracker.roots() + tracker.kindling() > 0);
        if (goNow && !wasGoNow)
        {
            alerts.fire(AlertKind.GO_NOW, config.goNowVisual(), notify(config.goNowNotify()), 2);
        }
        wasGoNow = goNow;
    }

    private void checkRoundStart()
    {
        int timer = client.getVarbitValue(VarbitID.WINT_TRANSMIT_RESPAWNDELAY);
        if (previousTimer < 0)
        {
            previousTimer = timer;
            return;
        }
        if (previousTimer > 0 && timer == 0)
        {
            alerts.clear();
            state.resetActivity();
            tracker.resetRound();
            energyClock.reset();
            wasGoNow = false;
            wasNotSafe = false;
            advisor.resetRoundPolicy();
            advisor.update();
        }
        if (previousTimer == 0 && timer > 0)
        {
            // Drop short-lived round-only messages such as GO NOW immediately
            // when the boss dies instead of letting them linger into downtime.
            alerts.clear();
            wasGoNow = false;
            wasNotSafe = false;
        }

        if (config.alertsEnabled() && config.roundStartAlert() && timer > 0)
        {
            int now = timer * 30 / 50;
            int previous = previousTimer * 30 / 50;
            if (previous > config.roundStartSeconds() && now <= config.roundStartSeconds())
            {
                alerts.fire(AlertKind.ROUND_STARTING, config.roundStartVisual(), notify(config.roundStartNotify()));
            }
        }
        previousTimer = timer;
    }

    private void applyNativeVisibility()
    {
        boolean replace = config.replaceNativeHud();
        setHidden(WintertodtState.NATIVE_WARMTH_CHILD, replace || config.hideNativeWarmth());
        setHidden(WintertodtState.NATIVE_ENERGY_CHILD, replace || config.hideNativeEnergy());
        state.setGamePointsHidden(replace || config.hideNativePoints());
    }

    private void restoreNative()
    {
        setHidden(WintertodtState.NATIVE_WARMTH_CHILD, false);
        setHidden(WintertodtState.NATIVE_ENERGY_CHILD, false);
        state.setGamePointsHidden(false);
    }

    private void setHidden(int child, boolean hidden)
    {
        Widget widget = client.getWidget(WintertodtState.WINTERTODT_GROUP, child);
        if (widget != null)
        {
            widget.setHidden(hidden);
        }
    }

    private void applyHudPreset(HudPreset preset)
    {
        if (preset == HudPreset.CUSTOM)
        {
            return;
        }

        boolean minimal = preset == HudPreset.MINIMAL;
        boolean advanced = preset == HudPreset.ADVANCED;

        configManager.setConfiguration(WintertodtHudConfig.GROUP, "warmthEnabled", true);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "energyEnabled", true);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showCoachPanel", true);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showCurrentPoints", true);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showInventoryPoints", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showRootCounts", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showPotentialPoints", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showProjectedPoints", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showAdviceText", true);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showAdviceDetail", !minimal);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showTimeEstimate", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showSpareTime", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showExtraRootEstimate", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "showAdvancedMath", advanced);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "alertsEnabled", true);
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "replaceNativeHud", true);

        // Normal presets keep the full planner under the hood and show only points + one instruction.
    }

    private void migrateUxDefaults()
    {
        // Fresh Wintertodt+ installs receive the validated defaults once. Upgrading
        // from v0.3.0 must NOT overwrite the user's already-customized Plus config.
        String version = configManager.getConfiguration(WintertodtHudConfig.GROUP, "plusDefaultsVersion");
        if (version == null || version.isEmpty())
        {
            applyHudPreset(HudPreset.RECOMMENDED);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "idleDelay", 0);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "goNowVisual", AlertVisual.BANNER);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "notSafeVisual", AlertVisual.BANNER);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "inventoryFullVisual", AlertVisual.BANNER);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "outOfRootsVisual", AlertVisual.BANNER);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "brazierOutVisual", AlertVisual.BANNER);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "roundStartVisual", AlertVisual.BANNER);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "lowWarmthVisual", AlertVisual.SCREEN_AND_RELEVANT);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "hideNativeWarmth", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "hideNativeEnergy", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "hideNativePoints", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "hideRuneLiteOverlay", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "fletchWhileMoving", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showCoachPanel", true);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showInventoryPoints", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showRootCounts", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showPotentialPoints", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showProjectedPoints", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showTimeEstimate", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showSpareTime", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showExtraRootEstimate", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "showAdvancedMath", false);
        }
        if (version == null || version.isEmpty() || (!"0.3.9".equals(version) && !"0.3.10".equals(version)))
        {
            // v0.3.9 makes Brazier Out opt-in. The generic Idle interruption is
            // enough by default and avoids two simultaneous messages.
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "brazierOutAlert", false);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "brazierOutNotify", false);
        }
        if (!"0.3.10".equals(version))
        {
            // Detailed planner tracing is intentionally opt-in. Keep normal play
            // free of per-event debug formatting and log I/O.
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "plannerTraceEnabled", false);
        }
        configManager.setConfiguration(WintertodtHudConfig.GROUP, "plusDefaultsVersion", "0.3.10");
    }

    private boolean notify(boolean perAlert)
    {
        return config.notificationsEnabled() && perAlert;
    }

    private void applyRuneLiteOverlayPreference()
    {
        if (config.replaceNativeHud() || config.hideRuneLiteOverlay())
        {
            if (!runeLiteOverlayForced)
            {
                String current = configManager.getConfiguration(CORE_WT_GROUP, CORE_WT_OVERLAY_KEY);
                originalRuneLiteOverlay = current == null ? Boolean.TRUE : Boolean.valueOf(current);
                configManager.setConfiguration(CORE_WT_GROUP, CORE_WT_OVERLAY_KEY, false);
                runeLiteOverlayForced = true;
            }
        }
        else
        {
            restoreRuneLiteOverlay();
        }
    }

    private void restoreRuneLiteOverlay()
    {
        if (runeLiteOverlayForced)
        {
            configManager.setConfiguration(CORE_WT_GROUP, CORE_WT_OVERLAY_KEY,
                originalRuneLiteOverlay == null ? true : originalRuneLiteOverlay);
            runeLiteOverlayForced = false;
        }
    }
}
