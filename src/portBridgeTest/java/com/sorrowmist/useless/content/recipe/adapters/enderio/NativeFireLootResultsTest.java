package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.google.gson.*;
import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeFireLootResultsTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    private static JsonObject table(String conditions, String functions) {
        return JsonParser.parseString("""
            {"pools":[{"rolls":1,"bonus_rolls":0,"conditions":%s,
             "entries":[{"type":"minecraft:item","name":"minecraft:diamond","functions":%s}]}]}
            """.formatted(conditions,functions)).getAsJsonObject();
    }
    @Test void nativeHalfChanceSingleRollKeepsExpectedYield() throws Exception {
        var outputs=NativeFireLootResults.parse(table("[{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}]","[]"),1);
        assertEquals(1,outputs.size());
        assertTrue(outputs.get(0).result().is(Items.DIAMOND));
        assertEquals(0.5,outputs.get(0).chance());
        assertEquals(1,outputs.get(0).minCount());
        assertEquals(1,outputs.get(0).maxCount());
    }
    @Test void uniformCountAndNbtAreBothRetained() throws Exception {
        var output=NativeFireLootResults.parse(table("[]","""
            [{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":2,"max":4}},
             {"function":"minecraft:set_nbt","tag":"{port_marker:42}"}]
            """),4).get(0);
        assertEquals(2,output.minCount()); assertEquals(4,output.maxCount());
        assertEquals(42,output.result().getTag().getInt("port_marker"));
    }
    @Test void contextualConditionsPoolFunctionsAndCombinedCapsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->NativeFireLootResults.parse(table(
                "[{\"condition\":\"minecraft:killed_by_player\"}]","[]"),1));
        var poolFunction=table("[]","[]");
        poolFunction.getAsJsonArray("pools").get(0).getAsJsonObject().add("functions",new JsonArray());
        assertThrows(IllegalArgumentException.class,()->NativeFireLootResults.parse(poolFunction,1));
        var twoPools=table("[]","[]"); var pools=twoPools.getAsJsonArray("pools"); pools.add(pools.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,()->NativeFireLootResults.parse(twoPools,1));
    }
}
