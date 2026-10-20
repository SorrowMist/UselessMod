package com.sorrowmist.useless.compat.forge1201.fluid;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import java.util.function.Predicate;

/** Immutable union of fluid IDs, live tag references, or exact/partial native NBT predicates. */
public final class FluidIngredient implements Predicate<FluidStack> {
    public static final Codec<FluidIngredient> CODEC = RecipeCodecs.jsonCodec(FluidIngredient::fromJson,FluidIngredient::toJson);
    public static final StreamCodec<FriendlyByteBuf,FluidIngredient> STREAM_CODEC = StreamCodec.of(
            (buf,value) -> buf.writeUtf(value.toJson().toString(),1_048_576),
            buf -> fromJson(JsonParser.parseString(buf.readUtf(1_048_576))));
    private enum Kind { FLUID, TAG, SAME_FLUID, SAME_TAG, NBT, MERGE_NBT, ANY }
    private final Kind kind;
    private final ResourceLocation id;
    private final CompoundTag nbt;
    private final boolean strict;
    private final List<FluidIngredient> children;
    private FluidIngredient(Kind kind, ResourceLocation id, CompoundTag nbt, boolean strict, List<FluidIngredient> children) {
        this.kind=kind; this.id=id; this.nbt=nbt==null?null:nbt.copy(); this.strict=strict;
        this.children=List.copyOf(children);
    }
    public static FluidIngredient single(Fluid fluid) {
        if (fluid==Fluids.EMPTY) return any(List.of());
        ResourceLocation id=BuiltInRegistries.FLUID.getKey(fluid);
        if (id==null) throw new IllegalArgumentException("Unregistered fluid");
        return new FluidIngredient(Kind.FLUID,id,null,false,List.of());
    }
    /** As with an ordinary item ingredient, single(stack) does not imply an NBT predicate. */
    public static FluidIngredient single(FluidStack stack) { return single(stack.getFluid()); }
    public static FluidIngredient tag(TagKey<Fluid> tag) {
        if (!tag.isFor(Registries.FLUID)) throw new IllegalArgumentException("Not a fluid tag");
        return new FluidIngredient(Kind.TAG,tag.location(),null,false,List.of());
    }
    public static FluidIngredient nbt(boolean strict, FluidStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty NBT fluid ingredient");
        return new FluidIngredient(Kind.NBT,BuiltInRegistries.FLUID.getKey(stack.getFluid()),stack.getTag(),strict,List.of());
    }
    public static FluidIngredient any(List<FluidIngredient> choices) {
        Objects.requireNonNull(choices);
        return choices.size()==1?choices.get(0):new FluidIngredient(Kind.ANY,null,null,false,choices);
    }
    @Override public boolean test(FluidStack stack) {
        if (stack==null || stack.isEmpty()) return false;
        return switch(kind) {
            case FLUID -> id.equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
            case SAME_FLUID -> BuiltInRegistries.FLUID.get(id).isSame(stack.getFluid());
            case SAME_TAG -> {
                boolean matched=false;
                for(var member:BuiltInRegistries.FLUID.getTagOrEmpty(TagKey.create(Registries.FLUID,id)))
                    if(member.value().isSame(stack.getFluid())) { matched=true; break; }
                yield matched;
            }
            case TAG -> stack.getFluid().builtInRegistryHolder().is(TagKey.create(Registries.FLUID,id));
            case NBT -> id.equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()))
                    && (strict ? Objects.equals(nbt,stack.getTag()) : NbtUtils.compareNbt(nbt,stack.getTag(),true));
            case MERGE_NBT -> id.equals(BuiltInRegistries.FLUID.getKey(stack.getFluid()))
                    && (nbt==null || nbt.isEmpty() || stack.getTag()!=null
                    && stack.getTag().copy().merge(nbt).equals(stack.getTag()));
            case ANY -> children.stream().anyMatch(child -> child.test(stack));
        };
    }
    /** A temporarily unbound/empty tag is NOT an empty expression and must survive reloads. */
    public boolean isEmpty() { return kind==Kind.ANY && children.stream().allMatch(FluidIngredient::isEmpty); }
    /** Family predicates use the general path: a single representative omits flowing variants. */
    public boolean isSimple() {
        return switch(kind) {
            case FLUID, TAG -> true;
            case ANY -> children.stream().allMatch(FluidIngredient::isSimple);
            default -> false;
        };
    }
    public FluidStack[] getStacks() {
        if(kind==Kind.ANY) return children.stream().flatMap(c -> Arrays.stream(c.getStacks())).toArray(FluidStack[]::new);
        if(kind==Kind.TAG || kind==Kind.SAME_TAG) {
            List<FluidStack> result=new ArrayList<>();
            for(var holder:BuiltInRegistries.FLUID.getTagOrEmpty(TagKey.create(Registries.FLUID,id)))
                if(holder.value()!=Fluids.EMPTY) result.add(new FluidStack(holder.value(),1));
            return result.toArray(FluidStack[]::new);
        }
        FluidStack stack=new FluidStack(BuiltInRegistries.FLUID.get(id),1);
        if(nbt!=null) stack.setTag(nbt.copy());
        return stack.isEmpty()?new FluidStack[0]:new FluidStack[]{stack};
    }
    public JsonElement toJson() {
        if(kind==Kind.ANY) {
            JsonArray array=new JsonArray(); children.forEach(child -> array.add(child.toJson())); return array;
        }
        JsonObject json=new JsonObject();
        json.addProperty((kind==Kind.TAG || kind==Kind.SAME_TAG)?"tag":"fluid",id.toString());
        if(kind==Kind.NBT || kind==Kind.MERGE_NBT) {
            json.addProperty("type",kind==Kind.MERGE_NBT?"useless_mod:merge_nbt_fluid":"useless_mod:nbt_fluid"); json.addProperty("strict",strict);
            if(nbt!=null) json.addProperty("nbt",com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical.snbt(nbt));
        }
        if(kind==Kind.SAME_FLUID || kind==Kind.SAME_TAG)
            json.addProperty("type",kind==Kind.SAME_TAG?"useless_mod:same_fluid_tag":"useless_mod:same_fluid");
        return json;
    }
    public static FluidIngredient fromJson(JsonElement value) { return parse(value,0); }
    private static FluidIngredient parse(JsonElement value,int depth) {
        if(depth>64) throw new JsonSyntaxException("Fluid ingredient nesting exceeds 64");
        if(value.isJsonArray()) {
            if(value.getAsJsonArray().size()>4096) throw new JsonSyntaxException("Too many fluid choices");
            List<FluidIngredient> result=new ArrayList<>();
            for(JsonElement child:value.getAsJsonArray()) result.add(parse(child,depth+1));
            return any(result);
        }
        if(!value.isJsonObject()) throw new JsonSyntaxException("Expected a fluid ingredient object/array");
        JsonObject json=value.getAsJsonObject();
        if(json.has("components") && json.getAsJsonObject("components").size()>0)
            throw new JsonSyntaxException("Use native 1.20.1 NBT instead of components");
        String type=json.has("type")?json.get("type").getAsString():"";
        if(type.equals("neoforge:compound") || type.equals("useless_mod:compound_fluid"))
            return parse(json.get("children"),depth+1);
        if(!type.isEmpty() && !type.equals("useless_mod:nbt_fluid") && !type.equals("useless_mod:merge_nbt_fluid") && !type.equals("useless_mod:same_fluid") && !type.equals("useless_mod:same_fluid_tag"))
            throw new JsonSyntaxException("Unsupported fluid predicate type: "+type);
        if(json.has("tag")) {
            if(json.has("fluid") || json.has("nbt")) throw new JsonSyntaxException("Ambiguous fluid tag predicate");
            if(type.equals("useless_mod:same_fluid_tag"))
                return new FluidIngredient(Kind.SAME_TAG,new ResourceLocation(json.get("tag").getAsString()),null,false,List.of());
            return tag(TagKey.create(Registries.FLUID,new ResourceLocation(json.get("tag").getAsString())));
        }
        ResourceLocation id=new ResourceLocation(json.get("fluid").getAsString());
        if(!BuiltInRegistries.FLUID.containsKey(id) || BuiltInRegistries.FLUID.get(id)==Fluids.EMPTY)
            throw new JsonSyntaxException("Unknown fluid ingredient: "+id);
        if(type.equals("useless_mod:nbt_fluid") || type.equals("useless_mod:merge_nbt_fluid") || json.has("nbt")) {
            CompoundTag tag=json.has("nbt")?CraftingHelper.getNBT(json.get("nbt")):null;
            return new FluidIngredient(type.equals("useless_mod:merge_nbt_fluid")?Kind.MERGE_NBT:Kind.NBT,id,tag,!json.has("strict") || json.get("strict").getAsBoolean(),List.of());
        }
        if(type.equals("useless_mod:same_fluid")) return new FluidIngredient(Kind.SAME_FLUID,id,null,false,List.of());
        return single(BuiltInRegistries.FLUID.get(id));
    }
    /** Structural tag introspection without replacing NBT predicates by enumerated members. */
    public java.util.Optional<List<TagKey<Fluid>>> tagOnlyTags() {
        if(kind==Kind.TAG) return java.util.Optional.of(List.of(TagKey.create(Registries.FLUID,id)));
        if(kind!=Kind.ANY || children.isEmpty()) return java.util.Optional.empty();
        var tags=new java.util.LinkedHashSet<TagKey<Fluid>>();
        for(var child:children) {
            var nested=child.tagOnlyTags();
            if(nested.isEmpty()) return java.util.Optional.empty();
            tags.addAll(nested.get());
        }
        return java.util.Optional.of(List.copyOf(tags));
    }
    @Override public boolean equals(Object other) {
        return other instanceof FluidIngredient value && kind==value.kind && Objects.equals(id,value.id)
                && Objects.equals(nbt,value.nbt) && strict==value.strict && children.equals(value.children);
    }
    @Override public int hashCode() { return Objects.hash(kind,id,nbt,strict,children); }
}
