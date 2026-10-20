package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Describes how an AE pattern output identifies a converted furnace recipe.
 * Amounts are intentionally excluded because smart-doubling scaling is represented by the
 * operation count rather than by a different recipe identity.
 */
public record RecipeOutputConstraint(AEKey key, MatchMode matchMode) {

    public RecipeOutputConstraint {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(matchMode, "matchMode");
        if (matchMode == MatchMode.ITEM_ID && !(key instanceof AEItemKey)) {
            matchMode = MatchMode.EXACT;
        }
    }

    public static RecipeOutputConstraint exact(AEKey key) {
        return new RecipeOutputConstraint(key, MatchMode.EXACT);
    }

    public static RecipeOutputConstraint itemId(AEKey key) {
        return new RecipeOutputConstraint(key, MatchMode.ITEM_ID);
    }

    public static List<RecipeOutputConstraint> exact(List<GenericStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }

        List<RecipeOutputConstraint> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null) {
                result.add(exact(stack.what()));
            }
        }
        return List.copyOf(result);
    }

    public boolean matches(AEKey candidate) {
        if (candidate == null) {
            return false;
        }
        if (matchMode == MatchMode.EXACT) {
            return key.equals(candidate);
        }
        return key instanceof AEItemKey expectedItem
                && candidate instanceof AEItemKey candidateItem
                && expectedItem.getItem() == candidateItem.getItem();
    }

    public enum MatchMode {
        EXACT,
        ITEM_ID
    }
}
