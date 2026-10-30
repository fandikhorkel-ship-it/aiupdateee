package com.necromancermod.registry;

import com.necromancermod.NecromancerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NecromancerMod.MODID);

    public static final RegistryObject<SoundEvent> LAUGH = register("necromancer.laugh");
    public static final RegistryObject<SoundEvent> ORB_CAST = register("necromancer.orb_cast");
    public static final RegistryObject<SoundEvent> MINION_SUMMONED = register("necromancer.minion_summoned");
    public static final RegistryObject<SoundEvent> SUMMON_CAST = register("necromancer.summon_cast");
    public static final RegistryObject<SoundEvent> IDLE = register("necromancer.idle");
    public static final RegistryObject<SoundEvent> HURT = register("necromancer.hurt");
    public static final RegistryObject<SoundEvent> DEATH = register("necromancer.death");
    public static final RegistryObject<SoundEvent> ORB_IMPACT = register("necromancer.orb_impact");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(NecromancerMod.MODID, name)));
    }
}
