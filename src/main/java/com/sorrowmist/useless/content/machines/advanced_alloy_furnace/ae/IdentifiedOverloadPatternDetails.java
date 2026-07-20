package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moakiee.ae2lt.overload.pattern.Ae2OverloadPatternDetails;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.overload.pattern.PatternExecutionHostKind;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Adds the complete encoded pattern definition to AE2LT's runtime identity. */
final class IdentifiedOverloadPatternDetails implements IPatternDetails, OverloadedProviderOnlyPatternDetails {
    private static final String FINGERPRINT_SEPARATOR = "|definition_sha256=";

    private final Ae2OverloadPatternDetails delegate;
    private final String identity;

    IdentifiedOverloadPatternDetails(
            Ae2OverloadPatternDetails delegate, HolderLookup.Provider registries) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.identity = delegate.overloadPatternIdentity()
                + FINGERPRINT_SEPARATOR
                + definitionFingerprint(delegate.getDefinition(), registries);
    }

    @Override
    public AEItemKey getDefinition() {
        return delegate.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return delegate.getInputs();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return delegate.getOutputs();
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return delegate.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        delegate.pushInputsToExternalInventory(inputHolder, inputSink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return delegate.getTooltip(level, flags);
    }

    @Override
    public PatternExecutionHostKind requiredHostKind() {
        return delegate.requiredHostKind();
    }

    @Override
    public String overloadPatternIdentity() {
        return identity;
    }

    @Override
    public OverloadPatternDetails overloadPatternDetailsView() {
        return delegate.overloadPatternDetailsView();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || obj instanceof IdentifiedOverloadPatternDetails other
                && delegate.equals(other.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    static String definitionFingerprint(AEItemKey definition, HolderLookup.Provider registries) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(registries, "registries");

        JsonElement encoded = AEItemKey.CODEC.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE), definition).getOrThrow();
        byte[] canonicalBytes = canonicalize(encoded).toString().getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var entry : element.getAsJsonObject().entrySet()) {
                sorted.put(entry.getKey(), entry.getValue());
            }
            JsonObject result = new JsonObject();
            for (var entry : sorted.entrySet()) {
                result.add(entry.getKey(), canonicalize(entry.getValue()));
            }
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(canonicalize(child));
            }
            return result;
        }
        return element.deepCopy();
    }
}
