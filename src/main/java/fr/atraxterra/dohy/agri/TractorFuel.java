package fr.atraxterra.dohy.agri;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Le carburant du tracteur, compte en unites. Deux sources : des objets (le
 * charbon, pour jouer sans autre mod) et des fluides, lus par la capacite de
 * fluide standard de NeoForge — c'est par elle que les bidons de NTM NEXT, ou
 * un seau de lave, se vident dans le reservoir.
 *
 * <p>Les tables sont dans {@code agriculture.json} : un fluide inconnu de la
 * table n'est pas un carburant, un identifiant absent du jeu est ignore.
 */
public final class TractorFuel {

    private TractorFuel() {
    }

    /** Unites par mB de ce fluide ; zero s'il n'est pas un carburant. */
    public static double perMb(Fluid fluid) {
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        Double factor = AgriConfig.config().farming().fluids().get(id);
        return factor == null ? 0.0d : factor;
    }

    /** Unites par piece de cet objet ; zero s'il n'est pas un carburant solide. */
    public static int perItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer units = AgriConfig.config().farming().items().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return units == null ? 0 : units;
    }

    /**
     * Vide dans un reservoir ce que le joueur tient : une piece de carburant
     * solide, ou le fluide d'un recipient. Rend les unites gagnees, zero si
     * l'objet n'est pas un carburant ou si le reservoir est plein.
     *
     * @param room la place qui reste dans le reservoir, en unites
     */
    public static int drain(Player player, InteractionHand hand, int room) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || room <= 0) {
            return 0;
        }
        int solid = perItem(held);
        if (solid > 0) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            return Math.min(room, solid);
        }
        ResourceHandler<FluidResource> handler =
                ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return 0;
        }
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            int held0 = handler.getAmountAsInt(index);
            double factor = resource.isEmpty() ? 0.0d : perMb(resource.getFluid());
            if (factor <= 0.0d || held0 <= 0) {
                continue;
            }
            // d'abord juste ce qu'il faut ; un seau ne se vide qu'en entier, d'ou le second essai
            int wanted = (int) Math.min(held0, Math.ceil(room / factor));
            for (int amount : new int[] {wanted, held0}) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int got = handler.extract(index, resource, amount, transaction);
                    if (got > 0) {
                        transaction.commit();
                        return (int) Math.min(room, Math.round(got * factor));
                    }
                }
            }
        }
        return 0;
    }

    /** Vrai si cet objet peut remplir un reservoir. */
    public static boolean isFuel(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (perItem(held) > 0) {
            return true;
        }
        if (held.isEmpty()) {
            return false;
        }
        ResourceHandler<FluidResource> handler =
                ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
        if (handler == null) {
            return false;
        }
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            if (!resource.isEmpty() && perMb(resource.getFluid()) > 0.0d) {
                return true;
            }
        }
        return false;
    }
}
