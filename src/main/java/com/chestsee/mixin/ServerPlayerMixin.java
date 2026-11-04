package com.chestsee.mixin;

import com.chestsee.util.ContainerMenuCallback;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerMixin
{
    @Inject(at = @At("RETURN"), method = "openHandledScreen")
    private void onOpenHandledScreen(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir)
    {
        if (cir.getReturnValue().isPresent())
        {
            ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
            ContainerMenuCallback.OPENED.invoker().invoke(player, player.currentScreenHandler);
        }
    }

    @Inject(at = @At("HEAD"), method = "onHandledScreenClosed")
    private void onHandledScreenClosed(CallbackInfo ci)
    {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ContainerMenuCallback.CLOSED.invoker().invoke(player, player.currentScreenHandler);
    }
}
