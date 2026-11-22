package vn.meowchan12.mobarenav2.utils;

public class ArenaMath {

    /**
     * Algorithm: Smart Mob Spawn Scaling.
     * [v8.3.2 REBALANCE]:
     * Each additional player adds +25% mobs (reduced from +50%), capped at 2.0x (reduced from 3.5x).
     * Prevents multi-player lobbies from exceeding entity density limits (200+ mobs).
     */
    public static int calculateSpawnAmount(int baseAmount, int playerCount, double customMultiplier) {
        if (playerCount <= 1) return (int) Math.round(baseAmount * customMultiplier);

        // Formula: Base * Multiplier * (1 + (Players - 1) * 0.25)
        double scalingFactor = 1.0 + ((playerCount - 1) * 0.25);

        // Cap maximum multiplier at 2.0x base amount
        scalingFactor = Math.min(scalingFactor, 2.0);

        return (int) Math.floor(baseAmount * customMultiplier * scalingFactor);
    }

    /**
     * Thuật toán: Tính máu Boss thông minh.
     * Sử dụng HP gốc từ file bosses.yml, người chơi đầu tiên chịu 100% HP.
     * Từ người chơi thứ 2 trở đi, mỗi người chỉ cộng thêm 30% HP gốc cho Boss.
     * Giúp Boss trâu hơn nhưng không biến thành "bịch bông" tốn thời gian.
     */
    public static double calculateBossHealth(double baseHpFromConfig, double customMultiplier, int playerCount) {
        if (playerCount <= 1) return baseHpFromConfig * customMultiplier;

        // Công thức: BaseHP * Multiplier * (100% + (Players - 1) * 30%)
        double healthScaling = 1.0 + ((playerCount - 1) * 0.30);

        return baseHpFromConfig * customMultiplier * healthScaling;
    }
}