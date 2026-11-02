package dev.aicompanion.client;

import dev.aicompanion.CompanionHealth;
import dev.aicompanion.entity.CompanionEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Player-shaped arms and armor let actual equipment appear on the companion in the world; the health block above him is drawn here too. */
public final class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, PlayerModel<CompanionEntity>> {
    /** A flat, blendable colour surface for the bars: position + colour only, no texture, no lighting (see Neat 1.21.1). */
    private static final net.minecraft.client.renderer.RenderType BAR = net.minecraft.client.renderer.RenderType.create(
            "aicompanion_health_bar", com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536,
            net.minecraft.client.renderer.RenderType.CompositeState.builder()
                    .setShaderState(net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(net.minecraft.client.renderer.RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE)
                    .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    private static final ResourceLocation[] TEXTURES = java.util.Arrays.stream(dev.aicompanion.CompanionExpression.values())
            .map(expression -> ResourceLocation.fromNamespaceAndPath("aicompanion", "textures/entity/" + expression.texture() + ".png"))
            .toArray(ResourceLocation[]::new);

    public CompanionRenderer(EntityRendererProvider.Context context) {
        this(context, CompanionSkin.bake(context.bakeLayer(ModelLayers.PLAYER)));
    }
    private CompanionRenderer(EntityRendererProvider.Context context, CompanionSkin skin) {
        super(context, new PlayerModel<CompanionEntity>(skin.root(), false) {
            @Override public void setupAnim(CompanionEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
                rightArmPose = entity.getMainHandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
                leftArmPose = entity.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
                if (entity.isUsingItem() && entity.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND
                        && entity.getOffhandItem().canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHIELD_BLOCK)) leftArmPose = ArmPose.BLOCK;
                if (entity.isUsingItem() && entity.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND && entity.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem) rightArmPose = ArmPose.BOW_AND_ARROW;
                super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                skin.animate(entity.expression(), CompanionVoiceAudio.mouthOpen(entity), ageInTicks + entity.getId() * 17);
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

    /** The fast stroke lying flat, as the game turns a swimming player (PlayerRenderer); the arms are the model's own crawl. */
    @Override
    protected void setupRotations(CompanionEntity entity, com.mojang.blaze3d.vertex.PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
        float swim = entity.getSwimAmount(partialTick);
        if (swim <= 0) return;
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(net.minecraft.util.Mth.lerp(swim, 0.0F, entity.isInWater() ? -90.0F - entity.getXRot() : -90.0F)));
        if (entity.isVisuallySwimming()) pose.translate(0.0F, -1.0F, 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        return TEXTURES[entity.expression().ordinal()];
    }

    /** His name, but only outside a GUI: the menu preview draws the name itself, above the model. */
    @Override
    protected void renderNameTag(CompanionEntity entity, net.minecraft.network.chat.Component displayName,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, float partialTick) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof CompanionInventoryScreen) return;
        super.renderNameTag(entity, displayName, pose, buffers, light, partialTick);
    }

    /** The health block is drawn after the model, at the name-tag anchor, so it billboards like the name itself. */
    @Override
    public void render(CompanionEntity entity, float yaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof CompanionInventoryScreen) return;
        if (!aimedAt(entity)) return;
        var anchor = entity.getAttachments().get(net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partialTick));
        pose.pushPose();
        pose.translate(anchor.x, anchor.y + 0.85D, anchor.z);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        // World geometry shrinks with distance like the model does; compensate so the block keeps a readable size
        // up close and stays visible far away.
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float distanceFactor = (float) Math.min(Math.max(Math.sqrt(camera.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())) / 6.0D, 0.55D), 3.0D);
        pose.scale(0.025F * distanceFactor, -0.025F * distanceFactor, 0.025F * distanceFactor);
        var matrix = pose.last().pose();
        var consumer = buffers.getBuffer(BAR);
        float healthShare = CompanionHealth.share(entity.getHealth(), entity.getMaxHealth());
        float armorShare = CompanionHealth.share(entity.getArmorValue(), CompanionHealth.ARMOR_BAR_CEILING);
        // One compact block: a bar as tall as a text line, the armor strip laid over it, and the numbers on top.
        float barWidth = 34.0F, barHeight = 8.0F;
        quad(consumer, matrix, -barWidth / 2.0F, 0.0F, barWidth, barHeight, 0x80000000, 0.0F);
        quad(consumer, matrix, -barWidth / 2.0F, 0.0F, barWidth * healthShare, barHeight, 0xFF3FBF3F, 1.0F);
        quad(consumer, matrix, -barWidth / 2.0F - 1.0F, -1.0F, (barWidth + 2.0F) * armorShare, barHeight + 3.0F, 0x88C8CDD4, 2.0F);

        // The bars are drawn without a depth test, so they must reach the screen before the text does; otherwise they
        // paint over the numbers when both buffers are flushed at the end of the frame. Flush our type only.
        if (buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source) source.endBatch(BAR);

        // The numbers are drawn in the same space, centred on the bar, smaller than a full text line.
        var numbers = net.minecraft.network.chat.Component.literal(CompanionHealth.health(entity.getHealth(), entity.getMaxHealth()));
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float textScale = 0.85F;
        pose.pushPose();
        pose.scale(textScale, textScale, 1.0F);
        font.drawInBatch(numbers, -font.width(numbers) / 2.0F, (barHeight - 8.0F * textScale) / 2.0F / textScale, 0xFFFFFFFF, false,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, light);
        pose.popPose();
        pose.popPose();
    }

    /** One flat coloured rectangle in the given space; larger z is nearer the camera. */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer consumer, org.joml.Matrix4f matrix,
            float x, float y, float width, float height, int argb, float z) {
        int alpha = (argb >>> 24) & 0xFF, red = (argb >> 16) & 0xFF, green = (argb >> 8) & 0xFF, blue = argb & 0xFF;
        consumer.addVertex(matrix, x, y + height, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x + width, y + height, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x + width, y, z).setColor(red, green, blue, alpha);
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }

    /**
     * Whether the crosshair is on him: the game's own pick entity answers for arm's reach, and beyond it the view ray is
     * intersected with his bounding box, so a distant glance at his side still counts. A block nearer than that hit
     * hides him: the bars are drawn over terrain, so they must not float in front of a wall. Past arm's reach the game's
     * own pick no longer looks at blocks, so the world is clipped along the same ray here.
     */
    private static boolean aimedAt(CompanionEntity entity) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.crosshairPickEntity == entity) return true;
        var player = client.player;
        if (player == null || player.level() != entity.level()) return false;
        var eye = player.getEyePosition();
        var reach = player.getViewVector(1.0F).scale(64.0D);
        var hit = entity.getBoundingBox().inflate(0.3D).clip(eye, eye.add(reach));
        if (hit.isEmpty()) return false;
        double distance = hit.get().distanceTo(eye);
        var pick = client.hitResult;
        if (pick != null && pick.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && pick.getLocation().distanceTo(eye) <= distance) return false;
        var blockHit = entity.level().clip(new net.minecraft.world.level.ClipContext(eye, hit.get(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
}
