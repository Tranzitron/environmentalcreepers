package fi.dy.masa.environmentalcreepers.mixin;

import fi.dy.masa.environmentalcreepers.EnvironmentalCreepers;
import fi.dy.masa.environmentalcreepers.config.Configs;
import fi.dy.masa.environmentalcreepers.util.ExplosionUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.Explosion.DestructionType;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ExplosionImpl.class)
public abstract class MixinExplosion {
    @Shadow
    @Final
    private ServerWorld world;
    @Shadow
    @Final
    private Entity entity;
    @Shadow
    @Final
    private Vec3d pos;
    @Shadow
    @Final
    @Mutable
    private float power;
    @Shadow
    @Final
    private DestructionType destructionType;

    @Invoker("getBlocksToDestroy")
    abstract List<BlockPos> invokeGetBlocksToDestroy();

    @Shadow
    @org.jetbrains.annotations.Nullable
    public abstract LivingEntity getCausingEntity();

    @Shadow
    public abstract Vec3d getPosition();

    @Shadow
    public abstract ServerWorld getWorld();


    @Inject(method = "<init>", at = @At("RETURN"))
    private void envc_modifyExplosionSize(ServerWorld world, Entity entity, DamageSource damageSource, ExplosionBehavior behavior, Vec3d pos, float power, boolean createFire, DestructionType destructionType, CallbackInfo ci) {
        if (entity instanceof CreeperEntity && Configs.Toggles.MODIFY_CREEPER_EXPLOSION_STRENGTH.getValue()) {
            if (entity.getDataTracker().get(IMixinCreeperEntity.envc_getCharged())) {
                this.power = Configs.Generic.CREEPER_EXPLOSION_STRENGTH_CHARGED.getFloatValue();
            } else {
                this.power = Configs.Generic.CREEPER_EXPLOSION_STRENGTH_NORMAL.getFloatValue();
            }
        }
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void envc_disableExplosionBlockDamageOrCompletely(CallbackInfo ci) {
        if (this.world.isClient == false) {
            EnvironmentalCreepers.logInfo(this::envc$printExplosionInfo);
        }

        if (Configs.Toggles.DISABLE_ALL_EXPLOSIONS.getValue()) {
            EnvironmentalCreepers.logInfo("MixinExplosion.envc_disableExplosionBlockDamageOrCompletely(), type: '{}'", (this.entity instanceof CreeperEntity) ? "Creeper" : "Other");
            ci.cancel();
        } else if (this.entity instanceof CreeperEntity) {
            if (Configs.Toggles.DISABLE_CREEPER_EXPLOSION_BLOCK_DAMAGE.getValue() || (Configs.Toggles.CREEPER_ALTITUDE_CONDITION.getValue() && (this.pos.y < Configs.Generic.CREEPER_ALTITUDE_DAMAGE_MIN_Y.getValue() || this.pos.y > Configs.Generic.CREEPER_ALTITUDE_DAMAGE_MAX_Y.getValue()))) {
                EnvironmentalCreepers.logInfo("MixinExplosion.envc_disableExplosionBlockDamageOrCompletely: clearAffectedBlockPositions(), type: 'Creeper'");
                this.invokeGetBlocksToDestroy().clear();
            }

            if (Configs.Toggles.CREEPER_EXPLOSION_CHAIN_REACTION.getValue()) {
                ExplosionUtils.causeCreeperChainReaction(this.world, new Vec3d(this.pos.x, this.pos.y, this.pos.z));
            }
        } else if (Configs.Toggles.DISABLE_OTHER_EXPLOSION_BLOCK_DAMAGE.getValue() && (this.entity instanceof CreeperEntity) == false) {
            EnvironmentalCreepers.logInfo("MixinExplosion.envc_disableExplosionBlockDamageOrCompletely: clearAffectedBlockPositions(), type: 'Other'");
            this.invokeGetBlocksToDestroy().clear();
        }
    }

    @Inject(method = "destroyBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/math/random/Random;)V"))
    private void envc_preventItemDrops(List<BlockPos> positions, CallbackInfo ci) {
        if (this.entity instanceof CreeperEntity) {
            if (Configs.Toggles.MODIFY_CREEPER_EXPLOSION_DROP_CHANCE.getValue() && Configs.Generic.CREEPER_EXPLOSION_BLOCK_DROP_CHANCE.getFloatValue() == 0.0f) {
                this.envc$removeBlocks();
            }
        } else {
            if (Configs.Toggles.MODIFY_OTHER_EXPLOSION_DROP_CHANCE.getValue() && Configs.Generic.OTHER_EXPLOSION_BLOCK_DROP_CHANCE.getFloatValue() == 0.0f) {
                this.envc$removeBlocks();
            }
        }
    }

