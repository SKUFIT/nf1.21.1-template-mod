package com.example.testmod.mixin;

import com.example.testmod.SheepShakerHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow private float offHandHeight;
    @Shadow private float offHandHeightO;

    @Shadow
    protected abstract void renderPlayerArm(PoseStack p_109372_, MultiBufferSource p_109373_, int p_109374_, float p_109375_, float p_109376_, HumanoidArm p_109377_);

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void testmod$renderOffhandArm(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer, LocalPlayer player, int combinedLight, CallbackInfo ci) {
        // Если левая рука пуста и игрок сейчас "трясет" овцу
        if (player.getOffhandItem().isEmpty() && SheepShakerHandler.isShaking(player)) {
            HumanoidArm offhandSide = player.getMainArm().getOpposite();
            
            // Интерполируем высоту руки (как это делает сама игра)
            float height = net.minecraft.util.Mth.lerp(partialTicks, this.offHandHeightO, this.offHandHeight);
            
            // Вызываем отрисовку
            this.renderPlayerArm(
                poseStack, 
                buffer, 
                combinedLight, 
                height, 
                player.getAttackAnim(partialTicks), 
                offhandSide
            );
        }
    }
}
