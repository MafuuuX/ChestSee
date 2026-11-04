package com.chestsee.log;

import java.time.LocalDateTime;
import java.util.List;

public record LogEntry(String time, LocalDateTime when, String player, List<String> lines)
{
	public boolean isItemChange()
	{
		return !lines.isEmpty() && lines.get(0).startsWith(LogReader.ADDED_PREFIX);
	}

	public String added()
	{
		return listPart(LogReader.ADDED_PREFIX);
	}

	public String removed()
	{
		return listPart(LogReader.REMOVED_PREFIX);
	}

	public String message()
	{
		return String.join(" ", lines);
	}

	private String listPart(String prefix)
	{
		for(String line : lines)
		{
			if(line.startsWith(prefix))
			{
				String list = line.substring(prefix.length()).trim();
				if(list.startsWith("[") && list.endsWith("]"))
					list = list.substring(1, list.length() - 1);
				return list;
			}
		}
		return "";
	}
}
