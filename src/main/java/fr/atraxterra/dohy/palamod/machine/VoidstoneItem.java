package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * La voidstone : une poubelle de poche.
 *
 * <p>Clic droit, et une case s'ouvre ou tout objet pose disparait — releve sur
 * {@code ItemVoidStone.func_77659_a}, qui ouvre {@code GUI_VOIDSTONE}, et sur
 * {@code VoidStoneInventory}, qui ne garde rien. Voir
 * {@link VoidstoneContainer} pour ce qu'elle refuse.
 *
 * <p><b>Pourquoi elle est portee maintenant.</b> La recette du cobblebreaker la
 * demande. Le chapitre 5a avait refuse de la porter comme simple ingredient
 * inerte, parce qu'un joueur la fabriquerait et constaterait qu'elle ne fait
 * rien : elle arrive donc avec son comportement, ou pas du tout.
 *
 * <p>Elle reutilise {@link MachineMenu} : une case, sans jauge, sans donnees
 * synchronisees — la machine la plus simple que ce menu puisse decrire.
 */
public class VoidstoneItem extends Item {

    private static final Component TITLE = Component.translatable("item.palamod.voidstone");

    public VoidstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, opener) -> new MachineMenu(
                            PalaMenus.VOIDSTONE.get(), containerId, inventory,
                            new VoidstoneContainer(),
                            new SimpleContainerData(MachineMenu.DATA_COUNT),
                            MachineLayout.VOIDSTONE),
                    TITLE));
        }
        return InteractionResult.SUCCESS;
    }
}
