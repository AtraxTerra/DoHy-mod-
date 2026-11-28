package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * La mutation inconnue ({@code ItemBotanistUnknownMutation}) : ce que rend la recolte d'une plante
 * qui a mute en fleurissant. Elle porte la plante vers laquelle la floraison a mute (composant
 * {@code palamod:botanist_mutation_target}, le NBT {@code mutation} de JobsReborn), que seul le
 * scanner de Genis revele (« A analyser au Scanner de Genis »). Sans plante portee, le scanner en
 * tire une au hasard parmi celles a la portee du joueur, a son niveau de Fermier
 * ({@code TileEntityBotanistScanner.draw}).
 */
public class UnknownMutationItem extends Item {

    public UnknownMutationItem(Properties properties) {
        super(properties);
    }

    /** Une mutation inconnue qui porte {@code target} (ou aucune plante si {@code target} est nul). */
    public static ItemStack create(Mutation target) {
        ItemStack stack = new ItemStack(PalaBotanist.UNKNOWN_MUTATION.get());
        if (target != null) {
            stack.set(PalaBotanist.MUTATION_TARGET.get(), target.id());
        }
        return stack;
    }

    /** La plante portee, si elle en porte une connue du mod. */
    public static Optional<Mutation> target(ItemStack stack) {
        String id = stack.get(PalaBotanist.MUTATION_TARGET.get());
        return id == null ? Optional.empty() : Optional.ofNullable(Mutation.byId(id));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.palamod.unknown_mutation.scan").withStyle(ChatFormatting.GRAY));
    }
}
