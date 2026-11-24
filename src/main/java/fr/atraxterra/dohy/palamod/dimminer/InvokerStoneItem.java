package fr.atraxterra.dohy.palamod.dimminer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * La Pierre d'Invocation ({@code invocker_stone}). Elle reste un ingredient (cles et sac de donjon) ;
 * <b>ajout</b> : accroupi, clic droit au sol dans la dimension du Mineur, elle s'y brise et eveille
 * le gardien des cristaux ({@link MinerBoss}), que l'original n'invoquait nulle part. Un seul a la
 * fois a 128 blocs, jamais en paisible (il y disparaitrait aussitot), et il lui faut sa place.
 */
public class InvokerStoneItem extends Item {

    public InvokerStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.isSecondaryUseActive() || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return DimMinerManager.levelIs(context.getLevel()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (!DimMinerManager.levelIs(level)) {
            DimMinerManager.message(player, ChatFormatting.GRAY, "boss_outside");
            return InteractionResult.FAIL;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            DimMinerManager.message(player, ChatFormatting.RED, "boss_peaceful");
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().above();
        if (MinerBoss.awake(level, Vec3.atCenterOf(pos))) {
            DimMinerManager.message(player, ChatFormatting.RED, "boss_already");
            return InteractionResult.FAIL;
        }
        MinerBoss boss = PalaDimMiner.MINER_BOSS.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (boss == null) {
            return InteractionResult.FAIL;
        }
        boss.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, player.getYRot() + 180.0f, 0.0f);
        if (!level.noCollision(boss)) {
            DimMinerManager.message(player, ChatFormatting.RED, "boss_room");
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(boss);
        level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.0d, boss.getZ(), 40, 0.8d, 1.0d,
                0.8d, 0.08d);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 0.3d, boss.getZ(), 20, 1.0d, 0.2d,
                1.0d, 0.02d);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE,
                1.6f, 0.6f);
        DimMinerManager.message(player, ChatFormatting.LIGHT_PURPLE, "boss_awake");
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.invoker_stone").withStyle(ChatFormatting.GRAY));
    }
}
