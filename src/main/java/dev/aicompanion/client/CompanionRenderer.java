package dev.aicompanion.client;

import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Player-shaped arms and armor let actual equipment appear on the companion in the world. */
public final class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, PlayerModel<CompanionEntity>> {
    private static final ResourceLocation[] TEXTURES = java.util.Arrays.stream(dev.aicompanion.CompanionExpression.values())
            .map(expression -> ResourceLocation.fromNamespaceAndPath("aicompanion", "textures/entity/" + expression.texture() + ".png"))
            .toArray(ResourceLocation[]::new);

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<CompanionEntity>(CompanionSkin.root(context.bakeLayer(ModelLayers.PLAYER)), false) {
            @Override public void setupAnim(CompanionEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                rightArmPose = entity.getMainHandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
                leftArmPose = entity.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
                if (entity.isUsingItem() && entity.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND
                        && entity.getOffhandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) leftArmPose = ArmPose.BLOCK;
                super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                if (entity.isMiningAnimation()) {
                    float stroke = net.minecraft.util.Mth.sin(ageInTicks * (float)Math.PI / 4);
                    rightArm.xRot = -1.1F + stroke * .85F - headPitch * .006F;
                    rightArm.yRot = -.18F; rightArm.zRot = .12F;
                    leftArm.xRot = -.25F; body.yRot = stroke * .06F;
                    rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body);
                } else if (entity.actionAnimation() != 0) {
                    float stroke = net.minecraft.util.Mth.sin(ageInTicks * .8F);
                    rightArm.xRot = -1.15F + stroke * .25F - headPitch * .006F;
                    rightArm.yRot = -.25F; rightArm.zRot = .1F;
                    leftArm.xRot = entity.actionAnimation() == 2 ? -.95F - stroke * .15F : -.25F;
                    body.yRot = stroke * .045F;
                    rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body);
                }
            }
        }, 0.5F);
        layers.removeIf(layer -> layer instanceof net.minecraft.client.renderer.entity.layers.ItemInHandLayer);
        addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<CompanionEntity, PlayerModel<CompanionEntity>>(this, context.getItemInHandRenderer()) {
            @Override protected void renderArmWithItem(net.minecraft.world.entity.LivingEntity living, net.minecraft.world.item.ItemStack stack,
                    net.minecraft.world.item.ItemDisplayContext display, net.minecraft.world.entity.HumanoidArm arm,
                    com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
                if (living instanceof CompanionEntity companion && arm == companion.getMainArm() && !companion.actionItem().isEmpty()) stack = companion.actionItem();
                super.renderArmWithItem(living, stack, display, arm, pose, buffers, light);
            }
            @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                    CompanionEntity entity, float limbSwing, float limbAmount, float partialTicks, float age, float yaw, float pitch) {
                if (!entity.actionItem().isEmpty()) {
                    renderArmWithItem(entity, entity.actionItem(), net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                            net.minecraft.world.entity.HumanoidArm.RIGHT, pose, buffers, light);
                    renderArmWithItem(entity, entity.getOffhandItem(), net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                            net.minecraft.world.entity.HumanoidArm.LEFT, pose, buffers, light);
                } else super.render(pose, buffers, light, entity, limbSwing, limbAmount, partialTicks, age, yaw, pitch);
            }
        });
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        return TEXTURES[entity.expression().ordinal()];
    }
}
