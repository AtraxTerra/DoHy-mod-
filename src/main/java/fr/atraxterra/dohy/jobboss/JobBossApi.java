package fr.atraxterra.dohy.jobboss;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.dungeon.JobBosses;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les boss de metier, cote autel (DoHy).
 *
 * <p>Sur l'autel du spawn, on depose les ressources de son metier — des
 * recoltes pour le Fermier, des lingots pour le Mineur, des butins de monstres
 * pour le Chasseur, des ingredients de potion pour l'Alchimiste — en autant de
 * fois qu'il le faut. L'offrande complete, le gardien arrive <b>une heure
 * plus tard</b> (heure reelle, redemarrages compris) et attend : on le
 * combat quand on veut, depuis l'autel ou d'un clic dans le chat.
 *
 * <p>Le combat est une partie de donjon d'une salle ({@link JobBosses}). A la
 * mort du gardien : de l'XP du metier et de l'argent tout de suite, le coffre
 * du gardien au centre de l'arene, et le gardien monte d'un niveau (dix au
 * plus) : une offrande plus grosse, un gardien plus fort, plus de recompense.
 * Une defaite ne coute rien : il attend encore.
 */
public final class JobBossApi {

    /** Une heure entre l'offrande complete et l'arrivee. */
    public static final long DELAY = 60L * 60L * 1000L;
    private static final long WARNING = 10L * 60L * 1000L;

    /** Une ressource de l'offrande : l'objet, et sa quantite au niveau 1. */
    private record Base(String id, int count) {
    }

    /** Ce qu'il faut deposer, au niveau du gardien. */
    public record Need(String id, Item item, int count) {
    }

    public enum State { OFFERING, COMING, READY }

    private JobBossApi() {
    }

    // -- L'offrande ---------------------------------------------------------------------

    /**
     * L'offrande de base de chaque metier, ce qu'on en recolte en une a deux
     * heures de jeu. Elle croit de moitie a chaque niveau du gardien.
     */
    private static List<Base> base(Job job) {
        return switch (job) {
            case FARMER -> List.of(new Base("minecraft:wheat", 192), new Base("minecraft:carrot", 128),
                    new Base("minecraft:potato", 128), new Base("minecraft:pumpkin", 32));
            case MINER -> List.of(new Base("minecraft:iron_ingot", 64), new Base("minecraft:gold_ingot", 32),
                    new Base("palamod:amethyst_ingot", 24), new Base("palamod:titane_ingot", 16),
                    new Base("palamod:paladium_ingot", 8));
            case HUNTER -> List.of(new Base("minecraft:rotten_flesh", 128), new Base("minecraft:bone", 64),
                    new Base("minecraft:string", 64), new Base("minecraft:gunpowder", 32),
                    new Base("minecraft:ender_pearl", 16));
            case ALCHEMIST -> List.of(new Base("minecraft:nether_wart", 96), new Base("minecraft:glowstone_dust", 64),
                    new Base("minecraft:blaze_rod", 16), new Base("minecraft:magma_cream", 16),
                    new Base("minecraft:spider_eye", 16), new Base("minecraft:ghast_tear", 4));
        };
    }

    public static List<Need> needs(Job job, int level) {
        List<Need> needs = new ArrayList<>();
        double factor = 1.0d + 0.5d * (Math.max(1, level) - 1);
        for (Base entry : base(job)) {
            Identifier key = Identifier.tryParse(entry.id());
            Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
            item.ifPresent(found -> needs.add(new Need(entry.id(), found,
                    (int) Math.round(entry.count() * factor))));
        }
        return needs;
    }

