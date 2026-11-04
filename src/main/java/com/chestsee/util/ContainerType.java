package com.chestsee.util;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractChestBoatEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;

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
		if(entity instanceof StorageMinecartEntity) return "minecart";
		if(entity instanceof AbstractChestBoatEntity) return "chest_boat";
		return "other";
	}

	// Furnaces and brewing stands change their own contents while they work,
	// so for them we compare the player's inventory instead of the container.
	public static boolean comparesPlayerInventory(String type)
	{
		return type.equals("furnace") || type.equals("brewing_stand");
	}
}
