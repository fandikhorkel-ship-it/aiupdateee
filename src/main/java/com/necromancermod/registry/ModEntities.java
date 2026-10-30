package com.necromancermod.registry;

import com.necromancermod.NecromancerMod;
import com.necromancermod.entity.NecromancerEntity;
import com.necromancermod.entity.NecromancerOrb;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NecromancerMod.MODID);

    public static final RegistryObject<EntityType<NecromancerEntity>> NECROMANCER = ENTITIES.register("necromancer",
            () -> EntityType.Builder.of(NecromancerEntity::new, MobCategory.MONSTER)
                    .sized(0.7F * NecromancerEntity.SCALE, 2.1F * NecromancerEntity.SCALE)
                    .clientTrackingRange(8)
                    .build("necromancer"));

    public static final RegistryObject<EntityType<NecromancerOrb>> ORB = ENTITIES.register("necromancer_orb",
            () -> EntityType.Builder.<NecromancerOrb>of(NecromancerOrb::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("necromancer_orb"));
}
