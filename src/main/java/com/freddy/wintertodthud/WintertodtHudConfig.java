package com.freddy.wintertodthud;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(WintertodtHudConfig.GROUP)
public interface WintertodtHudConfig extends Config
{
    String GROUP = "wintertodtPlus";

    @ConfigSection(name="Setup", description="The important controls", position=0)
    String generalSection = "setup";
    @ConfigSection(name="Alerts", description="Important reminders", position=10)
    String alertsSection = "alerts";
    @ConfigSection(name="Look", description="Quick appearance", position=20, closedByDefault=true)
    String lookSection = "look";
    // Hidden fine-tuning keys live outside the normal RuneLite config. Use the Wintertodt sidebar for them.
    String advancedSection = "";

    // Legacy aliases keep the internal settings organized without exposing dozens of rows.
    String pointsSection = advancedSection;
    String advisorSection = advancedSection;
    String panelStyleSection = advancedSection;
    String warmthSection = advancedSection;
    String energySection = advancedSection;
    String alertAppearanceSection = advancedSection;
    String nativeSection = advancedSection;

    // General
    @ConfigItem(keyName="hudPreset", name="Preset", description="Choose a simple starting layout", position=0, section=generalSection)
    default HudPreset hudPreset(){ return HudPreset.RECOMMENDED; }
    @ConfigItem(keyName="strategyMode", name="Goal", description="Maximize Firemaking XP or Wintertodt points", position=1, section=generalSection)
    default StrategyMode strategyMode(){ return StrategyMode.MAX_FM_XP; }
    @ConfigItem(keyName="plannerTraceEnabled", name="Planner trace", description="Write detailed Wintertodt+ planner diagnostics to RuneLite debug logging", position=999, section=advancedSection, hidden=true)
    default boolean plannerTraceEnabled(){ return false; }
    @ConfigItem(keyName="preset", name="Meter style", description="Quick style for Warmth and Energy", position=0, section=lookSection)
    default MeterPreset preset(){ return MeterPreset.COMPACT; }
    @ConfigItem(keyName="warmthEnabled", name="Warmth", description="Show custom Warmth", position=10, section=generalSection)
    default boolean warmthEnabled(){ return true; }
    @ConfigItem(keyName="energyEnabled", name="Energy", description="Show custom Energy", position=11, section=generalSection)
    default boolean energyEnabled(){ return true; }
    @ConfigItem(keyName="onlyInsideWintertodt", name="Only at Wintertodt", description="Hide custom HUD outside Wintertodt", position=4, section=advancedSection, hidden=true)
    default boolean onlyInsideWintertodt(){ return true; }

    @ConfigItem(keyName="alertsEnabled", name="Alerts", description="Enable Wintertodt+ alerts", position=14, section=generalSection)
    default boolean alertsEnabled(){ return true; }
    @ConfigItem(keyName="screenAlertsEnabled", name="Screen alerts", description="Allow screen alerts such as Low Warmth and Idle", position=0, section=alertsSection)
    default boolean screenAlertsEnabled(){ return true; }
    @ConfigItem(keyName="notificationsEnabled", name="RuneLite notifications", description="Allow RuneLite notifications from this plugin", position=1, section=alertsSection)
    default boolean notificationsEnabled(){ return true; }
    @ConfigItem(keyName="replaceNativeHud", name="Hide original HUD", description="Hide the game Warmth, Energy and Points plus RuneLite's Wintertodt overlay", position=15, section=generalSection)
    default boolean replaceNativeHud(){ return true; }

