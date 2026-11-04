package com.chestsee.util;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.ContainerUser;

@FunctionalInterface
public interface ChestClosedCallback
{
    void invoke(ContainerUser user);

    Event<ChestClosedCallback> EVENT = EventFactory.createArrayBacked(ChestClosedCallback.class,
            (listeners) -> (user) ->
            {
                for (ChestClosedCallback listener : listeners)
                {
                    listener.invoke(user);
                }
            });
}
