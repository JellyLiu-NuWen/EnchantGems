package org.nuwen.EnchantGems.gui;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import org.nuwen.EnchantGems.config.PluginFileManager;
import org.nuwen.EnchantGems.service.GemService;
import org.nuwen.EnchantGems.service.GemService.GemData;
import org.nuwen.EnchantGems.util.MessageHelper;

public final class ForgeMenuManager implements Listener {

    private static final int DEFAULT_MENU_SIZE = 27;
    private static final int DEFAULT_INPUT_SLOT = 11;
    private static final int DEFAULT_CONFIRM_SLOT = 13;
    private static final int DEFAULT_GEM_SLOT = 15;
    private static final List<String> DEFAULT_LAYOUT = List.of(
        "#########",
        "# `Tool` `Confirm` `Gem` #",
        "#########"
    );

    private final PluginFileManager fileManager;
    private final MessageHelper messageHelper;
    private final GemService gemService;
    private final Map<UUID, ForgeSession> sessions = new HashMap<>();

    public ForgeMenuManager(PluginFileManager fileManager, MessageHelper messageHelper, GemService gemService) {
        this.fileManager = fileManager;
        this.messageHelper = messageHelper;
        this.gemService = gemService;
    }

    public void openMenu(Player player) {
        ForgeSession previousSession = this.sessions.remove(player.getUniqueId());
        if (previousSession != null) {
            returnItems(previousSession, false);
        }

        MenuDefinition menu = resolveMenuDefinition();
        Inventory inventory = Bukkit.createInventory(null, menu.size(), menu.title());
        menu.staticItems().forEach((slot, item) -> inventory.setItem(slot, item.clone()));

        ForgeSession session = new ForgeSession(player.getUniqueId(), inventory, menu.layout());
        this.sessions.put(player.getUniqueId(), session);
        player.openInventory(inventory);
    }

