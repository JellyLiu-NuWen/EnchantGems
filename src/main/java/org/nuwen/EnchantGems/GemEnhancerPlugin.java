package org.nuwen.EnchantGems;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.nuwen.EnchantGems.command.GemCommand;
import org.nuwen.EnchantGems.config.PluginFileManager;
import org.nuwen.EnchantGems.gui.ForgeMenuManager;
import org.nuwen.EnchantGems.service.EnchantmentCatalog;
import org.nuwen.EnchantGems.service.GemService;
import org.nuwen.EnchantGems.util.MessageHelper;

public final class GemEnhancerPlugin extends JavaPlugin {

    private PluginFileManager fileManager;
    private MessageHelper messageHelper;
    private EnchantmentCatalog enchantmentCatalog;
    private GemService gemService;
    private ForgeMenuManager forgeMenuManager;

    @Override
    public void onEnable() {
        this.fileManager = new PluginFileManager(this);
        this.fileManager.load();
        this.messageHelper = new MessageHelper(this.fileManager);
        this.enchantmentCatalog = new EnchantmentCatalog();
        this.gemService = new GemService(this, this.fileManager, this.messageHelper, this.enchantmentCatalog);
        this.forgeMenuManager = new ForgeMenuManager(this.fileManager, this.messageHelper, this.gemService);

        registerCommands();
        getServer().getPluginManager().registerEvents(this.forgeMenuManager, this);
        logStartupInfo();
    }

    @Override
    public void onDisable() {
        if (this.forgeMenuManager != null) {
            this.forgeMenuManager.shutdown();
        }
    }

    private void registerCommands() {
        PluginCommand gemCommand = Objects.requireNonNull(getCommand("gem"), "Command gem missing from plugin.yml");
        GemCommand gemExecutor = new GemCommand(this.fileManager, this.messageHelper, this.gemService, this.forgeMenuManager);
        gemCommand.setExecutor(gemExecutor);
        gemCommand.setTabCompleter(gemExecutor);
    }

    private void logStartupInfo() {
        String version = this.fileManager.config().getString("version", getPluginMeta().getVersion());
        String developer = getPluginMeta().getAuthors().isEmpty() ? "Nu_Wen" : getPluginMeta().getAuthors().getFirst();
        String separator = "▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃▃";
        var console = getServer().getConsoleSender();
        console.sendMessage(Component.empty());
        console.sendMessage(Component.text(separator, NamedTextColor.YELLOW));
        console.sendMessage(Component.empty());
        console.sendMessage(
            Component.text("     [", NamedTextColor.DARK_GRAY)
                .append(Component.text("EnchantGems", NamedTextColor.AQUA))
                .append(Component.text("] 插件已启用", NamedTextColor.GREEN))
        );
        console.sendMessage(
            Component.text("     版本: ", NamedTextColor.GOLD)
                .append(Component.text(version, NamedTextColor.WHITE))
        );
        console.sendMessage(
            Component.text("     作者: ", NamedTextColor.GOLD)
                .append(Component.text(developer, NamedTextColor.WHITE))
        );
        console.sendMessage(Component.empty());
        console.sendMessage(Component.text(separator, NamedTextColor.YELLOW));
    }
}
