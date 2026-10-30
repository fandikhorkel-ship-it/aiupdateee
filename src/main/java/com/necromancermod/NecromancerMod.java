package com.necromancermod;

import com.necromancermod.registry.ModEntities;
import com.necromancermod.registry.ModItems;
import com.necromancermod.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NecromancerMod.MODID)
public class NecromancerMod {

    public static final String MODID = "necromancermod";

    public NecromancerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
    }
}
