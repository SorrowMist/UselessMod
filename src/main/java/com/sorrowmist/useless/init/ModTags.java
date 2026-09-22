package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public final class ModTags {

    public static final TagKey<Item> CATALYSTS = createItemTag("catalysts");
    public static final TagKey<Item> MOLDS = createItemTag("molds");
    public static final TagKey<Block> OMNIVERSAL_FURNACE_CASINGS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "omniversal_furnace_casings"));

    /**
     * 例外清单：即便造化杖开启了「无视工具挖掘等级」，这些方块仍按等级判定。
     *
     * <p>默认留空。整合包 / 数据包可把需要保留进度门槛的方块加进来，
     * 无需改动代码或关闭整体开关。
     */
    public static final TagKey<Block> BEEF_TOOL_TIER_LOCKED = createBlockTag("beef_tool_tier_locked");

    /**
     * 塑料方块总标签：涵盖全部四类塑料方块（普通 / 发光 / 连接纹理 / 发光连接纹理）。
     *
     * <p>用于需要整体引用塑料方块的场合，例如把 <code>#useless_mod:plastic_blocks</code>
     * 填入维度地板白名单即可一次放行全部塑料方块。
     */
    public static final TagKey<Block> PLASTIC_BLOCKS = createBlockTag("plastic_blocks");

    /** 普通塑料方块（不发光、无连接纹理）。 */
    public static final TagKey<Block> PLASTIC = createBlockTag("plastic");

    /** 发光塑料方块。 */
    public static final TagKey<Block> GLOW_PLASTIC = createBlockTag("glow_plastic");

    /** 连接纹理塑料方块（CTM）。 */
    public static final TagKey<Block> PLASTIC_CTM = createBlockTag("plastic_ctm");

    /** 发光连接纹理塑料方块（CTM）。 */
    public static final TagKey<Block> GLOW_PLASTIC_CTM = createBlockTag("glow_plastic_ctm");

    private static TagKey<Item> createItemTag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, path));
    }

    private static TagKey<Block> createBlockTag(String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, path));
    }

    private ModTags() {
    }
}
