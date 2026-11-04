package com.chestsee.config;

import com.chestsee.ChestSee;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChestSeeConfig
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int pageSize = 8;
	public int retentionDays = 0;
	public int maxFileSizeKb = 0;
	public List<String> excludedDimensions = new ArrayList<>();
	public Map<String, Boolean> containers = defaultContainers();

	private static Map<String, Boolean> defaultContainers()
	{
		Map<String, Boolean> map = new LinkedHashMap<>();
		for(String type : List.of("chest", "barrel", "shulker_box", "hopper", "dispenser", "dropper", "crafter",
				"furnace", "brewing_stand", "minecart", "chest_boat", "other"))
			map.put(type, true);
		return map;
	}

	public boolean isLogged(String containerType)
	{
		return containers.getOrDefault(containerType, true);
	}

	public boolean isExcluded(String dimensionId)
	{
		return excludedDimensions.contains(dimensionId);
	}

	public static ChestSeeConfig load(Path file)
	{
		ChestSeeConfig config = null;
		if(Files.exists(file))
		{
			try(Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				config = GSON.fromJson(reader, ChestSeeConfig.class);
			}
			catch(IOException | JsonParseException e)
			{
				ChestSee.LOGGER.error("Could not read " + file + ", using defaults", e);
				return new ChestSeeConfig();
			}
		}
		if(config == null)
			config = new ChestSeeConfig();

		if(config.excludedDimensions == null)
			config.excludedDimensions = new ArrayList<>();
		// keep new container types visible in the file after an update
		Map<String, Boolean> merged = defaultContainers();
		if(config.containers != null)
			merged.putAll(config.containers);
		config.containers = merged;
		if(config.pageSize < 1)
			config.pageSize = 8;

		config.save(file);
		return config;
	}

	public void save(Path file)
	{
		try
		{
			Files.createDirectories(file.getParent());
			try(Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8))
			{
				GSON.toJson(this, writer);
			}
		}
		catch(IOException e)
		{
			ChestSee.LOGGER.error("Could not write " + file, e);
		}
	}
}
