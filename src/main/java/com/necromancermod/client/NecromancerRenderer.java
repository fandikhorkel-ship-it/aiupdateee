package com.necromancermod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.necromancermod.NecromancerMod;
import com.necromancermod.entity.NecromancerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class NecromancerRenderer extends MobRenderer<NecromancerEntity, NecromancerModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NecromancerMod.MODID, "textures/entity/necromancer.png");

    public NecromancerRenderer(EntityRendererProvider.Context context) {
        super(context, new NecromancerModel(), 0.5F * NecromancerEntity.SCALE);
        this.addLayer(new NecromancerGlowLayer(this));
    }

    @Override
    protected void scale(NecromancerEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(NecromancerEntity.SCALE, NecromancerEntity.SCALE, NecromancerEntity.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(NecromancerEntity entity) {
        return TEXTURE;
    }
}
