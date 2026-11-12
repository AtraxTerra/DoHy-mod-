package fr.atraxterra.dohy.contract;

import fr.atraxterra.dohy.palamod.miner.GodVillager;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * La commande de trixium : un objet a livrer, different chaque jour, paye en trixium.
 *
 * <h2>Ce qui la distingue des autres contrats</h2>
 *
 * <ul>
 *   <li><b>Elle se livre.</b> Les contrats du jour se terminent tout seuls, en
 *       jouant ; celui-ci prend des objets dans l'inventaire, depuis l'ecran des
 *       contrats. Une livraison partielle compte : on peut apporter en deux fois.</li>
 *   <li><b>Elle bascule a midi</b>, et non a 5 h : c'est la demande du joueur, et
 *       la commande est ainsi neuve pour une session du soir comme de l'apres-midi.</li>
 *   <li><b>Elle paie en trixium</b>, pas en argent : le trixium ne sortait que du
 *       minage, et il alimente le concours du mois.</li>
 * </ul>
 *
 * <h2>L'objet du jour ne se tire pas, il se calcule</h2>
 *
 * <p>Il se deduit du numero du jour : rien a sauvegarder, le meme objet avant et
 * apres un redemarrage, et aucun moyen de le relancer. Les commandes du fichier
 * sont battues une fois par tour ; chaque objet passe donc une fois avant
 * qu'aucun ne revienne, et jamais deux jours de suite.
 *
 * <h2>La serie, et pourquoi elle ne punit pas</h2>
 *
 * <p>Les contrats n'ont pas de serie a entretenir : sauter un soir ne doit rien
 * couter. Celle-ci existe quand meme, a la demande du joueur, parce qu'elle
 * n'enleve rien : une commande manquee ne retire ni trixium ni contrat, elle
 * remet seulement a zero le compte qui mene au God Villager. Sept commandes de
 * suite le font apparaitre, et le compte repart.
 *
 * <p>Le God Villager de la serie ne demande pas le niveau de Mineur de son
 * apparition au hasard ({@link GodVillager}) : sept jours de livraisons sont la
 * condition, et son echange donne de toute facon de l'XP de Mineur.
 */
public final class TrixiumOrder {

    /** Les 36 cases du sac et de la barre : ni l'armure ni la main gauche ne se livrent. */
    private static final int CARRIED_SLOTS = 36;

    private TrixiumOrder() {
    }

    private static ContractConfig.TrixiumOrderConfig config() {
        return ContractConfig.config().trixiumOrder();
    }

    /** La cle du jour de commande : elle change a l'heure du datapack, midi par defaut. */
    public static long period() {
        return RealClock.dayKey(config().resetHour());
    }

    /** Minutes reelles avant la prochaine commande. */
    public static long minutesUntilChange() {
        return RealClock.minutesUntilNextDay(config().resetHour());
    }

    /** La commande du jour, ou {@code null} si le fichier n'en propose aucune. */
    public static ContractConfig.OrderEntry today() {
        return entryFor(period(), config().pool());
    }

    /**
     * La commande d'un jour donne.
     *
     * <p>Un tour dure autant de jours qu'il y a de commandes ; l'ordre d'un tour
     * est un melange dont la graine est le numero du tour.
     */
    static ContractConfig.OrderEntry entryFor(long period, List<ContractConfig.OrderEntry> pool) {
        int size = pool.size();
        if (size == 0) {
            return null;
        }
        long round = Math.floorDiv(period, (long) size);
        int position = (int) Math.floorMod(period, (long) size);
        return pool.get(order(round, size).get(position));
    }

    /** L'ordre de passage d'un tour, sans repetition a la jointure avec le tour d'avant. */
    private static List<Integer> order(long round, int size) {
        List<Integer> order = shuffled(round, size);
        if (size > 2) {
            List<Integer> before = shuffled(round - 1, size);
            // Le dernier du tour d'avant ne bouge jamais : l'echange ne touche que les deux premiers.
            if (before.get(size - 1).equals(order.get(0))) {
                Collections.swap(order, 0, 1);
            }
        }
        return order;
    }

