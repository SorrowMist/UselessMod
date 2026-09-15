package com.sorrowmist.useless.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum ExternalInventoryKind {
    FURNACE(0, "furnace"),
    PATTERN_ASSEMBLY(1, "pattern_assembly"),
    MOLD_HUB(2, "mold_hub"),
    PASSIVE_HATCH(3, "passive_hatch"),
    /**
     * 紧凑 F9 内部 176 条影子样板总线的整份持久化数据（不是物品栏，而是一段原始 NBT）。
     * 它跟着主机身上的 UUID 引用走，方块被拆掉时数据留在外置存储里等掉落物把它带回来。
     */
    COMPACT_PATTERN_BUS(4, "compact_pattern_bus");

    public static final Codec<ExternalInventoryKind> CODEC = Codec.STRING.xmap(
            ExternalInventoryKind::fromName,
            ExternalInventoryKind::serializedName);
    public static final StreamCodec<io.netty.buffer.ByteBuf, ExternalInventoryKind> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ExternalInventoryKind::fromId, ExternalInventoryKind::networkId);

    private final int networkId;
    private final String serializedName;

    ExternalInventoryKind(int networkId, String serializedName) {
        this.networkId = networkId;
        this.serializedName = serializedName;
    }

    public int networkId() {
        return networkId;
    }

    public String serializedName() {
        return serializedName;
    }

    private static ExternalInventoryKind fromId(int id) {
        for (ExternalInventoryKind kind : values()) {
            if (kind.networkId == id) return kind;
        }
        throw new IllegalArgumentException("Unknown external inventory kind id: " + id);
    }

    private static ExternalInventoryKind fromName(String name) {
        for (ExternalInventoryKind kind : values()) {
            if (kind.serializedName.equals(name)) return kind;
        }
        throw new IllegalArgumentException("Unknown external inventory kind: " + name);
    }
}
