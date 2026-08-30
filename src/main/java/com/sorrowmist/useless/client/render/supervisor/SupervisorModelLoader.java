package com.sorrowmist.useless.client.render.supervisor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class SupervisorModelLoader implements IGeometryLoader<SupervisorGeometry> {
    public static final SupervisorModelLoader INSTANCE = new SupervisorModelLoader();

    private SupervisorModelLoader() {
    }

    @Override
    public SupervisorGeometry read(JsonObject jsonObject, com.google.gson.JsonDeserializationContext context) {
        return SupervisorGeometry.read(GsonHelper.getAsJsonObject(jsonObject, "supervisor_model"));
    }
}

final class SupervisorGeometry extends SimpleUnbakedGeometry<SupervisorGeometry> {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final ModelState IDENTITY_MODEL_STATE = new SimpleModelState(Transformation.identity());

    private final List<ElementData> elements;
    private final Map<String, String> textures;

    private SupervisorGeometry(List<ElementData> elements, Map<String, String> textures) {
        this.elements = List.copyOf(elements);
        this.textures = Map.copyOf(textures);
    }

    static SupervisorGeometry read(JsonObject source) {
        Map<String, String> textures = new HashMap<>();
        JsonObject textureObject = GsonHelper.getAsJsonObject(source, "textures");
        for (Map.Entry<String, JsonElement> entry : textureObject.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Supervisor texture must be a string: " + entry.getKey());
            }
            textures.put(entry.getKey(), entry.getValue().getAsString());
        }

        List<ElementData> elements = new ArrayList<>();
        JsonArray elementArray = GsonHelper.getAsJsonArray(source, "elements");
        for (int index = 0; index < elementArray.size(); index++) {
            JsonObject element = elementArray.get(index).getAsJsonObject();
            elements.add(new ElementData(
                    readVector(element, "from"),
                    readVector(element, "to"),
                    readFaces(GsonHelper.getAsJsonObject(element, "faces")),
                    parseRotation(element.has("rotation")
                            ? GsonHelper.getAsJsonObject(element, "rotation") : null),
                    GsonHelper.getAsBoolean(element, "shade", true)
            ));
        }