    public static JobBossData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.JOB_BOSS);
    }

    public static State state(ServerPlayer player, Job job) {
        long arrival = data(player).guardian(job).arrival();
        if (arrival <= 0L) {
            return State.OFFERING;
        }
        return System.currentTimeMillis() >= arrival ? State.READY : State.COMING;
    }

    /** Millisecondes avant l'arrivee (0 s'il est la, ou pas appele). */
    public static long remaining(ServerPlayer player, Job job) {
        long arrival = data(player).guardian(job).arrival();
        return arrival <= 0L ? 0L : Math.max(0L, arrival - System.currentTimeMillis());
    }

    /** Combien de cet objet dans le sac (les 36 cases). */
    public static int held(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item) && !stack.isDamaged()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** La part deposee de l'offrande, de 0 a 1. */
    public static double progress(ServerPlayer player, Job job) {
        JobBossData.Guardian guardian = data(player).guardian(job);
        long need = 0L;
        long have = 0L;
        for (Need entry : needs(job, guardian.level())) {
            need += entry.count();
            have += Math.min(entry.count(), guardian.deposited(entry.id()));
        }
        return need <= 0L ? 1.0d : (double) have / need;
    }

    /**
     * Depose sur l'autel ce que le joueur a de l'offrande, sans depasser ce
     * qu'il manque. La raison du refus, ou {@code null}.
     */
    public static String offer(ServerPlayer player, Job job) {
        if (state(player, job) != State.OFFERING) {
            return JobBosses.name(job) + " est deja appele.";
        }
        JobBossData.Guardian guardian = data(player).guardian(job);
        Inventory inventory = player.getInventory();
        int moved = 0;
        for (Need need : needs(job, guardian.level())) {
            int missing = need.count() - guardian.deposited(need.id());
            for (int slot = 0; slot < 36 && missing > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.is(need.item()) || stack.isDamaged()) {
                    continue;
                }
                int take = Math.min(missing, stack.getCount());
                stack.shrink(take);
                missing -= take;
                moved += take;
                guardian.deposited.merge(need.id(), take, Integer::sum);
            }
        }
        if (moved == 0) {
            return "Tu n'as rien de ce qu'il reste a offrir.";
        }
        player.inventoryMenu.broadcastChanges();
        if (progress(player, job) >= 1.0d) {
            guardian.deposited.clear();
            guardian.arrival = System.currentTimeMillis() + DELAY;
            guardian.notices = 0;
            say(player, Component.literal("Offrande complete : " + JobBosses.name(job) + " (niveau " + guardian.level()
                    + ") arrive dans une heure. L'autel te previendra.").withStyle(ChatFormatting.YELLOW));
            player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS,
                    1.0f, 0.6f);
            DoHy.LOGGER.info("Boss de metier : {} appelle {} niveau {}", player.getGameProfile().name(),
                    JobBosses.name(job), guardian.level());
        } else {
            say(player, Component.literal(moved + " ressource(s) deposee(s) : offrande a "
                    + Math.round(progress(player, job) * 100) + " %.").withStyle(ChatFormatting.GRAY));
        }
        return null;
    }

    /** Entre dans l'arene du gardien, s'il est arrive. La raison du refus, ou {@code null}. */
    public static String fight(ServerPlayer player, Job job) {
        State state = state(player, job);
        if (state == State.OFFERING) {
            return JobBosses.name(job) + " n'est pas appele : son offrande est a faire a l'autel du spawn.";
        }
        if (state == State.COMING) {
            return JobBosses.name(job) + " arrive dans " + when(remaining(player, job)) + ".";
        }
        if (JobBosses.busy(player)) {
            return "Termine d'abord ta partie en cours.";
        }
        return JobBosses.start(player, job, data(player).guardian(job).level()) ? null
                : "L'arene n'est pas disponible.";
    }

    // -- La victoire ----------------------------------------------------------------------

    public static long xpReward(int level) {
        return 20_000L * Math.max(1, level);
    }

    public static long moneyReward(int level) {
        return 15_000L * Math.max(1, level);
    }

    /**
     * Le gardien tombe. Chaque joueur de l'arene touche l'XP du metier et
     * l'argent ; celui qui l'a appele voit son gardien monter d'un niveau et
     * repartir de zero.
     */
    public static void defeated(ServerPlayer player, Job job, int level, boolean caller) {
        long xp = xpReward(level);
        long money = moneyReward(level);
        JobApi.grant(player, job, xp);
        Economy.credit(player, money);
        if (caller) {
            JobBossData.Guardian guardian = data(player).guardian(job);
            guardian.kills++;
            guardian.level = Math.min(JobBosses.MAX_LEVEL, guardian.level + 1);
            guardian.arrival = 0L;
            guardian.notices = 0;
            guardian.deposited.clear();
            player.setData(DoHyAttachments.JOB_BOSS, data(player));
        }
        say(player, Component.literal(JobBosses.name(job) + " (niveau " + level + ") est vaincu : +"
                + MoneyFormat.exact(xp) + " XP de " + job.display() + ", +" + MoneyFormat.exact(money) + " $.")
                .withStyle(ChatFormatting.GOLD));
        DoHy.LOGGER.info("Boss de metier : {} bat {} niveau {}", player.getGameProfile().name(), JobBosses.name(job),
                level);
    }

    // -- Les annonces ----------------------------------------------------------------------

    /** Toutes les vingt secondes : « dans dix minutes », puis « il est arrive ». */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Job job : Job.values()) {
                JobBossData.Guardian guardian = data(player).guardian(job);
                if (guardian.arrival <= 0L) {
                    continue;
                }
                long left = guardian.arrival - System.currentTimeMillis();
                if (left <= 0L && (guardian.notices & JobBossData.Guardian.ANNOUNCED) == 0) {
                    guardian.notices |= JobBossData.Guardian.ANNOUNCED | JobBossData.Guardian.WARNED;
                    announce(player, job);
                } else if (left > 0L && left <= WARNING && (guardian.notices & JobBossData.Guardian.WARNED) == 0) {
                    guardian.notices |= JobBossData.Guardian.WARNED;
                    say(player, Component.literal(JobBosses.name(job) + " arrive dans " + when(left) + ".")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    /** A la connexion : les gardiens qui attendent. */
    public static void remind(ServerPlayer player) {
        for (Job job : Job.values()) {
            if (state(player, job) == State.READY) {
                data(player).guardian(job).notices |= JobBossData.Guardian.ANNOUNCED | JobBossData.Guardian.WARNED;
                announce(player, job);
            }
        }
    }

    private static void announce(ServerPlayer player, Job job) {
        MutableComponent line = Component.literal(JobBosses.name(job) + " est arrive : il t'attend dans l'arene. ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("[Combattre]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/autel combattre " + job.key()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("/autel combattre " + job.key())))));
        say(player, line);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS,
                0.5f, 1.2f);
    }

    static void say(ServerPlayer player, Component text) {
        player.sendSystemMessage(Component.literal("[Autel] ").withStyle(ChatFormatting.GOLD).append(text));
    }

    /** « 42 min », « 1 h 05 », « moins d'une minute ». */
    public static String when(long millis) {
        long minutes = (millis + 59_999L) / 60_000L;
        if (millis <= 0L) {
            return "maintenant";
        }
        if (minutes < 1L) {
            return "moins d'une minute";
        }
        return minutes >= 60L ? minutes / 60L + " h " + String.format("%02d", minutes % 60L) : minutes + " min";
    }

    // -- Administration (essais) -------------------------------------------------------------

    static void makeReady(ServerPlayer player, Job job) {
        JobBossData.Guardian guardian = data(player).guardian(job);
        guardian.deposited.clear();
        guardian.arrival = System.currentTimeMillis() - 1L;
        guardian.notices = 0;
    }

    static void fill(ServerPlayer player, Job job) {
        JobBossData.Guardian guardian = data(player).guardian(job);
        for (Need need : needs(job, guardian.level())) {
            guardian.deposited.put(need.id(), need.count() - 1);
        }
        guardian.arrival = 0L;
    }

    static void setLevel(ServerPlayer player, Job job, int level) {
        data(player).guardian(job).level = Math.max(1, Math.min(JobBosses.MAX_LEVEL, level));
    }
}
