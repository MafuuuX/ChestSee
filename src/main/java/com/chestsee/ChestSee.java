package com.chestsee;

import com.chestsee.command.ChestSeeCommand;
import com.chestsee.config.ChestSeeConfig;
import com.chestsee.log.LogMaintenance;
import com.chestsee.util.BlockPlaceCallback;
import com.chestsee.util.ContainerMenuCallback;
import com.chestsee.util.ContainerType;
import com.chestsee.util.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
	private static final Map<UUID, Tracked> clickedContainers = new HashMap<>();
	private static final Map<UUID, OpenContainer> openContainers = new HashMap<>();
	private static final Set<UUID> inspecting = new HashSet<>();
	private static Path logFolder;
	private static ChestSeeConfig config = new ChestSeeConfig();

	private record Tracked(List<Inventory> containers, List<String> logPaths, boolean comparePlayer) {}
	private record OpenContainer(ScreenHandler menu, Tracked tracked, List<String> items) {}

	@Override
	public void onInitialize()
	{
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChestSeeCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register((server ->
		{
			reloadConfig();
			logFolder = server.getSavePath(WorldSavePath.ROOT).resolve("ChestLog");
			clickedContainers.clear();
			openContainers.clear();
			inspecting.clear();

			for(String dimStr : List.of("", "Overworld", "The Nether", "The End"))
			{
				File folder = logFolder.resolve(dimStr).toFile();

				if (!folder.exists()) {
					boolean created = folder.mkdirs();
					if (!created)
						LOGGER.error("Failed to create folder: " + folder);
				}
			}

			int removed = LogMaintenance.removeOldEntries(logFolder, config.retentionDays);
			if(removed > 0)
				LOGGER.info("Removed " + removed + " log entries older than " + config.retentionDays + " days");
		}));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
		{
			clickedContainers.remove(handler.getPlayer().getUuid());
			inspecting.remove(handler.getPlayer().getUuid());
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
		{
			if(world.isClient())
				return ActionResult.PASS;

			BlockPos blockPos = hitResult.getBlockPos();
			if(isInspecting(player))
			{
				ChestSeeCommand.showBlockLog(((ServerPlayerEntity) player).getCommandSource(), (ServerWorld) world, blockPos, 1);
				return ActionResult.FAIL;
			}

			BlockEntity blockEntity = world.getBlockEntity(blockPos);
			if(!(blockEntity instanceof LockableContainerBlockEntity container))
				return ActionResult.PASS;

			String type = ContainerType.of(container);
			if(!shouldLog(world, type))
				return ActionResult.PASS;

			List<Inventory> containers = new ArrayList<>(List.of(container));
			List<String> paths = new ArrayList<>(List.of(getLogPath(world, world.getBlockState(blockPos), blockPos)));
			ChestBlockEntity secondChest = Utils.getSecondChest(world.getBlockState(blockPos), blockEntity, world);
			if(secondChest != null)
			{
				containers.add(secondChest);
				paths.add(getLogPath(world, secondChest.getCachedState(), secondChest.getPos()));
			}

			clickedContainers.put(player.getUuid(), new Tracked(containers, paths, ContainerType.comparesPlayerInventory(type)));
			return ActionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
		{
			if(world.isClient() || !isInspecting(player))
				return ActionResult.PASS;

			ChestSeeCommand.showBlockLog(((ServerPlayerEntity) player).getCommandSource(), (ServerWorld) world, pos, 1);
			return ActionResult.FAIL;
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> inspectEntity(player, world, entity));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> inspectEntity(player, world, entity));

		ContainerMenuCallback.OPENED.register((player, menu) ->
		{
			Tracked tracked = clickedContainers.remove(player.getUuid());
			if(menu.slots.isEmpty())
				return;

			Inventory opened = menu.slots.get(0).inventory;
			if(tracked == null)
			{
				// minecarts and chest boats open their menu with the entity itself as the container
				if(!(opened instanceof Entity entity) || !(opened instanceof VehicleInventory) || !shouldLog(entity.getEntityWorld(), ContainerType.of(entity)))
					return;
				tracked = new Tracked(List.of(opened), List.of(getEntityLogPath(entity)), false);
			}
			else
			{
				Inventory first = tracked.containers().get(0);
				if(opened != first && !(opened instanceof DoubleInventory compound && compound.isPart(first)))
					return;
			}

			openContainers.put(player.getUuid(), new OpenContainer(menu, tracked, snapshot(player, menu, tracked)));
		});

		ContainerMenuCallback.CLOSED.register((player, menu) ->
		{
			OpenContainer open = openContainers.get(player.getUuid());
			if(open == null || open.menu() != menu)
				return;
			openContainers.remove(player.getUuid());

			List<String> itemsBefore = open.items();
			List<String> itemsAfter = snapshot(player, menu, open.tracked());
			List<String> itemsAdded = Utils.getChangedItems(itemsBefore, itemsAfter);
			List<String> itemsRemoved = Utils.getChangedItems(itemsAfter, itemsBefore);
			if(open.tracked().comparePlayer())
			{
				List<String> swap = itemsAdded;
				itemsAdded = itemsRemoved;
				itemsRemoved = swap;
			}

			if(itemsAdded.isEmpty() && itemsRemoved.isEmpty())
				return;

			String timeStamp = Utils.getTimeStamp();
			for(String path : open.tracked().logPaths())
				Utils.writeLog(timeStamp, player.getName().getString(), path, itemsAdded, itemsRemoved);
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, blockPos, blockState, blockEntity) ->
		{
			if(isInspecting(player))
				return false;
			if(!(blockEntity instanceof LockableContainerBlockEntity) || !shouldLog(world, ContainerType.of(blockEntity)))
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
			if(!(chestEntity instanceof LockableContainerBlockEntity) || !shouldLog(world, ContainerType.of(chestEntity)))
				return;

			BlockState chestState = world.getBlockState(chestPos);
			ChestBlockEntity secondChest = Utils.getSecondChest(chestState, chestEntity, world);
			String playerName = context.getPlayer().getName().getString();

			Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, chestPos), "Placed Hopper underneath container");
			if(secondChest != null)
				Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, secondChest.getPos()), "Placed Hopper underneath container");
		}));
	}

	private static ActionResult inspectEntity(PlayerEntity player, World world, Entity entity)
	{
		if(world.isClient() || !(entity instanceof VehicleInventory) || !isInspecting(player))
			return ActionResult.PASS;

		ChestSeeCommand.showEntityLog(((ServerPlayerEntity) player).getCommandSource(), entity, 1);
		return ActionResult.FAIL;
	}

	private static List<String> snapshot(ServerPlayerEntity player, ScreenHandler menu, Tracked tracked)
	{
		List<ItemStack> items = new ArrayList<>();
		if(tracked.comparePlayer())
		{
			items.addAll(Utils.getItems(player.getInventory()));
			if(!menu.getCursorStack().isEmpty())
				items.add(menu.getCursorStack());
		}
		else
		{
			for(Inventory container : tracked.containers())
				items.addAll(Utils.getItems(container));
		}
		return Utils.itemStackListToStrList(items);
	}

	private static boolean shouldLog(World world, String containerType)
	{
		return config.isLogged(containerType) && !config.isExcluded(world.getRegistryKey().getValue().toString());
	}

	private static String getLogPath(World world, BlockState blockState, BlockPos blockPos)
	{
		String dimStr = Utils.getDimString(world.getDimensionEntry());
		String blockstr = blockState.getBlock().getName().getString();
		return logFolder.resolve(dimStr).resolve(blockstr + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ()).toString();
	}

	public static String getEntityLogPath(Entity entity)
	{
		String dimStr = Utils.getDimString(entity.getEntityWorld().getDimensionEntry());
		return logFolder.resolve(dimStr).resolve(entity.getType().getName().getString() + " " + entity.getUuid()).toString();
	}

	public static boolean isInspecting(PlayerEntity player)
	{
		return inspecting.contains(player.getUuid());
	}

	public static boolean toggleInspect(ServerPlayerEntity player)
	{
		if(inspecting.remove(player.getUuid()))
			return false;
		inspecting.add(player.getUuid());
		return true;
	}

	public static void reloadConfig()
	{
		config = ChestSeeConfig.load(FabricLoader.getInstance().getConfigDir().resolve("chestsee.json"));
	}

	public static ChestSeeConfig getConfig()
	{
		return config;
	}

	public static Path getLogFolder()
	{
		return logFolder;
	}
}
