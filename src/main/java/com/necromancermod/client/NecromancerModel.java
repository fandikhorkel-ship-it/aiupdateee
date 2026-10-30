package com.necromancermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.necromancermod.entity.NecromancerEntity;
import net.minecraft.client.model.EntityModel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Model Necromancera nacteny z geo/necromancer.bin (prevod Blockbench glTF modelu),
 * vcetne animaci idle / walk / projectile / summon.
 */
public class NecromancerModel extends EntityModel<NecromancerEntity> {

    private static final NecroModelData DATA =
            NecroModelData.loadFromClasspath("/assets/necromancermod/geo/necromancer.bin");

    private final NecroPoseController pose = new NecroPoseController(DATA);

    @Override
    public void setupAnim(NecromancerEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float actionSeconds = (ageInTicks - (float) entity.animStartTick) / 20.0F;
        this.pose.update(entity.getAnimState(), actionSeconds, limbSwing, limbSwingAmount,
                ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        poseStack.pushPose();
        // LivingEntityRenderer uz aplikoval scale(-1,-1,1) a translate(0,-1.501,0) (vanilla souradnice).
        // Model je v glTF souradnicich (Y nahoru, nohy v y=0), proto tento prevod vratime zpet.
        poseStack.translate(0.0D, 1.501D, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        for (int root : DATA.roots) {
            renderNode(root, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
        poseStack.popPose();
    }

    private void renderNode(int node, PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                            float red, float green, float blue, float alpha) {
        poseStack.pushPose();
        poseStack.translate((double) this.pose.outT[node * 3], (double) this.pose.outT[node * 3 + 1],
                (double) this.pose.outT[node * 3 + 2]);
        poseStack.mulPose(new Quaternionf(this.pose.outR[node * 4], this.pose.outR[node * 4 + 1],
                this.pose.outR[node * 4 + 2], this.pose.outR[node * 4 + 3]));

        float[] quads = DATA.quads[node];
        if (quads.length > 0) {
            PoseStack.Pose last = poseStack.last();
            Matrix4f matrix = last.pose();
            Matrix3f normal = last.normal();
            for (int k = 0; k < quads.length; k += 8) {
                buffer.vertex(matrix, quads[k], quads[k + 1], quads[k + 2])
                        .color(red, green, blue, alpha)
                        .uv(quads[k + 3], quads[k + 4])
                        .overlayCoords(overlay)
                        .uv2(light)
                        .normal(normal, quads[k + 5], quads[k + 6], quads[k + 7])
                        .endVertex();
            }
        }

        for (int child : DATA.children[node]) {
            renderNode(child, poseStack, buffer, light, overlay, red, green, blue, alpha);
        }
        poseStack.popPose();
    }
}
