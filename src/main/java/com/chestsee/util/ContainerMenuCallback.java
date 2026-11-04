package com.chestsee.util;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

@FunctionalInterface
public interface ContainerMenuCallback
{
    void invoke(ServerPlayer player, AbstractContainerMenu menu);

    Event<ContainerMenuCallback> OPENED = create();
    Event<ContainerMenuCallback> CLOSED = create();

    private static Event<ContainerMenuCallback> create()
    {
        return EventFactory.createArrayBacked(ContainerMenuCallback.class,
                (listeners) -> (player, menu) ->
                {
                    for (ContainerMenuCallback listener : listeners)
                    {
                        listener.invoke(player, menu);
                    }
                });
    }
}
