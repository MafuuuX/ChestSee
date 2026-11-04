package com.chestsee.command;

import com.chestsee.ChestSee;
import com.chestsee.log.LogEntry;
import com.chestsee.log.LogReader;
import com.chestsee.util.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
	{
		dispatcher.register(Commands.literal("chestsee")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("inspect")
				.executes(ChestSeeCommand::inspect))
			.then(Commands.literal("lookup")
				.then(Commands.argument("pos", BlockPosArgument.blockPos())
					.executes(ctx -> lookup(ctx, 1, ctx.getSource().getLevel()))
					.then(Commands.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> lookup(ctx, IntegerArgumentType.getInteger(ctx, "page"), ctx.getSource().getLevel()))
						.then(Commands.argument("dimension", DimensionArgument.dimension())
							.executes(ctx -> lookup(ctx, IntegerArgumentType.getInteger(ctx, "page"), DimensionArgument.getDimension(ctx, "dimension")))))))
			.then(Commands.literal("player")
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
					.executes(ctx -> player(ctx, 1))
					.then(Commands.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> player(ctx, IntegerArgumentType.getInteger(ctx, "page"))))))
			.then(Commands.literal("reload")
				.executes(ChestSeeCommand::reload)));
	}

	private static int inspect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
	{
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean on = ChestSee.toggleInspect(player);
		if(on)
			ctx.getSource().sendSuccess(() -> prefix().append(Component.literal("Inspect mode on. Right-click or punch a container to see its log.").withStyle(ChatFormatting.GRAY)), false);
		else
			ctx.getSource().sendSuccess(() -> prefix().append(Component.literal("Inspect mode off.").withStyle(ChatFormatting.GRAY)), false);
		return 1;
	}

	private static int lookup(CommandContext<CommandSourceStack> ctx, int page, ServerLevel level)
	{
		showBlockLog(ctx.getSource(), level, BlockPosArgument.getBlockPos(ctx, "pos"), page);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx)
	{
		ChestSee.reloadConfig();
		ctx.getSource().sendSuccess(() -> prefix().append(Component.literal("Config reloaded.").withStyle(ChatFormatting.GRAY)), true);
		return 1;
	}

	public static void showBlockLog(CommandSourceStack source, ServerLevel level, BlockPos pos, int page)
	{
		String dim = Utils.getDimString(level.dimensionTypeRegistration());
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
		String command = "/chestsee lookup " + where + " %d " + level.dimension().identifier();
		sendPage(source, title + " (" + dim + ")", "No log entries for " + where + " (" + dim + ").", lines, page, command);
	}

	public static void showEntityLog(CommandSourceStack source, Entity entity, int page)
	{
		Path file = Path.of(ChestSee.getEntityLogPath(entity) + ".txt");
		List<Line> lines = new ArrayList<>();
		if(Files.exists(file))
		{
			for(LogEntry entry : LogReader.read(file))
				lines.add(new Line(entry, null));
		}
		String name = entity.getType().getDescription().getString();
		sendPage(source, name, "No log entries for this " + name + ".", lines, page, null);
	}

	private static int player(CommandContext<CommandSourceStack> ctx, int page)
	{
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		Path root = ChestSee.getLogFolder();

		// rcon sends its reply as soon as the command returns, so console searches stay on the main thread
		if(source.getPlayer() == null)
		{
			sendPlayerHits(source, name, LogReader.searchPlayer(root, name), page);
			return 1;
		}

		source.sendSuccess(() -> prefix().append(Component.literal("Searching logs for " + name + "...").withStyle(ChatFormatting.GRAY)), false);
		CompletableFuture.supplyAsync(() -> LogReader.searchPlayer(root, name), SEARCH_THREAD)
			.whenComplete((hits, error) -> source.getServer().execute(() ->
			{
				if(error != null)
				{
					ChestSee.LOGGER.error("Player search failed", error);
					source.sendFailure(Component.literal("Search failed, see server log."));
					return;
				}
				sendPlayerHits(source, name, hits, page);
			}));
		return 1;
	}

	private static void sendPlayerHits(CommandSourceStack source, String name, List<LogReader.Hit> hits, int page)
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

	private static void sendPage(CommandSourceStack source, String title, String empty, List<Line> lines, int page, String pageCommand)
	{
		if(lines.isEmpty())
		{
			source.sendSuccess(() -> prefix().append(Component.literal(empty).withStyle(ChatFormatting.GRAY)), false);
			return;
		}

		lines.sort(Comparator.comparing((Line l) -> l.entry().when()).reversed());
		int pageSize = ChestSee.getConfig().pageSize;
		int pages = (lines.size() + pageSize - 1) / pageSize;
		int current = Math.min(page, pages);

		List<Component> out = new ArrayList<>();
		out.add(prefix().append(Component.literal(title).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("  page " + current + "/" + pages).withStyle(ChatFormatting.GRAY)));

		for(Line line : lines.subList((current - 1) * pageSize, Math.min(lines.size(), current * pageSize)))
			out.add(format(line));

		if(pages > 1 && pageCommand != null)
		{
			MutableComponent nav = Component.literal("");
			if(current > 1)
				nav.append(button("[< Prev]", pageCommand.formatted(current - 1)));
			if(current > 1 && current < pages)
				nav.append(Component.literal(" "));
			if(current < pages)
				nav.append(button("[Next >]", pageCommand.formatted(current + 1)));
			out.add(nav);
		}

		// rcon glues separate messages together without a line break
		if(source.getPlayer() == null)
		{
			MutableComponent all = Component.empty();
			for(int i = 0; i < out.size(); i++)
				all.append(i == 0 ? Component.empty() : Component.literal("\n")).append(out.get(i));
			source.sendSuccess(() -> all, false);
		}
		else
			for(Component c : out)
				source.sendSuccess(() -> c, false);
	}

	private static MutableComponent format(Line line)
	{
		LogEntry entry = line.entry();
		MutableComponent text = Component.literal(entry.time().replace("  ", " ") + " ").withStyle(ChatFormatting.DARK_GRAY);
		if(line.label() != null)
		{
			MutableComponent label = Component.literal(line.label() + " ").withStyle(ChatFormatting.AQUA);
			if(line.click() != null)
				label = label.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(line.click()))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show this container's log"))));
			text.append(label);
		}
		text.append(Component.literal(entry.player() + " ").withStyle(ChatFormatting.YELLOW));

		if(entry.isItemChange())
		{
			if(!entry.added().isEmpty())
				text.append(Component.literal("+" + entry.added()).withStyle(ChatFormatting.GREEN));
			if(!entry.added().isEmpty() && !entry.removed().isEmpty())
				text.append(Component.literal(" "));
			if(!entry.removed().isEmpty())
				text.append(Component.literal("-" + entry.removed()).withStyle(ChatFormatting.RED));
		}
		else
			text.append(Component.literal(entry.message()).withStyle(ChatFormatting.WHITE));
		return text;
	}

	private static MutableComponent button(String text, String command)
	{
		return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	private static MutableComponent prefix()
	{
		return Component.literal("[ChestSee] ").withStyle(ChatFormatting.DARK_AQUA);
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
