package fr.atraxterra.dohy.palamod.legendary;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Une pierre legendaire ({@code LegendaryStone}).
 *
 * <p>Clic droit : son effet, puis 24 heures de recharge (voir
 * {@link LegendaryStones}). La pierre aleatoire, elle, s'ouvre : elle disparait
 * et donne l'une des six autres, prete a servir.
 *
 * <p>La recharge est notee sur l'objet ({@code LAST_USE}) : elle suit la pierre
 * si on la donne. Elle brille quand elle est prete.
 */
public class LegendaryStoneItem extends Item {

    private final LegendaryEffect effect;

    public LegendaryStoneItem(LegendaryEffect effect, Properties properties) {
        super(properties);
        this.effect = effect;
    }

    public LegendaryEffect effect() {
        return effect;
    }

    /** Ce qui entre dans un gant : toutes les pierres sauf l'aleatoire. */
    public static boolean insertable(ItemStack stack) {
        return stack.getItem() instanceof LegendaryStoneItem stone && stone.effect != LegendaryEffect.RANDOM;
    }

    public static long lastUse(ItemStack stack) {
        return stack.getOrDefault(PalaLegendary.LAST_USE.get(), 0L);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (effect == LegendaryEffect.RANDOM) {
            return InteractionResult.SUCCESS.heldItemTransformedTo(open(serverPlayer, server, stack));
        }
        boolean used = LegendaryStones.activate(serverPlayer, server, effect, lastUse(stack),
                LegendaryStones.STONE_COOLDOWN, now -> stack.set(PalaLegendary.LAST_USE.get(), now));
        return used ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * La pierre aleatoire : une des six, au hasard, sans recharge. Rend ce que
     * la main doit tenir ensuite.
     *
     * <p>La pierre tiree passe par le resultat du clic, comme un seau rempli
     * ({@code ItemUtils.createFilledResult}). La retirer de la main puis
     * l'ajouter a l'inventaire ne marche pas en 26.2 : l'ajout la pose dans la
     * case de la main, devenue vide, et le jeu y remet aussitot la pile
     * consommee — la pierre disparaissait.
     */
    private static ItemStack open(ServerPlayer player, ServerLevel level, ItemStack stack) {
        LegendaryEffect drawn = LegendaryEffect.values()[1 + level.getRandom().nextInt(6)];
        ItemStack stone = new ItemStack(PalaLegendary.stone(drawn));
        Component name = stone.getHoverName();
        ItemStack held = ItemUtils.createFilledResult(stack, player, stone);
        LegendaryStones.particles(player, level, drawn);
        LegendaryStones.tell(player, Component.translatable("message.palamod.legendary.random", name));
        return held;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return effect == LegendaryEffect.RANDOM
                || lastUse(stack) + LegendaryStones.STONE_COOLDOWN <= System.currentTimeMillis();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (effect == LegendaryEffect.RANDOM) {
            tooltip.accept(Component.translatable("tooltip.palamod.legendary.random").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(status(lastUse(stack), LegendaryStones.STONE_COOLDOWN).withStyle(ChatFormatting.GRAY));
    }

    /** « Prete a l'utilisation ! » ou le temps restant. */
    static net.minecraft.network.chat.MutableComponent status(long lastUse, long cooldown) {
        long left = lastUse + cooldown - System.currentTimeMillis();
        return left <= 0L ? Component.translatable("tooltip.palamod.legendary.ready")
                : Component.translatable("tooltip.palamod.legendary.wait", LegendaryStones.duration(left));
    }
}
