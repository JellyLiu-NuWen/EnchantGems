package org.nuwen.EnchantGems.service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.nuwen.EnchantGems.GemEnhancerPlugin;
import org.nuwen.EnchantGems.config.PluginFileManager;
import org.nuwen.EnchantGems.service.EnchantmentCatalog.ResolvedEnchantment;
import org.nuwen.EnchantGems.util.MessageHelper;

public final class GemService {

    private static final List<ItemCategory> ITEM_CATEGORIES = List.of(
        new ItemCategory("剑", Material.DIAMOND_SWORD),
        new ItemCategory("斧", Material.DIAMOND_AXE),
        new ItemCategory("镐", Material.DIAMOND_PICKAXE),
        new ItemCategory("铲", Material.DIAMOND_SHOVEL),
        new ItemCategory("锄", Material.DIAMOND_HOE),
        new ItemCategory("弓", Material.BOW),
        new ItemCategory("弩", Material.CROSSBOW),
        new ItemCategory("三叉戟", Material.TRIDENT),
        new ItemCategory("狼牙棒", Material.MACE),
        new ItemCategory("钓鱼竿", Material.FISHING_ROD),
        new ItemCategory("剪刀", Material.SHEARS),
        new ItemCategory("头盔", Material.DIAMOND_HELMET),
        new ItemCategory("胸甲", Material.DIAMOND_CHESTPLATE),
        new ItemCategory("护腿", Material.DIAMOND_LEGGINGS),
        new ItemCategory("靴子", Material.DIAMOND_BOOTS),
        new ItemCategory("鞘翅", Material.ELYTRA),
        new ItemCategory("盾牌", Material.SHIELD)
    );

    private final GemEnhancerPlugin plugin;
    private final PluginFileManager fileManager;
    private final MessageHelper messageHelper;
    private final EnchantmentCatalog enchantmentCatalog;
    private final NamespacedKey isGemKey;
    private final NamespacedKey enchantTypeKey;
    private final NamespacedKey powerKey;

