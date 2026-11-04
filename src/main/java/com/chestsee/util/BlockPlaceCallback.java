package com.chestsee.util;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.item.context.BlockPlaceContext;

@FunctionalInterface
public interface BlockPlaceCallback
{
    void invoke(BlockPlaceContext context);

    Event<BlockPlaceCallback> EVENT = EventFactory.createArrayBacked(BlockPlaceCallback.class,
            (listeners) -> (context) ->
            {
                for (BlockPlaceCallback listener : listeners)
                {
                    listener.invoke(context);
                }
            });
}
