package com.chestsee.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;

// LuckPerms nodes, without a permission mod everything falls back to OP level 2
public class ChestSeePermissions
{
	private static final int OP_LEVEL = 2;

	public static boolean canInspect(ServerCommandSource source)
	{
		return Permissions.check(source, "chestsee.inspect", OP_LEVEL);
	}

	public static boolean canLookup(ServerCommandSource source)
	{
		return Permissions.check(source, "chestsee.lookup", OP_LEVEL);
	}

	public static boolean canSearchPlayers(ServerCommandSource source)
	{
		return Permissions.check(source, "chestsee.player", OP_LEVEL);
	}

	public static boolean canReload(ServerCommandSource source)
	{
		return Permissions.check(source, "chestsee.reload", OP_LEVEL);
	}

	public static boolean canUseAny(ServerCommandSource source)
	{
		return canInspect(source) || canLookup(source) || canSearchPlayers(source) || canReload(source);
	}
}
