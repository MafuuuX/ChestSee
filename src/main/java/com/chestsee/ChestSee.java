package com.chestsee;

import com.chestsee.util.BlockPlaceCallback;
import com.chestsee.util.ChestClosedCallback;
import com.chestsee.util.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class ChestSee implements ModInitializer
{
	public static String chestClosedBy = "";
	public static final Logger LOGGER = LoggerFactory.getLogger("chestsee");
	List<String> folderPaths = new ArrayList<>();

	@Override
	public void onInitialize()
	{
		ServerLifecycleEvents.SERVER_STARTED.register((server ->
		{
			folderPaths.add(server.getWorldPath(LevelResource.ROOT).resolve("ChestLog").toString());
			folderPaths.add(server.getWorldPath(LevelResource.ROOT).resolve("ChestLog" + File.separator + "Overworld").toString());
			folderPaths.add(server.getWorldPath(LevelResource.ROOT).resolve("ChestLog" + File.separator + "The Nether").toString());
			folderPaths.add(server.getWorldPath(LevelResource.ROOT).resolve("ChestLog" + File.separator + "The End").toString());

			for(String folderPath : folderPaths)
			{
				File folder = new File(folderPath);

				if (!folder.exists()) {
					boolean created = folder.mkdirs();
					if (!created)
						LOGGER.error("Failed to create folder: " + folderPath);
				}
			}
		}));

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
		{
			chestClosedBy = "";
			BlockPos blockPos = hitResult.getBlockPos();
			BlockState blockState = world.getBlockState(blockPos);
			Block block = blockState.getBlock();
			BlockEntity blockEntity = world.getBlockEntity(blockPos);
			ChestBlockEntity secondChest = Utils.getSecondChest(blockState, blockEntity, world);

			if(!(blockEntity instanceof RandomizableContainerBlockEntity))
				return InteractionResult.PASS;

			String blockstr = block.getName().getString();

			String dimStr = Utils.getDimString(world.dimensionTypeRegistration());

			AtomicReference<String> savePath = new AtomicReference<>(folderPaths.get(0) + File.separator + dimStr + File.separator + blockstr + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ());

			List<ItemStack> itemsBefore = Utils.getItems((RandomizableContainerBlockEntity) blockEntity);
			if(secondChest != null)
				itemsBefore.addAll(Utils.getItems(secondChest));
			List<String> itemsBeforeStr = Utils.itemStackListToStrList(itemsBefore);

			CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
			{
				while (!chestClosedBy.equals(player.getName().toString()))
				{
					try
					{
						Thread.sleep(100);
					} catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
						LOGGER.error("Thread interrupted while waiting for chest to close", e);
					}
				}

				List<ItemStack> itemsAfter = Utils.getItems((RandomizableContainerBlockEntity) blockEntity);
				if(secondChest != null)
					itemsAfter.addAll(Utils.getItems(secondChest));
				List<String> itemsAfterStr = Utils.itemStackListToStrList(itemsAfter);
				String timeStamp = Utils.getTimeStamp();

				List<String> itemsAdded = Utils.getChangedItems(itemsBeforeStr, itemsAfterStr);
				List<String> itemsRemoved = Utils.getChangedItems(itemsAfterStr, itemsBeforeStr);

				if(!itemsAdded.isEmpty() || !itemsRemoved.isEmpty())
				{
					Utils.writeLog(timeStamp, player.getName().getString(), savePath.get(), itemsAdded, itemsRemoved);
					if (secondChest != null) {
						savePath.set(folderPaths.get(0) + File.separator + dimStr + File.separator + blockstr + " " + secondChest.getBlockPos().getX() + " " + secondChest.getBlockPos().getY() + " " + secondChest.getBlockPos().getZ());
						Utils.writeLog(timeStamp, player.getName().getString(), savePath.get(), itemsAdded, itemsRemoved);
					}
				}
			});
			return InteractionResult.PASS;
		});

		ChestClosedCallback.EVENT.register((user ->
		{
			chestClosedBy = user.getLivingEntity().getName().toString();
		}));

		PlayerBlockBreakEvents.BEFORE.register((world, player, blockPos, blockState, blockEntity) ->
		{
			Block block = blockState.getBlock();
			if(!(blockEntity instanceof RandomizableContainerBlockEntity))
				return true;

			String blockstr = block.getName().getString();
			String dimStr = Utils.getDimString(world.dimensionTypeRegistration());
			String savePath = folderPaths.get(0) + File.separator + dimStr + File.separator + blockstr + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ();

			Utils.writeMessageLog(Utils.getTimeStamp(), player.getName().getString(), savePath, "Broke " + blockstr);
			return true;
		});

		BlockPlaceCallback.EVENT.register((context -> {
			Level world = context.getLevel();
			BlockPos blockPos = context.getClickedPos();
			BlockState blockState = world.getBlockState(blockPos);

			if(blockState.getBlock() == Blocks.HOPPER)
			{
				BlockPos chestPos = blockPos.offset(0, 1, 0);
				if(!(world.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity))
					return;
				BlockState chestState = world.getBlockState(chestPos);
				ChestBlockEntity secondChest = Utils.getSecondChest(chestState, world.getBlockEntity(chestPos), world);

				String blockstr = chestState.getBlock().getName().getString();
				String dimStr = Utils.getDimString(world.dimensionTypeRegistration());
				String savePath = folderPaths.get(0) + File.separator + dimStr + File.separator + blockstr + " " + chestPos.getX() + " " + chestPos.getY() + " " + chestPos.getZ();

				Utils.writeMessageLog(Utils.getTimeStamp(), context.getPlayer().getName().getString(), savePath, "Placed Hopper underneath container");
				if(secondChest != null)
				{
					savePath = folderPaths.get(0) + File.separator + dimStr + File.separator + blockstr + " " + secondChest.getBlockPos().getX() + " " + secondChest.getBlockPos().getY() + " " + secondChest.getBlockPos().getZ();
					Utils.writeMessageLog(Utils.getTimeStamp(), context.getPlayer().getName().getString(), savePath, "Placed Hopper underneath container");
				}
			}
		}));
	}
}
