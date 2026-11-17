package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.palamod.alchemist.MysticEnchant;
import fr.atraxterra.dohy.palamod.pillage.FinderItem;
import fr.atraxterra.dohy.palamod.pillage.FinderType;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Le casque Unclaim Helmet ({@code EventHandlerAlchemist.anvilUpdateEvent},
 * {@code WidgetUnclaimFinder}).
 *
 * <p>L'enchantement seul n'affiche rien : il faut fondre a l'enclume le casque
 * enchante (a gauche) avec un Unclaim Finder (a droite), pour 15 niveaux. Le
 * casque retient la couleur du finder, une fois pour toutes, et fait office de
 * ce finder tant qu'on le porte. Le finder est consomme.
 */
@EventBusSubscriber
public final class UnclaimHelmet {

    static final int XP_COST = 15;

    private UnclaimHelmet() {
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!left.is(ItemTags.HEAD_ARMOR) || MysticEnchant.UNCLAIM_HELMET.levelOn(left) <= 0
                || left.has(PalaDefense.UNCLAIM_TYPE.get())
                || !(event.getRight().getItem() instanceof FinderItem finder) || finder.type() == FinderType.SPAWNER) {
            return;
        }
        ItemStack output = left.copy();
        output.set(PalaDefense.UNCLAIM_TYPE.get(), finder.type().name());
        event.setOutput(output);
        event.setXpCost(XP_COST);
        event.setMaterialCost(1);
    }

    /** Le finder que le casque remplace, ou {@code null} s'il n'en a pas recu. */
    public static FinderType type(ItemStack helmet) {
        String name = helmet.get(PalaDefense.UNCLAIM_TYPE.get());
        if (name == null || MysticEnchant.UNCLAIM_HELMET.levelOn(helmet) <= 0) {
            return null;
        }
        try {
            FinderType type = FinderType.valueOf(name);
            return type == FinderType.SPAWNER ? null : type;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Un finder de la couleur retenue, pour l'icone et la jauge du HUD. */
    public static ItemStack finder(ItemStack helmet) {
        FinderType type = type(helmet);
        if (type == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(switch (type) {
            case GREEN, SPAWNER -> PalaPillage.UNCLAIM_FINDER.get();
            case ORANGE -> PalaPillage.UNCLAIM_FINDER_ORANGE.get();
            case RED -> PalaPillage.UNCLAIM_FINDER_RED.get();
            case BLUE -> PalaPillage.UNCLAIM_FINDER_BLUE.get();
        });
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (MysticEnchant.UNCLAIM_HELMET.levelOn(stack) <= 0) {
            return;
        }
        ItemStack finder = finder(stack);
        event.getToolTip().add(finder.isEmpty()
                ? Component.translatable("tooltip.palamod.unclaim_helmet.empty").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.palamod.unclaim_helmet", finder.getHoverName())
                        .withStyle(ChatFormatting.GREEN));
    }
}