    // Coach (points + advice share one movable box)
    @ConfigItem(keyName="showCoachPanel", name="Coach", description="Show points and simple live advice", position=12, section=generalSection)
    default boolean showCoachPanel(){ return true; }
    // Legacy keys are kept hidden so old profiles migrate cleanly.
    @ConfigItem(keyName="showPointsPanel", name="Legacy points panel", description="Legacy setting", position=0, section=advancedSection, hidden=true)
    default boolean showPointsPanel(){ return true; }
    @ConfigItem(keyName="showCurrentPoints", name="Show current points", description="Show current round points", position=1, section=advancedSection, hidden=true)
    default boolean showCurrentPoints(){ return true; }
    @ConfigItem(keyName="showInventoryPoints", name="Show inventory points", description="Show points stored in your inventory", position=2, section=advancedSection, hidden=true)
    default boolean showInventoryPoints(){ return true; }
    @ConfigItem(keyName="showRootCounts", name="Show roots", description="Show root and kindling counts", position=3, section=advancedSection, hidden=true)
    default boolean showRootCounts(){ return true; }
    @ConfigItem(keyName="showPotentialPoints", name="Show potential", description="Show points if remaining roots are fletched", position=4, section=advancedSection, hidden=true)
    default boolean showPotentialPoints(){ return true; }
    @ConfigItem(keyName="showProjectedPoints", name="Show projected", description="Show total after the recommended plan", position=5, section=advancedSection, hidden=true)
    default boolean showProjectedPoints(){ return true; }

    // Advisor
    @ConfigItem(keyName="showAdvisorPanel", name="Legacy advice panel", description="Legacy setting", position=0, section=advancedSection, hidden=true)
    default boolean showAdvisorPanel(){ return true; }
    @ConfigItem(keyName="safetyMode", name="Legacy timing safety", description="Legacy compatibility setting; v0.2.12 uses fixed 5-tick reward and 1-tick XP deadlines.", position=1, section=generalSection, hidden=true)
    default SafetyMode safetyMode(){ return SafetyMode.NORMAL; }
    @ConfigItem(keyName="showAdviceText", name="Show main advice", description="Show CUT, FLETCH, BURN or GO NOW", position=3, section=advancedSection, hidden=true)
    default boolean showAdviceText(){ return true; }
    @ConfigItem(keyName="showAdviceDetail", name="Show advice detail", description="Show the short second advice line", position=4, section=advancedSection, hidden=true)
    default boolean showAdviceDetail(){ return true; }
    @ConfigItem(keyName="showTimeEstimate", name="Show time left", description="Show estimated round time", position=5, section=advancedSection, hidden=true)
    default boolean showTimeEstimate(){ return true; }
    @ConfigItem(keyName="showSpareTime", name="Show spare time", description="Show estimated time left after your plan", position=6, section=advancedSection, hidden=true)
    default boolean showSpareTime(){ return true; }
    @ConfigItem(keyName="showExtraRootEstimate", name="Show safe roots", description="Show how many more roots are likely safe", position=7, section=advancedSection, hidden=true)
    default boolean showExtraRootEstimate(){ return true; }
    @ConfigItem(keyName="showAdvancedMath", name="Show detailed math", description="Show tick-rate details", position=8, section=advancedSection, hidden=true)
    default boolean showAdvancedMath(){ return false; }
    @ConfigItem(keyName="assumeRunning", name="Assume running", description="Estimate movement at two tiles per tick", position=9, section=advancedSection, hidden=true)
    default boolean assumeRunning(){ return true; }
    @ConfigItem(keyName="fletchWhileMoving", name="Fletch while moving", description="Allow movement and fletching time to overlap", position=10, section=advancedSection, hidden=true)
    default boolean fletchWhileMoving(){ return true; }
    @ConfigItem(keyName="extraSafetyTicks", name="Extra safety ticks", description="Extra time kept in reserve", position=11, section=advancedSection, hidden=true)
    @Range(min=0,max=30) default int extraSafetyTicks(){ return 2; }
    @ConfigItem(keyName="fallbackBrazierTiles", name="Fallback distance", description="Used only if brazier distance cannot be read", position=13, section=advancedSection, hidden=true)
    @Range(min=1,max=30) default int fallbackBrazierTiles(){ return 9; }

