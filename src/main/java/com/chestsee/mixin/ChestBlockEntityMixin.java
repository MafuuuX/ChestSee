package com.chestsee.mixin;

import com.chestsee.util.ChestClosedCallback;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ContainerUser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public class ChestBlockEntityMixin
{
    @Inject(at = @At("TAIL"), method = "onClose")
    public void onClose(ContainerUser user, CallbackInfo ci)
    {
        ChestClosedCallback.EVENT.invoker().invoke(user);
    }
}
