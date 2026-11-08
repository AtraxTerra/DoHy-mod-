package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Les interactions avec les machines : ouvrir, et ameliorer.
 *
 * <p>Tout passe par {@code PlayerInteractEvent.RightClickBlock}, dont la
 * signature est verifiee et qui sert deja aux verrous de metier. L'alternative
 * — surcharger {@code useWithoutItem} et {@code useItemOn} sur chaque bloc —
 * demanderait de deviner deux signatures protegees de plus, pour le meme
 * resultat.
 *
 * <p><b>L'ordre compte.</b> Les verrous de metier s'executent sur le meme
 * evenement et l'annulent quand le niveau manque ; en lisant
 * {@code isCanceled()} d'abord, on evite d'ouvrir l'interface d'une machine que
 * le joueur n'a pas le droit d'utiliser. Sans ce test, le refus s'afficherait
 * et l'interface s'ouvrirait quand meme.
 */
@EventBusSubscriber
public final class PalaMachineEvents {

    private PalaMachineEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity entity = event.getLevel().getBlockEntity(event.getPos());
        if (!(entity instanceof MachineBlockEntity machine)) {
            return;
        }

        // Une amelioration en main s'applique ; sinon on ouvre l'interface. Un
        // joueur qui tient une amelioration veut l'appliquer, pas ranger de la
        // pierre.
        if (machine instanceof CobblebreakerBlockEntity cobblebreaker
                && applyUpgrade(player, cobblebreaker, event.getItemStack())) {
            event.setCanceled(true);
            return;
        }

        // Accroupi avec un item en main : on laisse poser le bloc, comme pour
        // un coffre. Sinon l'interface s'ouvre.
        if (player.isShiftKeyDown() && !event.getItemStack().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        player.openMenu(machine);
    }

    /**
     * Applique une amelioration de cobblebreaker.
     *
     * <p>Rend {@code true} si l'objet a ete consomme. Un palier egal ou
     * inferieur ne consomme rien et le dit : perdre une amelioration de
     * paladium en la posant sur une machine qui l'a deja serait une perte
     * seche, ce que le principe 4 de {@code DESIGN.md} interdit.
     */
    private static boolean applyUpgrade(ServerPlayer player, CobblebreakerBlockEntity machine,
                                        ItemStack held) {
        CobblebreakerTier candidate = tierOf(held);
        if (candidate == null) {
            return false;
        }

        if (!machine.upgradeTo(candidate)) {
            player.sendSystemMessage(Component.literal("Cette machine est deja au palier ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(machine.tier().key()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" — l'amelioration n'a pas ete consommee.")
                            .withStyle(ChatFormatting.GRAY)));
            return true;
        }

        // L'etat du bloc porte aussi le palier : c'est lui qui change la
        // texture. Les deux sont mis a jour ici, et nulle part ailleurs.
        machine.getLevel().setBlockAndUpdate(machine.getBlockPos(),
                machine.getBlockState().setValue(CobblebreakerBlock.TIER, candidate));

        held.shrink(1);
        machine.getLevel().playSound(null, machine.getBlockPos(),
                SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        player.sendSystemMessage(Component.literal("Cobblebreaker ameliore : ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(candidate.key()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format(" (x%.2f)", candidate.speedFactor()))
                        .withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }

    private static CobblebreakerTier tierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(PalaMachines.AMETHYST_UPGRADE.get())) {
            return CobblebreakerTier.AMETHYST;
        }
        if (stack.is(PalaMachines.TITANE_UPGRADE.get())) {
            return CobblebreakerTier.TITANE;
        }
        if (stack.is(PalaMachines.PALADIUM_UPGRADE.get())) {
            return CobblebreakerTier.PALADIUM;
        }
        return null;
    }
}