    // Panel style
    @ConfigItem(keyName="panelWidth", name="Width", description="Coach panel width", position=0, section=advancedSection, hidden=true)
    @Range(min=120,max=420) default int panelWidth(){ return 205; }
    @ConfigItem(keyName="panelFontName", name="Font", description="Panel font family", position=1, section=advancedSection, hidden=true)
    default String panelFontName(){ return "Verdana"; }
    @ConfigItem(keyName="panelFontSize", name="Font size", description="Panel text size", position=2, section=advancedSection, hidden=true)
    @Range(min=10,max=32) default int panelFontSize(){ return 13; }
    @ConfigItem(keyName="panelBold", name="Bold", description="Use bold panel text", position=3, section=advancedSection, hidden=true)
    default boolean panelBold(){ return true; }
    @Alpha @ConfigItem(keyName="panelBackground", name="Background", description="Panel background", position=4, section=advancedSection, hidden=true)
    default Color panelBackground(){ return new Color(28,21,15,220); }
    @Alpha @ConfigItem(keyName="panelBorder", name="Border", description="Panel border", position=5, section=advancedSection, hidden=true)
    default Color panelBorder(){ return new Color(151,113,49,230); }
    @ConfigItem(keyName="panelText", name="Text", description="Normal panel text", position=6, section=advancedSection, hidden=true)
    default Color panelText(){ return Color.WHITE; }
    @ConfigItem(keyName="panelGood", name="Good", description="Safe/success text", position=7, section=advancedSection, hidden=true)
    default Color panelGood(){ return new Color(100,220,120); }
    @ConfigItem(keyName="panelWarn", name="Warning", description="Urgent text", position=8, section=advancedSection, hidden=true)
    default Color panelWarn(){ return new Color(255,190,70); }
    @ConfigItem(keyName="panelDanger", name="Danger", description="Critical text", position=9, section=advancedSection, hidden=true)
    default Color panelDanger(){ return new Color(255,90,90); }

