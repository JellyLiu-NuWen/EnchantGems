package org.nuwen.EnchantGems.util;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.nuwen.EnchantGems.config.PluginFileManager;

public final class MessageHelper {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final PluginFileManager fileManager;

    public MessageHelper(PluginFileManager fileManager) {
        this.fileManager = fileManager;
    }

    public void sendConfigured(CommandSender sender, String path) {
        sendConfigured(sender, path, Map.of());
    }

    public void sendConfigured(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(prefixed(path, placeholders));
    }

    public Component configuredComponent(String path) {
        return configuredComponent(path, Map.of());
    }

    public Component configuredComponent(String path, Map<String, String> placeholders) {
        return deserialize(text(path, placeholders));
    }

    public Component prefixed(String messagePath) {
        return prefixed(messagePath, Map.of());
    }

    public Component prefixed(String messagePath, Map<String, String> placeholders) {
        String prefix = text("prefix", placeholders);
        String message = text(messagePath, placeholders);
        return deserialize(prefix + message);
    }

    public String text(String path) {
        return text(path, Map.of());
    }

    public String text(String path, Map<String, String> placeholders) {
        String value = this.fileManager.language().getString(path, "");
        return applyPlaceholders(value == null ? "" : value, placeholders);
    }

    public Component deserialize(String value) {
        return LEGACY_SERIALIZER.deserialize(value == null ? "" : value);
    }

    public List<Component> deserializeList(List<String> values) {
        return deserializeList(values, Map.of());
    }

    public List<Component> deserializeList(List<String> values, Map<String, String> placeholders) {
        return values.stream().map(line -> deserialize(applyPlaceholders(line, placeholders))).toList();
    }

    public String applyPlaceholders(String value, Map<String, String> placeholders) {
        String result = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
