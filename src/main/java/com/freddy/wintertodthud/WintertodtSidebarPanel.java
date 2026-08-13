package com.freddy.wintertodthud;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.TitleCaseListCellRenderer;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.SwingUtil;

/**
 * Standard-width accordion-style Wintertodt+ sidebar.
 *
 * RuneLite keeps ownership of Swing control LookAndFeel. This panel only styles
 * lightweight containers and accordion headers; it never replaces button/combo
 * UI delegates. That keeps ordinary mouse/keyboard behavior predictable.
 */
@SuppressWarnings("serial")
public class WintertodtSidebarPanel extends PluginPanel
{
    private static final int CONTROL_HEIGHT = 22;
    private static final int STANDARD_CONTROL_WIDTH = 100;
    private static final int COLOR_CONTROL_WIDTH = 86;
    private static final Set<String> ALPHA_COLOR_KEYS = new HashSet<>(Arrays.asList(
        "panelBackground", "panelBorder",
        "warmthFillColor", "warmthEmptyColor", "warmthBorderColor",
        "warmthRegenRingColor", "warmthRegenRingTrackColor",
        "warmthConsumablePreviewColor", "warmthRegenPreviewColor",
        "energyFillColor", "energyEmptyColor", "energyBorderColor",
        "idleScreenColor", "lowWarmthScreenColor", "criticalWarmthScreenColor",
        "screenFlashColor", "meterFlashColor"));

    private enum WriteMode
    {
        PLAIN,
        HUD_CUSTOM,
        METER_CUSTOM
    }

    private final ConfigManager configManager;
    private final WintertodtHudConfig config;
    private final WintertodtDebugState debugState;
    private final ColorPickerManager colorPickerManager;
    private final TitleCaseListCellRenderer titleCaseRenderer = new TitleCaseListCellRenderer();
    private final List<Runnable> configSyncers = new ArrayList<>();
    private final List<AccordionSection> majorSections = new ArrayList<>();
    private final JPanel scrollContent;
    private final JScrollPane scrollPane;
    private boolean syncing;

    // Live/debug labels.
    private final JLabel statusLabel = new JLabel("Waiting for Wintertodt");
    private final JLabel warmthLabel = new JLabel("Warmth: --");
    private final JLabel energyLabel = new JLabel("Energy: --");
    private final JLabel pointsLabel = new JLabel("Points: --");
    private final JLabel bagLabel = new JLabel("Bag: --");
    private final JLabel adviceLabel = new JLabel("Advice: --");
    private final JLabel clockLabel = new JLabel("Clock: --");
    private final JLabel stationLabel = new JLabel("Stations: --");
    private final JLabel routeLabel = new JLabel("Route: --");
    private final JLabel learningLabel = new JLabel("Observed: --");
    private final JLabel auditLabel = new JLabel("Audit: waiting");

    @Inject
    WintertodtSidebarPanel(ConfigManager configManager, WintertodtHudConfig config,
                           WintertodtDebugState debugState, ColorPickerManager colorPickerManager)
    {
        super(false);
        this.configManager = configManager;
        this.config = config;
        this.debugState = debugState;
        this.colorPickerManager = colorPickerManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        scrollContent = new ViewportWidthPanel();
        scrollContent.setOpaque(true);
        scrollContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollContent.setBorder(new EmptyBorder(8, 10, 10, 10));
        scrollContent.add(buildHeader());
        scrollContent.add(Box.createVerticalStrut(7));

        // Requested order: alerts first, then text/font/color, then geometry,
        // then HUD/data, with diagnostics last.
        scrollContent.add(major("Alerts", buildAlerts(), false));
        scrollContent.add(major("Text / Fonts / Colors", buildTextAndColors(), false));
        scrollContent.add(major("Shapes / Sizes / Colors", buildShapesAndSizes(), false));
        scrollContent.add(major("HUD / Coach", buildHudAndCoach(), false));
        scrollContent.add(major("Debug", buildDebug(), false));

        scrollPane = new JScrollPane(scrollContent,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // Do not install a custom ScrollBarUI. RuneLite's LookAndFeel supplies
        // RuneLiteScrollBarUI (7 px) and the correct track/thumb colors.
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Do not snapshot ScrollBar.width here. RuneLiteLAF owns scrollbar metrics
        // and its UI delegate; pinning a size during pre-LAF construction was what
        // produced the oversized Windows-looking scrollbar in v0.3.4.
        add(scrollPane, BorderLayout.CENTER);

        refreshConfig();
        refreshLive();
    }

    @Override
    public void onActivate()
    {
        // RuneLite calls this after the sidebar tab is visible and has its real
        // viewport width. Re-run layout here so first-open controls cannot retain
        // construction-time preferred widths.
        revalidateSidebarLayout();
        SwingUtilities.invokeLater(this::revalidateSidebarLayout);
    }

    private void revalidateSidebarLayout()
    {
        scrollContent.revalidate();
        scrollContent.repaint();
        scrollPane.getViewport().revalidate();
        scrollPane.revalidate();
        scrollPane.repaint();
    }


    static BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(ColorScheme.DARKER_GRAY_COLOR);
            g.fillRoundRect(0, 0, 16, 16, 5, 5);
            g.setColor(ColorScheme.BRAND_ORANGE);
            g.fillRoundRect(2, 3, 12, 4, 3, 3);
            g.setColor(ColorScheme.GRAND_EXCHANGE_LIMIT);
            g.fillRoundRect(2, 9, 12, 4, 3, 3);
            g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            g.drawRoundRect(0, 0, 15, 15, 5, 5);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    void refreshAsync()
    {
        SwingUtilities.invokeLater(this::refreshLive);
    }

    void refreshConfigAsync()
    {
        SwingUtilities.invokeLater(() ->
        {
            refreshConfig();
            refreshLive();
        });
    }

    private JPanel buildHeader()
    {
        JPanel p = vertical();

        JLabel title = new JLabel("Wintertodt+");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);

        JLabel hint = small("Compact controls · Alt-drag HUD pieces to move");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(Box.createVerticalStrut(2));
        p.add(hint);
        p.add(Box.createVerticalStrut(7));
        p.add(row("Goal", combo("strategyMode", StrategyMode.values(), config::strategyMode, WriteMode.PLAIN)));
        return p;
    }