    // Warmth
    @ConfigItem(keyName="warmthWidth", name="Width", description="Warmth width", position=0, section=advancedSection, hidden=true)
    @Range(min=24,max=600) default int warmthWidth(){ return 190; }
    @ConfigItem(keyName="warmthHeight", name="Height", description="Warmth height", position=1, section=advancedSection, hidden=true)
    @Range(min=12,max=300) default int warmthHeight(){ return 32; }
    @ConfigItem(keyName="warmthShape", name="Shape", description="Warmth shape", position=2, section=advancedSection, hidden=true)
    default MeterShape warmthShape(){ return MeterShape.ROUNDED_RECTANGLE; }
    @ConfigItem(keyName="warmthFillDirection", name="Fill", description="Warmth fill direction", position=3, section=advancedSection, hidden=true)
    default FillDirection warmthFillDirection(){ return FillDirection.LEFT_TO_RIGHT; }
    @Alpha @ConfigItem(keyName="warmthFillColor", name="Fill color", description="Warmth fill", position=4, section=advancedSection, hidden=true)
    default Color warmthFillColor(){ return new Color(255,135,30,220); }
    @Alpha @ConfigItem(keyName="warmthEmptyColor", name="Empty color", description="Warmth empty area", position=5, section=advancedSection, hidden=true)
    default Color warmthEmptyColor(){ return new Color(30,30,30,190); }
    @Alpha @ConfigItem(keyName="warmthBorderColor", name="Border color", description="Warmth border", position=6, section=advancedSection, hidden=true)
    default Color warmthBorderColor(){ return new Color(255,255,255,220); }
    @ConfigItem(keyName="warmthBorderWidth", name="Border width", description="Warmth border width", position=7, section=advancedSection, hidden=true)
    @Range(min=0,max=12) default int warmthBorderWidth(){ return 2; }
    @ConfigItem(keyName="warmthTextMode", name="Text", description="Warmth text format", position=8, section=advancedSection, hidden=true)
    default MeterTextMode warmthTextMode(){ return MeterTextMode.LABEL_PERCENT; }
    @ConfigItem(keyName="warmthPrefix", name="Prefix", description="Text before Warmth", position=9, section=advancedSection, hidden=true)
    default String warmthPrefix(){ return ""; }
    @ConfigItem(keyName="warmthSuffix", name="Suffix", description="Text after Warmth", position=10, section=advancedSection, hidden=true)
    default String warmthSuffix(){ return ""; }
    @ConfigItem(keyName="warmthFontName", name="Font", description="Warmth font", position=11, section=advancedSection, hidden=true)
    default String warmthFontName(){ return "Verdana"; }
    @ConfigItem(keyName="warmthFontSize", name="Font size", description="Warmth font size", position=12, section=advancedSection, hidden=true)
    @Range(min=8,max=72) default int warmthFontSize(){ return 15; }
    @ConfigItem(keyName="warmthBold", name="Bold", description="Bold Warmth text", position=13, section=advancedSection, hidden=true)
    default boolean warmthBold(){ return true; }
    @ConfigItem(keyName="warmthTextColor", name="Text color", description="Warmth text color", position=14, section=advancedSection, hidden=true)
    default Color warmthTextColor(){ return Color.WHITE; }
    @ConfigItem(keyName="warmthTextX", name="Text X", description="Warmth text horizontal offset", position=15, section=advancedSection, hidden=true)
    @Range(min=-300,max=300) default int warmthTextX(){ return 0; }
    @ConfigItem(keyName="warmthTextY", name="Text Y", description="Warmth text vertical offset", position=16, section=advancedSection, hidden=true)
    @Range(min=-300,max=300) default int warmthTextY(){ return 0; }
    @ConfigItem(keyName="warmthRegenRingEnabled", name="Regen ring", description="Show progress toward the next natural Warmth regeneration pulse", position=17, section=advancedSection, hidden=true)
    default boolean warmthRegenRingEnabled(){ return true; }
    @ConfigItem(keyName="warmthRegenRingWidth", name="Regen ring width", description="Warmth regeneration ring width", position=18, section=advancedSection, hidden=true)
    @Range(min=1,max=6) default int warmthRegenRingWidth(){ return 2; }
    @Alpha @ConfigItem(keyName="warmthRegenRingColor", name="Regen ring color", description="Filled portion of the Warmth regeneration ring", position=19, section=advancedSection, hidden=true)
    default Color warmthRegenRingColor(){ return new Color(255,205,110,235); }
    @Alpha @ConfigItem(keyName="warmthRegenRingTrackColor", name="Regen ring track", description="Unfilled portion of the Warmth regeneration ring", position=20, section=advancedSection, hidden=true)
    default Color warmthRegenRingTrackColor(){ return new Color(255,255,255,45); }
    @ConfigItem(keyName="warmthRegenAmountText", name="Regen amount", description="Show the expected base Warmth pulse amount", position=21, section=advancedSection, hidden=true)
    default boolean warmthRegenAmountText(){ return true; }
    @ConfigItem(keyName="warmthConsumablePreviewEnabled", name="Food preview", description="Preview the Warmth restored by the hovered food or healing potion dose", position=22, section=advancedSection, hidden=true)
    default boolean warmthConsumablePreviewEnabled(){ return true; }
    @Alpha @ConfigItem(keyName="warmthConsumablePreviewColor", name="Food preview color", description="Immediate hovered-food Warmth preview", position=23, section=advancedSection, hidden=true)
    default Color warmthConsumablePreviewColor(){ return new Color(175,65,15,220); }
    @ConfigItem(keyName="warmthRegenPreviewEnabled", name="Regen preview", description="Preview where the next passive Warmth pulse will take the meter", position=24, section=advancedSection, hidden=true)
    default boolean warmthRegenPreviewEnabled(){ return true; }
    @Alpha @ConfigItem(keyName="warmthRegenPreviewColor", name="Regen preview color", description="Future passive Warmth preview", position=25, section=advancedSection, hidden=true)
    default Color warmthRegenPreviewColor(){ return new Color(255,215,125,105); }

