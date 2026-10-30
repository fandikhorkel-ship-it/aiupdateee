package com.necromancermod.client;

import com.necromancermod.NecromancerMod;
import com.necromancermod.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NecromancerMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NECROMANCER.get(), NecromancerRenderer::new);
        event.registerEntityRenderer(ModEntities.ORB.get(), NecromancerOrbRenderer::new);
    }
}
