package com.chestsee.mixin;

import com.chestsee.util.ContainerMenuCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin
{
    @Inject(at = @At("RETURN"), method = "openMenu")
    private void onOpenMenu(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir)
    {
        if (cir.getReturnValue().isPresent())
        {
            ServerPlayer player = (ServerPlayer) (Object) this;
            ContainerMenuCallback.OPENED.invoker().invoke(player, player.containerMenu);
        }
    }

    @Inject(at = @At("HEAD"), method = "doCloseContainer")
    private void onCloseContainer(CallbackInfo ci)
    {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ContainerMenuCallback.CLOSED.invoker().invoke(player, player.containerMenu);
    }
}
