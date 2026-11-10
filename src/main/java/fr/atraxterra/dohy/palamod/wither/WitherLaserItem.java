package fr.atraxterra.dohy.palamod.wither;

import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Le laser de ciblage ({@code WITHER_TARGET_LASER_SYSTEM}) : il envoie les
 * withers predateur et supreme vers un point.
 *
 * <p>Sur Paladium, on reglait la distance a la molette et on verrouillait un
 * point dans une balise a donner au wither. Ici : clic droit dans le vide pour
 * viser le bloc regarde (512 blocs au plus), puis clic droit sur le wither
 * pour l'y envoyer. Seize envois.
 */
public class WitherLaserItem extends Item {

    static final double REACH = 512.0d;
    static final int MIN_DISTANCE = 5;

    public WitherLaserItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            FactionApi.tell(server, "Aucun bloc vise a portee du laser.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        BlockPos target = block.getBlockPos();
        if (target.distSqr(player.blockPosition()) < MIN_DISTANCE * MIN_DISTANCE) {
            FactionApi.tell(server, "Le point doit etre a plus de " + MIN_DISTANCE + " blocs.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        player.getItemInHand(hand).set(PalaWithers.LASER_TARGET.get(), target.immutable());
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                0.8f, 1.6f);
        FactionApi.tell(server, "Cible verrouillee : " + target.getX() + " " + target.getY() + " " + target.getZ()
                + ". Clic droit sur un wither predateur ou supreme pour l'y envoyer.", ChatFormatting.AQUA);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof PalaWither wither)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos point = stack.get(PalaWithers.LASER_TARGET.get());
        if (!wither.kind().directed()) {
            FactionApi.tell(server, "Seuls les withers predateur et supreme suivent le laser.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (point == null) {
            FactionApi.tell(server, "Verrouille d'abord une cible : clic droit dans le vide.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (!WitherSummons.controls(wither, server)) {
            FactionApi.tell(server, "Ce wither n'est pas le tien.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        wither.setWaypoint(point);
        stack.hurtAndBreak(1, server, hand);
        FactionApi.tell(server, "Le wither part vers " + point.getX() + " " + point.getZ() + ".", ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }
}
