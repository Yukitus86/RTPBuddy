package dev.rtpbuddy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.rtpbuddy.RTPBuddy;
import dev.rtpbuddy.util.FileOps;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type REGION_LIST = new TypeToken<List<RegionPreset>>() {
    }.getType();

    private final Path configFile;
    private RTPBuddyConfig config = new RTPBuddyConfig();

    public ConfigManager(Path configDir) {
        this.configFile = configDir.resolve("config.json");
    }

    public RTPBuddyConfig get() {
        return config;
    }

    public void load() {
        FileOps.recoverStrayTemp(configFile);
        String json = FileOps.readOrNull(configFile);
        if (json == null) {
            config = new RTPBuddyConfig();
            config.regions = loadBundledPreset("donutsmp");
            RTPBuddy.LOGGER.info("[RTPBuddy] no config found, seeding with '{}' preset ({} regions)",
                    config.activePreset, config.regions.size());
            save();
            return;
        }
        try {
            RTPBuddyConfig parsed = GSON.fromJson(json, RTPBuddyConfig.class);
            config = parsed != null ? parsed : new RTPBuddyConfig();
        } catch (JsonSyntaxException e) {
            RTPBuddy.LOGGER.error("[RTPBuddy] config.json is malformed, falling back to defaults: {}", e.getMessage());
            config = new RTPBuddyConfig();
            config.regions = loadBundledPreset("donutsmp");
        }
        config.sanitise();
        if (mergeNewPresetRegions()) {
            save();
        }
        if (config.regions.isEmpty()) {
            // A config written before the preset could be read would leave capture
            // unable to match anything, so re-seed and persist rather than limping.
            config.regions = loadBundledPreset(config.activePreset);
            if (!config.regions.isEmpty()) {
                RTPBuddy.LOGGER.info("[RTPBuddy] region list was empty, re-seeded from '{}' preset ({} regions)",
                        config.activePreset, config.regions.size());
                save();
            }
        }
    }

    public void save() {
        try {
            FileOps.writeAtomic(configFile, GSON.toJson(config));
        } catch (IOException e) {
            RTPBuddy.LOGGER.error("[RTPBuddy] could not write config.json: {}", e.toString());
        }
    }

    /**
     * Adds regions the bundled preset has gained since this config was written.
     *
     * <p>A config from an older release lists only the three destinations that
     * existed then, so the zone commands DonutSMP actually offers would stay
     * unmatched forever and every {@code /rtp asia} would record as nothing.
     * Only ids that are missing are added - a region the player edited, renamed
     * or deleted on purpose is left exactly as it is, and the pass runs once per
     * version bump rather than on every load.
     *
     * @return true when something was added and the config needs writing
     */
    private boolean mergeNewPresetRegions() {
        if (config.configVersion >= RTPBuddyConfig.CURRENT_VERSION) {
            return false;
        }
        config.configVersion = RTPBuddyConfig.CURRENT_VERSION;
        if (config.activePreset == null || config.activePreset.isBlank() || config.regions.isEmpty()) {
            return true;
        }
        List<RegionPreset> preset = loadBundledPreset(config.activePreset);
        List<String> added = new ArrayList<>();
        for (RegionPreset region : preset) {
            if (region == null || region.id == null || config.findRegion(region.id) != null) {
                continue;
            }
            config.regions.add(region);
            added.add(region.id);
        }
        if (!added.isEmpty()) {
            RTPBuddy.LOGGER.info("[RTPBuddy] added {} region(s) from the '{}' preset: {}",
                    added.size(), config.activePreset, String.join(", ", added));
        }
        return true;
    }

    /** Replaces the region list with a bundled preset, keeping everything else. */
    public boolean applyPreset(String presetId) {
        List<RegionPreset> preset = loadBundledPreset(presetId);
        if (preset.isEmpty()) {
            return false;
        }
        config.regions = preset;
        config.activePreset = presetId;
        save();
        return true;
    }

    public List<RegionPreset> loadBundledPreset(String presetId) {
        String resource = "/rtpbuddy/presets/" + presetId + ".json";
        try (InputStream in = ConfigManager.class.getResourceAsStream(resource)) {
            if (in == null) {
                RTPBuddy.LOGGER.warn("[RTPBuddy] bundled preset '{}' not found", presetId);
                return new ArrayList<>();
            }
            List<RegionPreset> regions = GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), REGION_LIST);
            return regions != null ? regions : new ArrayList<>();
        } catch (IOException | JsonSyntaxException e) {
            RTPBuddy.LOGGER.warn("[RTPBuddy] could not read preset '{}': {}", presetId, e.toString());
            return new ArrayList<>();
        }
    }
}
