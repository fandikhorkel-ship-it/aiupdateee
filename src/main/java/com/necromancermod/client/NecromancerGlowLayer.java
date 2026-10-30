package com.necromancermod.client;

import com.necromancermod.NecromancerMod;
import com.necromancermod.entity.NecromancerEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Glowing eyes, staff crystal and gems (texture mask rendered at full brightness). */
public class NecromancerGlowLayer extends EyesLayer<NecromancerEntity, NecromancerModel> {

    private static final RenderType GLOW = RenderType.eyes(
            new ResourceLocation(NecromancerMod.MODID, "textures/entity/necromancer_glow.png"));

    public NecromancerGlowLayer(RenderLayerParent<NecromancerEntity, NecromancerModel> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return GLOW;
    }
}