    private JPanel buildAlerts()
    {
        JPanel root = vertical();

        JPanel master = vertical();
        master.add(check("Enable alerts", "alertsEnabled", config::alertsEnabled, WriteMode.HUD_CUSTOM));
        master.add(check("Screen alerts", "screenAlertsEnabled", config::screenAlertsEnabled, WriteMode.HUD_CUSTOM));
        master.add(check("RuneLite notifications", "notificationsEnabled", config::notificationsEnabled, WriteMode.HUD_CUSTOM));
        root.add(card(master));

        JPanel idle = vertical();
        idle.add(check("Enabled", "idleAlert", config::idleAlert, WriteMode.HUD_CUSTOM));
        idle.add(row("After sec", step("idleDelay", 0, 30, config::idleDelay, WriteMode.HUD_CUSTOM)));
        idle.add(row("Where", combo("idleVisual", AlertVisual.values(), config::idleVisual, WriteMode.HUD_CUSTOM)));
        idle.add(check("Notify", "idleNotify", config::idleNotify, WriteMode.HUD_CUSTOM));
        idle.add(row("Screen color", color("idleScreenColor", config::idleScreenColor, WriteMode.HUD_CUSTOM)));
        root.add(nested("Idle", idle, false));

        JPanel low = vertical();
        low.add(check("Enabled", "lowWarmthAlert", config::lowWarmthAlert, WriteMode.HUD_CUSTOM));
        low.add(row("Warn at", step("lowWarmthThreshold", 1, 99, config::lowWarmthThreshold, WriteMode.HUD_CUSTOM)));
        low.add(row("Where", combo("lowWarmthVisual", AlertVisual.values(), config::lowWarmthVisual, WriteMode.HUD_CUSTOM)));
        low.add(check("Notify", "lowWarmthNotify", config::lowWarmthNotify, WriteMode.HUD_CUSTOM));
        low.add(row("Screen color", color("lowWarmthScreenColor", config::lowWarmthScreenColor, WriteMode.HUD_CUSTOM)));
        root.add(nested("Low Warmth", low, false));

        JPanel critical = vertical();
        critical.add(check("Enabled", "criticalWarmthAlert", config::criticalWarmthAlert, WriteMode.HUD_CUSTOM));
        critical.add(row("Eat at", step("criticalWarmthThreshold", 1, 99, config::criticalWarmthThreshold, WriteMode.HUD_CUSTOM)));
        critical.add(row("Where", combo("criticalWarmthVisual", AlertVisual.values(), config::criticalWarmthVisual, WriteMode.HUD_CUSTOM)));
        critical.add(check("Notify", "criticalWarmthNotify", config::criticalWarmthNotify, WriteMode.HUD_CUSTOM));
        critical.add(row("Screen color", color("criticalWarmthScreenColor", config::criticalWarmthScreenColor, WriteMode.HUD_CUSTOM)));
        root.add(nested("Eat Now", critical, false));

        JPanel end = vertical();
        end.add(check("Low Energy", "lowEnergyAlert", config::lowEnergyAlert, WriteMode.HUD_CUSTOM));
        end.add(row("Low Energy %", step("lowEnergyThreshold", 1, 99, config::lowEnergyThreshold, WriteMode.HUD_CUSTOM)));
        end.add(row("Low Energy visual", combo("lowEnergyVisual", AlertVisual.values(), config::lowEnergyVisual, WriteMode.HUD_CUSTOM)));
        end.add(check("Low Energy notify", "lowEnergyNotify", config::lowEnergyNotify, WriteMode.HUD_CUSTOM));
        end.add(check("Go now", "goNowAlert", config::goNowAlert, WriteMode.HUD_CUSTOM));
        end.add(row("Go now visual", combo("goNowVisual", AlertVisual.values(), config::goNowVisual, WriteMode.HUD_CUSTOM)));
        end.add(check("Go now notify", "goNowNotify", config::goNowNotify, WriteMode.HUD_CUSTOM));
        end.add(check("500 at risk", "notSafeAlert", config::notSafeAlert, WriteMode.HUD_CUSTOM));
        end.add(row("500-risk visual", combo("notSafeVisual", AlertVisual.values(), config::notSafeVisual, WriteMode.HUD_CUSTOM)));
        end.add(check("500-risk notify", "notSafeNotify", config::notSafeNotify, WriteMode.HUD_CUSTOM));
        root.add(nested("Round / Emergency", end, false));

        JPanel events = vertical();
        events.add(check("Inventory full", "inventoryFullAlert", config::inventoryFullAlert, WriteMode.HUD_CUSTOM));
        events.add(check("Inventory-full notify", "inventoryFullNotify", config::inventoryFullNotify, WriteMode.HUD_CUSTOM));
        events.add(check("Out of roots", "outOfRootsAlert", config::outOfRootsAlert, WriteMode.HUD_CUSTOM));
        events.add(check("Out-of-roots notify", "outOfRootsNotify", config::outOfRootsNotify, WriteMode.HUD_CUSTOM));
        events.add(check("Brazier out", "brazierOutAlert", config::brazierOutAlert, WriteMode.HUD_CUSTOM));
        events.add(check("Brazier-out notify", "brazierOutNotify", config::brazierOutNotify, WriteMode.HUD_CUSTOM));
        events.add(check("Action interrupted", "interruptAlert", config::interruptAlert, WriteMode.HUD_CUSTOM));
        events.add(check("Interrupted notify", "interruptNotify", config::interruptNotify, WriteMode.HUD_CUSTOM));
        events.add(check("Round starting", "roundStartAlert", config::roundStartAlert, WriteMode.HUD_CUSTOM));
        events.add(row("Start warning sec", step("roundStartSeconds", 1, 60, config::roundStartSeconds, WriteMode.HUD_CUSTOM)));
        events.add(check("Round-start notify", "roundStartNotify", config::roundStartNotify, WriteMode.HUD_CUSTOM));
        root.add(nested("Other Events", events, false));

        return root;
    }

