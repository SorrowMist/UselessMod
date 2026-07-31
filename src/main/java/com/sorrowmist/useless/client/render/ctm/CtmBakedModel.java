package com.sorrowmist.useless.client.render.ctm;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class CtmBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Long> CONNECTIONS = new ModelProperty<>();

    private final List<SpritePair> spritePairs;
    private final ConcurrentMap<QuadCacheKey, List<BakedQuad>> quadCache =
            new ConcurrentHashMap<>();

    CtmBakedModel(
            BakedModel originalModel, TextureAtlasSprite baseSprite,
            TextureAtlasSprite ctmSprite) {
        this(originalModel, baseSprite, ctmSprite, null, null);
    }

    CtmBakedModel(
            BakedModel originalModel, TextureAtlasSprite baseSprite,
            TextureAtlasSprite ctmSprite, @Nullable TextureAtlasSprite overlayBaseSprite,
            @Nullable TextureAtlasSprite overlayCtmSprite) {
        super(originalModel);
        List<SpritePair> pairs = new ArrayList<>(2);
        pairs.add(new SpritePair(baseSprite, ctmSprite));
        if (overlayBaseSprite != null && overlayCtmSprite != null) {
            pairs.add(new SpritePair(overlayBaseSprite, overlayCtmSprite));
        }
        spritePairs = List.copyOf(pairs);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level, BlockPos pos, BlockState state,
            ModelData modelData) {
        ModelData originalData = originalModel.getModelData(level, pos, state, modelData);
        return originalData.derive()
                .with(CONNECTIONS, CtmConnectionMask.collect(level, pos, state))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData modelData, @Nullable RenderType renderType) {
        Long packed = modelData.get(CONNECTIONS);
        if (state == null || packed == null) {
            return originalModel.getQuads(state, side, random, modelData, renderType);
        }
        QuadCacheKey key = new QuadCacheKey(packed, side == null ? -1 : side.ordinal());
        return quadCache.computeIfAbsent(key, ignored -> transform(
                originalModel.getQuads(state, side, random, modelData, renderType), packed));
    }

    private List<BakedQuad> transform(List<BakedQuad> source, long packed) {
        if (source.isEmpty()) {
            return List.of();
        }
        List<BakedQuad> result = new ArrayList<>(source.size() * 4);
        for (BakedQuad quad : source) {
            SpritePair sprites = spritePairFor(quad);
            if (sprites == null) {
                result.add(quad);
                continue;
            }
            result.addAll(slice(quad, CtmConnectionMask.forFace(packed, quad.getDirection()),
                    sprites.base(), sprites.ctm()));
        }
        return List.copyOf(result);
    }

    private List<BakedQuad> slice(
            BakedQuad source, int mask, TextureAtlasSprite baseSprite,
            TextureAtlasSprite ctmSprite) {
        Direction face = source.getDirection();
        int[] sourceVertices = canonicalizeWinding(source.getVertices(), face);
        CtmFaceAxes axes = CtmFaceAxes.forFace(face);
        float[][][] positions = new float[2][2][3];
        float[][] sourceU = new float[2][2];
        float[][] sourceV = new float[2][2];
        boolean[][] found = new boolean[2][2];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            float x = Float.intBitsToFloat(
                    sourceVertices[offset + IQuadTransformer.POSITION]);
            float y = Float.intBitsToFloat(
                    sourceVertices[offset + IQuadTransformer.POSITION + 1]);
            float z = Float.intBitsToFloat(
                    sourceVertices[offset + IQuadTransformer.POSITION + 2]);
            float faceU = coordinate(axes.right(), x, y, z);
            float faceV = coordinate(axes.down(), x, y, z);
            int cornerX = snap(faceU);
            int cornerY = snap(faceV);
            found[cornerX][cornerY] = true;
            for (int axis = 0; axis < 3; axis++) {
                positions[cornerX][cornerY][axis] = Float.intBitsToFloat(
                        sourceVertices[offset + IQuadTransformer.POSITION + axis]);
            }
            sourceU[cornerX][cornerY] = baseSprite.getUOffset(Float.intBitsToFloat(
                    sourceVertices[offset + IQuadTransformer.UV0]));
            sourceV[cornerX][cornerY] = baseSprite.getVOffset(Float.intBitsToFloat(
                    sourceVertices[offset + IQuadTransformer.UV0 + 1]));
        }
        if (!found[0][0] || !found[1][0] || !found[0][1] || !found[1][1]) {
            return List.of(source);
        }

        List<BakedQuad> result = new ArrayList<>(4);
        for (int cornerY = 0; cornerY < 2; cornerY++) {
            for (int cornerX = 0; cornerX < 2; cornerX++) {
                CtmQuadrantSelector.Tile tile =
                        CtmQuadrantSelector.select(mask, cornerX, cornerY);
                TextureAtlasSprite sprite = tile.baseTexture() ? baseSprite : ctmSprite;
                int[] vertices = Arrays.copyOf(sourceVertices, sourceVertices.length);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * IQuadTransformer.STRIDE;
                    float x = Float.intBitsToFloat(
                            sourceVertices[offset + IQuadTransformer.POSITION]);
                    float y = Float.intBitsToFloat(
                            sourceVertices[offset + IQuadTransformer.POSITION + 1]);
                    float z = Float.intBitsToFloat(
                            sourceVertices[offset + IQuadTransformer.POSITION + 2]);
                    float vertexU = coordinate(axes.right(), x, y, z);
                    float vertexV = coordinate(axes.down(), x, y, z);
                    float geometryU = (cornerX + snap(vertexU)) * 0.5F;
                    float geometryV = (cornerY + snap(vertexV)) * 0.5F;
                    for (int axis = 0; axis < 3; axis++) {
                        vertices[offset + IQuadTransformer.POSITION + axis] =
                                Float.floatToRawIntBits(bilinear(
                                        positions[0][0][axis], positions[1][0][axis],
                                        positions[0][1][axis], positions[1][1][axis],
                                        geometryU, geometryV));
                    }
                    float interpolatedU = bilinear(
                            sourceU[0][0], sourceU[1][0], sourceU[0][1], sourceU[1][1],
                            geometryU, geometryV);
                    float interpolatedV = bilinear(
                            sourceV[0][0], sourceV[1][0], sourceV[0][1], sourceV[1][1],
                            geometryU, geometryV);
                    float textureU = tile.baseTexture()
                            ? interpolatedU
                            : (tile.x() + normalizeWithin(
                                    interpolatedU,
                                    bilinear(sourceU[0][0], sourceU[1][0],
                                            sourceU[0][1], sourceU[1][1],
                                            cornerX * 0.5F, geometryV),
                                    bilinear(sourceU[0][0], sourceU[1][0],
                                            sourceU[0][1], sourceU[1][1],
                                            (cornerX + 1) * 0.5F, geometryV))) * 0.25F;
                    float textureV = tile.baseTexture()
                            ? interpolatedV
                            : (tile.y() + normalizeWithin(
                                    interpolatedV,
                                    bilinear(sourceV[0][0], sourceV[1][0],
                                            sourceV[0][1], sourceV[1][1],
                                            geometryU, cornerY * 0.5F),
                                    bilinear(sourceV[0][0], sourceV[1][0],
                                            sourceV[0][1], sourceV[1][1],
                                            geometryU, (cornerY + 1) * 0.5F))) * 0.25F;
                    vertices[offset + IQuadTransformer.UV0] =
                            Float.floatToRawIntBits(sprite.getU(textureU));
                    vertices[offset + IQuadTransformer.UV0 + 1] =
                            Float.floatToRawIntBits(sprite.getV(textureV));
                }
                result.add(new BakedQuad(vertices, source.getTintIndex(), source.getDirection(),
                        sprite, source.isShade(), source.hasAmbientOcclusion()));
            }
        }
        return result;
    }

    /**
     * Vanilla models do not promise a common starting vertex for every face.
     * GregTech rotates the winding to a face-specific anchor before subdividing;
     * doing the same keeps vertex lighting and quad winding stable on all sides.
     */
    private static int[] canonicalizeWinding(int[] source, Direction face) {
        int anchor = -1;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            float x = Float.intBitsToFloat(
                    source[offset + IQuadTransformer.POSITION]);
            float y = Float.intBitsToFloat(
                    source[offset + IQuadTransformer.POSITION + 1]);
            float z = Float.intBitsToFloat(
                    source[offset + IQuadTransformer.POSITION + 2]);
            boolean matches = switch (face) {
                case UP -> near(x, 0.0F) && near(z, 0.0F);
                case DOWN -> near(x, 0.0F) && near(z, 1.0F);
                case NORTH -> near(x, 1.0F) && near(y, 1.0F);
                case SOUTH -> near(x, 0.0F) && near(y, 1.0F);
                case EAST -> near(y, 1.0F) && near(z, 1.0F);
                case WEST -> near(y, 1.0F) && near(z, 0.0F);
            };
            if (matches) {
                anchor = vertex;
                break;
            }
        }
        if (anchor <= 0) {
            return source;
        }
        int[] canonical = new int[source.length];
        for (int vertex = 0; vertex < 4; vertex++) {
            int sourceVertex = (vertex + anchor) & 3;
            System.arraycopy(source,
                    sourceVertex * IQuadTransformer.STRIDE,
                    canonical,
                    vertex * IQuadTransformer.STRIDE,
                    IQuadTransformer.STRIDE);
        }
        return canonical;
    }

    private static boolean near(float first, float second) {
        return Math.abs(first - second) < 0.01F;
    }

    @Nullable
    private SpritePair spritePairFor(BakedQuad quad) {
        for (SpritePair sprites : spritePairs) {
            if (quad.getSprite() == sprites.base()
                    || quad.getSprite().contents().name()
                    .equals(sprites.base().contents().name())) {
                return sprites;
            }
        }
        return null;
    }

    private static float coordinate(Direction direction, float x, float y, float z) {
        float value = switch (direction.getAxis()) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? value : 1.0F - value;
    }

    private static float normalizeWithin(float value, float min, float max) {
        if (Math.abs(max - min) < 1.0e-6F) {
            return 0.5F;
        }
        return Math.max(0.0F, Math.min(1.0F, (value - min) / (max - min)));
    }

    private static int snap(float coordinate) {
        return coordinate < 0.5F ? 0 : 1;
    }

    private static float bilinear(
            float topLeft, float topRight, float bottomLeft, float bottomRight,
            float u, float v) {
        float top = topLeft + (topRight - topLeft) * u;
        float bottom = bottomLeft + (bottomRight - bottomLeft) * u;
        return top + (bottom - top) * v;
    }

    private record QuadCacheKey(long connections, int side) {
    }

    private record SpritePair(TextureAtlasSprite base, TextureAtlasSprite ctm) {
    }
}
