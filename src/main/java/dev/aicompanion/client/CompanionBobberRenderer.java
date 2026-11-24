package dev.aicompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aicompanion.entity.CompanionBobber;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * His bobber and its line, drawn as the game draws a player's (FishingHookRenderer, which draws nothing for a hook whose owner is
 * no player): the game's bobber, and the line to the hand that holds his rod, as a player's is seen from the side.
 */
public final class CompanionBobberRenderer extends EntityRenderer<CompanionBobber> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType BOBBER = RenderType.entityCutout(TEXTURE);

    public CompanionBobberRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(CompanionBobber bobber, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int light) {
        if (!(bobber.getOwner() instanceof LivingEntity fisher)) return;
        pose.pushPose();
        pose.pushPose();
        pose.scale(.5F, .5F, .5F);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose face = pose.last();
        VertexConsumer quad = buffer.getBuffer(BOBBER);
        corner(quad, face, light, 0F, 0, 0, 1);
        corner(quad, face, light, 1F, 0, 1, 1);
        corner(quad, face, light, 1F, 1, 1, 0);
        corner(quad, face, light, 0F, 1, 0, 0);
        pose.popPose();
        // The hand of his rod, where the game puts a player's seen from the side.
        int side = fisher.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float body = Mth.lerp(partialTicks, fisher.yBodyRotO, fisher.yBodyRot) * Mth.DEG_TO_RAD, scale = fisher.getScale();
        double sin = Mth.sin(body), cos = Mth.cos(body), across = side * .35 * scale, ahead = .8 * scale;
        Vec3 hand = fisher.getEyePosition(partialTicks).add(-cos * across - sin * ahead, -.45 * scale, -sin * across + cos * ahead);
        Vec3 from = bobber.getPosition(partialTicks).add(0, .25, 0);
        float x = (float) (hand.x - from.x), y = (float) (hand.y - from.y), z = (float) (hand.z - from.z);
        VertexConsumer line = buffer.getBuffer(RenderType.lineStrip());
        PoseStack.Pose last = pose.last();
        for (int part = 0; part <= 16; part++) string(x, y, z, line, last, part / 16F, (part + 1) / 16F);
        pose.popPose();
        super.render(bobber, yaw, partialTicks, pose, buffer, light);
    }

    private static void corner(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        consumer.addVertex(pose, x - .5F, y - .5F, 0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0F, 1F, 0F);
    }

    /** One piece of the line, sagging as the game's does. */
    private static void string(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float part, float next) {
        float px = x * part, py = y * (part * part + part) * .5F + .25F, pz = z * part;
        float nx = x * next - px, ny = y * (next * next + next) * .5F + .25F - py, nz = z * next - pz;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        consumer.addVertex(pose, px, py, pz).setColor(0xFF000000).setNormal(pose, nx / length, ny / length, nz / length);
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionBobber bobber) { return TEXTURE; }
}
