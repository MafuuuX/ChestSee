package com.chestsee;

import com.chestsee.util.BlockPlaceCallback;
import com.chestsee.util.ContainerMenuCallback;
import com.chestsee.util.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class ChestSee implements ModInitializer
{
	public static final Logger LOGGER = LoggerFactory.getLogger("chestsee");
	private static final Map<UUID, List<RandomizableContainerBlockEntity>> clickedContainers = new HashMap<>();
	private static final Map<UUID, OpenContainer> openContainers = new HashMap<>();
	private static Path logFolder;

	private record OpenContainer(AbstractContainerMenu menu, List<RandomizableContainerBlockEntity> containers, List<String> items) {}

	@Override
	public void onInitialize()
	{
		ServerLifecycleEvents.SERVER_STARTED.register((server ->
		{
			logFolder = server.getWorldPath(LevelResource.ROOT).resolve("ChestLog");
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

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clickedContainers.remove(handler.getPlayer().getUUID()));

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
		{
			BlockPos blockPos = hitResult.getBlockPos();
			BlockEntity blockEntity = world.getBlockEntity(blockPos);

			if(world.isClientSide() || !(blockEntity instanceof RandomizableContainerBlockEntity container))
				return InteractionResult.PASS;

			List<RandomizableContainerBlockEntity> containers = new ArrayList<>(List.of(container));
			ChestBlockEntity secondChest = Utils.getSecondChest(world.getBlockState(blockPos), blockEntity, world);
			if(secondChest != null)
				containers.add(secondChest);

			clickedContainers.put(player.getUUID(), containers);
			return InteractionResult.PASS;
		});

		ContainerMenuCallback.OPENED.register((player, menu) ->
		{
			List<RandomizableContainerBlockEntity> containers = clickedContainers.remove(player.getUUID());
			if(containers == null || menu.slots.isEmpty())
				return;

			Container opened = menu.slots.get(0).container;
			if(opened != containers.get(0) && !(opened instanceof CompoundContainer compound && compound.contains(containers.get(0))))
				return;

			openContainers.put(player.getUUID(), new OpenContainer(menu, containers, getItems(containers)));
		});

		ContainerMenuCallback.CLOSED.register((player, menu) ->
		{
			OpenContainer open = openContainers.get(player.getUUID());
			if(open == null || open.menu() != menu)
				return;
			openContainers.remove(player.getUUID());

			List<String> itemsAfter = getItems(open.containers());
			List<String> itemsAdded = Utils.getChangedItems(open.items(), itemsAfter);
			List<String> itemsRemoved = Utils.getChangedItems(itemsAfter, open.items());

			if(itemsAdded.isEmpty() && itemsRemoved.isEmpty())
				return;

			String timeStamp = Utils.getTimeStamp();
			for(RandomizableContainerBlockEntity container : open.containers())
				Utils.writeLog(timeStamp, player.getName().getString(), getLogPath(container.getLevel(), container.getBlockState(), container.getBlockPos()), itemsAdded, itemsRemoved);
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, blockPos, blockState, blockEntity) ->
		{
			if(!(blockEntity instanceof RandomizableContainerBlockEntity))
				return true;

			String blockstr = blockState.getBlock().getName().getString();
			Utils.writeMessageLog(Utils.getTimeStamp(), player.getName().getString(), getLogPath(world, blockState, blockPos), "Broke " + blockstr);
			return true;
		});

		BlockPlaceCallback.EVENT.register((context -> {
			Level world = context.getLevel();
			BlockPos blockPos = context.getClickedPos();

			if(world.isClientSide() || context.getPlayer() == null || world.getBlockState(blockPos).getBlock() != Blocks.HOPPER)
				return;

			BlockPos chestPos = blockPos.above();
			BlockEntity chestEntity = world.getBlockEntity(chestPos);
			if(!(chestEntity instanceof RandomizableContainerBlockEntity))
				return;

			BlockState chestState = world.getBlockState(chestPos);
			ChestBlockEntity secondChest = Utils.getSecondChest(chestState, chestEntity, world);
			String playerName = context.getPlayer().getName().getString();

			Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, chestPos), "Placed Hopper underneath container");
			if(secondChest != null)
				Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, secondChest.getBlockPos()), "Placed Hopper underneath container");
		}));
	}

	private static List<String> getItems(List<RandomizableContainerBlockEntity> containers)
	{
		List<ItemStack> items = new ArrayList<>();
		for(RandomizableContainerBlockEntity container : containers)
			items.addAll(Utils.getItems(container));
		return Utils.itemStackListToStrList(items);
	}

	private static String getLogPath(Level world, BlockState blockState, BlockPos blockPos)
	{
		String dimStr = Utils.getDimString(world.dimensionTypeRegistration());
		String blockstr = blockState.getBlock().getName().getString();
		return logFolder.resolve(dimStr).resolve(blockstr + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ()).toString();
	}
}
