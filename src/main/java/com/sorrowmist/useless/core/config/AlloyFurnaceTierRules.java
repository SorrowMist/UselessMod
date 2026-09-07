package com.sorrowmist.useless.core.config;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Resolves the configured minimum tier for an alloy-furnace recipe id. */
public final class AlloyFurnaceTierRules {
    public static final int NO_MACHINE_TIER = -1;
    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 10;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile List<String> cachedConfiguration = List.of();
    private static volatile List<Rule> cachedRules = List.of();
    private static volatile long configurationVersion;

    private AlloyFurnaceTierRules() {
    }

    public static int requiredTier(ResourceLocation recipeId) {
        if (recipeId == null) return 0;

        List<Rule> rules = rules();
        String id = recipeId.toString();
        Rule best = null;
        for (Rule rule : rules) {
            if (rule.matches(id) && (best == null || rule.isMoreSpecificThan(best))) {
                best = rule;
            }
        }
        return best == null ? 0 : best.tier();
    }

    public static boolean allows(ResourceLocation recipeId, int machineTier) {
        return machineTier == NO_MACHINE_TIER
                || machineTier >= requiredTier(recipeId);
    }

    /** Changes whenever the raw config list changes, so recipe caches can include it. */
    public static long configurationVersion() {
        rules();
        return configurationVersion;
    }

    public static boolean isValidEntry(Object entry) {
        if (!(entry instanceof String value)) return false;

        String[] parts = value.split(",", -1);
        String pattern = parts.length == 2
                ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
        if (parts.length != 2 || !isValidPattern(pattern)) return false;

        try {
            int tier = Integer.parseInt(parts[1].trim());
            return tier >= MIN_TIER && tier <= MAX_TIER;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static List<Rule> rules() {
        List<String> configured = ConfigManager.getFurnaceRecipeTierRules();
        if (configured.equals(cachedConfiguration)) return cachedRules;

        synchronized (AlloyFurnaceTierRules.class) {
            if (!configured.equals(cachedConfiguration)) {
                List<Rule> parsed = new ArrayList<>();
                for (String entry : configured) {
                    Rule rule = parse(entry);
                    if (rule != null) parsed.add(rule);
                }
                cachedRules = List.copyOf(parsed);
                cachedConfiguration = configured;
                configurationVersion++;
            }
            return cachedRules;
        }
    }

    private static Rule parse(String entry) {
        String[] parts = entry.split(",", -1);
        if (parts.length != 2) {
            LOGGER.warn("Ignoring invalid alloy-furnace tier rule '{}': expected pattern,tier", entry);
            return null;
        }

        String pattern = parts[0].trim().toLowerCase(Locale.ROOT);
        int tier;
        try {
            tier = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException exception) {
            LOGGER.warn("Ignoring invalid alloy-furnace tier rule '{}': tier is not an integer", entry);
            return null;
        }
        if (!isValidPattern(pattern)) {
            LOGGER.warn("Ignoring invalid alloy-furnace tier rule '{}': invalid recipe id pattern", entry);
            return null;
        }
        if (tier < MIN_TIER || tier > MAX_TIER) {
            LOGGER.warn("Ignoring invalid alloy-furnace tier rule '{}': tier must be between {} and {}",
                    entry, MIN_TIER, MAX_TIER);
            return null;
        }

        StringBuilder regex = new StringBuilder("^");
        int literalLength = 0;
        boolean wildcard = false;
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == '*') {
                regex.append(".*");
                wildcard = true;
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
                literalLength++;
            }
        }
        regex.append('$');
        return new Rule(pattern, tier, wildcard, literalLength, Pattern.compile(regex.toString()));
    }

    private static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) return false;

        int namespaceSeparators = 0;
        for (int index = 0; index < pattern.length(); index++) {
            char character = pattern.charAt(index);
            if (character == '*') continue;
            if (character == ':') {
                if (++namespaceSeparators > 1) return false;
                continue;
            }
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.' || character == '/') {
                continue;
            }
            return false;
        }
        return true;
    }

    private record Rule(
            String pattern,
            int tier,
            boolean wildcard,
            int literalLength,
            Pattern matcher) {
        private boolean matches(String recipeId) {
            return matcher.matcher(recipeId).matches();
        }

        private boolean isMoreSpecificThan(Rule other) {
            if (wildcard != other.wildcard) return !wildcard;
            if (literalLength != other.literalLength) return literalLength > other.literalLength;
            return true;
        }
    }
}