    // Energy
    @ConfigItem(keyName="energyWidth", name="Width", description="Energy width", position=0, section=advancedSection, hidden=true)
    @Range(min=24,max=600) default int energyWidth(){ return 190; }
    @ConfigItem(keyName="energyHeight", name="Height", description="Energy height", position=1, section=advancedSection, hidden=true)
    @Range(min=12,max=300) default int energyHeight(){ return 32; }
    @ConfigItem(keyName="energyShape", name="Shape", description="Energy shape", position=2, section=advancedSection, hidden=true)
    default MeterShape energyShape(){ return MeterShape.ROUNDED_RECTANGLE; }
    @ConfigItem(keyName="energyFillDirection", name="Fill", description="Energy fill direction", position=3, section=advancedSection, hidden=true)
    default FillDirection energyFillDirection(){ return FillDirection.LEFT_TO_RIGHT; }
    @Alpha @ConfigItem(keyName="energyFillColor", name="Fill color", description="Energy fill", position=4, section=advancedSection, hidden=true)
    default Color energyFillColor(){ return new Color(70,185,255,220); }
    @Alpha @ConfigItem(keyName="energyEmptyColor", name="Empty color", description="Energy empty area", position=5, section=advancedSection, hidden=true)
    default Color energyEmptyColor(){ return new Color(30,30,30,190); }
    @Alpha @ConfigItem(keyName="energyBorderColor", name="Border color", description="Energy border", position=6, section=advancedSection, hidden=true)
    default Color energyBorderColor(){ return new Color(255,255,255,220); }
    @ConfigItem(keyName="energyBorderWidth", name="Border width", description="Energy border width", position=7, section=advancedSection, hidden=true)
    @Range(min=0,max=12) default int energyBorderWidth(){ return 2; }
    @ConfigItem(keyName="energyTextMode", name="Text", description="Energy text format", position=8, section=advancedSection, hidden=true)
    default MeterTextMode energyTextMode(){ return MeterTextMode.LABEL_PERCENT; }
    @ConfigItem(keyName="energyPrefix", name="Prefix", description="Text before Energy", position=9, section=advancedSection, hidden=true)
    default String energyPrefix(){ return ""; }
    @ConfigItem(keyName="energySuffix", name="Suffix", description="Text after Energy", position=10, section=advancedSection, hidden=true)
    default String energySuffix(){ return ""; }
    @ConfigItem(keyName="energyFontName", name="Font", description="Energy font", position=11, section=advancedSection, hidden=true)
    default String energyFontName(){ return "Verdana"; }
    @ConfigItem(keyName="energyFontSize", name="Font size", description="Energy font size", position=12, section=advancedSection, hidden=true)
    @Range(min=8,max=72) default int energyFontSize(){ return 15; }
    @ConfigItem(keyName="energyBold", name="Bold", description="Bold Energy text", position=13, section=advancedSection, hidden=true)
    default boolean energyBold(){ return true; }
    @ConfigItem(keyName="energyTextColor", name="Text color", description="Energy text color", position=14, section=advancedSection, hidden=true)
    default Color energyTextColor(){ return Color.WHITE; }
    @ConfigItem(keyName="energyTextX", name="Text X", description="Energy text horizontal offset", position=15, section=advancedSection, hidden=true)
    @Range(min=-300,max=300) default int energyTextX(){ return 0; }
    @ConfigItem(keyName="energyTextY", name="Text Y", description="Energy text vertical offset", position=16, section=advancedSection, hidden=true)
    @Range(min=-300,max=300) default int energyTextY(){ return 0; }
    @ConfigItem(keyName="energyNextRoundTimerEnabled", name="Next-round timer", description="Turn the Energy meter into a countdown between Wintertodt rounds", position=17, section=advancedSection, hidden=true)
    default boolean energyNextRoundTimerEnabled(){ return true; }

