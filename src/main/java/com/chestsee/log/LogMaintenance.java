package com.chestsee.log;

import com.chestsee.ChestSee;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class LogMaintenance
{
	public static int removeOldEntries(Path logRoot, int retentionDays)
	{
		if(retentionDays <= 0 || !Files.isDirectory(logRoot))
			return 0;

		LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
		List<Path> files;
		try(Stream<Path> walk = Files.walk(logRoot, 2))
		{
			files = walk.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".txt")).toList();
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.warn("Could not clean up " + logRoot, e);
			return 0;
		}

		int removed = 0;
		for(Path file : files)
		{
			List<String> blocks = splitBlocks(file);
			List<String> kept = new ArrayList<>();
			for(String block : blocks)
			{
				List<LogEntry> parsed = LogReader.parse(block);
				if(parsed.isEmpty() || !parsed.get(0).when().isBefore(cutoff))
					kept.add(block);
			}
			if(kept.size() == blocks.size())
				continue;

			removed += blocks.size() - kept.size();
			rewrite(file, kept);
		}
		return removed;
	}

	public static void trimToSize(Path file, long maxBytes)
	{
		try
		{
			if(maxBytes <= 0 || !Files.exists(file) || Files.size(file) <= maxBytes)
				return;
		}
		catch(IOException e)
		{
			return;
		}

		List<String> blocks = splitBlocks(file);
		long size = 0;
		int first = blocks.size();
		// keep the newest entries that still fit
		while(first > 0)
		{
			long next = size + blocks.get(first - 1).getBytes(StandardCharsets.UTF_8).length + 2;
			if(next > maxBytes && first < blocks.size())
				break;
			size = next;
			first--;
		}
		rewrite(file, blocks.subList(first, blocks.size()));
	}

	private static List<String> splitBlocks(Path file)
	{
		List<String> blocks = new ArrayList<>();
		try
		{
			for(String block : Files.readString(file, StandardCharsets.UTF_8).replace("\r", "").split("\n\n"))
			{
				if(!block.isBlank())
					blocks.add(block.strip());
			}
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.warn("Could not read " + file, e);
		}
		return blocks;
	}

	private static void rewrite(Path file, List<String> blocks)
	{
		try
		{
			if(blocks.isEmpty())
			{
				Files.deleteIfExists(file);
				return;
			}
			StringBuilder out = new StringBuilder();
			for(String block : blocks)
				out.append(block).append("\n\n");
			Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.warn("Could not rewrite " + file, e);
		}
	}
}
