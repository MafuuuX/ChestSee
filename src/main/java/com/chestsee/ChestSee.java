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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
	private static final Map<UUID, Tracked> clickedContainers = new HashMap<>();
	private static final Map<UUID, OpenContainer> openContainers = new HashMap<>();
	private static final Set<UUID> inspecting = new HashSet<>();
	private static Path logFolder;
	private static ChestSeeConfig config = new ChestSeeConfig();

	private record Tracked(List<Container> containers, List<String> logPaths, boolean comparePlayer) {}
	private record OpenContainer(AbstractContainerMenu menu, Tracked tracked, List<String> items) {}

	@Override
	public void onInitialize()
	{
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChestSeeCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register((server ->
		{
			reloadConfig();
			logFolder = server.getWorldPath(LevelResource.ROOT).resolve("ChestLog");
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
			clickedContainers.remove(handler.getPlayer().getUUID());
			inspecting.remove(handler.getPlayer().getUUID());
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
		{
			if(world.isClientSide())
				return InteractionResult.PASS;

			BlockPos blockPos = hitResult.getBlockPos();
			if(isInspecting(player))
			{
				ChestSeeCommand.showBlockLog(((ServerPlayer) player).createCommandSourceStack(), (ServerLevel) world, blockPos, 1);
				return InteractionResult.FAIL;
			}

			BlockEntity blockEntity = world.getBlockEntity(blockPos);
			if(!(blockEntity instanceof BaseContainerBlockEntity container))
				return InteractionResult.PASS;

			String type = ContainerType.of(container);
			if(!shouldLog(world, type))
				return InteractionResult.PASS;

			List<Container> containers = new ArrayList<>(List.of(container));
			List<String> paths = new ArrayList<>(List.of(getLogPath(world, world.getBlockState(blockPos), blockPos)));
			ChestBlockEntity secondChest = Utils.getSecondChest(world.getBlockState(blockPos), blockEntity, world);
			if(secondChest != null)
			{
				containers.add(secondChest);
				paths.add(getLogPath(world, secondChest.getBlockState(), secondChest.getBlockPos()));
			}

			clickedContainers.put(player.getUUID(), new Tracked(containers, paths, ContainerType.comparesPlayerInventory(type)));
			return InteractionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
		{
			if(world.isClientSide() || !isInspecting(player))
				return InteractionResult.PASS;

			ChestSeeCommand.showBlockLog(((ServerPlayer) player).createCommandSourceStack(), (ServerLevel) world, pos, 1);
			return InteractionResult.FAIL;
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> inspectEntity(player, world, entity));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> inspectEntity(player, world, entity));

		ContainerMenuCallback.OPENED.register((player, menu) ->
		{
			Tracked tracked = clickedContainers.remove(player.getUUID());
			if(menu.slots.isEmpty())
				return;

			Container opened = menu.slots.get(0).container;
			if(tracked == null)
			{
				// minecarts and chest boats open their menu with the entity itself as the container
				if(!(opened instanceof Entity entity) || !(opened instanceof ContainerEntity) || !shouldLog(entity.level(), ContainerType.of(entity)))
					return;
				tracked = new Tracked(List.of(opened), List.of(getEntityLogPath(entity)), false);
			}
			else
			{
				Container first = tracked.containers().get(0);
				if(opened != first && !(opened instanceof CompoundContainer compound && compound.contains(first)))
					return;
			}

			openContainers.put(player.getUUID(), new OpenContainer(menu, tracked, snapshot(player, menu, tracked)));
		});

		ContainerMenuCallback.CLOSED.register((player, menu) ->
		{
			OpenContainer open = openContainers.get(player.getUUID());
			if(open == null || open.menu() != menu)
				return;
			openContainers.remove(player.getUUID());

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
			if(!(blockEntity instanceof BaseContainerBlockEntity) || !shouldLog(world, ContainerType.of(blockEntity)))
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
			if(!(chestEntity instanceof BaseContainerBlockEntity) || !shouldLog(world, ContainerType.of(chestEntity)))
				return;

			BlockState chestState = world.getBlockState(chestPos);
			ChestBlockEntity secondChest = Utils.getSecondChest(chestState, chestEntity, world);
			String playerName = context.getPlayer().getName().getString();

			Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, chestPos), "Placed Hopper underneath container");
			if(secondChest != null)
				Utils.writeMessageLog(Utils.getTimeStamp(), playerName, getLogPath(world, chestState, secondChest.getBlockPos()), "Placed Hopper underneath container");
		}));
	}

	private static InteractionResult inspectEntity(Player player, Level world, Entity entity)
	{
		if(world.isClientSide() || !(entity instanceof ContainerEntity) || !isInspecting(player))
			return InteractionResult.PASS;

		ChestSeeCommand.showEntityLog(((ServerPlayer) player).createCommandSourceStack(), entity, 1);
		return InteractionResult.FAIL;
	}

	private static List<String> snapshot(ServerPlayer player, AbstractContainerMenu menu, Tracked tracked)
	{
		List<ItemStack> items = new ArrayList<>();
		if(tracked.comparePlayer())
		{
			items.addAll(Utils.getItems(player.getInventory()));
			if(!menu.getCarried().isEmpty())
				items.add(menu.getCarried());
		}
		else
		{
			for(Container container : tracked.containers())
				items.addAll(Utils.getItems(container));
		}
		return Utils.itemStackListToStrList(items);
	}

	private static boolean shouldLog(Level world, String containerType)
	{
		return config.isLogged(containerType) && !config.isExcluded(world.dimension().identifier().toString());
	}

	private static String getLogPath(Level world, BlockState blockState, BlockPos blockPos)
	{
		String dimStr = Utils.getDimString(world.dimensionTypeRegistration());
		String blockstr = blockState.getBlock().getName().getString();
		return logFolder.resolve(dimStr).resolve(blockstr + " " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ()).toString();
	}

	public static String getEntityLogPath(Entity entity)
	{
		String dimStr = Utils.getDimString(entity.level().dimensionTypeRegistration());
		return logFolder.resolve(dimStr).resolve(entity.getType().getDescription().getString() + " " + entity.getUUID()).toString();
	}

	public static boolean isInspecting(Player player)
	{
		return inspecting.contains(player.getUUID());
	}

	public static boolean toggleInspect(ServerPlayer player)
	{
		if(inspecting.remove(player.getUUID()))
			return false;
		inspecting.add(player.getUUID());
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
