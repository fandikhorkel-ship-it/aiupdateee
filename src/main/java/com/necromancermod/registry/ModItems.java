package com.necromancermod.registry;

import com.necromancermod.NecromancerMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NecromancerMod.MODID);

    public static final RegistryObject<Item> NECROMANCER_SPAWN_EGG = ITEMS.register("necromancer_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NECROMANCER, 0x2B1E3D, 0x2FD0FF, new Item.Properties()));
}
