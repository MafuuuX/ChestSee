package com.chestsee;

import com.chestsee.util.BlockPlaceCallback;
import com.chestsee.util.ContainerMenuCallback;
import com.chestsee.util.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class ChestSee implements ModInitializer
{
	public static final Logger LOGGER = LoggerFactory.getLogger("chestsee");
	private static final Map<UUID, List<LootableContainerBlockEntity>> clickedContainers = new HashMap<>();
	private static final Map<UUID, OpenContainer> openContainers = new HashMap<>();
	private static Path logFolder;

	private record OpenContainer(ScreenHandler handler, List<LootableContainerBlockEntity> containers, List<String> items) {}

	@Override
	public void onInitialize()
	{
		ServerLifecycleEvents.SERVER_STARTED.register((server ->
		{
			logFolder = server.getSavePath(WorldSavePath.ROOT).resolve("ChestLog");
			clickedContainers.clear();
			openContainers.clear();

			for(String dimStr : List.of("", "Overworld", "The Nether", "The End"))
			{
				File folder = logFolder.resolve(dimStr).toFile();

				if (!folder.exists()) {
					boolean created = folder.mkdirs();
					if (!created)
						LOGGER.error("Failed to create folder: " + folder);
				}
			}
		}));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clickedContainers.remove(handler.getPlayer().getUuid()));

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
		{
			BlockPos blockPos = hitResult.getBlockPos();
			BlockEntity blockEntity = world.getBlockEntity(blockPos);

			if(world.isClient() || !(blockEntity instanceof LootableContainerBlockEntity container))
				return ActionResult.PASS;

			List<LootableContainerBlockEntity> containers = new ArrayList<>(List.of(container));
			ChestBlockEntity secondChest = Utils.getSecondChest(world.getBlockState(blockPos), blockEntity, world);
			if(secondChest != null)
				containers.add(secondChest);

			clickedContainers.put(player.getUuid(), containers);
			return ActionResult.PASS;
		});

		ContainerMenuCallback.OPENED.register((player, handler) ->
		{
			List<LootableContainerBlockEntity> containers = clickedContainers.remove(player.getUuid());
			if(containers == null || handler.slots.isEmpty())
				return;

			Inventory opened = handler.slots.get(0).inventory;
			if(opened != containers.get(0) && !(opened instanceof DoubleInventory doubleInventory && doubleInventory.isPart(containers.get(0))))
				return;

			openContainers.put(player.getUuid(), new OpenContainer(handler, containers, getItems(containers)));
		});

		ContainerMenuCallback.CLOSED.register((player, handler) ->
		{
			OpenContainer open = openContainers.get(player.getUuid());
			if(open == null || open.handler() != handler)
				return;
			openContainers.remove(player.getUuid());

			List<String> itemsAfter = getItems(open.containers());
			List<String> itemsAdded = Utils.getChangedItems(open.items(), itemsAfter);
			List<String> itemsRemoved = Utils.getChangedItems(itemsAfter, open.items());

			if(itemsAdded.isEmpty() && itemsRemoved.isEmpty())
				return;

			String timeStamp = Utils.getTimeStamp();
			for(LootableContainerBlockEntity container : open.containers())
				Utils.writeLog(timeStamp, player.getName().getString(), getLogPath(container.getWorld(), container.getCachedState(), container.getPos()), itemsAdded, itemsRemoved);
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, blockPos, blockState, blockEntity) ->
		{
			if(!(blockEntity instanceof LootableContainerBlockEntity))
				return true;

			String blockstr = blockState.getBlock().getName().getString();
			Utils.writeMessageLog(Utils.getTimeStamp(), player.getName().getString(), getLogPath(world, blockState, blockPos), "Broke " + blockstr);
			return true;
		});

		BlockPlaceCallback.EVENT.register((context -> {
			World world = context.getWorld();
			BlockPos blockPos = context.getBlockPos();

			if(world.isClient() || context.getPlayer() == null || world.getBlockState(blockPos).getBlock() != Blocks.HOPPER)
				return;

			BlockPos chestPos = blockPos.up();
			BlockEntity chestEntity = world.getBlockEntity(chestPos);
			if(!(chestEntity instanceof LootableContainerBlockEntity))
				return;

			BlockState chestState = world.getBlockState(chestPos);
			ChestBlockEntity secondChest = Utils.getSecondChest(chestState, chestEntity, world);
			String playerName = context.getPlayer().getName().getString();

			Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, chestPos), "Placed Hopper underneath container");
			if(secondChest != null)
				Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, secondChest.getPos()), "Placed Hopper underneath container");
		}));
	}

	private static List<String> getItems(List<LootableContainerBlockEntity> containers)
	{
		List<ItemStack> items = new ArrayList<>();
		for(LootableContainerBlockEntity container : containers)
			items.addAll(Utils.getItems(container));
		return Utils.itemStackListToStrList(items);
	}

	private static String getLogPath(World world, BlockState blockState, BlockPos blockPos)
	{
		String dimStr = Utils.getDimString(world.getDimensionEntry());
		String blockstr = blockState.getBlock().getName().getString();
		return logFolder.resolve(dimStr).resolve(blockstr + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ()).toString();
	}
}
