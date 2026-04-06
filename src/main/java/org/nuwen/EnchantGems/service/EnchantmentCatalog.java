package org.nuwen.EnchantGems.service;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.Nullable;

public final class EnchantmentCatalog {

    private static final Map<String, String> LEGACY_TO_PATH = Map.ofEntries(
        Map.entry("PROTECTION_ENVIRONMENTAL", "protection"),
        Map.entry("PROTECTION_FIRE", "fire_protection"),
        Map.entry("PROTECTION_FALL", "feather_falling"),
        Map.entry("PROTECTION_EXPLOSIONS", "blast_protection"),
        Map.entry("PROTECTION_PROJECTILE", "projectile_protection"),
        Map.entry("OXYGEN", "respiration"),
        Map.entry("WATER_WORKER", "aqua_affinity"),
        Map.entry("THORNS", "thorns"),
        Map.entry("DEPTH_STRIDER", "depth_strider"),
        Map.entry("FROST_WALKER", "frost_walker"),
        Map.entry("BINDING_CURSE", "binding_curse"),
        Map.entry("DAMAGE_ALL", "sharpness"),
        Map.entry("DAMAGE_UNDEAD", "smite"),
        Map.entry("DAMAGE_ARTHROPODS", "bane_of_arthropods"),
        Map.entry("KNOCKBACK", "knockback"),
        Map.entry("FIRE_ASPECT", "fire_aspect"),
        Map.entry("LOOT_BONUS_MOBS", "looting"),
        Map.entry("SWEEPING_EDGE", "sweeping_edge"),
        Map.entry("DIG_SPEED", "efficiency"),
        Map.entry("SILK_TOUCH", "silk_touch"),
        Map.entry("DURABILITY", "unbreaking"),
        Map.entry("LOOT_BONUS_BLOCKS", "fortune"),
        Map.entry("ARROW_DAMAGE", "power"),
        Map.entry("ARROW_KNOCKBACK", "punch"),
        Map.entry("ARROW_FIRE", "flame"),
        Map.entry("ARROW_INFINITE", "infinity"),
        Map.entry("LUCK", "luck_of_the_sea"),
        Map.entry("LURE", "lure"),
        Map.entry("LOYALTY", "loyalty"),
        Map.entry("IMPALING", "impaling"),
        Map.entry("RIPTIDE", "riptide"),
        Map.entry("CHANNELING", "channeling"),
        Map.entry("MULTISHOT", "multishot"),
        Map.entry("QUICK_CHARGE", "quick_charge"),
        Map.entry("PIERCING", "piercing"),
        Map.entry("DENSITY", "density"),
        Map.entry("BREACH", "breach"),
        Map.entry("WIND_BURST", "wind_burst"),
        Map.entry("MENDING", "mending"),
        Map.entry("VANISHING_CURSE", "vanishing_curse"),
        Map.entry("SOUL_SPEED", "soul_speed"),
        Map.entry("SWIFT_SNEAK", "swift_sneak")
    );

    private final Map<String, String> pathToLegacyId;

    public EnchantmentCatalog() {
        this.pathToLegacyId = new LinkedHashMap<>();
        LEGACY_TO_PATH.forEach((legacyId, path) -> this.pathToLegacyId.put(path, legacyId));
    }

    public @Nullable ResolvedEnchantment resolve(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String path = LEGACY_TO_PATH.get(upper);
        if (path != null) {
            return buildResolved(upper, NamespacedKey.minecraft(path));
        }

        NamespacedKey namespacedKey = toNamespacedKey(trimmed);
        if (namespacedKey == null) {
            return null;
        }

        Enchantment enchantment = registry().get(namespacedKey);
        if (enchantment == null) {
            return null;
        }

        String canonicalId = this.pathToLegacyId.getOrDefault(namespacedKey.getKey(), namespacedKey.asString().toUpperCase(Locale.ROOT));
        return new ResolvedEnchantment(canonicalId, enchantment);
    }

    public Collection<String> suggestions() {
        Set<String> suggestions = new LinkedHashSet<>(LEGACY_TO_PATH.keySet());
        registry().forEach(enchantment -> suggestions.add(Objects.requireNonNull(enchantment.getKey()).asString()));
        return suggestions;
    }

    private @Nullable ResolvedEnchantment buildResolved(String legacyId, NamespacedKey key) {
        Enchantment enchantment = registry().get(key);
        return enchantment == null ? null : new ResolvedEnchantment(legacyId, enchantment);
    }

    private @Nullable NamespacedKey toNamespacedKey(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            return NamespacedKey.fromString(normalized);
        }
        return NamespacedKey.minecraft(normalized);
    }

    private org.bukkit.Registry<Enchantment> registry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    public record ResolvedEnchantment(String canonicalId, Enchantment enchantment) {
    }
}