    // Alerts
    @ConfigItem(keyName="idleAlert", name="Idle", description="Alert when you stop acting", position=30, section=alertsSection)
    default boolean idleAlert(){ return true; }
    @ConfigItem(keyName="idleDelay", name="Idle after", description="Seconds before Idle alert", position=31, section=advancedSection, hidden=true)
    @Range(min=0,max=30) default int idleDelay(){ return 0; }
    @ConfigItem(keyName="idleVisual", name="Idle visual", description="Where Idle appears", position=2, section=advancedSection, hidden=true)
    default AlertVisual idleVisual(){ return AlertVisual.SCREEN_AND_BOTH; }
    @ConfigItem(keyName="idleNotify", name="Idle notification", description="Send RuneLite notification", position=3, section=advancedSection, hidden=true)
    default boolean idleNotify(){ return false; }

    @Alpha @ConfigItem(keyName="idleScreenColor", name="Idle color", description="Screen color while Idle", position=32, section=alertsSection)
    default Color idleScreenColor(){ return new Color(125, 90, 255, 62); }

    @ConfigItem(keyName="lowWarmthAlert", name="Low Warmth", description="Alert at low Warmth", position=10, section=alertsSection)
    default boolean lowWarmthAlert(){ return true; }
    @ConfigItem(keyName="lowWarmthThreshold", name="Low Warmth at", description="Low Warmth threshold", position=11, section=advancedSection, hidden=true)
    @Range(min=1,max=99) default int lowWarmthThreshold(){ return 35; }
    @ConfigItem(keyName="lowWarmthVisual", name="Low Warmth visual", description="Where Low Warmth appears", position=12, section=advancedSection, hidden=true)
    default AlertVisual lowWarmthVisual(){ return AlertVisual.SCREEN_AND_RELEVANT; }
    @ConfigItem(keyName="lowWarmthNotify", name="Low Warmth notification", description="Send RuneLite notification", position=13, section=advancedSection, hidden=true)
    default boolean lowWarmthNotify(){ return false; }

    @Alpha @ConfigItem(keyName="lowWarmthScreenColor", name="Low Warmth color", description="Screen color at Low Warmth", position=12, section=alertsSection)
    default Color lowWarmthScreenColor(){ return new Color(255, 145, 35, 65); }

    @ConfigItem(keyName="criticalWarmthAlert", name="Eat now", description="Urgent Warmth alert", position=20, section=alertsSection)
    default boolean criticalWarmthAlert(){ return true; }
    @ConfigItem(keyName="criticalWarmthThreshold", name="Eat now at", description="Critical Warmth threshold", position=21, section=advancedSection, hidden=true)
    @Range(min=1,max=99) default int criticalWarmthThreshold(){ return 20; }
    @ConfigItem(keyName="criticalWarmthVisual", name="Critical visual", description="Where Critical Warmth appears", position=22, section=advancedSection, hidden=true)
    default AlertVisual criticalWarmthVisual(){ return AlertVisual.SCREEN_AND_RELEVANT; }
    @ConfigItem(keyName="criticalWarmthNotify", name="Critical notification", description="Send RuneLite notification", position=23, section=advancedSection, hidden=true)
    default boolean criticalWarmthNotify(){ return true; }

    @Alpha @ConfigItem(keyName="criticalWarmthScreenColor", name="Eat-now color", description="Screen color at critical Warmth", position=22, section=alertsSection)
    default Color criticalWarmthScreenColor(){ return new Color(255, 35, 35, 88); }

