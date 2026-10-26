package dev.aicompanion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.aicompanion.schematic.CompanionSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The see-through building, as Litematica shows a schematic: the block models baked once into a vertex buffer relative to the
 * placement origin, drawn every frame with the translucent block shader at reduced opacity. Moving the placement only moves the
 * buffer; rotating, mirroring or another schematic bakes it again.
 */
final class CompanionSchematicGhost {
    private static final Direction[] SIDES = Direction.values();
    private VertexBuffer buffer;
    private BlockPos origin = BlockPos.ZERO;
    private boolean empty = true;

    /** Bakes the blocks of the placement that {@code skip} does not reject (blocks already standing right, while building). */
    void bake(CompanionSchematic schematic, CompanionSchematic.Placement placement, Predicate<BlockPos> skip) {
        Minecraft minecraft = Minecraft.getInstance();
        origin = placement.origin();
        Map<Long, BlockState> cells = new HashMap<>();
        schematic.forEach((x, y, z, raw) -> { if (!raw.isAir()) cells.put(placement.world(x, y, z).asLong(), placement.state(raw)); });
        var dispatcher = minecraft.getBlockRenderer();
        var colors = minecraft.getBlockColors();
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 18)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (var entry : cells.entrySet()) {
                BlockPos pos = BlockPos.of(entry.getKey());
                BlockState state = entry.getValue();
                if (state.getBlock() instanceof LiquidBlock || skip.test(pos)) continue;
                var model = dispatcher.getBlockModel(state);
                boolean opaque = state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                pose.pushPose();
                // A hair smaller than the block: a real block standing there hides its ghost instead of flickering with it.
                pose.translate(pos.getX() - origin.getX() + .5F, pos.getY() - origin.getY() + .5F, pos.getZ() - origin.getZ() + .5F);
                pose.scale(.996F, .996F, .996F);
                pose.translate(-.5F, -.5F, -.5F);
                boolean any = false;
                for (int side = 0; side <= SIDES.length; side++) {
                    Direction direction = side < SIDES.length ? SIDES[side] : null;
                    if (direction != null && opaque) {
                        BlockState next = cells.get(pos.relative(direction).asLong());
                        if (next != null && next.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
                    }
                    random.setSeed(state.getSeed(pos));
                    for (BakedQuad quad : model.getQuads(state, direction, random, ModelData.EMPTY, null)) {
                        float red = 1, green = 1, blue = 1;
                        if (quad.isTinted()) {
                            int color = colors.getColor(state, minecraft.level, pos, quad.getTintIndex());
                            red = (color >> 16 & 255) / 255F; green = (color >> 8 & 255) / 255F; blue = (color & 255) / 255F;
                        }
                        builder.putBulkData(pose.last(), quad, red, green, blue, 1F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        any = true;
                    }
                }
                // Chests, beds, signs are drawn by their block entities: a plain cube of their texture shows where they go.
                if (!any) cube(builder, pose, model.getParticleIcon(ModelData.EMPTY));
                pose.popPose();
            }
            MeshData mesh = builder.build();
            empty = mesh == null;
            if (mesh == null) return;
            if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
        }
    }

    private static void cube(VertexConsumer consumer, PoseStack pose, TextureAtlasSprite sprite) {
        Matrix4f matrix = pose.last().pose();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        // Corners in the order of the game's FaceInfo (counter-clockwise from outside), then the normal.
        float[][] faces = {
                {0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, -1, 0}, {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},
                {1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, -1}, {0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 1},
                {0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, -1, 0, 0}, {1, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0}};
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (float[] face : faces) for (int corner = 0; corner < 4; corner++)
            consumer.addVertex(matrix, face[corner * 3], face[corner * 3 + 1], face[corner * 3 + 2]).setColor(1F, 1F, 1F, 1F)
                    .setUv(uv[corner][0], uv[corner][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(face[12], face[13], face[14]);
    }

    /** At AFTER_TRANSLUCENT_BLOCKS, with the camera rotation of the level and the origin of where it stands now. */
    void draw(RenderLevelStageEvent event, BlockPos at, float alpha) {
        if (buffer == null || empty) return;
        Vec3 camera = event.getCamera().getPosition();
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        RenderSystem.setShaderColor(1F, 1F, 1F, alpha);
        var shader = RenderSystem.getShader();
        if (shader != null) {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, event.getModelViewMatrix(), event.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set((float) (at.getX() - camera.x), (float) (at.getY() - camera.y), (float) (at.getZ() - camera.z));
            shader.apply();
            buffer.bind();
            buffer.draw();
            VertexBuffer.unbind();
            if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0F, 0F, 0F);
            shader.clear();
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        type.clearRenderState();
    }

    static void outline(RenderLevelStageEvent event, BoundingBox box, float red, float green, float blue) {
        Vec3 camera = event.getCamera().getPosition();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.translate(-camera.x, -camera.y, -camera.z);
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1).inflate(.01);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), area, red, green, blue, 1F);
        buffers.endBatch(RenderType.lines());
    }

    void close() {
        if (buffer != null) buffer.close();
        buffer = null; empty = true;
    }
}