    @Inject(method = "getDestructionType", at = @At("HEAD"), cancellable = true)
    private void envc_overrideDestructionType(CallbackInfoReturnable<DestructionType> cir) {
        if (this.destructionType != DestructionType.DESTROY_WITH_DECAY) {
            return;
        }

        if (this.entity instanceof CreeperEntity) {
            if (Configs.Toggles.MODIFY_CREEPER_EXPLOSION_DROP_CHANCE.getValue() && Configs.Generic.CREEPER_EXPLOSION_BLOCK_DROP_CHANCE.getFloatValue() >= 1.0f) {
                cir.setReturnValue(DestructionType.DESTROY);
            }
        } else {
            if (Configs.Toggles.MODIFY_OTHER_EXPLOSION_DROP_CHANCE.getValue() && Configs.Generic.OTHER_EXPLOSION_BLOCK_DROP_CHANCE.getFloatValue() >= 1.0f) {
                cir.setReturnValue(DestructionType.DESTROY);
            }
        }
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void envc_disableExplosionCompletely1(CallbackInfo ci) {
        if (Configs.Toggles.DISABLE_ALL_EXPLOSIONS.getValue()) {
            EnvironmentalCreepers.logInfo("MixinExplosion.disableExplosionCompletely1(), type: '{}'", (this.entity instanceof CreeperEntity) ? "Creeper" : "Other");
            ci.cancel();
        }
    }

    @Redirect(method = "damageEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private List<Entity> envc_disableExplosionEntityDamage(ServerWorld instance, Entity entity, Box box) {
        List<Entity> list = world.getOtherEntities(entity, box);
        Set<Entity> immune = new HashSet<>();

        for (Entity e : list) {
            if (this.envc$isImmuneToExplosion(e)) {
                immune.add(e);
            }
        }

        if (immune.isEmpty() == false) {
            EnvironmentalCreepers.logInfo("MixinExplosion.disableExplosionEntityDamage(), type: '{}'", (this.entity instanceof CreeperEntity) ? "Creeper" : "Other");
            list.removeAll(immune);
        }

        return list;
    }

    @Unique
    private boolean envc$isImmuneToExplosion(Entity entity) {
        Configs.ListType type = Configs.Lists.entityClassListType;

        if (this.entity instanceof CreeperEntity) {
            if ((Configs.Toggles.DISABLE_CREEPER_EXPLOSION_ENTITY_DAMAGE.getValue() || (Configs.Toggles.DISABLE_CREEPER_EXPLOSION_ITEM_DAMAGE.getValue() && entity instanceof ItemEntity)) && (type == Configs.ListType.NONE || (type == Configs.ListType.WHITELIST && Configs.EXPLOSION_ENTITY_WHITELIST.contains(entity.getClass())) || (type == Configs.ListType.BLACKLIST && Configs.EXPLOSION_ENTITY_BLACKLIST.contains(entity.getClass()) == false))) {
                return true;
            }
        } else {
            if ((Configs.Toggles.DISABLE_OTHER_EXPLOSION_ENTITY_DAMAGE.getValue() || (Configs.Toggles.DISABLE_OTHER_EXPLOSION_ITEM_DAMAGE.getValue() && entity instanceof ItemEntity)) && (type == Configs.ListType.NONE || (type == Configs.ListType.WHITELIST && Configs.EXPLOSION_ENTITY_WHITELIST.contains(entity.getClass())) || (type == Configs.ListType.BLACKLIST && Configs.EXPLOSION_ENTITY_BLACKLIST.contains(entity.getClass()) == false))) {
                return true;
            }
        }

        return entity.isImmuneToExplosion((Explosion) (Object) this);
    }

    @Unique
    private void envc$removeBlocks() {
        BlockState air = Blocks.AIR.getDefaultState();

        Profilers.get().push("explosion_blocks");

        for (BlockPos pos : this.invokeGetBlocksToDestroy()) {
            BlockState state = this.world.getBlockState(pos);
            this.world.setBlockState(pos, air, Block.NOTIFY_ALL);
            state.getBlock().onDestroyedByExplosion(this.getWorld(), pos, (Explosion) (Object) this);
        }

        Profilers.get().pop();

        this.invokeGetBlocksToDestroy().clear();
    }

    @Unique
    private String envc$printExplosionInfo() {
        Entity causingEntity = this.getCausingEntity();
        return String.format("Explosion @ [%.5f, %.5f, %.5f], power: %.2f - type: '%s' - explosion class: '%s', placer: '%s'", this.pos.x, this.pos.y, this.pos.z, this.power, (this.entity instanceof CreeperEntity) ? "Creeper" : "Other", this.getClass().getName(), causingEntity != null ? causingEntity.getClass().getName() : "<null>");
    }
}