    @ConfigItem(keyName="lowEnergyAlert", name="Low Energy", description="Alert near round end", position=30, section=advancedSection, hidden=true)
    default boolean lowEnergyAlert(){ return true; }
    @ConfigItem(keyName="lowEnergyThreshold", name="Low Energy %", description="Low Energy threshold", position=31, section=advancedSection, hidden=true)
    @Range(min=1,max=99) default int lowEnergyThreshold(){ return 10; }
    @ConfigItem(keyName="lowEnergyVisual", name="Low Energy visual", description="Where Low Energy appears", position=32, section=advancedSection, hidden=true)
    default AlertVisual lowEnergyVisual(){ return AlertVisual.RELEVANT_METER; }
    @ConfigItem(keyName="lowEnergyNotify", name="Low Energy notification", description="Send RuneLite notification", position=33, section=advancedSection, hidden=true)
    default boolean lowEnergyNotify(){ return false; }

    @ConfigItem(keyName="goNowAlert", name="Go now", description="Alert when it is time to burn", position=40, section=advancedSection, hidden=true)
    default boolean goNowAlert(){ return true; }
    @ConfigItem(keyName="goNowVisual", name="Go-now visual", description="Where Go now appears", position=41, section=advancedSection, hidden=true)
    default AlertVisual goNowVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="goNowNotify", name="Go-now notification", description="Send RuneLite notification", position=42, section=advancedSection, hidden=true)
    default boolean goNowNotify(){ return false; }

    @ConfigItem(keyName="notSafeAlert", name="500 at risk", description="Alert when 500 points are mathematically unreachable", position=50, section=advancedSection, hidden=true)
    default boolean notSafeAlert(){ return true; }
    @ConfigItem(keyName="notSafeVisual", name="Emergency visual", description="Where the warning appears", position=51, section=advancedSection, hidden=true)
    default AlertVisual notSafeVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="notSafeNotify", name="Emergency notification", description="Send RuneLite notification", position=52, section=advancedSection, hidden=true)
    default boolean notSafeNotify(){ return false; }

    @ConfigItem(keyName="inventoryFullAlert", name="Inventory full", description="Alert when inventory fills", position=60, section=advancedSection, hidden=true)
    default boolean inventoryFullAlert(){ return true; }
    @ConfigItem(keyName="inventoryFullVisual", name="Inventory-full visual", description="Where Inventory full appears", position=61, section=advancedSection, hidden=true)
    default AlertVisual inventoryFullVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="inventoryFullNotify", name="Inventory-full notification", description="Send RuneLite notification", position=62, section=advancedSection, hidden=true)
    default boolean inventoryFullNotify(){ return false; }

    @ConfigItem(keyName="outOfRootsAlert", name="Out of roots", description="Alert when roots run out", position=70, section=advancedSection, hidden=true)
    default boolean outOfRootsAlert(){ return true; }
    @ConfigItem(keyName="outOfRootsVisual", name="Out-of-roots visual", description="Where Out of roots appears", position=71, section=advancedSection, hidden=true)
    default AlertVisual outOfRootsVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="outOfRootsNotify", name="Out-of-roots notification", description="Send RuneLite notification", position=72, section=advancedSection, hidden=true)
    default boolean outOfRootsNotify(){ return false; }

    @ConfigItem(keyName="brazierOutAlert", name="Brazier out", description="Alert when brazier goes out", position=80, section=advancedSection, hidden=true)
    default boolean brazierOutAlert(){ return false; }
    @ConfigItem(keyName="brazierOutVisual", name="Brazier-out visual", description="Where Brazier out appears", position=81, section=advancedSection, hidden=true)
    default AlertVisual brazierOutVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="brazierOutNotify", name="Brazier-out notification", description="Send RuneLite notification", position=82, section=advancedSection, hidden=true)
    default boolean brazierOutNotify(){ return false; }

    @ConfigItem(keyName="interruptAlert", name="Interrupted", description="Alert when damage stops an action", position=90, section=advancedSection, hidden=true)
    default boolean interruptAlert(){ return false; }
    @ConfigItem(keyName="interruptVisual", name="Interruption visual", description="Where Interrupted appears", position=91, section=advancedSection, hidden=true)
    default AlertVisual interruptVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="interruptNotify", name="Interruption notification", description="Send RuneLite notification", position=92, section=advancedSection, hidden=true)
    default boolean interruptNotify(){ return false; }

    @ConfigItem(keyName="roundStartAlert", name="Round starting", description="Alert before a new round", position=100, section=advancedSection, hidden=true)
    default boolean roundStartAlert(){ return true; }
    @ConfigItem(keyName="roundStartSeconds", name="Round-start seconds", description="Alert this many seconds before start", position=101, section=advancedSection, hidden=true)
    @Range(min=1,max=60) default int roundStartSeconds(){ return 5; }
    @ConfigItem(keyName="roundStartVisual", name="Round-start visual", description="Where Round starting appears", position=102, section=advancedSection, hidden=true)
    default AlertVisual roundStartVisual(){ return AlertVisual.BANNER; }
    @ConfigItem(keyName="roundStartNotify", name="Round-start notification", description="Send RuneLite notification", position=103, section=advancedSection, hidden=true)
    default boolean roundStartNotify(){ return true; }

    // Alert style
    @Alpha @ConfigItem(keyName="screenFlashColor", name="Screen flash", description="Screen flash color", position=0, section=advancedSection, hidden=true)
    default Color screenFlashColor(){ return new Color(255,35,35,85); }
    @Alpha @ConfigItem(keyName="meterFlashColor", name="Meter flash", description="Meter flash color", position=1, section=advancedSection, hidden=true)
    default Color meterFlashColor(){ return new Color(255,255,80,150); }
    @ConfigItem(keyName="flashPeriodMs", name="Flash period", description="Full flash cycle in milliseconds", position=2, section=advancedSection, hidden=true)
    @Range(min=100,max=3000) default int flashPeriodMs(){ return 600; }
    @ConfigItem(keyName="showAlertText", name="Alert text", description="Show large screen text", position=3, section=advancedSection, hidden=true)
    default boolean showAlertText(){ return true; }
    @ConfigItem(keyName="alertFontName", name="Alert font", description="Alert font family", position=4, section=advancedSection, hidden=true)
    default String alertFontName(){ return "Verdana"; }
    @ConfigItem(keyName="alertFontSize", name="Alert font size", description="Large alert text size", position=5, section=advancedSection, hidden=true)
    @Range(min=12,max=96) default int alertFontSize(){ return 34; }
    @ConfigItem(keyName="eventDuration", name="Event duration", description="Seconds one-shot alerts stay active", position=6, section=advancedSection, hidden=true)
    @Range(min=1,max=15) default int eventDuration(){ return 3; }
    @ConfigItem(keyName="repeatPersistentNotifications", name="Repeat notifications", description="Repeat persistent RuneLite notifications", position=7, section=advancedSection, hidden=true)
    default boolean repeatPersistentNotifications(){ return false; }
    @ConfigItem(keyName="repeatSeconds", name="Repeat every", description="Seconds between repeats", position=8, section=advancedSection, hidden=true)
    @Range(min=2,max=120) default int repeatSeconds(){ return 10; }

    // Native UI
    @ConfigItem(keyName="hideNativeWarmth", name="Hide game Warmth", description="Hide Jagex Warmth", position=0, section=advancedSection, hidden=true)
    default boolean hideNativeWarmth(){ return false; }
    @ConfigItem(keyName="hideNativeEnergy", name="Hide game Energy", description="Hide Jagex Energy", position=1, section=advancedSection, hidden=true)
    default boolean hideNativeEnergy(){ return false; }
    @ConfigItem(keyName="hideNativePoints", name="Hide game Points", description="Hide the game Points text when found", position=2, section=advancedSection, hidden=true)
    default boolean hideNativePoints(){ return false; }
    @ConfigItem(keyName="hideRuneLiteOverlay", name="Hide RuneLite overlay", description="Temporarily hide RuneLite's built-in Wintertodt overlay", position=3, section=advancedSection, hidden=true)
    default boolean hideRuneLiteOverlay(){ return true; }
}
