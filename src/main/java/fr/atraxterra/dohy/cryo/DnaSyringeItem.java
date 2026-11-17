package fr.atraxterra.dohy.cryo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * La seringue d'extraction : un clic droit sur une bete paisible et adulte en
 * preleve une fiole d'ADN de grade I. Une meme bete ne se preleve qu'une fois
 * toutes les cinq minutes de jeu ({@link Dna#COOLDOWN}), garde par un
 * attachement sur la bete : pour beaucoup d'ADN, il faut beaucoup de betes.
 */
public class DnaSyringeItem extends Item {

    public DnaSyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        EntityType<?> type = target.getType();
        if (!Dna.passive(type)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server) || !(target.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (target.isBaby()) {
            return refuse(server, "Trop jeune : son ADN n'est pas encore stable.");
        }
        if (!Dna.yields(level, type)) {
            return refuse(server, "Son clone ne donnerait rien : la chambre n'en tirerait aucun butin.");
        }
        long now = level.getGameTime();
        long last = target.getData(DoHyCryo.DNA_DRAWN.get());
        if (last > 0L && now - last < Dna.COOLDOWN) {
            long seconds = (Dna.COOLDOWN - (now - last) + 19L) / 20L;
            return refuse(server, "Deja prelevee : reviens dans " + (seconds >= 60 ? seconds / 60 + " min "
                    + seconds % 60 + " s" : seconds + " s") + ".");
        }
        target.setData(DoHyCryo.DNA_DRAWN.get(), now);
        ItemStack dna = Dna.stack(type, 1);
        if (!player.getInventory().add(dna)) {
            player.drop(dna, false);
        }
        stack.hurtAndBreak(1, player, hand);
        level.playSound(null, target.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.WAX_OFF, target.getX(), target.getY() + target.getBbHeight() * 0.6d,
                target.getZ(), 8, 0.25d, 0.2d, 0.25d, 0.02d);
        server.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.dohy.dna.drawn",
                type.getDescription()).withStyle(ChatFormatting.AQUA)));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult refuse(ServerPlayer player, String message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)
                .withStyle(ChatFormatting.GRAY)));
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.dohy.dna_syringe.hint").withStyle(ChatFormatting.GRAY));
    }
}