    public GemService(GemEnhancerPlugin plugin, PluginFileManager fileManager, MessageHelper messageHelper, EnchantmentCatalog enchantmentCatalog) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.messageHelper = messageHelper;
        this.enchantmentCatalog = enchantmentCatalog;
        this.isGemKey = new NamespacedKey(plugin, "is_gem");
        this.enchantTypeKey = new NamespacedKey(plugin, "enchant_type");
        this.powerKey = new NamespacedKey(plugin, "power");
    }

    public GemCreationResult createGem(String enchantmentInput, int power) {
        if (power <= 0) {
            return GemCreationResult.failure("messages.invalid_power", Map.of());
        }

        ResolvedEnchantment resolved = this.enchantmentCatalog.resolve(enchantmentInput);
        if (resolved == null) {
            return GemCreationResult.failure("messages.unknown_enchantment", Map.of("enchantment", enchantmentInput));
        }

        if (!isGemEnabled(resolved.canonicalId())) {
            return GemCreationResult.failure("messages.gem_disabled", Map.of("enchantment", resolved.canonicalId()));
        }

        Map<String, String> placeholders = buildPlaceholders(resolved.canonicalId(), power);
        ItemStack gem = new ItemStack(resolveMaterial(resolved.canonicalId()));
        ItemMeta meta = Objects.requireNonNull(gem.getItemMeta());
        meta.customName(this.messageHelper.deserialize(resolveNameTemplate(resolved.canonicalId(), placeholders)));

        List<String> loreTemplate = resolveLoreTemplate(resolved.canonicalId());
        if (!loreTemplate.isEmpty()) {
            meta.lore(this.messageHelper.deserializeList(loreTemplate, placeholders));
        }

        if (resolveGlint(resolved.canonicalId())) {
            meta.setEnchantmentGlintOverride(true);
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(this.isGemKey, PersistentDataType.BYTE, (byte) 1);
        container.set(this.enchantTypeKey, PersistentDataType.STRING, resolved.canonicalId());
        container.set(this.powerKey, PersistentDataType.INTEGER, power);

        gem.setItemMeta(meta);
        return GemCreationResult.success(gem);
    }

    public @Nullable GemData readGem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte isGem = container.get(this.isGemKey, PersistentDataType.BYTE);
        String enchantType = container.get(this.enchantTypeKey, PersistentDataType.STRING);
        Integer power = container.get(this.powerKey, PersistentDataType.INTEGER);

        if (isGem == null || isGem != (byte) 1 || enchantType == null || power == null || power <= 0) {
            return null;
        }

        if (!isGemEnabled(enchantType)) {
            return null;
        }

        ResolvedEnchantment resolved = this.enchantmentCatalog.resolve(enchantType);
        if (resolved == null) {
            return null;
        }

        return new GemData(resolved.canonicalId(), resolved.enchantment(), power);
    }

    public int maxAllowedLevel(String canonicalId, Enchantment enchantment) {
        ConfigurationSection section = gemSection(canonicalId);
        int specific = section == null ? Integer.MIN_VALUE : section.getInt("max_level", Integer.MIN_VALUE);
        if (specific != Integer.MIN_VALUE && specific > 0) {
            return specific;
        }
        if (specific == -1) {
            return Integer.MAX_VALUE;
        }

        ConfigurationSection defaultSection = defaultSection();
        int fallback = defaultSection == null ? -1 : defaultSection.getInt("max_level", -1);
        if (fallback > 0) {
            return fallback;
        }
        return Integer.MAX_VALUE;
    }

    public Collection<String> suggestEnchantmentInputs() {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>(this.enchantmentCatalog.suggestions());
        ConfigurationSection gemsSection = this.fileManager.gems().getConfigurationSection("gems");
        if (gemsSection != null) {
            suggestions.addAll(gemsSection.getKeys(false));
        }
        return suggestions;
    }

    public String displayNameForInput(String enchantmentInput) {
        ResolvedEnchantment resolved = this.enchantmentCatalog.resolve(enchantmentInput);
        return resolved == null ? enchantmentInput : resolveDisplayName(resolved.canonicalId());
    }

    private Map<String, String> buildPlaceholders(String canonicalId, int power) {
        return Map.of(
            "enchant_id", canonicalId,
            "enchant_name", resolveDisplayName(canonicalId),
            "applicable_items", resolveApplicableItems(this.enchantmentCatalog.resolve(canonicalId)),
            "power", Integer.toString(power)
        );
    }

    private String resolveApplicableItems(@Nullable ResolvedEnchantment resolved) {
        if (resolved == null) {
            return "未知";
        }

        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (ItemCategory category : ITEM_CATEGORIES) {
            if (resolved.enchantment().canEnchantItem(new ItemStack(category.material()))) {
                labels.add(category.label());
            }
        }

        boolean fullArmor = labels.contains("头盔") && labels.contains("胸甲") && labels.contains("护腿") && labels.contains("靴子");
        List<String> ordered = new ArrayList<>();
        if (fullArmor) {
            ordered.add("护甲");
        }

        for (ItemCategory category : ITEM_CATEGORIES) {
            String label = category.label();
            if (fullArmor && (label.equals("头盔") || label.equals("胸甲") || label.equals("护腿") || label.equals("靴子"))) {
                continue;
            }
            if (labels.contains(label)) {
                ordered.add(label);
            }
        }

        return ordered.isEmpty() ? "通用装备" : String.join(" / ", ordered);
    }

    private boolean isGemEnabled(String canonicalId) {
        ConfigurationSection section = gemSection(canonicalId);
        return section == null || section.getBoolean("enabled", true);
    }

    private Material resolveMaterial(String canonicalId) {
        String materialName = firstNonBlank(
            value(canonicalId, "material"),
            defaultValue("material"),
            Material.AMETHYST_SHARD.name()
        );
        Material material = Material.matchMaterial(materialName);
        return material == null ? Material.AMETHYST_SHARD : material;
    }

    private String resolveNameTemplate(String canonicalId, Map<String, String> placeholders) {
        String template = firstNonBlank(
            value(canonicalId, "name"),
            defaultValue("name"),
            "&b{enchant_name}宝石 &7+{power}"
        );
        return this.messageHelper.applyPlaceholders(template, placeholders);
    }

    private List<String> resolveLoreTemplate(String canonicalId) {
        ConfigurationSection section = gemSection(canonicalId);
        List<String> specific = section == null ? List.of() : section.getStringList("lore");
        if (!specific.isEmpty()) {
            return specific;
        }
        ConfigurationSection defaultSection = defaultSection();
        return defaultSection == null ? List.of() : defaultSection.getStringList("lore");
    }

    private boolean resolveGlint(String canonicalId) {
        ConfigurationSection section = gemSection(canonicalId);
        if (section != null && section.contains("glint")) {
            return section.getBoolean("glint");
        }
        ConfigurationSection defaultSection = defaultSection();
        return defaultSection == null || defaultSection.getBoolean("glint", true);
    }

    private String resolveDisplayName(String canonicalId) {
        return firstNonBlank(value(canonicalId, "display_name"), prettyName(canonicalId));
    }

    private @Nullable ConfigurationSection gemSection(String canonicalId) {
        return this.fileManager.gems().getConfigurationSection("gems." + canonicalId);
    }

    private @Nullable ConfigurationSection defaultSection() {
        return this.fileManager.gems().getConfigurationSection("default_gem");
    }

    private @Nullable String value(String canonicalId, String path) {
        ConfigurationSection section = gemSection(canonicalId);
        return section == null ? null : section.getString(path);
    }

    private @Nullable String defaultValue(String path) {
        ConfigurationSection section = defaultSection();
        return section == null ? null : section.getString(path);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String prettyName(String canonicalId) {
        String[] parts = canonicalId.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public record GemData(String enchantTypeId, Enchantment enchantment, int power) {
    }

    public record GemCreationResult(boolean success, @Nullable ItemStack gem, @Nullable String messagePath, Map<String, String> placeholders) {

        public static GemCreationResult success(ItemStack gem) {
            return new GemCreationResult(true, gem, null, Map.of());
        }

        public static GemCreationResult failure(String messagePath, Map<String, String> placeholders) {
            return new GemCreationResult(false, null, messagePath, placeholders);
        }
    }

    private record ItemCategory(String label, Material material) {
    }
}