    private JPanel buildTextAndColors()
    {
        JPanel root = vertical();

        JPanel coach = vertical();
        coach.add(row("Font", text("panelFontName", config::panelFontName, WriteMode.HUD_CUSTOM)));
        coach.add(row("Size", step("panelFontSize", 10, 32, config::panelFontSize, WriteMode.HUD_CUSTOM)));
        coach.add(check("Bold", "panelBold", config::panelBold, WriteMode.HUD_CUSTOM));
        coach.add(row("Text", color("panelText", config::panelText, WriteMode.HUD_CUSTOM)));
        coach.add(row("Good", color("panelGood", config::panelGood, WriteMode.HUD_CUSTOM)));
        coach.add(row("Warning", color("panelWarn", config::panelWarn, WriteMode.HUD_CUSTOM)));
        coach.add(row("Danger", color("panelDanger", config::panelDanger, WriteMode.HUD_CUSTOM)));
        coach.add(row("Background", color("panelBackground", config::panelBackground, WriteMode.HUD_CUSTOM)));
        coach.add(row("Border", color("panelBorder", config::panelBorder, WriteMode.HUD_CUSTOM)));
        root.add(nested("Coach Text / Colors", coach, false));

        JPanel warmth = vertical();
        warmth.add(row("Text mode", combo("warmthTextMode", MeterTextMode.values(), config::warmthTextMode, WriteMode.METER_CUSTOM)));
        warmth.add(row("Font", text("warmthFontName", config::warmthFontName, WriteMode.METER_CUSTOM)));
        warmth.add(row("Size", step("warmthFontSize", 8, 72, config::warmthFontSize, WriteMode.METER_CUSTOM)));
        warmth.add(check("Bold", "warmthBold", config::warmthBold, WriteMode.METER_CUSTOM));
        warmth.add(row("Text color", color("warmthTextColor", config::warmthTextColor, WriteMode.METER_CUSTOM)));
        warmth.add(row("Prefix", text("warmthPrefix", config::warmthPrefix, WriteMode.METER_CUSTOM)));
        warmth.add(row("Suffix", text("warmthSuffix", config::warmthSuffix, WriteMode.METER_CUSTOM)));
        warmth.add(row("Text X", step("warmthTextX", -300, 300, config::warmthTextX, WriteMode.METER_CUSTOM)));
        warmth.add(row("Text Y", step("warmthTextY", -300, 300, config::warmthTextY, WriteMode.METER_CUSTOM)));
        root.add(nested("Warmth Text", warmth, false));

        JPanel energy = vertical();
        energy.add(row("Text mode", combo("energyTextMode", MeterTextMode.values(), config::energyTextMode, WriteMode.METER_CUSTOM)));
        energy.add(row("Font", text("energyFontName", config::energyFontName, WriteMode.METER_CUSTOM)));
        energy.add(row("Size", step("energyFontSize", 8, 72, config::energyFontSize, WriteMode.METER_CUSTOM)));
        energy.add(check("Bold", "energyBold", config::energyBold, WriteMode.METER_CUSTOM));
        energy.add(row("Text color", color("energyTextColor", config::energyTextColor, WriteMode.METER_CUSTOM)));
        energy.add(row("Prefix", text("energyPrefix", config::energyPrefix, WriteMode.METER_CUSTOM)));
        energy.add(row("Suffix", text("energySuffix", config::energySuffix, WriteMode.METER_CUSTOM)));
        energy.add(row("Text X", step("energyTextX", -300, 300, config::energyTextX, WriteMode.METER_CUSTOM)));
        energy.add(row("Text Y", step("energyTextY", -300, 300, config::energyTextY, WriteMode.METER_CUSTOM)));
        root.add(nested("Energy Text", energy, false));

        JPanel alert = vertical();
        alert.add(check("Show large alert text", "showAlertText", config::showAlertText, WriteMode.HUD_CUSTOM));
        alert.add(row("Font", text("alertFontName", config::alertFontName, WriteMode.HUD_CUSTOM)));
        alert.add(row("Size", step("alertFontSize", 12, 96, config::alertFontSize, WriteMode.HUD_CUSTOM)));
        alert.add(row("Screen flash", color("screenFlashColor", config::screenFlashColor, WriteMode.HUD_CUSTOM)));
        alert.add(row("Meter flash", color("meterFlashColor", config::meterFlashColor, WriteMode.HUD_CUSTOM)));
        alert.add(row("Flash ms", step("flashPeriodMs", 100, 3000, config::flashPeriodMs, WriteMode.HUD_CUSTOM)));
        alert.add(row("Event sec", step("eventDuration", 1, 15, config::eventDuration, WriteMode.HUD_CUSTOM)));
        root.add(nested("Alert Text / Flash", alert, false));

        return root;
    }