    public void shutdown() {
        List<ForgeSession> openSessions = new ArrayList<>(this.sessions.values());
        this.sessions.clear();
        for (ForgeSession session : openSessions) {
            returnItems(session, false);
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ForgeSession session = getSession(player, event.getView().getTopInventory());
        if (session == null) {
            return;
        }

        if (shouldCancelGlobalClick(event)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot == session.layout().confirmSlot()) {
            event.setCancelled(true);
            attemptUpgrade(player, session);
            return;
        }

        if (rawSlot < event.getView().getTopInventory().getSize()) {
            if (!session.layout().interactiveSlots().contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }

            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ForgeSession session = getSession(player, event.getView().getTopInventory());
        if (session == null) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        ForgeSession session = getSession(player, event.getInventory());
        if (session == null) {
            return;
        }

        this.sessions.remove(player.getUniqueId());
        returnItems(session, true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ForgeSession session = this.sessions.remove(player.getUniqueId());
        if (session != null) {
            returnItems(session, false);
        }
    }

    private boolean shouldCancelGlobalClick(InventoryClickEvent event) {
        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();

        if (clickType == ClickType.DOUBLE_CLICK || clickType == ClickType.CREATIVE || clickType == ClickType.NUMBER_KEY || clickType == ClickType.SWAP_OFFHAND) {
            return true;
        }

        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || action == InventoryAction.COLLECT_TO_CURSOR
            || action == InventoryAction.HOTBAR_SWAP;
    }

    private void attemptUpgrade(Player player, ForgeSession session) {
        Inventory inventory = session.inventory();
        MenuLayout layout = session.layout();
        ItemStack tool = inventory.getItem(layout.inputSlot());
        ItemStack gemStack = inventory.getItem(layout.gemSlot());

        if (tool == null || tool.getType().isAir()) {
            fail(player, "messages.missing_weapon");
            return;
        }

        GemData gemData = this.gemService.readGem(gemStack);
        if (gemData == null) {
            fail(player, "messages.invalid_gem");
            return;
        }

        if (!tool.containsEnchantment(gemData.enchantment()) && !gemData.enchantment().canEnchantItem(tool)) {
            fail(player, "messages.gem_not_applicable");
            return;
        }

        if (!tool.containsEnchantment(gemData.enchantment()) && hasConflict(tool, gemData)) {
            fail(player, "messages.upgrade_fail_conflict");
            return;
        }

        int currentLevel = tool.getEnchantmentLevel(gemData.enchantment());
        long calculatedLevel = (long) currentLevel + gemData.power();
        int targetLevel = (int) Math.min(Integer.MAX_VALUE, calculatedLevel);
        if (calculatedLevel > this.gemService.maxAllowedLevel(gemData.enchantTypeId(), gemData.enchantment())) {
            fail(player, "messages.upgrade_fail_max_level");
            return;
        }

        tool.addUnsafeEnchantment(gemData.enchantment(), targetLevel);
        consumeGem(inventory, layout, gemStack);

        player.playSound(player.getLocation(), resolveSoundKey("sounds.success", "minecraft:entity.player.levelup"), 1.0F, 1.0F);
        this.messageHelper.sendConfigured(player, "messages.upgrade_success");
    }

    private boolean hasConflict(ItemStack tool, GemData gemData) {
        for (var entry : tool.getEnchantments().entrySet()) {
            if (entry.getKey().equals(gemData.enchantment())) {
                continue;
            }
            if (entry.getKey().conflictsWith(gemData.enchantment()) || gemData.enchantment().conflictsWith(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private void consumeGem(Inventory inventory, MenuLayout layout, ItemStack gemStack) {
        int remaining = gemStack.getAmount() - 1;
        if (remaining <= 0) {
            inventory.setItem(layout.gemSlot(), null);
            return;
        }

        gemStack.setAmount(remaining);
        inventory.setItem(layout.gemSlot(), gemStack);
    }

    private void fail(Player player, String messagePath) {
        player.playSound(player.getLocation(), resolveSoundKey("sounds.fail", "minecraft:entity.villager.no"), 1.0F, 1.0F);
        this.messageHelper.sendConfigured(player, messagePath);
    }

    private MenuDefinition resolveMenuDefinition() {
        if (menuConfig().contains("Layout") || menuConfig().contains("Icons") || menuConfig().contains("Title")) {
            return resolveTrMenuDefinition();
        }
        return fallbackDefinition();
    }

    private MenuDefinition resolveTrMenuDefinition() {
        List<String> rows = resolveLayoutRows();
        List<String> flatLayout = flattenLayout(rows);
        if (flatLayout.isEmpty()) {
            return fallbackDefinition();
        }

        Map<String, List<Integer>> positions = collectPositions(flatLayout);
        Map<Integer, ItemStack> staticItems = new LinkedHashMap<>();
        int inputSlot = -1;
        int gemSlot = -1;
        int confirmSlot = -1;

        ConfigurationSection icons = menuConfig().getConfigurationSection("Icons");
        if (icons != null) {
            for (String iconId : icons.getKeys(false)) {
                ConfigurationSection icon = icons.getConfigurationSection(iconId);
                if (icon == null) {
                    continue;
                }

                List<Integer> slots = positions.getOrDefault(iconId, List.of());
                if (slots.isEmpty()) {
                    continue;
                }

                String feature = normalizeFeature(icon.getString("feature", ""));
                ItemStack displayItem = buildDisplayItem(icon);
                switch (feature) {
                    case "tool", "toolslot", "tool-slot", "input", "inputslot", "input-slot" -> inputSlot = slots.getFirst();
                    case "gem", "gemslot", "gem-slot" -> gemSlot = slots.getFirst();
                    case "confirm", "confirmbutton", "confirm-button" -> {
                        confirmSlot = slots.getFirst();
                        if (displayItem != null) {
                            staticItems.put(confirmSlot, displayItem);
                        }
                    }
                    default -> {
                        if (displayItem != null) {
                            for (int slot : slots) {
                                staticItems.put(slot, displayItem);
                            }
                        }
                    }
                }
            }
        }

        if (!isValidLayout(rows.size(), inputSlot, confirmSlot, gemSlot)) {
            return fallbackDefinition();
        }

        Component title = this.messageHelper.deserialize(resolveAnimatedText(menuConfig().get("Title"), "&8武器强化"));
        return new MenuDefinition(
            title,
            rows.size() * 9,
            new MenuLayout(inputSlot, confirmSlot, gemSlot),
            staticItems
        );
    }

    private boolean isValidLayout(int rows, int inputSlot, int confirmSlot, int gemSlot) {
        int size = rows * 9;
        if (rows < 1 || rows > 6) {
            return false;
        }
        if (inputSlot < 0 || inputSlot >= size || confirmSlot < 0 || confirmSlot >= size || gemSlot < 0 || gemSlot >= size) {
            return false;
        }
        return inputSlot != confirmSlot && inputSlot != gemSlot && confirmSlot != gemSlot;
    }

    private List<String> resolveLayoutRows() {
        Object raw = menuConfig().get("Layout");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof List<?> nested) {
                return nested.stream().map(String::valueOf).toList();
            }
            return list.stream().map(String::valueOf).toList();
        }
        return DEFAULT_LAYOUT;
    }

    private List<String> flattenLayout(List<String> rows) {
        List<String> flat = new ArrayList<>();
        for (String row : rows) {
            List<String> tokens = tokenizeLayoutRow(row);
            if (tokens.size() != 9) {
                return List.of();
            }
            flat.addAll(tokens);
        }
        return flat;
    }

    private List<String> tokenizeLayoutRow(String row) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < row.length()) {
            char character = row.charAt(index);
            if (character == '`') {
                int end = row.indexOf('`', index + 1);
                if (end <= index + 1) {
                    return List.of();
                }
                tokens.add(row.substring(index + 1, end));
                index = end + 1;
                continue;
            }

            tokens.add(String.valueOf(character));
            index++;
        }
        return tokens;
    }

    private Map<String, List<Integer>> collectPositions(List<String> flatLayout) {
        Map<String, List<Integer>> positions = new HashMap<>();
        for (int slot = 0; slot < flatLayout.size(); slot++) {
            positions.computeIfAbsent(flatLayout.get(slot), ignored -> new ArrayList<>()).add(slot);
        }
        return positions;
    }

    private @Nullable ItemStack buildDisplayItem(ConfigurationSection icon) {
        ConfigurationSection display = icon.getConfigurationSection("display");
        if (display == null) {
            return null;
        }

        Material material = resolveMaterial(resolveAnimatedText(display.get("material"), "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = Objects.requireNonNull(item.getItemMeta());

        String name = resolveAnimatedText(display.get("name"), "");
        if (!name.isEmpty()) {
            meta.customName(this.messageHelper.deserialize(name));
        }

        List<String> lore = resolveAnimatedLore(display.get("lore"));
        if (!lore.isEmpty()) {
            meta.lore(this.messageHelper.deserializeList(lore));
        }

        item.setItemMeta(meta);
        return item;
    }

    private String resolveAnimatedText(@Nullable Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof String string) {
                return string;
            }
        }
        return fallback;
    }

    private List<String> resolveAnimatedLore(@Nullable Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof List<?> nested) {
                return nested.stream().map(String::valueOf).toList();
            }
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String normalizeFeature(String feature) {
        return feature.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").replace("-", "");
    }

    private Material resolveMaterial(String materialName, Material fallback) {
        if (materialName == null || materialName.isBlank()) {
            return fallback;
        }

        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            return material;
        }

        String normalized = materialName.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        material = Material.matchMaterial(normalized);
        return material == null ? fallback : material;
    }

    private MenuDefinition fallbackDefinition() {
        Map<Integer, ItemStack> staticItems = new LinkedHashMap<>();
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = Objects.requireNonNull(background.getItemMeta());
        backgroundMeta.customName(Component.text(" "));
        background.setItemMeta(backgroundMeta);

        for (int slot = 0; slot < DEFAULT_MENU_SIZE; slot++) {
            if (slot == DEFAULT_INPUT_SLOT || slot == DEFAULT_CONFIRM_SLOT || slot == DEFAULT_GEM_SLOT) {
                continue;
            }
            staticItems.put(slot, background);
        }

        ItemStack confirm = new ItemStack(Material.ANVIL);
        ItemMeta confirmMeta = Objects.requireNonNull(confirm.getItemMeta());
        confirmMeta.customName(this.messageHelper.deserialize("&a&l确认强化"));
        confirm.setItemMeta(confirmMeta);
        staticItems.put(DEFAULT_CONFIRM_SLOT, confirm);

        return new MenuDefinition(
            this.messageHelper.deserialize("&8武器强化"),
            DEFAULT_MENU_SIZE,
            new MenuLayout(DEFAULT_INPUT_SLOT, DEFAULT_CONFIRM_SLOT, DEFAULT_GEM_SLOT),
            staticItems
        );
    }

    private String resolveSoundKey(String path, String fallback) {
        String configured = this.fileManager.config().getString(path, fallback);
        String normalizedConfigured = normalizeSoundKey(configured);
        if (existsSound(normalizedConfigured)) {
            return normalizedConfigured;
        }
        return normalizeSoundKey(fallback);
    }

    private boolean existsSound(String soundKey) {
        NamespacedKey key = NamespacedKey.fromString(soundKey);
        if (key == null) {
            return false;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(key) != null;
    }

    private String normalizeSoundKey(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return "minecraft:entity.player.levelup";
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            return normalized;
        }
        if (normalized.contains(".")) {
            return "minecraft:" + normalized;
        }
        return "minecraft:" + normalized.replace('_', '.');
    }

    private FileConfiguration menuConfig() {
        return this.fileManager.menu();
    }

    private void returnItems(ForgeSession session, boolean notifyOnDrop) {
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            return;
        }

        boolean droppedAny = returnItem(player, session.inventory().getItem(session.layout().inputSlot()));
        droppedAny = returnItem(player, session.inventory().getItem(session.layout().gemSlot())) || droppedAny;
        session.inventory().setItem(session.layout().inputSlot(), null);
        session.inventory().setItem(session.layout().gemSlot(), null);

        if (notifyOnDrop && droppedAny) {
            this.messageHelper.sendConfigured(player, "messages.inventory_full_drop");
        }
    }

    private boolean returnItem(Player player, @Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack.clone());
        boolean dropped = !leftovers.isEmpty();
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return dropped;
    }

    private @Nullable ForgeSession getSession(Player player, Inventory topInventory) {
        ForgeSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            return null;
        }
        return session.inventory().equals(topInventory) ? session : null;
    }

    private record ForgeSession(UUID playerId, Inventory inventory, MenuLayout layout) {
    }

    private record MenuDefinition(Component title, int size, MenuLayout layout, Map<Integer, ItemStack> staticItems) {
    }

    private record MenuLayout(int inputSlot, int confirmSlot, int gemSlot) {

        private List<Integer> interactiveSlots() {
            return List.of(this.inputSlot, this.gemSlot);
        }
    }
}
