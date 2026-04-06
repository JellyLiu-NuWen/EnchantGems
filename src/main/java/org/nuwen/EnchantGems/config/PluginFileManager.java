package org.nuwen.EnchantGems.config;

import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginFileManager {

    private static final String DEFAULT_LANGUAGE = "zh_CN";

    private final JavaPlugin plugin;
    private FileConfiguration menuConfig;
    private FileConfiguration gemConfig;
    private FileConfiguration languageConfig;
    private String languageCode;

    public PluginFileManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.plugin.saveDefaultConfig();
        this.plugin.reloadConfig();
        this.menuConfig = loadOrCreate("menu.yml");
        this.gemConfig = loadOrCreate("gems.yml");
        this.languageCode = this.plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        this.languageConfig = loadLanguage(this.languageCode);
    }

    public FileConfiguration config() {
        return this.plugin.getConfig();
    }

    public FileConfiguration menu() {
        return this.menuConfig;
    }

    public FileConfiguration gems() {
        return this.gemConfig;
    }

    public FileConfiguration language() {
        return this.languageConfig;
    }

    public String languageCode() {
        return this.languageCode;
    }

    private FileConfiguration loadLanguage(String code) {
        String resourcePath = "lang/" + code + ".yml";
        File file = new File(this.plugin.getDataFolder(), resourcePath);
        if (!file.exists() && this.plugin.getResource(resourcePath) == null) {
            this.languageCode = DEFAULT_LANGUAGE;
            resourcePath = "lang/" + DEFAULT_LANGUAGE + ".yml";
        }
        return loadOrCreate(resourcePath);
    }

    private FileConfiguration loadOrCreate(String resourcePath) {
        File file = new File(this.plugin.getDataFolder(), resourcePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists() && this.plugin.getResource(resourcePath) != null) {
            this.plugin.saveResource(resourcePath, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
