package com.freddy.wintertodthud;

final class AdvisorMath
{
    private AdvisorMath(){}

    static int inventoryPoints(int roots, int kindling)
    {
        return Math.max(0, roots) * WintertodtMechanics.ROOT_POINTS
            + Math.max(0, kindling) * WintertodtMechanics.KINDLING_POINTS;
    }

    static int potentialInventoryPoints(int roots, int kindling)
    {
        return (Math.max(0, roots) + Math.max(0, kindling))
            * WintertodtMechanics.KINDLING_POINTS;
    }

    static int minimumFletchesFor500(int points, int roots, int kindling)
    {
        int rawProjected = Math.max(0, points) + inventoryPoints(roots, kindling);
        if (rawProjected >= WintertodtMechanics.REWARD_THRESHOLD)
        {
            return 0;
        }
        int missing = WintertodtMechanics.REWARD_THRESHOLD - rawProjected;
        int fletches = (int)Math.ceil(missing / (double)WintertodtMechanics.FLETCH_BONUS_POINTS);
        return Math.max(0, Math.min(Math.max(0, roots), fletches));
    }
}
