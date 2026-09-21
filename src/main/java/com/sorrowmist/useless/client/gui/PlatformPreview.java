package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blocks.IColoredBlock;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import com.sorrowmist.useless.world.dimension.PlatformLayout;
import com.sorrowmist.useless.world.dimension.PlatformStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 维度传送方块配置界面的生成预览。
 *
 * <p>俯视图用 {@link PlatformLayout}（与世界生成器同一份算法）逐格采样，因此预览
 * 与实际生成结果不会脱节；侧视图显示层数、起始 Y 与基岩位置。
 *
 * <p><b>两种渲染模式</b>：一格方块能分到的屏幕像素足够（≥ {@link #MIN_TEXTURE_CELL}）时
 * 画真实材质，否则自动降级为平均色块——周期很大时贴图会退化成噪点，色块反而更清晰。
 *
 * <p><b>为什么烘焙成贴图</b>：{@code GuiGraphics} 的每次 blit 都会单独提交一次绘制，
 * 逐格画材质在密集网格下会到上千次 draw call；所以配置变化时把整个预览烘焙进一张
 * {@link DynamicTexture}，每帧只画一次。色块模式同样走这条路，顺带省掉了逐行合并。
 */
final class PlatformPreview implements AutoCloseable {
    /** 低于这个像素数就放弃材质、改用平均色块。 */
    private static final int MIN_TEXTURE_CELL = 4;
    /** 无法识别颜色时的中性灰。 */
    private static final int FALLBACK_COLOR = 0xFF7A7A7A;
    private static final int GRID_COLOR = 0x33000000;
    private static final int BEDROCK_COLOR = 0xFF3A3A3A;
    private static final int ORIGIN_COLOR = 0xFFD03A2E;
    private static final int MIN_BUILD_Y = -64;
    private static final int MAX_BUILD_Y = 320;

    /** 无染色时的白色乘数。 */
    private static final int NO_TINT = 0xFFFFFFFF;

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final Map<BlockState, Integer> COLOR_CACHE = new HashMap<>();
    private static final Map<BlockState, TextureAtlasSprite> SPRITE_CACHE = new HashMap<>();
    private static final Map<BlockState, Integer> TINT_CACHE = new HashMap<>();

    private final int viewW;
    private final int viewH;
    private final int sideW;
    private final int sideH;
    private final DynamicTexture topTexture;
    private final DynamicTexture sideTexture;
    private final ResourceLocation topTextureId;
    private final ResourceLocation sideTextureId;
    private boolean closed;

    private PlatformStyle style;
    private DimensionGenerationConfig config;

    /* 俯视布局 */
    private int periodX = 16;
    private int periodZ = 16;
    private int cell = 1;
    private int stepX = 1;
    private int stepZ = 1;
    private boolean textured;
    private int cols;
    private int rows;
    private int offsetCols;
    private int offsetRows;

    /* 侧视布局 */
    private int sideRows;
    private int sideCellY = 1;
    private int sideBottomY;
    private int sideTopY;
    private int sideBedrockY;
    private boolean sideHasBedrock;

    PlatformPreview(int viewW, int viewH, int sideW, int sideH) {
        this.viewW = Math.max(1, viewW);
        this.viewH = Math.max(1, viewH);
        this.sideW = Math.max(1, sideW);
        this.sideH = Math.max(1, sideH);
        this.topTexture = new DynamicTexture(this.viewW, this.viewH, false);
        this.sideTexture = new DynamicTexture(this.sideW, this.sideH, false);
        int suffix = NEXT_ID.incrementAndGet();
        this.topTextureId = ResourceLocation.fromNamespaceAndPath(
                UselessMod.MODID, "dynamic/dimension_preview_top_" + suffix);
        this.sideTextureId = ResourceLocation.fromNamespaceAndPath(
                UselessMod.MODID, "dynamic/dimension_preview_side_" + suffix);
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        manager.register(topTextureId, topTexture);
        manager.register(sideTextureId, sideTexture);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        manager.release(topTextureId);
        manager.release(sideTextureId);
    }

    /** 配置或样式变化时才重新采样；record 的值语义让比较很便宜。 */
    void refresh(PlatformStyle style, DimensionGenerationConfig config) {
        if (style == null || config == null) {
            this.style = null;
            this.config = null;
            return;
        }
        if (style == this.style && config.equals(this.config)) return;
        this.style = style;
        this.config = config;
        computeLayout();
        bake();
    }

    boolean isValid() {
        return style != null && config != null;
    }

    /** 当前是否用真实材质渲染（否则是平均色块）。 */
    boolean isTextured() {
        return isValid() && textured;
    }

    /** 一个完整周期的尺寸（格），用于界面提示。 */
    int periodBlocksX() {
        return periodX;
    }

    int periodBlocksZ() {
        return periodZ;
    }

    /** 色块模式下 1 像素覆盖多少格；材质模式恒为 1。 */
    int blocksPerPixel() {
        return textured || cell > 1 ? 1 : Math.max(stepX, stepZ);
    }

    int topY() {
        return isValid() ? PlatformLayout.topY(config) : 0;
    }

    int bottomY() {
        return isValid() ? PlatformLayout.bottomY(config) : 0;
    }

    int bedrockY() {
        return isValid() && sideHasBedrock ? sideBedrockY : Integer.MIN_VALUE;
    }

    /* ================= 布局 ================= */

    private void computeLayout() {
        periodX = periodBlocks(config, true);
        periodZ = periodBlocks(config, false);
        double blockPx = Math.min(viewW / (double) periodX, viewH / (double) periodZ);
        if (blockPx >= MIN_TEXTURE_CELL) {
            textured = true;
            cell = Math.max(1, (int) Math.floor(blockPx));
            stepX = 1;
            stepZ = 1;
        } else if (blockPx >= 1.0D) {
            textured = false;
            cell = Math.max(1, (int) Math.floor(blockPx));
            stepX = 1;
            stepZ = 1;
        } else {
            // 一个方块分不到一个像素：让 1 像素覆盖多格，保证至少看到一个完整周期。
            textured = false;
            cell = 1;
            stepX = Math.max(1, (int) Math.ceil(periodX / (double) viewW));
            stepZ = Math.max(1, (int) Math.ceil(periodZ / (double) viewH));
        }
        cols = (viewW + cell - 1) / cell;
        rows = (viewH + cell - 1) / cell;
        int periodCols = (periodX + stepX - 1) / stepX;
        int periodRows = (periodZ + stepZ - 1) / stepZ;
        offsetCols = Math.max(0, (cols - periodCols) / 2);
        offsetRows = Math.max(0, (rows - periodRows) / 2);
    }

    /**
     * 一个完整重复单元的边长（格）。
     *
     * <p>马路模式下，平台本身以区块（16 格）为周期，而道路/边界以
     * {@code boundaryInterval + roadWidth} 个区块为周期；中心标记只依赖
     * {@code boundaryInterval}。多联模式把边界间隔当作合并尺寸。
     */
    private static int periodBlocks(DimensionGenerationConfig config, boolean xAxis) {
        if (config.mode() == DimensionGenerationConfig.Mode.MULTI) {
            int size = xAxis ? config.boundaryIntervalX() : config.boundaryIntervalZ();
            return Math.max(16, Math.max(1, size) * 16);
        }
        int interval = xAxis ? config.boundaryIntervalX() : config.boundaryIntervalZ();
        int other = xAxis ? config.boundaryIntervalZ() : config.boundaryIntervalX();
        if (config.roadWidth() > 0 && interval > 0) {
            return (interval + config.roadWidth()) * 16;
        }
        if (config.centerMarkerEnabled() && interval > 0 && other > 0) {
            return interval * 16;
        }
        return 16;
    }

    /* ================= 烘焙 ================= */

    private void bake() {
        bakeTop();
        bakeSide();
    }

    private void bakeTop() {
        NativeImage image = topTexture.getPixels();
        if (image == null) return;
        int background = argbToAbgr(MachineScreenStyle.PANEL_COLOR);
        fill(image, viewW, viewH, background);

        for (int cy = 0; cy < rows; cy++) {
            int py = cy * cell;
            if (py >= viewH) break;
            int baseZ = PlatformLayout.mod((cy - offsetRows) * stepZ, periodZ);
            for (int cx = 0; cx < cols; cx++) {
                int px = cx * cell;
                if (px >= viewW) break;
                int baseX = PlatformLayout.mod((cx - offsetCols) * stepX, periodX);
                BlockState state = sampleState(baseX, baseZ);
                // 边缘上的格子按整格缩放、只裁掉越界像素，否则会被压扁。
                if (textured) {
                    TextureAtlasSprite sprite = spriteFor(state);
                    if (sprite != null) {
                        drawSprite(image, px, py, cell, viewW, viewH, sprite, tintFor(state));
                        continue;
                    }
                }
                fill(image, viewW, viewH, px, py, cell, cell, argbToAbgr(colorFor(state)));
            }
        }
        topTexture.upload();
    }

    /**
     * 超采样时取子网格里最常见的方块，而不是平均值——平均会把一格宽的道路或
     * 边界线混进周围的填充色里，反而看不见。
     */
    private BlockState sampleState(int baseX, int baseZ) {
        if (stepX == 1 && stepZ == 1) return stateAt(baseX, baseZ);

        int nx = Math.min(stepX, 3);
        int nz = Math.min(stepZ, 3);
        BlockState[] seen = new BlockState[nx * nz];
        int[] counts = new int[nx * nz];
        int distinct = 0;
        for (int j = 0; j < nz; j++) {
            int bz = baseZ + j * stepZ / nz;
            for (int i = 0; i < nx; i++) {
                BlockState state = stateAt(baseX + i * stepX / nx, bz);
                int found = -1;
                for (int k = 0; k < distinct; k++) {
                    // BlockState 由注册表统一持有，引用比较即可。
                    if (seen[k] == state) {
                        found = k;
                        break;
                    }
                }
                if (found < 0) {
                    seen[distinct] = state;
                    counts[distinct] = 1;
                    distinct++;
                } else {
                    counts[found]++;
                }
            }
        }
        BlockState best = seen[0];
        int bestCount = counts[0];
        for (int k = 1; k < distinct; k++) {
            if (counts[k] > bestCount) {
                bestCount = counts[k];
                best = seen[k];
            }
        }
        return best;
    }

    /** 俯视看到的是顶面：装饰方块优先，否则是该列的平台方块。 */
    private BlockState stateAt(int blockX, int blockZ) {
        int chunkX = Math.floorDiv(blockX, 16);
        int localX = Math.floorMod(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        int localZ = Math.floorMod(blockZ, 16);
        BlockState decoration = PlatformLayout.surfaceDecoration(
                style, config, chunkX, chunkZ, localX, localZ);
        if (decoration != null) return decoration;
        return PlatformLayout.platformState(style, config, chunkX, chunkZ, localX, localZ);
    }

    private void bakeSide() {
        sideHasBedrock = config.generateBedrock();
        sideBedrockY = PlatformLayout.bedrockY(config, MIN_BUILD_Y, MAX_BUILD_Y);
        int bottom = PlatformLayout.bottomY(config);
        int top = PlatformLayout.topY(config);
        sideBottomY = sideHasBedrock ? Math.min(bottom, sideBedrockY) : bottom;
        sideTopY = Math.max(top, sideBottomY);
        int range = sideTopY - sideBottomY + 1;

        if (range <= sideH) {
            sideCellY = Math.max(1, sideH / range);
            sideRows = range;
        } else {
            sideCellY = 1;
            sideRows = Math.min(sideH, range);
        }
        int yStep = (int) Math.ceil(range / (double) sideRows);

        NativeImage image = sideTexture.getPixels();
        if (image == null) return;
        int empty = argbToAbgr(MachineScreenStyle.PANEL_COLOR);
        fill(image, sideW, sideH, empty);

        // 直接按平台带的起止行填充，而不是逐行采样：抽稀后一行代表多格 Y，
        // 薄板（例如层数=1）可能整段落在两个采样行之间而完全消失。
        int platformLowRow = clampRow((bottom + 1 - sideBottomY) / yStep);
        int platformHighRow = clampRow((top - sideBottomY) / yStep);
        for (int col = 0; col < sideW; col++) {
            // 与俯视图首行对齐：侧视的顶面颜色就是俯视最上面一行的颜色。
            int sourceCol = Math.min(cols - 1, col / cell);
            BlockState surface = stateAt(
                    PlatformLayout.mod((sourceCol - offsetCols) * stepX, periodX),
                    PlatformLayout.mod(-offsetRows * stepZ, periodZ));
            int abgr = argbToAbgr(colorFor(surface));
            for (int row = platformLowRow; row <= platformHighRow; row++) {
                // Y 越大越靠上：剖面要像真实侧视，不能把基岩画到平台上方。
                int py = (sideRows - 1 - row) * sideCellY;
                for (int dy = 0; dy < sideCellY && py + dy < sideH; dy++) {
                    image.setPixelRGBA(col, py + dy, abgr);
                }
            }
        }
        // 基岩只有一格厚，同样会落不到采样行上，单独补一条线；后画所以压在平台带下方那一行。
        if (sideHasBedrock) {
            int py = (sideRows - 1 - clampRow((sideBedrockY - sideBottomY) / yStep)) * sideCellY;
            int abgr = argbToAbgr(BEDROCK_COLOR);
            for (int dy = 0; dy < Math.max(1, sideCellY) && py + dy < sideH; dy++) {
                for (int col = 0; col < sideW; col++) {
                    image.setPixelRGBA(col, py + dy, abgr);
                }
            }
        }
        sideTexture.upload();
    }

    private int clampRow(int row) {
        return Math.max(0, Math.min(sideRows - 1, row));
    }

    /**
     * 把精灵按盒式滤波缩放成一格 {@code cell × cell} 像素写入，比最近邻干净得多。
     * 越界的像素直接丢弃（{@code clipWidth/clipHeight} 是图像边界）。
     */
    private static void drawSprite(NativeImage image, int px, int py, int cell,
                                   int clipWidth, int clipHeight,
                                   TextureAtlasSprite sprite, int tint) {
        NativeImage source;
        try {
            SpriteContents contents = sprite.contents();
            source = contents.getOriginalImage();
        } catch (Throwable ignored) {
            return;
        }
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) return;
        int tintR = (tint >>> 16) & 0xFF;
        int tintG = (tint >>> 8) & 0xFF;
        int tintB = tint & 0xFF;

        for (int y = 0; y < cell; y++) {
            int dy = py + y;
            if (dy >= clipHeight) break;
            int y0 = y * sourceHeight / cell;
            int y1 = Math.min(sourceHeight, Math.max(y0 + 1, (y + 1) * sourceHeight / cell));
            for (int x = 0; x < cell; x++) {
                int dx = px + x;
                if (dx >= clipWidth) break;
                int x0 = x * sourceWidth / cell;
                int x1 = Math.min(sourceWidth, Math.max(x0 + 1, (x + 1) * sourceWidth / cell));
                image.setPixelRGBA(dx, dy,
                        multiplyTint(boxAverage(source, x0, x1, y0, y1), tintR, tintG, tintB));
            }
        }
    }

    /**
     * 把染色乘到已平均好的颜色上（ABGR 进、ABGR 出）。
     *
     * <p>本模组的塑料方块共用一张**灰度**贴图，颜色完全来自 {@code BlockColors} 的
     * tint（模型每个面都声明了 {@code tintindex: 0}）。不乘这一步，材质模式就会画成灰的。
     */
    private static int multiplyTint(int abgr, int tintR, int tintG, int tintB) {
        if (tintR == 255 && tintG == 255 && tintB == 255) return abgr;
        int red = ((abgr & 0xFF) * tintR) / 255;
        int green = (((abgr >>> 8) & 0xFF) * tintG) / 255;
        int blue = (((abgr >>> 16) & 0xFF) * tintB) / 255;
        return (0xFF << 24) | (blue << 16) | (green << 8) | red;
    }

    /** 源区域内按 alpha 加权的平均色，返回 ABGR（与 {@code setPixelRGBA} 一致）。 */
    private static int boxAverage(NativeImage source, int x0, int x1, int y0, int y1) {
        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        long sumA = 0;
        int samples = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int pixel;
                try {
                    pixel = source.getPixelRGBA(x, y);
                } catch (Throwable ignored) {
                    continue;
                }
                int alpha = (pixel >>> 24) & 0xFF;
                sumR += (long) (pixel & 0xFF) * alpha;
                sumG += (long) ((pixel >>> 8) & 0xFF) * alpha;
                sumB += (long) ((pixel >>> 16) & 0xFF) * alpha;
                sumA += alpha;
                samples++;
            }
        }
        if (samples == 0 || sumA == 0) return argbToAbgr(FALLBACK_COLOR);
        int red = (int) (sumR / sumA);
        int green = (int) (sumG / sumA);
        int blue = (int) (sumB / sumA);
        int alpha = (int) (sumA / samples);
        if (alpha < 255) {
            // 半透明方块（玻璃等）：按 alpha 合成到面板底色上，否则在浅色面板里几乎看不见。
            float keep = alpha / 255.0F;
            red = composite(red, keep, 16);
            green = composite(green, keep, 8);
            blue = composite(blue, keep, 0);
        }
        return (0xFF << 24) | (blue << 16) | (green << 8) | red;
    }

    /** 与 {@link #multiplyTint} 相同，只是输入输出都是 ARGB。 */
    private static int multiplyTintArgb(int argb, int tint) {
        int tintR = (tint >>> 16) & 0xFF;
        int tintG = (tint >>> 8) & 0xFF;
        int tintB = tint & 0xFF;
        if (tintR == 255 && tintG == 255 && tintB == 255) return argb;
        int red = (((argb >>> 16) & 0xFF) * tintR) / 255;
        int green = (((argb >>> 8) & 0xFF) * tintG) / 255;
        int blue = ((argb & 0xFF) * tintB) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int composite(int channel, float keep, int shift) {
        int panel = (MachineScreenStyle.PANEL_COLOR >> shift) & 0xFF;
        return (int) (channel * keep + panel * (1.0F - keep));
    }

    private static void fill(NativeImage image, int imageWidth, int imageHeight, int abgr) {
        fill(image, imageWidth, imageHeight, 0, 0, imageWidth, imageHeight, abgr);
    }

    private static void fill(NativeImage image, int imageWidth, int imageHeight,
                             int px, int py, int width, int height, int abgr) {
        for (int y = py; y < py + height && y < imageHeight; y++) {
            for (int x = px; x < px + width && x < imageWidth; x++) {
                image.setPixelRGBA(x, y, abgr);
            }
        }
    }

    /** ARGB（{@code GuiGraphics.fill} 用）转 ABGR（{@code NativeImage} 用）。 */
    private static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00)
                | ((argb & 0x00FF0000) >>> 16)
                | ((argb & 0x000000FF) << 16);
    }

    /* ================= 取色 ================= */

    /** 顶面贴图；取不到时回退粒子图标，仍取不到返回 null。 */
    private static TextureAtlasSprite spriteFor(BlockState state) {
        TextureAtlasSprite cached = SPRITE_CACHE.get(state);
        if (cached != null) return cached;
        TextureAtlasSprite sprite = resolveSprite(state);
        if (sprite != null) SPRITE_CACHE.put(state, sprite);
        return sprite;
    }

    private static TextureAtlasSprite resolveSprite(BlockState state) {
        try {
            BlockModelShaper shaper = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
            BakedModel model = shaper.getBlockModel(state);
            // NeoForge 的 getQuads 重载；renderType 传 null 表示不限渲染层。
            List<BakedQuad> up = model.getQuads(state, Direction.UP, RandomSource.create(),
                    ModelData.EMPTY, null);
            if (!up.isEmpty()) return up.get(0).getSprite();
            return model.getParticleIcon(ModelData.EMPTY);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 代表色（ARGB）。优先本模组有色方块的精确 RGB，其次顶面纹理平均色，最后才用
     * MapColor 兜底——MapColor 只有几十个色值、且大量方块（玻璃、栏杆、门、
     * 多数模组方块）返回 {@code MapColor.NONE}，只靠它会让预览出现黑块。
     */
    private static int colorFor(BlockState state) {
        Integer cached = COLOR_CACHE.get(state);
        if (cached != null) return cached;
        int color = resolveColor(state);
        COLOR_CACHE.put(state, color);
        return color;
    }

    /**
     * 方块染色（ARGB）。本模组所有塑料方块共用同一张灰度贴图，颜色全靠
     * {@code ClientSetup.onBlockColor} 注册的 tint，因此取色与材质都必须乘上它。
     * 没有染色时返回白色乘数。
     */
    private static int tintFor(BlockState state) {
        Integer cached = TINT_CACHE.get(state);
        if (cached != null) return cached;
        int tint = NO_TINT;
        try {
            int resolved = Minecraft.getInstance().getBlockColors().getColor(state, null, null, 0);
            // -1 表示该方块没有染色（原版约定），此时按白色处理。
            if (resolved != -1) tint = 0xFF000000 | resolved;
        } catch (Throwable ignored) {
            // 少数方块的染色回调需要真实世界坐标，拿不到就当作无染色。
        }
        TINT_CACHE.put(state, tint);
        return tint;
    }

    private static int resolveColor(BlockState state) {
        if (state.getBlock() instanceof IColoredBlock colored) {
            return colored.getColor().getRgb();
        }
        TextureAtlasSprite sprite = spriteFor(state);
        if (sprite != null) {
            int averaged = averageColor(sprite);
            if (averaged != 0) {
                int tint = tintFor(state);
                return multiplyTintArgb(averaged, tint);
            }
        }
        try {
            MapColor mapColor = state.getBlock().defaultMapColor();
            if (mapColor != null && mapColor != MapColor.NONE) {
                return 0xFF000000 | mapColor.col;
            }
        } catch (Throwable ignored) {
            // 少数模组方块会在这里抛异常，落到下面的兜底色。
        }
        return FALLBACK_COLOR;
    }

    /** 整张贴图的 alpha 加权平均色（ARGB）；全透明或读取失败返回 0 表示不可用。 */
    private static int averageColor(TextureAtlasSprite sprite) {
        try {
            SpriteContents contents = sprite.contents();
            NativeImage image = contents.getOriginalImage();
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) return 0;
            int abgr = boxAverage(image, 0, width, 0, height);
            // 转回 ARGB：boxAverage 已经做过 alpha 合成，alpha 恒为 255。
            int red = abgr & 0xFF;
            int green = (abgr >>> 8) & 0xFF;
            int blue = (abgr >>> 16) & 0xFF;
            return 0xFF000000 | (red << 16) | (green << 8) | blue;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /* ================= 绘制 ================= */

    void renderTop(GuiGraphics graphics, int x0, int y0) {
        if (!isValid()) return;
        // 贴图是立即绘制，网格与原点标记是 fill（排队）——先后顺序天然正确：
        // 网格会在帧末提交，落在贴图之上。
        graphics.blit(topTextureId, x0, y0, viewW, viewH, 0.0F, 0.0F,
                viewW, viewH, viewW, viewH);
        graphics.enableScissor(x0, y0, x0 + viewW, y0 + viewH);
        drawChunkGrid(graphics, x0, y0);
        drawOriginMarker(graphics, x0, y0);
        // 排队的 fill 要在这里提交，否则裁剪在帧末已经解除，裁剪形同虚设。
        graphics.flush();
        graphics.disableScissor();
    }

    void renderSide(GuiGraphics graphics, int x0, int y0) {
        if (!isValid()) return;
        graphics.blit(sideTextureId, x0, y0, sideW, sideH, 0.0F, 0.0F,
                sideW, sideH, sideW, sideH);
    }

    /** 每 16 格一条区块分界线；超采样时线宽不足 1 像素，直接跳过。 */
    private void drawChunkGrid(GuiGraphics graphics, int x0, int y0) {
        if (stepX != 1 || stepZ != 1) return;
        for (int cx = 0; cx < cols; cx++) {
            int baseX = PlatformLayout.mod((cx - offsetCols) * stepX, periodX);
            if (PlatformLayout.mod(baseX, 16) != 0) continue;
            int px = x0 + cx * cell;
            if (px < x0 + viewW) {
                graphics.fill(px, y0, px + 1, y0 + viewH, GRID_COLOR);
            }
        }
        for (int cy = 0; cy < rows; cy++) {
            int baseZ = PlatformLayout.mod((cy - offsetRows) * stepZ, periodZ);
            if (PlatformLayout.mod(baseZ, 16) != 0) continue;
            int py = y0 + cy * cell;
            if (py < y0 + viewH) {
                graphics.fill(x0, py, x0 + viewW, py + 1, GRID_COLOR);
            }
        }
    }

    /** 周期原点（区块 0,0 的角落）标记，便于对位。 */
    private void drawOriginMarker(GuiGraphics graphics, int x0, int y0) {
        int px = x0 + offsetCols * cell;
        int py = y0 + offsetRows * cell;
        if (px < x0 || px >= x0 + viewW || py < y0 || py >= y0 + viewH) return;
        graphics.fill(px - 3, py, px + 4, py + 1, ORIGIN_COLOR);
        graphics.fill(px, py - 3, px + 1, py + 4, ORIGIN_COLOR);
    }

    /* ================= 鼠标查询 ================= */

    /** 俯视区局部坐标对应的方块坐标，越界返回 null。 */
    int[] blockAt(int localX, int localY) {
        if (!isValid() || localX < 0 || localY < 0) return null;
        int cx = localX / cell;
        int cy = localY / cell;
        if (cx >= cols || cy >= rows) return null;
        int blockX = PlatformLayout.mod((cx - offsetCols) * stepX, periodX);
        int blockZ = PlatformLayout.mod((cy - offsetRows) * stepZ, periodZ);
        return new int[]{blockX, blockZ};
    }

    /** 该坐标上的方块，用于悬浮提示。 */
    BlockState stateAtBlock(int blockX, int blockZ) {
        return isValid() ? stateAt(blockX, blockZ) : null;
    }

    /** 判断该方块当前承担的角色，返回 i18n 键后缀；无法归类时返回 null。 */
    String roleKey(BlockState state) {
        if (!isValid() || state == null) return null;
        if (state.is(config.roadBlockA())) return "road";
        if (state.is(config.roadBlockB())) return "road_edge";
        if (state.is(config.roadBlockC())) return "road_marking";
        if (state.is(config.centerMarkerBlock())) return "marker";
        if (state.is(config.boundaryBlockA()) || state.is(config.boundaryBlockB())) return "boundary";
        if (state.is(config.borderBlock())) return "border";
        if (state.is(config.centerBlock())) return "center";
        if (state.is(config.fillBlock())) return "fill";
        return null;
    }
}