    private JPanel buildShapesAndSizes()
    {
        JPanel root = vertical();

        JPanel warmth = vertical();
        warmth.add(row("Width", step("warmthWidth", 24, 600, config::warmthWidth, WriteMode.METER_CUSTOM)));
        warmth.add(row("Height", step("warmthHeight", 12, 300, config::warmthHeight, WriteMode.METER_CUSTOM)));
        warmth.add(row("Shape", combo("warmthShape", MeterShape.values(), config::warmthShape, WriteMode.METER_CUSTOM)));
        warmth.add(row("Fill", combo("warmthFillDirection", FillDirection.values(), config::warmthFillDirection, WriteMode.METER_CUSTOM)));
        warmth.add(row("Fill color", color("warmthFillColor", config::warmthFillColor, WriteMode.METER_CUSTOM)));
        warmth.add(row("Empty color", color("warmthEmptyColor", config::warmthEmptyColor, WriteMode.METER_CUSTOM)));
        warmth.add(row("Border color", color("warmthBorderColor", config::warmthBorderColor, WriteMode.METER_CUSTOM)));
        warmth.add(row("Border px", step("warmthBorderWidth", 0, 12, config::warmthBorderWidth, WriteMode.METER_CUSTOM)));
        root.add(nested("Warmth Meter", warmth, false));

        JPanel preview = vertical();
        preview.add(check("Regen ring", "warmthRegenRingEnabled", config::warmthRegenRingEnabled, WriteMode.METER_CUSTOM));
        preview.add(row("Ring width", step("warmthRegenRingWidth", 1, 6, config::warmthRegenRingWidth, WriteMode.METER_CUSTOM)));
        preview.add(row("Ring color", color("warmthRegenRingColor", config::warmthRegenRingColor, WriteMode.METER_CUSTOM)));
        preview.add(row("Ring track", color("warmthRegenRingTrackColor", config::warmthRegenRingTrackColor, WriteMode.METER_CUSTOM)));
        preview.add(check("Show regen amount", "warmthRegenAmountText", config::warmthRegenAmountText, WriteMode.METER_CUSTOM));
        preview.add(check("Food preview", "warmthConsumablePreviewEnabled", config::warmthConsumablePreviewEnabled, WriteMode.METER_CUSTOM));
        preview.add(row("Food preview", color("warmthConsumablePreviewColor", config::warmthConsumablePreviewColor, WriteMode.METER_CUSTOM)));
        preview.add(check("Passive preview", "warmthRegenPreviewEnabled", config::warmthRegenPreviewEnabled, WriteMode.METER_CUSTOM));
        preview.add(row("Passive preview", color("warmthRegenPreviewColor", config::warmthRegenPreviewColor, WriteMode.METER_CUSTOM)));
        root.add(nested("Warmth Preview / Regen", preview, false));

        JPanel energy = vertical();
        energy.add(row("Width", step("energyWidth", 24, 600, config::energyWidth, WriteMode.METER_CUSTOM)));
        energy.add(row("Height", step("energyHeight", 12, 300, config::energyHeight, WriteMode.METER_CUSTOM)));
        energy.add(row("Shape", combo("energyShape", MeterShape.values(), config::energyShape, WriteMode.METER_CUSTOM)));
        energy.add(row("Fill", combo("energyFillDirection", FillDirection.values(), config::energyFillDirection, WriteMode.METER_CUSTOM)));
        energy.add(row("Fill color", color("energyFillColor", config::energyFillColor, WriteMode.METER_CUSTOM)));
        energy.add(row("Empty color", color("energyEmptyColor", config::energyEmptyColor, WriteMode.METER_CUSTOM)));
        energy.add(row("Border color", color("energyBorderColor", config::energyBorderColor, WriteMode.METER_CUSTOM)));
        energy.add(row("Border px", step("energyBorderWidth", 0, 12, config::energyBorderWidth, WriteMode.METER_CUSTOM)));
        energy.add(check("Next-round countdown", "energyNextRoundTimerEnabled", config::energyNextRoundTimerEnabled, WriteMode.METER_CUSTOM));
        root.add(nested("Energy Meter", energy, false));

        JPanel coach = vertical();
        coach.add(row("Panel width", step("panelWidth", 120, 420, config::panelWidth, WriteMode.HUD_CUSTOM)));
        root.add(nested("Coach Geometry", coach, false));

        return root;
    }

