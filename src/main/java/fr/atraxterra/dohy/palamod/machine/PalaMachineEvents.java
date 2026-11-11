package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.golem.GolemBoxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Les interactions avec les machines : ouvrir, ameliorer, alimenter.
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

        // Le totem passe avant : il n'est pas une MachineBlockEntity, n'ayant
        // pas d'inventaire ni d'interface, et le test suivant l'ecarterait.
        if (entity instanceof TotemBlockEntity totem) {
            if (useTotem(player, totem, event.getItemStack())) {
                event.setCanceled(true);
            }
            return;
        }

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

        // La boite a golem : accroupi, main vide, on change de metal.
        if (machine instanceof GolemBoxBlockEntity box && player.isShiftKeyDown()
                && event.getItemStack().isEmpty()) {
            event.setCanceled(true);
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                box.cycleMetal(player);
            }
            return;
        }

        // Le controleur de spawners : accroupi, main vide, ce qui manque au palier suivant.
        if (machine instanceof fr.atraxterra.dohy.palamod.hunter.SpawnerControllerBlockEntity controller
                && player.isShiftKeyDown() && event.getItemStack().isEmpty()) {
            event.setCanceled(true);
            if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                controller.reportNext(player);
            }
            return;
        }

        // Accroupi avec un item en main : on laisse poser le bloc, comme pour
        // un coffre. Sinon l'interface s'ouvre.
        if (player.isShiftKeyDown() && !event.getItemStack().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (machine.canOpen(player)) {
            player.openMenu(machine);
        }
    }

    /**
     * Le totem : alimenter, consulter, ou montrer la zone.
     *
     * <p>Les trois gestes sont ceux de l'original — lingot en main pour
     * alimenter, main vide pour consulter, accroupi pour afficher la zone. Le
     * totem n'a volontairement pas d'interface : son etat tient en deux
     * nombres, et une fenetre a une seule case aurait demande une troisieme
     * texture d'interface pour moins d'information qu'une ligne de chat.
     *
     * <p>Rend {@code true} si le geste a ete pris en compte, donc si
     * l'evenement doit etre annule.
     */
    private static boolean useTotem(ServerPlayer player, TotemBlockEntity totem, ItemStack held) {
        if (player.isShiftKeyDown()) {
            showZone(player, totem);
            return true;
        }

        if (held.is(PalaItems.PALADIUM_INGOT.get())) {
            if (!totem.addFuel(held)) {
                player.sendSystemMessage(Component.literal(
                                "Le totem est plein — le lingot n'a pas ete consomme.")
                        .withStyle(ChatFormatting.GRAY));
                return true;
            }
            held.shrink(1);
            fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "totem:fuel", 1);
            player.level().playSound(null, totem.getBlockPos(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.2f);
            report(player, totem);
            return true;
        }

        if (held.isEmpty()) {
            report(player, totem);
            return true;
        }
        // Autre chose en main : on laisse le geste normal (poser un bloc).
        return false;
    }

    /** L'etat du totem en une ligne : ce qu'il a, et ce qu'il peut encore faire. */
    private static void report(ServerPlayer player, TotemBlockEntity totem) {
        ItemStack fuel = totem.fuel();
        if (fuel.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                            "Totem vide — il lui faut des lingots de paladium.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.literal("Totem : ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(fuel.getCount() + " lingot"
                                + (fuel.getCount() > 1 ? "s" : ""))
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(", soit ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(totem.remainingBoosts()))
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" poussees de croissance.")
                        .withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Montre la zone du totem, sol fertilise par sol fertilise.
     *
     * <p>On n'affiche pas le carre de 15x15 mais <b>les cases qui comptent</b> :
     * le totem ne travaille que sur la terre fertilisee, et c'est justement ce
     * qu'un joueur ne peut pas deviner. Un carre complet aurait laisse croire
     * que tout le carre est accelere.
     */
    private static void showZone(ServerPlayer player, TotemBlockEntity totem) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = totem.getBlockPos();
        int shown = 0;

        for (int dx = -TotemBlockEntity.RADIUS; dx <= TotemBlockEntity.RADIUS; dx++) {
            for (int dz = -TotemBlockEntity.RADIUS; dz <= TotemBlockEntity.RADIUS; dz++) {
                BlockPos soil = center.offset(dx, -1, dz);
                if (!TotemBlockEntity.isFertilizedSoil(level.getBlockState(soil))) {
                    continue;
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        soil.getX() + 0.5, soil.getY() + 1.1, soil.getZ() + 0.5,
                        1, 0.0, 0.0, 0.0, 0.0);
                shown++;
            }
        }

        player.sendSystemMessage(Component.literal(shown + " case"
                        + (shown > 1 ? "s" : "") + " de terre fertilisee dans la zone.")
                .withStyle(ChatFormatting.GRAY));
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
