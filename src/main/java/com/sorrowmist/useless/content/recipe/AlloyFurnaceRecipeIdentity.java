package com.sorrowmist.useless.content.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record AlloyFurnaceRecipeIdentity(ResourceLocation recipeId, String fingerprint) {
    public AlloyFurnaceRecipeIdentity {
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.isBlank()) throw new IllegalArgumentException("Recipe fingerprint cannot be blank");
    }
}