    private JPanel buildHudAndCoach()
    {
        JPanel root = vertical();

        JPanel setup = vertical();
        setup.add(row("HUD preset", combo("hudPreset", HudPreset.values(), config::hudPreset, WriteMode.PLAIN)));
        setup.add(row("Meter preset", combo("preset", MeterPreset.values(), config::preset, WriteMode.PLAIN)));
        setup.add(check("Warmth meter", "warmthEnabled", config::warmthEnabled, WriteMode.HUD_CUSTOM));
        setup.add(check("Energy meter", "energyEnabled", config::energyEnabled, WriteMode.HUD_CUSTOM));
        setup.add(check("Coach", "showCoachPanel", config::showCoachPanel, WriteMode.HUD_CUSTOM));
        setup.add(check("Only at Wintertodt", "onlyInsideWintertodt", config::onlyInsideWintertodt, WriteMode.HUD_CUSTOM));
        setup.add(check("Hide original HUD", "replaceNativeHud", config::replaceNativeHud, WriteMode.HUD_CUSTOM));
        JButton recommended = new JButton("Use recommended setup");
        recommended.setFocusable(false);
        recommended.addActionListener(e -> set("hudPreset", HudPreset.RECOMMENDED));
        recommended.setAlignmentX(Component.LEFT_ALIGNMENT);
        setup.add(Box.createVerticalStrut(4));
        setup.add(recommended);
        root.add(nested("General", setup, false));

        JPanel data = vertical();
        data.add(check("Current points", "showCurrentPoints", config::showCurrentPoints, WriteMode.HUD_CUSTOM));
        data.add(check("In-bag points", "showInventoryPoints", config::showInventoryPoints, WriteMode.HUD_CUSTOM));
        data.add(check("Roots + kindling", "showRootCounts", config::showRootCounts, WriteMode.HUD_CUSTOM));
        data.add(check("If all fletched", "showPotentialPoints", config::showPotentialPoints, WriteMode.HUD_CUSTOM));
        data.add(check("After-plan points", "showProjectedPoints", config::showProjectedPoints, WriteMode.HUD_CUSTOM));
        data.add(check("Main advice", "showAdviceText", config::showAdviceText, WriteMode.HUD_CUSTOM));
        data.add(check("Second advice line", "showAdviceDetail", config::showAdviceDetail, WriteMode.HUD_CUSTOM));
        data.add(check("Time estimate", "showTimeEstimate", config::showTimeEstimate, WriteMode.HUD_CUSTOM));
        data.add(check("Spare time", "showSpareTime", config::showSpareTime, WriteMode.HUD_CUSTOM));
        data.add(check("More-roots estimate", "showExtraRootEstimate", config::showExtraRootEstimate, WriteMode.HUD_CUSTOM));
        data.add(check("Tick details", "showAdvancedMath", config::showAdvancedMath, WriteMode.HUD_CUSTOM));
        root.add(nested("Coach Information", data, false));

        JPanel original = vertical();
        original.add(check("Hide game Warmth", "hideNativeWarmth", config::hideNativeWarmth, WriteMode.HUD_CUSTOM));
        original.add(check("Hide game Energy", "hideNativeEnergy", config::hideNativeEnergy, WriteMode.HUD_CUSTOM));
        original.add(check("Hide game Points", "hideNativePoints", config::hideNativePoints, WriteMode.HUD_CUSTOM));
        original.add(check("Hide RuneLite Wintertodt panel", "hideRuneLiteOverlay", config::hideRuneLiteOverlay, WriteMode.HUD_CUSTOM));
        root.add(nested("Original HUD", original, false));

        return root;
    }

