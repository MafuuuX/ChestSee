package com.chestsee.util;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface ContainerMenuCallback
{
    void invoke(ServerPlayerEntity player, ScreenHandler handler);

    Event<ContainerMenuCallback> OPENED = create();
    Event<ContainerMenuCallback> CLOSED = create();

    private static Event<ContainerMenuCallback> create()
    {
        return EventFactory.createArrayBacked(ContainerMenuCallback.class,
                (listeners) -> (player, handler) ->
                {
                    for (ContainerMenuCallback listener : listeners)
                    {
                        listener.invoke(player, handler);
                    }
                });
    }
}
