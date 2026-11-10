package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * La pierre d'aggro ({@code AGRO_STONE}) : quatre utilisations, par une.
 *
 * <p>Clic droit sur un monstre : il lache sa cible et te prend pour la sienne
 * ({@code setTarget}, {@code setRevengeTarget}). De quoi detourner un monstre
 * de ton golem, de ta monture — ou d'un mur.
 */
public class AggroStoneItem extends Item {

    public AggroStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Mob mob) || !(target instanceof Enemy)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!DefenseGate.check(serverPlayer, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        mob.setTarget(serverPlayer);
        mob.setLastHurtByMob(serverPlayer);
        stack.hurtAndBreak(1, serverPlayer, hand);
        serverPlayer.level().playSound(null, mob.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
                SoundSource.PLAYERS, 0.8f, 1.4f);
        return InteractionResult.SUCCESS;
    }
}
