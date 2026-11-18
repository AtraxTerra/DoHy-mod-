package fr.atraxterra.dohy.palamod.alchemist;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Iterator;

/**
 * Les effets des enchantements de l'Alchimiste qui ne tiennent pas dans une
 * seule classe de PalaMod : voleur d'XP, recuperation, recuperation de
 * minage, experience. Releves dans {@code hunter.events.EventsManager}
 * ({@code onXpStealer}, {@code onRecuperation}, {@code onRecuperationDig}) et
 * {@code ExperienceEventHandler}.
 *
 * <p>Les regles de calcul sont des methodes statiques a part, pour que
 * le banc d'essai les appelle telles quelles.
 */
@EventBusSubscriber
public final class PalaEnchantEffects {

    /** Chances par coup du voleur d'XP, en pour cent, aux niveaux 1 a 4. */
    static final int[] STEAL_CHANCE = {2, 4, 7, 10};
    /** Points d'XP voles, aux niveaux 1 a 4. */
    static final int[] STEAL_AMOUNT = {40, 50, 70, 80};
    /** Bonus de l'enchantement experience, en pour cent, aux niveaux 1 a 3 ({@code getBonus}). */
    static final int[] EXPERIENCE_BONUS = {1, 3, 5};

    private PalaEnchantEffects() {
    }

    // -- Voleur d'XP -----------------------------------------------------------

    /**
     * Un coup d'epee porte d'un joueur a un autre : avec la chance du niveau,
     * l'attaquant prend des points d'XP a sa victime. Il faut un coup direct
     * qui a fait des degats, comme l'original (qui lisait la source immediate).
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getInflictedDamage() <= 0.0f
                || !(event.getSource().getDirectEntity() instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        int level = MysticEnchant.XP_STEALER.levelOn(attacker, attacker.getMainHandItem());
        if (level > 0) {
            steal(attacker, victim, level, attacker.getRandom());
        }
    }

    /** Le vol lui-meme ; renvoie les points pris, 0 si le tirage a echoue. */
    public static int steal(ServerPlayer attacker, ServerPlayer victim, int level, RandomSource random) {
        int index = Math.min(level, STEAL_CHANCE.length) - 1;
        if (index < 0 || random.nextInt(100) >= STEAL_CHANCE[index]) {
            return 0;
        }
        if (victim.totalExperience <= 0 && victim.experienceLevel <= 0) {
            return 0;
        }
        int amount = Math.min(STEAL_AMOUNT[index], Math.max(0, victim.totalExperience));
        if (amount <= 0) {
            return 0;
        }
        victim.giveExperiencePoints(-amount);
        attacker.giveExperiencePoints(amount);
        return amount;
    }

    // -- Recuperation ----------------------------------------------------------

    /**
     * Un monstre tue a l'epee : son butin va dans le sac, ce qui ne tient pas
     * reste au sol. Les creatures nommees gardent leur butin au sol, comme dans
     * l'original. Priorite basse : les autres effets de butin passent avant.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Mob mob) || mob.hasCustomName()
                || !(event.getSource().getDirectEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (MysticEnchant.RECUPERATION.levelOn(player, player.getMainHandItem()) > 0) {
            collect(player, event.getDrops().iterator());
        }
    }

    /** Un bloc casse avec l'outil enchante : meme chose, en tout dernier. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled() || !(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        if (MysticEnchant.RECUPERATION_DIG.levelOn(player, event.getTool()) > 0) {
            collect(player, event.getDrops().iterator());
        }
    }

    /** Range ce qui tient dans le sac ; une pile qui ne tient pas en entier reste au sol, diminuee. */
    static void collect(ServerPlayer player, Iterator<ItemEntity> drops) {
        while (drops.hasNext()) {
            ItemEntity drop = drops.next();
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) {
                drops.remove();
                continue;
            }
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                drops.remove();
            } else {
                drop.setItem(stack);
            }
        }
    }

    // -- Experience -------------------------------------------------------------

    /** Epee en main, chaque orbe ramassee rapporte 1, 3 ou 5 % de plus, arrondi vers le bas. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int level = MysticEnchant.EXPERIENCE.levelOn(player, player.getMainHandItem());
        int bonus = experienceBonus(event.getOrb().getValue(), level);
        if (bonus > 0) {
            player.giveExperiencePoints(bonus);
        }
    }

    public static int experienceBonus(int value, int level) {
        if (level <= 0 || value <= 0) {
            return 0;
        }
        int percent = EXPERIENCE_BONUS[Math.min(level, EXPERIENCE_BONUS.length) - 1];
        return value * percent / 100;
    }
}
