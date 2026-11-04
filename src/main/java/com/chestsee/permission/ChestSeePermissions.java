package com.chestsee.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;

// LuckPerms nodes, without a permission mod everything falls back to OP level 2
public class ChestSeePermissions
{
	private static final int OP_LEVEL = 2;

	public static boolean canInspect(CommandSourceStack source)
	{
		return Permissions.check(source, "chestsee.inspect", OP_LEVEL);
	}

	public static boolean canLookup(CommandSourceStack source)
	{
		return Permissions.check(source, "chestsee.lookup", OP_LEVEL);
	}

	public static boolean canSearchPlayers(CommandSourceStack source)
	{
		return Permissions.check(source, "chestsee.player", OP_LEVEL);
	}

	public static boolean canReload(CommandSourceStack source)
	{
		return Permissions.check(source, "chestsee.reload", OP_LEVEL);
	}

	public static boolean canUseAny(CommandSourceStack source)
	{
		return canInspect(source) || canLookup(source) || canSearchPlayers(source) || canReload(source);
	}
}
