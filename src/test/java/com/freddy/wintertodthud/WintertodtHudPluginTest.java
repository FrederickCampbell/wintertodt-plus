package com.freddy.wintertodthud;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
@SuppressWarnings("unchecked")
public class WintertodtHudPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(WintertodtHudPlugin.class);
        RuneLite.main(args);
    }
}
