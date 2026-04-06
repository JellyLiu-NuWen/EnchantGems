package org.nuwen.EnchantGems.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nuwen.EnchantGems.config.PluginFileManager;
import org.nuwen.EnchantGems.gui.ForgeMenuManager;
import org.nuwen.EnchantGems.service.GemService;
import org.nuwen.EnchantGems.service.GemService.GemCreationResult;
import org.nuwen.EnchantGems.util.MessageHelper;

public final class GemCommand implements CommandExecutor, TabCompleter {

    private final PluginFileManager fileManager;
    private final MessageHelper messageHelper;
    private final GemService gemService;
    private final ForgeMenuManager forgeMenuManager;

    public GemCommand(PluginFileManager fileManager, MessageHelper messageHelper, GemService gemService, ForgeMenuManager forgeMenuManager) {
        this.fileManager = fileManager;
        this.messageHelper = messageHelper;
        this.gemService = gemService;
        this.forgeMenuManager = forgeMenuManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "upgrade" -> handleUpgrade(sender);
            case "forge" -> handleUpgrade(sender);
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean handleUpgrade(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            this.messageHelper.sendConfigured(sender, "messages.only_player");
            return true;
        }

        if (!player.hasPermission("gemenhancer.use")) {
            this.messageHelper.sendConfigured(player, "messages.no_permission");
            return true;
        }

        this.forgeMenuManager.openMenu(player);
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gemenhancer.give")) {
            this.messageHelper.sendConfigured(sender, "messages.no_permission");
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.usage_give"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            this.messageHelper.sendConfigured(sender, "messages.player_not_found", java.util.Map.of("player", args[1]));
            return true;
        }

        int power;
        try {
            power = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            this.messageHelper.sendConfigured(sender, "messages.invalid_power");
            return true;
        }

        GemCreationResult result = this.gemService.createGem(args[2], power);
        if (!result.success()) {
            this.messageHelper.sendConfigured(sender, result.messagePath(), result.placeholders());
            return true;
        }

        ItemStack gem = result.gem();
        if (gem == null) {
            this.messageHelper.sendConfigured(sender, "messages.gem_create_failed");
            return true;
        }

        String enchantmentName = this.gemService.displayNameForInput(args[2]);
        target.getInventory().addItem(gem).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        this.messageHelper.sendConfigured(sender, "messages.gem_given_sender", java.util.Map.of(
            "player", target.getName(),
            "enchantment", args[2],
            "enchantment_name", enchantmentName,
            "power", Integer.toString(power)
        ));
        if (!sender.equals(target)) {
            this.messageHelper.sendConfigured(target, "messages.gem_received_target", java.util.Map.of(
                "enchantment", args[2],
                "enchantment_name", enchantmentName,
                "power", Integer.toString(power)
            ));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("gemenhancer.reload")) {
            this.messageHelper.sendConfigured(sender, "messages.no_permission");
            return true;
        }

        try {
            this.fileManager.load();
            this.messageHelper.sendConfigured(sender, "messages.reload_success");
        } catch (Exception exception) {
            this.messageHelper.sendConfigured(sender, "messages.reload_failed");
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.help_title"));
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.help_subtitle"));
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.usage_give"));
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.usage_upgrade"));
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.usage_forge"));
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.usage_reload"));
        sender.sendMessage(this.messageHelper.configuredComponent("commands.gem.help_footer"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "upgrade", "forge", "reload"), args[0]);
        }

        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> players = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> players.add(player.getName()));
            return filter(players, args[1]);
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filter(List.copyOf(this.gemService.suggestEnchantmentInputs()), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerInput))
            .sorted()
            .toList();
    }
}