    private JPanel buildDebug()
    {
        JPanel root = vertical();

        JPanel live = vertical();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        adviceLabel.setFont(adviceLabel.getFont().deriveFont(Font.BOLD));
        live.add(statusLabel);
        live.add(Box.createVerticalStrut(4));
        live.add(warmthLabel);
        live.add(energyLabel);
        live.add(pointsLabel);
        live.add(bagLabel);
        live.add(Box.createVerticalStrut(4));
        live.add(adviceLabel);
        root.add(nested("Live", live, false));

        JPanel calc = vertical();
        calc.add(clockLabel);
        calc.add(stationLabel);
        calc.add(routeLabel);
        calc.add(learningLabel);
        calc.add(Box.createVerticalStrut(6));
        calc.add(check("Assume running", "assumeRunning", config::assumeRunning, WriteMode.HUD_CUSTOM));
        calc.add(row("Fallback tiles", step("fallbackBrazierTiles", 1, 30, config::fallbackBrazierTiles, WriteMode.HUD_CUSTOM)));
        root.add(nested("Clock / Route", calc, false));

        JPanel audit = vertical();
        audit.add(check("Planner trace", "plannerTraceEnabled", config::plannerTraceEnabled, WriteMode.HUD_CUSTOM));
        audit.add(Box.createVerticalStrut(5));
        audit.add(auditLabel);
        audit.add(Box.createVerticalStrut(5));
        audit.add(small("Off by default. Use RUN_DEBUG.bat, then enable this only while collecting a planner trace."));
        root.add(nested("Session Trace", audit, false));

        return root;
    }

    private void refreshConfig()
    {
        syncing = true;
        try
        {
            for (Runnable syncer : configSyncers)
            {
                syncer.run();
            }
        }
        finally
        {
            syncing = false;
        }
    }

    private void refreshLive()
    {
        WintertodtDebugSnapshot live = debugState.snapshot();
        boolean in = live.inWintertodt();
        statusLabel.setText(in ? "Wintertodt active" : "Waiting for Wintertodt");
        statusLabel.setForeground(in ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.TEXT_COLOR);
        warmthLabel.setText("Warmth: " + value(in ? live.warmth() : -1, "%"));
        energyLabel.setText("Energy: " + value(live.energy(), "%"));
        pointsLabel.setText("Points: " + (in ? Integer.toString(live.points()) : "--"));
        bagLabel.setText(in ? "Bag: " + live.roots() + " roots · " + live.kindling() + " kindling" : "Bag: --");

        if (in && live.action() != AdvisorAction.WAITING)
        {
            String text = sentence(live.advice());
            if (!live.adviceDetail().isEmpty())
            {
                text += " · " + live.adviceDetail();
            }
            adviceLabel.setText("Advice: " + text);
            switch (live.action().level())
            {
                case DANGER:
                    adviceLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
                    break;
                case WARN:
                    adviceLabel.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
                    break;
                case GOOD:
                    adviceLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
                    break;
                default:
                    adviceLabel.setForeground(ColorScheme.TEXT_COLOR);
                    break;
            }
        }
        else
        {
            adviceLabel.setText("Advice: --");
            adviceLabel.setForeground(ColorScheme.TEXT_COLOR);
        }

        if (in)
        {
            if (live.earliestTicks() >= 0)
            {
                if (live.earliestTicks() == live.latestTicks())
                {
                    clockLabel.setText("Clock: LOCKED · ~" + seconds(live.earliestTicks()) + " sec");
                }
                else
                {
                    clockLabel.setText("Clock: ~" + seconds(live.earliestTicks()) + "–"
                        + seconds(live.latestTicks()) + " sec · " + live.knownPhases() + "/"
                        + Math.max(0, live.activeDrainers()) + " phases");
                }
            }
            else
            {
                clockLabel.setText("Clock: " + sentence(live.clockStatus()));
            }
            stationLabel.setText("Stations: " + live.stationText());
            routeLabel.setText("Route: brazier " + (live.brazierAnchor() ? "✓" : "—")
                + " · roots " + (live.rootAnchor() ? "✓" : "—")
                + (live.rootToBrazierTiles() >= 0 ? " · " + live.rootToBrazierTiles() + " tiles" : ""));
            learningLabel.setText("Observed: " + live.sampleCounts());
        }
        else
        {
            clockLabel.setText("Clock: --");
            stationLabel.setText("Stations: --");
            routeLabel.setText("Route: --");
            learningLabel.setText("Observed: --");
        }

        auditLabel.setText("Trace: " + live.auditState());
        auditLabel.setToolTipText("Planner trace is opt-in and only records when RuneLite debug logging is active.");
    }

    private AccordionSection major(String title, JPanel body, boolean open)
    {
        AccordionSection section = new AccordionSection(title, body, open, true);
        majorSections.add(section);
        return section;
    }

    private AccordionSection nested(String title, JPanel body, boolean open)
    {
        return new AccordionSection(title, body, open, false);
    }

