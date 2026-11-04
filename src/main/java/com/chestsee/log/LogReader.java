package com.chestsee.log;

import com.chestsee.ChestSee;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class LogReader
{
	public static final String ADDED_PREFIX = "Items Added:";
	public static final String REMOVED_PREFIX = "Items Removed:";

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy  HH:mm:ss");
	private static final Pattern HEADER = Pattern.compile("^\\[(\\d\\d\\.\\d\\d\\.\\d\\d  \\d\\d:\\d\\d:\\d\\d)] (\\S+)$");
	private static final Pattern BLOCK_FILE = Pattern.compile("^(.+) (-?\\d+) (-?\\d+) (-?\\d+)\\.txt$");

	public record Hit(LogEntry entry, String dimension, String container, BlockPos pos) {}

	public static List<LogEntry> read(Path file)
	{
		String content;
		try
		{
			content = Files.readString(file, StandardCharsets.UTF_8);
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.warn("Could not read " + file, e);
			return List.of();
		}
		return parse(content);
	}

	public static List<LogEntry> parse(String content)
	{
		List<LogEntry> entries = new ArrayList<>();
		for(String block : content.replace("\r", "").split("\n\n"))
		{
			String[] lines = block.strip().split("\n");
			if(lines.length < 2)
				continue;

			Matcher header = HEADER.matcher(lines[0]);
			if(!header.matches())
				continue;

			LocalDateTime when;
			try
			{
				when = LocalDateTime.parse(header.group(1), TIME_FORMAT);
			}
			catch(DateTimeParseException e)
			{
				continue;
			}
			entries.add(new LogEntry(header.group(1), when, header.group(2), List.of(lines).subList(1, lines.length)));
		}
		return entries;
	}

	public static List<Path> filesAt(Path dimFolder, BlockPos pos)
	{
		String suffix = " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".txt";
		if(!Files.isDirectory(dimFolder))
			return List.of();

		try(Stream<Path> files = Files.list(dimFolder))
		{
			return files.filter(f -> f.getFileName().toString().endsWith(suffix)
					&& BLOCK_FILE.matcher(f.getFileName().toString()).matches())
				.sorted()
				.toList();
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.warn("Could not list " + dimFolder, e);
			return List.of();
		}
	}

	public static List<Hit> searchPlayer(Path logRoot, String player)
	{
		List<Hit> hits = new ArrayList<>();
		if(!Files.isDirectory(logRoot))
			return hits;

		List<Path> files;
		try(Stream<Path> walk = Files.walk(logRoot, 2))
		{
			files = walk.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".txt")).toList();
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.warn("Could not search " + logRoot, e);
			return hits;
		}

		for(Path file : files)
		{
			String dimension = file.getParent().equals(logRoot) ? "" : file.getParent().getFileName().toString();
			String name = file.getFileName().toString();
			String container = name.substring(0, name.length() - 4);
			BlockPos pos = null;

			Matcher m = BLOCK_FILE.matcher(name);
			if(m.matches())
			{
				container = m.group(1);
				pos = new BlockPos(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
			}

			for(LogEntry entry : read(file))
			{
				if(entry.player().equalsIgnoreCase(player))
					hits.add(new Hit(entry, dimension, container, pos));
			}
		}

		hits.sort(Comparator.comparing((Hit h) -> h.entry().when()).reversed());
		return hits;
	}
}
