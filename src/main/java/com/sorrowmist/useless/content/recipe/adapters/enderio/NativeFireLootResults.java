package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.base.common.recipe.FireCraftingRecipe;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.TagParser;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

/** Native EnderIO fire recipes reference server-data loot tables instead of inline results. */
final class NativeFireLootResults {
    record Result(ItemStack result,int minCount,int maxCount,double chance) {}
    static List<Result> read(FireCraftingRecipe recipe,Level level) {
        var server=level==null?null:level.getServer();
        if(server==null) server=ServerLifecycleHooks.getCurrentServer();
        if(server==null) return List.of();
        ResourceLocation id=recipe.getLootTable();
        var resource=server.getResourceManager().getResource(new ResourceLocation(id.getNamespace(),"loot_tables/"+id.getPath()+".json"));
        if(resource.isEmpty()) return List.of();
        try(var reader=resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject(),recipe.getMaxItemDrops());
        } catch(Exception unsupported) { throw new IllegalArgumentException("Cannot convert EnderIO fire loot "+id,unsupported); }
    }
    static List<Result> parse(JsonObject table,int maximum) throws Exception {
        if(table.has("functions")) throw new IllegalArgumentException("Table functions require runtime loot context");
        var results=new ArrayList<Result>();
        long maximumTotal=0;
        for(var poolElement:table.getAsJsonArray("pools")) {
            var pool=poolElement.getAsJsonObject();
            if(pool.has("functions")) throw new IllegalArgumentException("Pool functions require runtime loot context");
            // Multiple weighted entries with independent conditions need a joint probability model.
            var entries=pool.getAsJsonArray("entries");
            if(entries.size()!=1) throw new IllegalArgumentException("Unsupported conditional weighted fire pool");
            double chance=conditions(pool);
            if(pool.has("bonus_rolls") && pool.get("bonus_rolls").getAsDouble()!=0) throw new IllegalArgumentException("Luck-dependent fire pool");
            double rolls=pool.get("rolls").getAsDouble();
            if(rolls!=1) throw new IllegalArgumentException("Unsupported fire pool rolls: "+rolls);
            var entry=entries.get(0).getAsJsonObject(); chance*=conditions(entry);
            if(!"minecraft:item".equals(entry.get("type").getAsString())) throw new IllegalArgumentException("Non-item fire entry");
            var id=new ResourceLocation(entry.get("name").getAsString());
            var item=BuiltInRegistries.ITEM.getOptional(id).orElseThrow(()->new IllegalArgumentException("Unknown fire output: "+id));
            var stack=new ItemStack(item); int min=1,max=1;
            if(entry.has("functions")) for(var element:entry.getAsJsonArray("functions")) {
                var function=element.getAsJsonObject();
                if(function.has("conditions")) throw new IllegalArgumentException("Conditional fire function");
                switch(function.get("function").getAsString()) {
                    case "minecraft:set_count" -> {
                        if(function.has("add") && function.get("add").getAsBoolean()) throw new IllegalArgumentException("Additive count");
                        var count=function.get("count");
                        if(count.isJsonPrimitive()) min=max=count.getAsInt();
                        else {
                            var range=count.getAsJsonObject();
                            if(range.has("type") && !range.get("type").getAsString().equals("minecraft:uniform")) throw new IllegalArgumentException("Context fire count");
                            min=range.get("min").getAsInt();max=range.get("max").getAsInt();
                        }
                    }
                    case "minecraft:set_nbt" -> stack.setTag(TagParser.parseTag(function.get("tag").getAsString()));
                    default -> throw new IllegalArgumentException("Unknown fire loot function");
                }
            }
            if(min<0 || max<min || max>maximum || !Double.isFinite(chance) || chance<0 || chance>1) throw new IllegalArgumentException("Invalid or capped fire loot quantity");
            maximumTotal+=max;
            if(maximumTotal>maximum) throw new IllegalArgumentException("Combined fire pools exceed native drop cap");
            results.add(new Result(stack,min,max,chance));
        }
        return List.copyOf(results);
    }
    private static double conditions(JsonObject object) {
        double chance=1;
        if(object.has("conditions")) for(var element:object.getAsJsonArray("conditions")) {
            var condition=element.getAsJsonObject();
            if(!"minecraft:random_chance".equals(condition.get("condition").getAsString())) throw new IllegalArgumentException("Fire condition requires runtime context");
            double p=condition.get("chance").getAsDouble();
            if(!Double.isFinite(p) || p<0 || p>1) throw new IllegalArgumentException("Invalid fire probability");
            chance*=p;
        }
        return chance;
    }
}