    private static List<Integer> shuffled(long round, int size) {
        List<Integer> order = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            order.add(index);
        }
        // java.util.Random est specifie : la meme graine donne le meme ordre partout.
        Collections.shuffle(order, new Random(round * 0x9E3779B97F4A7C15L + 0x7472697869756DL));
        return order;
    }

    /** Ce qui a ete livre pour la commande en cours. */
    public static int delivered(ServerPlayer player) {
        ContractData data = ContractApi.data(player);
        return data.orderPeriod() == period() ? data.orderProgress() : 0;
    }

    /** Vrai si la commande du jour est livree en entier. */
    public static boolean done(ServerPlayer player) {
        return ContractApi.data(player).orderDonePeriod() == period();
    }

    /**
     * Le nombre de commandes livrees de suite, aujourd'hui compris.
     *
     * <p>Une serie ne vaut que si la derniere commande livree est celle
     * d'aujourd'hui ou celle d'hier : au-dela, un jour a ete saute.
     */
    public static int streak(ServerPlayer player) {
        ContractData data = ContractApi.data(player);
        return data.orderDonePeriod() >= period() - 1 ? data.orderStreak() : 0;
    }

    /** Le nombre de jours de suite qui fait venir le God Villager ; 0 si la serie est coupee. */
    public static int streakGoal() {
        return Math.max(0, config().streak());
    }

    /** Combien le joueur porte de cet objet, hors armure et main gauche. */
    public static int carried(ServerPlayer player, Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            return 0;
        }
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CARRIED_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Annonce la commande quand elle change : a la connexion, et a midi pour un joueur present. */
    public static void refresh(ServerPlayer player) {
        ContractConfig.OrderEntry entry = today();
        ContractData data = ContractApi.data(player);
        long period = period();
        if (entry == null || data.orderPeriod() == period) {
            return;
        }
        data.setOrder(period, 0);
        player.sendSystemMessage(Component.literal("Commande de trixium : ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(entry.amount() + " ").withStyle(ChatFormatting.WHITE))
                .append(name(entry.item()).copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  contre " + config().reward() + " ").withStyle(ChatFormatting.GOLD))
                .append(name(config().rewardItem()).copy().withStyle(ChatFormatting.GOLD)));
    }

    /**
     * Livre ce que le joueur porte, a hauteur de ce qui manque.
     *
     * @return le refus a afficher, ou {@code null} si quelque chose a ete livre
     */
    public static String deliver(ServerPlayer player) {
        ContractConfig.OrderEntry entry = today();
        if (entry == null) {
            return "Aucune commande de trixium aujourd'hui.";
        }
        if (done(player)) {
            return "La commande du jour est deja livree.";
        }
        Item item = BuiltInRegistries.ITEM.getOptional(entry.item()).orElse(null);
        if (item == null) {
            return "Cet objet n'existe pas dans ce jeu.";
        }

        ContractData data = ContractApi.data(player);
        long period = period();
        int before = delivered(player);
        int taken = take(player, item, entry.amount() - before);
        if (taken <= 0) {
            return "Tu ne portes pas ce que demande la commande.";
        }
        int progress = before + taken;
        data.setOrder(period, progress);

        if (progress < entry.amount()) {
            player.sendSystemMessage(Component.literal("Commande de trixium : " + taken + " livre(s), reste "
                    + (entry.amount() - progress)).withStyle(ChatFormatting.GRAY));
            return null;
        }
        complete(player, data, period);
        return null;
    }

    private static void complete(ServerPlayer player, ContractData data, long period) {
        ContractConfig.TrixiumOrderConfig config = config();
        int streak = data.orderDonePeriod() == period - 1 ? data.orderStreak() + 1 : 1;
        int goal = streakGoal();
        boolean summon = goal > 0 && streak >= goal;
        // La serie accomplie repart de zero : le prochain God Villager demande sept nouvelles commandes.
        data.setOrderDone(period, summon ? 0 : streak);

        Item reward = BuiltInRegistries.ITEM.getOptional(config.rewardItem()).orElse(null);
        if (reward != null && config.reward() > 0) {
            give(player, new ItemStack(reward, config.reward()));
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 1.2f);
        MutableComponent line = Component.literal("Commande livree — ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("+" + config.reward() + " ").withStyle(ChatFormatting.GOLD))
                .append(name(config.rewardItem()).copy().withStyle(ChatFormatting.GOLD));
        if (goal > 0) {
            line.append(Component.literal("  · serie " + streak + " / " + goal)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        player.sendSystemMessage(line);
        ContractApi.grantGlobalXp(player, config.globalXp(), "commande de trixium livree");

        if (summon) {
            player.sendSystemMessage(Component.literal(goal + " commandes de suite : le God Villager vient a toi.")
                    .withStyle(ChatFormatting.GOLD));
            GodVillager.spawn(player);
        }
    }

    /** Retire jusqu'a {@code wanted} exemplaires du sac ; rend ce qui a ete pris. */
    private static int take(ServerPlayer player, Item item, int wanted) {
        int left = wanted;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CARRIED_SLOTS && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int part = Math.min(left, stack.getCount());
            stack.shrink(part);
            left -= part;
        }
        if (left < wanted) {
            inventory.setChanged();
        }
        return wanted - left;
    }

    /** Donne une pile, decoupee a la taille maximale de l'objet ; ce qui ne rentre pas tombe au sol. */
    private static void give(ServerPlayer player, ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        int left = stack.getCount();
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(max, left));
            left -= part.getCount();
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                player.drop(part, false);
            }
        }
    }

    private static Component name(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? Component.literal(id.getPath()) : new ItemStack(item).getHoverName();
    }
}
