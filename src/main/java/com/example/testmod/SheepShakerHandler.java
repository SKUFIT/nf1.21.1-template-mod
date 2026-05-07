package com.example.testmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TestMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class SheepShakerHandler {

    private static final String SHAKE_COUNT_TAG = "testmod:shake_count";
    private static final String LAST_SHAKE_TIME_TAG = "testmod:last_shake_time";
    private static final int MAX_SHAKES = 5;
    private static final long SHAKE_TIMEOUT_TICKS = 40; // 2 seconds

    /**
     * Проверка для клиента: должен ли игрок видеть вторую руку?
     * Упростили: рука видна всегда, когда мы сзади овцы с пустыми руками.
     */
    public static boolean isShaking(Player player) {
        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            net.minecraft.world.phys.EntityHitResult hit = getEntityHit(player);
            if (hit != null && hit.getEntity() instanceof Sheep sheep) {
                return !sheep.isSheared() && isPlayerBehind(player, sheep);
            }
        }
        return false;
    }

    private static net.minecraft.world.phys.EntityHitResult getEntityHit(Player player) {
        double range = 3.0; 
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(range));
        return net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            player.level(), player, start, end, 
            player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(range)).inflate(1.0), 
            e -> e instanceof Sheep);
    }

    @SubscribeEvent
    public static void onSheepInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Sheep sheep)) return;
        
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return;

        if (sheep.isSheared() || !sheep.readyForShearing()) return;

        if (isPlayerBehind(player, sheep)) {
            shakeSheep(player, sheep);
            
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 10, 1, false, false));
                
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    public static boolean isPlayerBehind(Player player, Sheep sheep) {
        Vec3 sheepForward = sheep.getViewVector(1.0F).normalize();
        Vec3 toPlayer = player.position().subtract(sheep.position()).normalize();
        double dot = sheepForward.dot(toPlayer);
        return dot < -0.4; 
    }

    private static void shakeSheep(Player player, Sheep sheep) {
        long currentTime = sheep.level().getGameTime();
        long lastShake = sheep.getPersistentData().getLong(LAST_SHAKE_TIME_TAG);
        int shakes = sheep.getPersistentData().getInt(SHAKE_COUNT_TAG);

        if (currentTime - lastShake > SHAKE_TIMEOUT_TICKS) {
            shakes = 0;
        }

        shakes++;
        sheep.getPersistentData().putInt(SHAKE_COUNT_TAG, shakes);
        sheep.getPersistentData().putLong(LAST_SHAKE_TIME_TAG, currentTime);
        
        Vec3 playerPos = player.position();
        Vec3 lookVec = player.getLookAngle().normalize();
        double pullDist = 0.4;
        Vec3 targetPos = playerPos.add(lookVec.x * pullDist, 0, lookVec.z * pullDist);
        
        sheep.setPos(targetPos.x, sheep.getY(), targetPos.z);
        sheep.setYRot(player.getYRot());
        sheep.setYHeadRot(player.getYRot());

        if (shakes >= MAX_SHAKES) {
            sheep.getPersistentData().remove(SHAKE_COUNT_TAG);
            sheep.getPersistentData().remove(LAST_SHAKE_TIME_TAG);
            sheep.shear(SoundSource.PLAYERS);
            
            player.push(0, 0.05, 0);
        } else {
            float progress = (float) shakes / MAX_SHAKES;
            
            sheep.level().playSound(null, sheep.getX(), sheep.getY(), sheep.getZ(), 
                SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F + progress);
            sheep.level().playSound(null, sheep.getX(), sheep.getY(), sheep.getZ(), 
                SoundEvents.SHEEP_AMBIENT, SoundSource.PLAYERS, 0.5F + progress, 1.5F + progress);
            
            if (sheep.level() instanceof ServerLevel serverLevel) {
                net.minecraft.world.item.Item woolItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace(sheep.getColor().getName() + "_wool"));
                
                serverLevel.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                    net.minecraft.core.particles.ParticleTypes.ITEM, new ItemStack(woolItem)), 
                    sheep.getX(), sheep.getY() + 0.5, sheep.getZ(), 
                    15, 0.1, 0.1, 0.1, 0.15);

                // ЕЩЕ БОЛЬШЕ ПЫЛИ
                serverLevel.sendParticles(ParticleTypes.CLOUD, 
                    sheep.getX(), sheep.getY() + 0.5, sheep.getZ(), 
                    20, 0.3, 0.3, 0.3, 0.05);
            }
            
            player.swing(InteractionHand.MAIN_HAND, true);
            player.swing(InteractionHand.OFF_HAND, true);

            double playerJitter = 0.02;
            player.push(
                (player.getRandom().nextDouble() - 0.5) * playerJitter,
                0.01,
                (player.getRandom().nextDouble() - 0.5) * playerJitter
            );
            player.hurtMarked = true;

            double shakePower = 0.2 + (progress * 0.2);
            sheep.push(
                (sheep.getRandom().nextDouble() - 0.5) * shakePower,
                0.15,
                (sheep.getRandom().nextDouble() - 0.5) * shakePower
            );
            sheep.hurtMarked = true; 
        }
    }
}
