package com.chestsee.command;

import com.chestsee.ChestSee;
import com.chestsee.log.LogEntry;
import com.chestsee.log.LogReader;
import com.chestsee.permission.ChestSeePermissions;
import com.chestsee.util.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChestSeeCommand
{
	private static final ExecutorService SEARCH_THREAD = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "ChestSee search");
		thread.setDaemon(true);
		return thread;
	});

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
	{
		dispatcher.register(CommandManager.literal("chestsee")
			.requires(ChestSeePermissions::canUseAny)
			.then(CommandManager.literal("inspect")
				.requires(ChestSeePermissions::canInspect)
				.executes(ChestSeeCommand::inspect))
			.then(CommandManager.literal("lookup")
				.requires(ChestSeePermissions::canLookup)
				.then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
					.executes(ctx -> lookup(ctx, 1, ctx.getSource().getWorld()))
					.then(CommandManager.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> lookup(ctx, IntegerArgumentType.getInteger(ctx, "page"), ctx.getSource().getWorld()))
						.then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
							.executes(ctx -> lookup(ctx, IntegerArgumentType.getInteger(ctx, "page"), DimensionArgumentType.getDimensionArgument(ctx, "dimension")))))))
			.then(CommandManager.literal("player")
				.requires(ChestSeePermissions::canSearchPlayers)
				.then(CommandManager.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(ctx.getSource().getPlayerNames(), builder))
					.executes(ctx -> player(ctx, 1))
					.then(CommandManager.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> player(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
			.then(CommandManager.literal("reload")
				.requires(ChestSeePermissions::canReload)
				.executes(ChestSeeCommand::reload)));
	}

	private static int inspect(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException
	{
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		boolean on = ChestSee.toggleInspect(player);
		if(on)
			ctx.getSource().sendFeedback(() -> prefix().append(Text.literal("Inspect mode on. Right-click or punch a container to see its log.").formatted(Formatting.GRAY)), false);
		else
			ctx.getSource().sendFeedback(() -> prefix().append(Text.literal("Inspect mode off.").formatted(Formatting.GRAY)), false);
		return 1;
	}

	private static int lookup(CommandContext<ServerCommandSource> ctx, int page, ServerWorld level)
	{
		showBlockLog(ctx.getSource(), level, BlockPosArgumentType.getBlockPos(ctx, "pos"), page);
		return 1;
	}

	private static int reload(CommandContext<ServerCommandSource> ctx)
	{
		ChestSee.reloadConfig();
		ctx.getSource().sendFeedback(() -> prefix().append(Text.literal("Config reloaded.").formatted(Formatting.GRAY)), true);
		return 1;
	}

	public static void showBlockLog(ServerCommandSource source, ServerWorld level, BlockPos pos, int page)
	{
		String dim = Utils.getDimString(level.getDimensionEntry());
		List<Path> files = LogReader.filesAt(ChestSee.getLogFolder().resolve(dim), pos);
		String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();

		List<Line> lines = new ArrayList<>();
		for(Path file : files)
		{
			String name = file.getFileName().toString();
			String container = name.substring(0, name.length() - (" " + where + ".txt").length());
			for(LogEntry entry : LogReader.read(file))
				lines.add(new Line(entry, files.size() > 1 ? container : null));
		}

		String title = files.size() == 1 ? files.get(0).getFileName().toString().replace(".txt", "") : where;
		String command = "/chestsee lookup " + where + " %d " + level.getRegistryKey().getValue();
		sendPage(source, title + " (" + dim + ")", "No log entries for " + where + " (" + dim + ").", lines, page, command);
	}

	public static void showEntityLog(ServerCommandSource source, Entity entity, int page)
	{
		Path file = Path.of(ChestSee.getEntityLogPath(entity) + ".txt");
		List<Line> lines = new ArrayList<>();
		if(Files.exists(file))
		{
			for(LogEntry entry : LogReader.read(file))
				lines.add(new Line(entry, null));
		}
		String name = entity.getType().getName().getString();
		sendPage(source, name, "No log entries for this " + name + ".", lines, page, null);
	}

	private static int player(CommandContext<ServerCommandSource> ctx, int page)
	{
		ServerCommandSource source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		Path root = ChestSee.getLogFolder();

		// rcon sends its reply as soon as the command returns, so console searches stay on the main thread
		if(source.getPlayer() == null)
		{
			sendPlayerHits(source, name, LogReader.searchPlayer(root, name), page);
			return 1;
		}

		source.sendFeedback(() -> prefix().append(Text.literal("Searching logs for " + name + "...").formatted(Formatting.GRAY)), false);
		CompletableFuture.supplyAsync(() -> LogReader.searchPlayer(root, name), SEARCH_THREAD)
			.whenComplete((hits, error) -> source.getServer().execute(() ->
			{
				if(error != null)
				{
					ChestSee.LOGGER.error("Player search failed", error);
					source.sendError(Text.literal("Search failed, see server log."));
					return;
				}
				sendPlayerHits(source, name, hits, page);
			}));
		return 1;
	}

	private static void sendPlayerHits(ServerCommandSource source, String name, List<LogReader.Hit> hits, int page)
	{
		List<Line> lines = new ArrayList<>();
		for(LogReader.Hit hit : hits)
		{
			String label = hit.container() + (hit.pos() != null ? " " + hit.pos().getX() + " " + hit.pos().getY() + " " + hit.pos().getZ() : "");
			String lookup = null;
			String dimId = dimensionId(hit.dimension());
			if(hit.pos() != null && dimId != null)
				lookup = "/chestsee lookup " + hit.pos().getX() + " " + hit.pos().getY() + " " + hit.pos().getZ() + " 1 " + dimId;
			lines.add(new Line(hit.entry(), label + " (" + hit.dimension() + ")", lookup));
		}
		sendPage(source, "Actions by " + name, "No logged actions by " + name + ".", lines, page, "/chestsee player " + name + " %d");
	}

	private record Line(LogEntry entry, String label, String click)
	{
		Line(LogEntry entry, String label)
		{
			this(entry, label, null);
		}
	}

	private static void sendPage(ServerCommandSource source, String title, String empty, List<Line> lines, int page, String pageCommand)
	{
		if(lines.isEmpty())
		{
			source.sendFeedback(() -> prefix().append(Text.literal(empty).formatted(Formatting.GRAY)), false);
			return;
		}

		lines.sort(Comparator.comparing((Line l) -> l.entry().when()).reversed());
		int pageSize = ChestSee.getConfig().pageSize;
		int pages = (lines.size() + pageSize - 1) / pageSize;
		int current = Math.min(page, pages);

		List<Text> out = new ArrayList<>();
		out.add(prefix().append(Text.literal(title).formatted(Formatting.GOLD))
			.append(Text.literal("  page " + current + "/" + pages).formatted(Formatting.GRAY)));

		for(Line line : lines.subList((current - 1) * pageSize, Math.min(lines.size(), current * pageSize)))
			out.add(format(line));

		if(pages > 1 && pageCommand != null)
		{
			MutableText nav = Text.literal("");
			if(current > 1)
				nav.append(button("[< Prev]", pageCommand.formatted(current - 1)));
			if(current > 1 && current < pages)
				nav.append(Text.literal(" "));
			if(current < pages)
				nav.append(button("[Next >]", pageCommand.formatted(current + 1)));
			out.add(nav);
		}

		// rcon glues separate messages together without a line break
		if(source.getPlayer() == null)
		{
			MutableText all = Text.empty();
			for(int i = 0; i < out.size(); i++)
				all.append(i == 0 ? Text.empty() : Text.literal("\n")).append(out.get(i));
			source.sendFeedback(() -> all, false);
		}
		else
			for(Text c : out)
				source.sendFeedback(() -> c, false);
	}

	private static MutableText format(Line line)
	{
		LogEntry entry = line.entry();
		MutableText text = Text.literal(entry.time().replace("  ", " ") + " ").formatted(Formatting.DARK_GRAY);
		if(line.label() != null)
		{
			MutableText label = Text.literal(line.label() + " ").formatted(Formatting.AQUA);
			if(line.click() != null)
				label = label.styled(style -> style.withClickEvent(new ClickEvent.RunCommand(line.click()))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Show this container's log"))));
			text.append(label);
		}
		text.append(Text.literal(entry.player() + " ").formatted(Formatting.YELLOW));

		if(entry.isItemChange())
		{
			if(!entry.added().isEmpty())
				text.append(Text.literal("+" + entry.added()).formatted(Formatting.GREEN));
			if(!entry.added().isEmpty() && !entry.removed().isEmpty())
				text.append(Text.literal(" "));
			if(!entry.removed().isEmpty())
				text.append(Text.literal("-" + entry.removed()).formatted(Formatting.RED));
		}
		else
			text.append(Text.literal(entry.message()).formatted(Formatting.WHITE));
		return text;
	}

	private static MutableText button(String text, String command)
	{
		return Text.literal(text).styled(style -> style.withColor(Formatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	private static MutableText prefix()
	{
		return Text.literal("[ChestSee] ").formatted(Formatting.DARK_AQUA);
	}

	private static String dimensionId(String folder)
	{
		return switch(folder)
		{
			case "Overworld" -> "minecraft:overworld";
			case "The Nether" -> "minecraft:the_nether";
			case "The End" -> "minecraft:the_end";
			default -> null;
		};
	}
}