        return new SupervisorGeometry(elements, textures);
    }

    private static Map<Direction, FaceData> readFaces(JsonObject facesObject) {
        EnumMap<Direction, FaceData> faces = new EnumMap<>(Direction.class);
        for (Map.Entry<String, JsonElement> entry : facesObject.entrySet()) {
            Direction direction = Direction.byName(entry.getKey());
            if (direction == null) {
                throw new JsonParseException("Unknown Supervisor face: " + entry.getKey());
            }

            JsonObject face = entry.getValue().getAsJsonObject();
            float[] uv = null;
            if (face.has("uv")) {
                JsonArray uvArray = GsonHelper.getAsJsonArray(face, "uv");
                if (uvArray.size() != 4) {
                    throw new JsonParseException("Supervisor face UV must contain four values");
                }
                uv = new float[4];
                for (int index = 0; index < uv.length; index++) {
                    uv[index] = GsonHelper.convertToFloat(uvArray.get(index), "uv[" + index + "]");
                }
            }

            int uvRotation = GsonHelper.getAsInt(face, "rotation", 0);
            if (uvRotation < 0 || uvRotation % 90 != 0 || uvRotation > 270) {
                throw new JsonParseException("Invalid Supervisor UV rotation: " + uvRotation);
            }

            faces.put(direction, new FaceData(
                    GsonHelper.getAsString(face, "texture"),
                    uv,
                    uvRotation,
                    GsonHelper.getAsInt(face, "tintindex", -1)
            ));
        }
        if (faces.isEmpty()) {
            throw new JsonParseException("Supervisor element must contain at least one face");
        }
        return Map.copyOf(faces);
    }

    private static Vector3f readVector(JsonObject object, String key) {
        JsonArray values = GsonHelper.getAsJsonArray(object, key);
        if (values.size() != 3) {
            throw new JsonParseException("Supervisor " + key + " must contain three values");
        }
        return new Vector3f(
                GsonHelper.convertToFloat(values.get(0), key + "[0]"),
                GsonHelper.convertToFloat(values.get(1), key + "[1]"),
                GsonHelper.convertToFloat(values.get(2), key + "[2]")
        );
    }

    static Matrix4f parseRotation(JsonObject rotation) {
        if (rotation == null) {
            return new Matrix4f();
        }

        Vector3f origin = readVector(rotation, "origin").mul(PIXEL);
        Matrix4f transform = new Matrix4f()
                .translate(origin.x(), origin.y(), origin.z());
        if (rotation.has("axis")) {
            Direction.Axis axis = Direction.Axis.byName(GsonHelper.getAsString(rotation, "axis"));
            if (axis == null) {
                throw new JsonParseException("Invalid Supervisor rotation axis");
            }
            float angle = (float) Math.toRadians(GsonHelper.getAsFloat(rotation, "angle"));
            switch (axis) {
                case X -> transform.rotateX(angle);
                case Y -> transform.rotateY(angle);
                case Z -> transform.rotateZ(angle);
            }
        } else {
            float x = (float) Math.toRadians(getOptionalFloat(rotation, "x"));
            float y = (float) Math.toRadians(getOptionalFloat(rotation, "y"));
            float z = (float) Math.toRadians(getOptionalFloat(rotation, "z"));
            transform.rotateZYX(z, y, x);
        }
        return transform.translate(-origin.x(), -origin.y(), -origin.z());
    }

    private static float getOptionalFloat(JsonObject object, String key) {
        return object.has(key) ? GsonHelper.getAsFloat(object, key) : 0.0F;
    }

    static Matrix4f composeTransforms(ModelState modelTransform, Matrix4f elementTransform) {
        Matrix4f modelMatrix = new Matrix4f()
                .translate(0.5F, 0.5F, 0.5F)
                .mul(modelTransform.getRotation().getMatrix())
                .translate(-0.5F, -0.5F, -0.5F);
        return modelMatrix.mul(elementTransform);
    }

    private Material materialFor(String textureReference) {
        String key = textureReference.startsWith("#")
                ? textureReference.substring(1) : textureReference;
        String texture = textures.get(key);
        if (texture == null) {
            throw new JsonParseException("Missing Supervisor texture reference: " + textureReference);
        }
        if (texture.startsWith("#")) {
            return materialFor(texture);
        }

        ResourceLocation location = texture.contains(":")
                ? ResourceLocation.parse(texture)
                : UselessMod.id("block/" + texture);
        return new Material(TextureAtlas.LOCATION_BLOCKS, location);
    }

    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder,
                            ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                            ModelState modelTransform) {
        Map<Material, TextureAtlasSprite> spriteCache = new HashMap<>();
        for (ElementData data : elements) {
            EnumMap<Direction, BlockElementFace> faces = new EnumMap<>(Direction.class);
            for (Map.Entry<Direction, FaceData> entry : data.faces.entrySet()) {
                FaceData face = entry.getValue();
                faces.put(entry.getKey(), new BlockElementFace(
                        null,
                        face.tintIndex,
                        face.texture,
                        new BlockFaceUV(face.uv == null ? null : face.uv.clone(), face.uvRotation)
                ));
            }

            BlockElement element = new BlockElement(
                    new Vector3f(data.from),
                    new Vector3f(data.to),
                    faces,
                    null,
                    data.shade
            );
            Matrix4f transform = composeTransforms(modelTransform, data.elementTransform);
            var transformer = QuadTransformers.applying(new Transformation(transform));

            for (Map.Entry<Direction, BlockElementFace> entry : faces.entrySet()) {
                Direction faceDirection = entry.getKey();
                BlockElementFace face = entry.getValue();
                Material material = materialFor(face.texture());
                TextureAtlasSprite sprite = spriteCache.computeIfAbsent(material, spriteGetter);
                BakedQuad baked = UnbakedGeometryHelper.bakeElementFace(
                        element, face, sprite, faceDirection, IDENTITY_MODEL_STATE);
                BakedQuad transformed = transformer.process(baked);
                modelBuilder.addUnculledFace(new BakedQuad(
                        transformed.getVertices(),
                        transformed.getTintIndex(),
                        FaceBakery.calculateFacing(transformed.getVertices()),
                        transformed.getSprite(),
                        transformed.isShade(),
                        transformed.hasAmbientOcclusion()
                ));
            }
        }
    }

    private record FaceData(String texture, float[] uv, int uvRotation, int tintIndex) {
    }

    private record ElementData(Vector3f from, Vector3f to, Map<Direction, FaceData> faces,
                               Matrix4f elementTransform, boolean shade) {
    }
}
