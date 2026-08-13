package com.freddy.wintertodthud;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Resolves the Warmth gained by the inventory item currently under the mouse.
 *
 * Uses only public RuneLite API. Wintertodt restores 35% Warmth for food whose
 * next bite normally heals at least 4 Hitpoints, and 30% for a rejuvenation
 * potion dose. Multi-bite food such as cake previews the next bite (+35), which
 * is exactly what one click will do.
 */
@Singleton
public class WarmthConsumablePreview
{
    private static final Set<String> BELOW_FOUR_HP = new HashSet<>(Arrays.asList(
        "potato", "onion", "cabbage", "anchovies", "equa leaves", "tomato",
        "banana", "orange", "orange slices", "orange chunks", "pineapple ring",
        "pineapple chunks", "cheese", "spinach roll", "lemon", "lemon chunks",
        "lemon slices", "lime", "lime chunks", "lime slices", "dwellberries",
        "king worm", "shrimp", "cooked meat", "cooked chicken", "brutal green dragon roe",
        "chocolate bar", "cooked ugthanki meat", "toad's legs", "birthday cake slice",
        "locust meat", "purple sweets", "dwarven rock cake"
    ));

    private final Client client;

    @Inject
    WarmthConsumablePreview(Client client)
    {
        this.client = client;
    }

    int hoveredWarmthGain()
    {
        final MenuEntry[] menu = client.getMenuEntries();
        if (menu == null || menu.length == 0)
        {
            return 0;
        }

        final MenuEntry entry = menu[menu.length - 1];
        final Widget widget = entry.getWidget();
        if (widget == null || widget.getId() != InterfaceID.Inventory.ITEMS)
        {
            return 0;
        }

        final int itemId = widget.getItemId();
        if (itemId <= 0)
        {
            return 0;
        }

        final ItemComposition composition = client.getItemDefinition(itemId);
        if (composition == null)
        {
            return 0;
        }

        String name = composition.getMembersName();
        if (name == null || name.isEmpty())
        {
            name = composition.getName();
        }
        final String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("rejuvenation potion"))
        {
            return 30;
        }

        if (BELOW_FOUR_HP.contains(normalized))
        {
            return 0;
        }

        final String[] actions = composition.getInventoryActions();
        if (actions != null)
        {
            for (String action : actions)
            {
                if (action != null && action.equalsIgnoreCase("Eat"))
                {
                    return 35;
                }
            }
        }

        return 0;
    }
}
