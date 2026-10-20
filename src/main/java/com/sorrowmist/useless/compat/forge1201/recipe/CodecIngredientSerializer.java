package com.sorrowmist.useless.compat.forge1201.recipe;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;

public final class CodecIngredientSerializer<T extends CodecIngredient<T>> implements IIngredientSerializer<T> {
    private final Codec<T> codec;
    public CodecIngredientSerializer(Codec<T> codec) { this.codec=codec; }
    @Override public T parse(JsonObject json) {
        return codec.parse(JsonOps.INSTANCE,json).getOrThrow(false,message -> { throw new JsonSyntaxException(message); });
    }
    @Override public T parse(FriendlyByteBuf buffer) {
        return parse(JsonParser.parseString(buffer.readUtf(1_048_576)).getAsJsonObject());
    }
    @Override public void write(FriendlyByteBuf buffer,T ingredient) {
        buffer.writeUtf(toJson(ingredient).toString(),1_048_576);
    }
    public JsonObject toJson(T ingredient) {
        JsonObject json=codec.encodeStart(JsonOps.INSTANCE,ingredient)
                .getOrThrow(false,message -> { throw new JsonSyntaxException(message); }).getAsJsonObject();
        var id=CraftingHelper.getID(this);
        if(id==null) throw new IllegalStateException("Custom ingredient serializer has not been registered");
        json.addProperty("type",id.toString()); return json;
    }
}
