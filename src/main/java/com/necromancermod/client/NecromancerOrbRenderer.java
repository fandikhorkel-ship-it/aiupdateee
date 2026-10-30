package com.necromancermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.necromancermod.NecromancerMod;
import com.necromancermod.entity.NecromancerOrb;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Modra svitici koule (billboard), stejna technika jako DragonFireballRenderer. */
public class NecromancerOrbRenderer extends EntityRenderer<NecromancerOrb> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NecromancerMod.MODID, "textures/entity/necromancer_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final int FULL_BRIGHT = 15728880;

    public NecromancerOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(NecromancerOrb entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.6F, 0.6F, 0.6F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        vertex(consumer, matrix, normal, 0.0F, 0, 0, 1);
        vertex(consumer, matrix, normal, 1.0F, 0, 1, 1);
        vertex(consumer, matrix, normal, 1.0F, 1, 1, 0);
        vertex(consumer, matrix, normal, 0.0F, 1, 0, 0);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, int y, int u, int v) {
        consumer.vertex(matrix, x - 0.5F, (float) y - 0.25F, 0.0F)
                .color(255, 255, 255, 255)
                .uv((float) u, (float) v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(NecromancerOrb entity) {
        return TEXTURE;
    }
}
