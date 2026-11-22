package vn.meowchan12.mobarenav2.upgrade;

/**
 * [v8.4.0]: Identifies the wave-reward category of a pending upgrade draft choice.
 * Used in ArenaPlayerProfile's FIFO queue so each draft opening strictly resolves
 * its own title and Normal/Super roll rates from settings.yml without bleed-over.
 */
public enum UpgradeDraftType {
    NORMAL("normal"),
    SWARM("swarm"),
    BOSS("boss"),
    STARTER("starter");

    private final String configKey;

    UpgradeDraftType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
