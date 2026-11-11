package fr.atraxterra.dohy.palamod.hunter;

import fr.atraxterra.dohy.palamod.mount.Dancarok;
import fr.atraxterra.dohy.palamod.mount.MountGem;
import fr.atraxterra.dohy.palamod.mount.MountGemstoneItem;
import fr.atraxterra.dohy.palamod.mount.MountManager;
import fr.atraxterra.dohy.palamod.mount.MountTexts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.Set;

/**
 * L'epee de capture et l'epee en endium a bout d'usure.
 *
 * <p><b>Capture</b> ({@code EventsManager.onKillEntity}, priorite la plus haute,
 * puis {@code ItemCaptureSword.hitEntity}) : toute mort causee par un joueur qui
 * tient l'epee de capture est annulee et le corps disparait — ni butin ni XP. Si
 * la creature est capturable et qu'il porte une pierre vide, la <b>derniere</b>
 * pierre vide de son inventaire la garde. L'objet tenu par la creature est
 * retire ; son armure est gardee. <b>Ecarts</b> : les joueurs ne sont plus
 * concernes (l'original annulait aussi leur mort, une faute) ; un gros slime ne
 * se divise plus en le capturant (il le faisait deux fois, une duplication) ; une
 * pile de creatures n'en perd qu'une.
 *
 * <p>Capturables : les huit de 1.7.10 (cochon, vache, mouton, slime, zombie et
 * zombie-villageois, creeper, poulet), le lapin, et cinq creatures que PalaMod
 * ajoutait et qui sont vanilla en 26.2 (tortue, panda, dauphin, perroquet,
 * chevre). Le cheval ne l'a jamais ete ; le wither ne l'etait que sur le serveur
 * de minage ; les autres creatures de PalaMod ne sont pas portees. Le Dancarok
 * sauvage donne une gemme de monture (chapitre 14).
 */
@EventBusSubscriber
public final class HunterEvents {

    private static final Set<EntityType<?>> CAPTURABLE = Set.of(
            EntityTypes.PIG, EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.SLIME,
            EntityTypes.ZOMBIE, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.CREEPER, EntityTypes.CHICKEN,
            EntityTypes.RABBIT, EntityTypes.TURTLE, EntityTypes.PANDA, EntityTypes.DOLPHIN,
            EntityTypes.PARROT, EntityTypes.GOAT);

    private HunterEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKill(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof Player
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !player.getMainHandItem().is(PalaHunter.CAPTURE_SWORD.get())) {
            return;
        }
        event.setCanceled(true);
        if (victim instanceof Dancarok mount) {
            captureMount(player, mount);
            victim.discard();
            return;
        }
        if (victim instanceof Mob mob) {
            MobStacks.split(mob);
        }
        ItemStack stone = CAPTURABLE.contains(victim.getType()) ? emptyStone(player.getInventory()) : null;
        if (stone != null) {
            if (victim instanceof Mob mob) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            victim.saveWithoutId(output);
            CompoundTag data = output.buildResult();
            data.remove("UUID");
            stone.set(PalaHunter.CAPTURED.get(), new CapturedEntity(
                    BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), data, victim.getName()));
            fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "capture:" + BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()), 1);
        }
        victim.discard();
    }

    /**
     * Branche monture de {@code ItemCaptureSword.hitEntity} : un Dancarok
     * sauvage tue avec une pierre vide sur soi donne une gemme sauvage, a
     * apprivoiser dans un enclos. La pierre est consommee, pas remplie.
     * <b>Ecarts</b> : la pierre n'est prise qu'une fois la capture acquise
     * (l'original la retirait avant de verifier que la monture etait sauvage),
     * et la gemme tombe aux pieds du joueur si son inventaire est plein.
     */
    private static void captureMount(ServerPlayer player, Dancarok mount) {
        if (mount.isTamed()) {
            return;
        }
        ItemStack stone = emptyStone(player.getInventory());
        if (stone == null) {
            return;
        }
        stone.shrink(1);
        MountManager.give(player, MountGemstoneItem.stackOf(MountGem.wild()));
        fr.atraxterra.dohy.palamod.achievement.Achievements.count(player, "mount:capture", 1);
        MountTexts.success(player, "captured");
    }

    /** La derniere pierre de capture vide des 36 cases, comme l'original. */
    private static ItemStack emptyStone(Inventory inventory) {
        ItemStack found = null;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(PalaHunter.CAPTURE_STONE.get()) && !CaptureStoneItem.isFull(stack)) {
                found = stack;
            }
        }
        return found;
    }

    /** {@code onLeftClickEntity} rend vrai a bout d'usure : le coup n'a pas lieu. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        ItemStack held = event.getEntity().getMainHandItem();
        if (held.getItem() instanceof EndiumSwordItem && EndiumSwordItem.isWornOut(held)) {
            event.setCanceled(true);
        }
    }
}
