package com.freddy.wintertodthud;

final class DisplayRules
{
    private DisplayRules(){}
    static boolean showCoach(WintertodtHudConfig c){ return c.showCoachPanel(); }
    static boolean showPotential(WintertodtHudConfig c){ return c.showPotentialPoints(); }
    static boolean showTime(WintertodtHudConfig c){ return c.showTimeEstimate(); }
    static boolean showExtraRoots(WintertodtHudConfig c){ return c.showExtraRootEstimate(); }
    static boolean showAdvanced(WintertodtHudConfig c){ return c.showAdvancedMath(); }
}
