package fr.atraxterra.dohy.pass;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Une relique du pass : l'objet exclusif du niveau 35, un par mois. En main
 * secondaire, elle donne son effet ({@link PassEvents}). Elle porte le mois ou
 * elle a ete gagnee ({@link DoHyPass#RELIC_SEASON}) : une relique de janvier
 * 2027 n'est pas celle de janvier 2028.
 */
public class RelicItem extends Item {

    private final Relic relic;

    public RelicItem(Relic relic, Properties properties) {
        super(properties);
        this.relic = relic;
    }

    public Relic relic() {
        return relic;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Integer season = stack.get(DoHyPass.RELIC_SEASON.get());
        tooltip.accept(Component.literal(season == null ? "Relique du pass mensuel"
                : "Relique du pass " + PassSeason.ofMonth(season) + " " + PassSeason.year(season))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("En main secondaire : " + relic.effectName() + " I")
                .withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.literal("Exclusive : seul le pass de ce mois la donne").withStyle(ChatFormatting.DARK_GRAY));
    }
}