    private final class AccordionSection extends JPanel
    {
        private final JPanel header = new JPanel(new BorderLayout());
        private final JButton toggle = new JButton();
        private final JLabel titleLabel;
        private final JPanel body;
        private final boolean major;
        private boolean open;

        AccordionSection(String title, JPanel body, boolean open, boolean major)
        {
            super(new BorderLayout());
            this.open = open;
            this.major = major;

            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 2400));
            setBorder(new EmptyBorder(0, 0, major ? 4 : 2, 0));

            Color headerBackground = major ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR;
            Color separator = major ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.BORDER_COLOR;
            header.setOpaque(true);
            header.setBackground(headerBackground);
            header.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, separator),
                new EmptyBorder(major ? 5 : 4, 0, major ? 4 : 3, 0)));

            SwingUtil.removeButtonDecorations(toggle);
            toggle.setFocusable(false);
            toggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            toggle.setPreferredSize(new Dimension(20, major ? 25 : 22));
            toggle.setBorder(new EmptyBorder(0, 0, 0, 5));
            toggle.addActionListener(e -> setOpen(!this.open));
            header.add(toggle, BorderLayout.WEST);

            titleLabel = new JLabel(title);
            titleLabel.setForeground(major ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
            titleLabel.setFont(major
                ? FontManager.getRunescapeBoldFont()
                : FontManager.getDefaultBoldFont().deriveFont(12f));
            header.add(titleLabel, BorderLayout.CENTER);

            MouseAdapter headerMouse = new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    setOpen(!AccordionSection.this.open);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    header.setBackground(major
                        ? ColorScheme.DARK_GRAY_HOVER_COLOR
                        : ColorScheme.DARKER_GRAY_HOVER_COLOR);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    header.setBackground(major
                        ? ColorScheme.DARK_GRAY_COLOR
                        : ColorScheme.DARKER_GRAY_COLOR);
                }
            };
            header.addMouseListener(headerMouse);
            titleLabel.addMouseListener(headerMouse);

            JPanel bodyWrap = new JPanel(new BorderLayout());
            bodyWrap.setOpaque(true);
            bodyWrap.setBackground(major ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
            bodyWrap.setBorder(new EmptyBorder(major ? 5 : 6, major ? 0 : 8, major ? 5 : 7, major ? 0 : 8));
            bodyWrap.add(body, BorderLayout.CENTER);
            this.body = bodyWrap;

            add(header, BorderLayout.NORTH);
            add(this.body, BorderLayout.CENTER);

            // Apply the constructor's initial accordion state immediately.
            // v0.3.6 updated the arrow/state but left every body visible until
            // the first close action, producing CLOSED arrow + VISIBLE body.
            this.body.setVisible(open);
            updateHeader();
        }

        void setOpen(boolean open)
        {
            if (major && open)
            {
                for (AccordionSection other : majorSections)
                {
                    if (other != this)
                    {
                        other.setOpenInternal(false);
                    }
                }
            }
            setOpenInternal(open);
            revalidate();
            repaint();
            revalidateSidebarLayout();
            SwingUtilities.invokeLater(WintertodtSidebarPanel.this::revalidateSidebarLayout);
        }

        private void setOpenInternal(boolean open)
        {
            this.open = open;
            body.setVisible(open);
            updateHeader();
        }

        private void updateHeader()
        {
            toggle.setText(open ? "▾" : "▸");
            toggle.setToolTipText(open ? "Collapse" : "Expand");
            titleLabel.setToolTipText(open ? "Collapse " + titleLabel.getText() : "Expand " + titleLabel.getText());
        }
    }

    private JCheckBox check(String label, String key, Supplier<Boolean> getter, WriteMode mode)
    {
        // Keep RuneLiteCheckBoxUI installed by the client LookAndFeel.
        JCheckBox box = new JCheckBox(label);
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.addActionListener(e ->
        {
            if (!syncing)
            {
                write(key, box.isSelected(), mode);
            }
        });
        configSyncers.add(() -> box.setSelected(Boolean.TRUE.equals(getter.get())));
        return box;
    }

    private IntStepper step(String key, int min, int max, Supplier<Integer> getter, WriteMode mode)
    {
        IntStepper stepper = new IntStepper(getter.get(), min, max);
        stepper.addChangeListener(e ->
        {
            if (!syncing)
            {
                write(key, stepper.getValue(), mode);
            }
        });
        configSyncers.add(() -> stepper.setValue(getter.get()));
        return stepper;
    }

    private <T> JComboBox<T> combo(String key, T[] values, Supplier<T> getter, WriteMode mode)
    {
        JComboBox<T> box = new JComboBox<>(values);
        box.setFocusable(false);
        // Match RuneLite's native config enum controls.
        box.setRenderer(titleCaseRenderer);
        box.setPreferredSize(new Dimension(STANDARD_CONTROL_WIDTH, CONTROL_HEIGHT));
        box.addActionListener(e ->
        {
            Object selected = box.getSelectedItem();
            box.setToolTipText(selected == null ? null : selected.toString().replace('_', ' '));
            if (!syncing && selected != null)
            {
                write(key, selected, mode);
            }
        });
        configSyncers.add(() ->
        {
            box.setSelectedItem(getter.get());
            Object selected = box.getSelectedItem();
            box.setToolTipText(selected == null ? null : selected.toString().replace('_', ' '));
        });
        return box;
    }

    private JTextField text(String key, Supplier<String> getter, WriteMode mode)
    {
        // Leave the text field UI/background/border to RuneLiteLAF.
        JTextField field = new JTextField();
        field.setPreferredSize(new Dimension(STANDARD_CONTROL_WIDTH, 24));
        Runnable commit = () ->
        {
            if (!syncing)
            {
                write(key, field.getText(), mode);
            }
        };
        field.addActionListener(e -> commit.run());
        field.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent e)
            {
                commit.run();
            }
        });
        configSyncers.add(() ->
        {
            String value = getter.get();
            if (!field.hasFocus())
            {
                field.setText(value == null ? "" : value);
            }
        });
        return field;
    }

    private ColorJButton color(String key, Supplier<Color> getter, WriteMode mode)
    {
        boolean alphaHidden = !ALPHA_COLOR_KEYS.contains(key);
        Color existing = getter.get();
        if (existing == null)
        {
            existing = Color.BLACK;
        }

        ColorJButton button = new ColorJButton(colorText(existing, alphaHidden), existing);
        button.setFocusable(false);
        button.setPreferredSize(new Dimension(COLOR_CONTROL_WIDTH, CONTROL_HEIGHT));
        button.addActionListener(e ->
        {
            Color current = getter.get();
            if (current == null)
            {
                current = button.getColor();
            }

            RuneliteColorPicker picker = colorPickerManager.create(
                WintertodtSidebarPanel.this, current, prettyKey(key), alphaHidden);
            picker.setLocationRelativeTo(button);
            picker.setOnColorChange(c ->
            {
                button.setColor(c);
                button.setText(colorText(c, alphaHidden));
            });
            picker.setOnClose(c ->
            {
                if (c != null)
                {
                    write(key, c, mode);
                }
            });
            picker.setVisible(true);
        });
        configSyncers.add(() -> setColorButton(button, getter.get(), alphaHidden));
        return button;
    }

    private void write(String key, Object value, WriteMode mode)
    {
        set(key, value);
        if (mode == WriteMode.METER_CUSTOM)
        {
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "preset", MeterPreset.CUSTOM);
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "hudPreset", HudPreset.CUSTOM);
        }
        else if (mode == WriteMode.HUD_CUSTOM && !"hudPreset".equals(key))
        {
            configManager.setConfiguration(WintertodtHudConfig.GROUP, "hudPreset", HudPreset.CUSTOM);
        }
    }

    private void set(String key, Object value)
    {
        if (value != null)
        {
            configManager.setConfiguration(WintertodtHudConfig.GROUP, key, value);
        }
    }


    /**
     * Scroll content which always follows the viewport width. This prevents
     * BoxLayout children from keeping an oversized construction-time preferred
     * width and being clipped until a later collapse/reopen revalidation.
     */
    private static final class ViewportWidthPanel extends JPanel implements Scrollable
    {
        ViewportWidthPanel()
        {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private static JPanel vertical()
    {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JPanel card(JPanel content)
    {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        p.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
            new EmptyBorder(6, 8, 6, 8)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private static JPanel row(String label, Component control)
    {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(2, 0, 2, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel l = new JLabel(label);
        l.setForeground(ColorScheme.TEXT_COLOR);
        p.add(l, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(control);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private static JLabel small(String text)
    {
        JLabel l = new JLabel("<html>" + text + "</html>");
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setFont(FontManager.getRunescapeSmallFont());
        return l;
    }

    private static void setColorButton(ColorJButton button, Color color, boolean alphaHidden)
    {
        if (color == null)
        {
            return;
        }
        button.setColor(color);
        button.setText(colorText(color, alphaHidden));
        button.setToolTipText("RGBA " + color.getRed() + ", " + color.getGreen() + ", "
            + color.getBlue() + ", " + color.getAlpha());
    }

    private static String colorText(Color color, boolean alphaHidden)
    {
        // Keep the standard-width sidebar compact. Alpha-enabled colors still
        // show opacity in RuneLite's checkerboard swatch and the RGBA tooltip.
        return "#" + ColorUtil.colorToHexCode(color).toUpperCase();
    }

    private static String prettyKey(String key)
    {
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        if (spaced.isEmpty())
        {
            return "Choose color";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String value(int value, String suffix)
    {
        return value < 0 ? "--" : value + suffix;
    }

    private static int seconds(int ticks)
    {
        return Math.max(0, (int)Math.ceil(ticks * 0.6));
    }

    private static String sentence(String text)
    {
        if (text == null || text.isEmpty())
        {
            return "--";
        }
        return text.replace('_', ' ');
    }
}
