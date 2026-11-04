package com.chestsee.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.level.block.entity.*;

public class ContainerType
{
	public static String of(BlockEntity blockEntity)
	{
		if(blockEntity instanceof ChestBlockEntity) return "chest";
		if(blockEntity instanceof BarrelBlockEntity) return "barrel";
		if(blockEntity instanceof ShulkerBoxBlockEntity) return "shulker_box";
		if(blockEntity instanceof HopperBlockEntity) return "hopper";
		if(blockEntity instanceof DropperBlockEntity) return "dropper";
		if(blockEntity instanceof DispenserBlockEntity) return "dispenser";
		if(blockEntity instanceof CrafterBlockEntity) return "crafter";
		if(blockEntity instanceof AbstractFurnaceBlockEntity) return "furnace";
		if(blockEntity instanceof BrewingStandBlockEntity) return "brewing_stand";
		return "other";
	}

	public static String of(Entity entity)
	{
		if(entity instanceof AbstractMinecartContainer) return "minecart";
		if(entity instanceof AbstractChestBoat) return "chest_boat";
		return "other";
	}

	// Furnaces and brewing stands change their own contents while they work,
	// so for them we compare the player's inventory instead of the container.
	public static boolean comparesPlayerInventory(String type)
	{
		return type.equals("furnace") || type.equals("brewing_stand");
	}
}
