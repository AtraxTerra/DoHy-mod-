package fr.atraxterra.dohy.palamod.pvp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Un stick de combat ({@code ItemStickBase}) : clic droit, un effet, une
 * recharge, un point d'usure.
 *
 * <p><b>La recharge est par type et par joueur</b>, pas par objet : elle est
 * notee sur le joueur ({@code timer-<type>} dans ses donnees d'entite, ici
 * {@link PalaPvp#STICK_COOLDOWNS}). Deux sticks de force partagent donc la
 * leur — « elle s'applique a tous les sticks identiques de l'inventaire, meme
 * ceux qui n'ont pas servi », dit le wiki. Elle court en temps reel : elle
 * s'ecoule aussi joueur deconnecte.
 *
 * <p>La jauge de recharge du jeu ({@code ItemCooldowns}) la montre sur la barre
 * d'action. Elle ne survit ni a la deconnexion ni a la mort ; {@link PvpEvents}
 * la repose depuis les donnees du joueur, qui elles survivent.
 *
 * <p>Seul Solidite s'applique a un stick (a l'enclume, sa valeur
 * d'enchantement etant nulle comme dans l'original).
 */
public class StickItem extends Item {

    private final StickType type;

    public StickItem(StickType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public StickType type() {
        return type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        long now = System.currentTimeMillis();
        Map<String, Long> timers = serverPlayer.getData(PalaPvp.STICK_COOLDOWNS);
        long ready = timers.getOrDefault(type.key(), 0L);
        if (ready > now) {
            long seconds = (ready - now + 999L) / 1000L;
            serverPlayer.sendSystemMessage(Component.translatable("message.palamod.stick.cooldown", seconds)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        stack.hurtAndBreak(1, serverPlayer, hand);
        type.apply(serverPlayer, server);
        timers.put(type.key(), now + type.cooldownSeconds() * 1000L);
        serverPlayer.setData(PalaPvp.STICK_COOLDOWNS, timers);
        serverPlayer.getCooldowns().addCooldown(new ItemStack(this), type.cooldownSeconds() * 20);
        server.sendParticles(new DustParticleOptions(type.color(), 1.0f),
                player.getX(), player.getY() + 1.0d, player.getZ(), 40, 0.6d, 0.6d, 0.6d, 0.0d);
        return InteractionResult.SUCCESS;
    }

    /** {@code Reparation: n / 5}. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.stick.repairs",
                stack.getOrDefault(PalaPvp.STICK_REPAIRS.get(), 0), StickType.MAX_REPAIRS));
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }
}
