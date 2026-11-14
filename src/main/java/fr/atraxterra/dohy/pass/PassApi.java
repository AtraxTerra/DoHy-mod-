package fr.atraxterra.dohy.pass;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.contract.ContractDisplay;
import fr.atraxterra.dohy.contract.ContractType;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.palamod.dungeon.JobBottleItem;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Le pass mensuel : un mois, 35 niveaux, des quetes qui donnent l'XP du pass.
 *
 * <p>Chaque jour (bascule a 5 h), trois quetes tirees du pool ; chaque mois,
 * six quetes au theme du mois. Elles comptent les memes gestes que les
 * contrats ({@link fr.atraxterra.dohy.contract.ContractApi#progress} les leur
 * transmet). Chaque niveau a sa recompense, a recuperer dans l'ecran du pass ;
 * le dernier donne la relique du mois, qu'aucun autre mois ne donne.
 *
 * <p>Le mois suivant, tout repart de zero : les recompenses atteintes et
 * oubliees sont donnees a la bascule, rien n'est perdu de ce qui etait gagne.
 */
public final class PassApi {

    private PassApi() {
    }

    public static PassData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.PASS.get());
    }

    public static int level(PassData data) {
        PassConfig.Config config = PassConfig.config();
        return data.level(config.xpPerLevel(), config.levels());
    }

    public static int level(ServerPlayer player) {
        return level(data(player));
    }

    /** L'XP gagnee dans le niveau en cours ; -1 au dernier niveau. */
    public static long xpIntoLevel(PassData data) {
        PassConfig.Config config = PassConfig.config();
        if (level(data) >= config.levels()) {
            return -1L;
        }
        return data.xp() % Math.max(1L, config.xpPerLevel());
    }

    public static PassConfig.Theme theme(int season) {
        return PassConfig.config().theme(PassSeason.month(season));
    }

    public static Relic relic(int season) {
        return Relic.of(PassSeason.month(season));
    }

    // -- Les bascules ---------------------------------------------------------------------

    /**
     * Ouvre le mois et le jour s'ils ont change. Une comparaison de cles : trois
     * jours d'absence donnent une seule serie de quetes, pas trois.
     */
    public static void rollIfDue(ServerPlayer player) {
        PassConfig.Config config = PassConfig.config();
        if (config.themes().isEmpty()) {
            return;
        }
        PassData data = data(player);
        int season = PassSeason.current();
        if (data.season() != season) {
            if (data.season() >= 0) {
                close(player, data);
            }
            PassConfig.Theme theme = config.theme(PassSeason.month(season));
            List<PassQuest> quests = new ArrayList<>();
            if (theme != null) {
                for (PassConfig.Quest quest : theme.quests()) {
                    if (ContractType.byName(quest.type()) != ContractType.UNKNOWN) {
                        quests.add(PassQuest.of(quest));
                    }
                }
            }
            data.start(season, quests);
            announceSeason(player, season, theme);
        }
        long day = PassSeason.dayKey();
        if (data.day() != day) {
            data.setDaily(day, pick(config.daily(), new Random(player.getRandom().nextLong())));
        }
    }

    private static List<PassQuest> pick(PassConfig.Daily daily, Random random) {
        List<PassConfig.Quest> pool = new ArrayList<>(daily.pool());
        Collections.shuffle(pool, random);
        List<PassQuest> chosen = new ArrayList<>();
        for (PassConfig.Quest quest : pool) {
            if (chosen.size() >= daily.count()) {
                break;
            }
            if (ContractType.byName(quest.type()) != ContractType.UNKNOWN) {
                chosen.add(PassQuest.of(quest));
            }
        }
        return chosen;
    }

    /** La fin d'un mois : ce qui etait atteint et pas encore recupere est donne maintenant. */
    private static void close(ServerPlayer player, PassData data) {
        PassConfig.Config config = PassConfig.config();
        int old = data.season();
        int reached = level(data);
        int given = 0;
        for (PassConfig.Reward reward : config.rewards()) {
            if (reward.level() <= reached && !data.claimed(reward.level())) {
                give(player, reward, old);
                given++;
            }
        }
        player.sendSystemMessage(Component.literal("[Pass] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("Le pass " + PassSeason.ofMonth(old) + " est termine, niveau " + reached
                        + " / " + config.levels() + (given > 0 ? " : " + given
                        + " recompense(s) oubliee(s), donnee(s) maintenant." : ".")).withStyle(ChatFormatting.GRAY)));
        DoHy.LOGGER.info("Pass : fin de {} pour {} au niveau {} ({} recompenses donnees a la bascule)",
                PassSeason.label(old), player.getGameProfile().name(), reached, given);
    }

    private static void announceSeason(ServerPlayer player, int season, PassConfig.Theme theme) {
        String name = theme == null ? "" : " · " + theme.name();
        player.sendSystemMessage(Component.literal("[Pass] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("Le pass " + PassSeason.ofMonth(season) + " commence" + name
                        + ". Au niveau " + PassConfig.config().levels() + " : " + relic(season).title() + ". ")
                        .withStyle(ChatFormatting.WHITE))
                .append(link("[Ouvrir]")));
    }

    static MutableComponent link(String label) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/pass"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/pass"))));
    }

    // -- La progression -------------------------------------------------------------------

    /** Fait avancer les quetes du pass de ce type dont la cible passe le test. */
    public static void progress(ServerPlayer player, ContractType type, Predicate<String> targetTest, long delta) {
        if (delta <= 0) {
            return;
        }
        PassData data = data(player);
        if (data.isEmpty() || data.season() != PassSeason.current()) {
            return;
        }
        advance(player, data, false, type, targetTest, delta);
        advance(player, data, true, type, targetTest, delta);
    }

    private static void advance(ServerPlayer player, PassData data, boolean month, ContractType type,
                                Predicate<String> targetTest, long delta) {
        List<PassQuest> list = month ? data.monthly() : data.daily();
        for (int index = 0; index < list.size(); index++) {
            PassQuest quest = list.get(index);
            if (quest.done() || quest.kind() != type) {
                continue;
            }
            if (quest.target().isPresent() && !targetTest.test(quest.target().get())) {
                continue;
            }
            PassQuest advanced = quest.advance(delta);
            data.replace(month, index, advanced);
            if (advanced.done()) {
                complete(player, advanced, month);
            }
        }
    }

    private static void complete(ServerPlayer player, PassQuest quest, boolean month) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER,
                0.6f, 1.4f);
        player.sendSystemMessage(Component.literal("[Pass] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal((month ? "Quete du mois terminee : " : "Quete du jour terminee : ")
                        + ContractDisplay.label(quest.asContract())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  +" + MoneyFormat.exact(quest.xp()) + " XP").withStyle(ChatFormatting.AQUA)));
        grantXp(player, quest.xp());
    }

    /** De l'XP de pass ; les niveaux franchis s'annoncent. */
    public static void grantXp(ServerPlayer player, long amount) {
        if (amount <= 0) {
            return;
        }
        PassData data = data(player);
        int before = level(data);
        data.addXp(amount);
        int after = level(data);
        if (after > before) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.MASTER, 0.7f, 0.8f);
            player.sendSystemMessage(Component.literal("[Pass] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("Niveau " + after + " / " + PassConfig.config().levels()
                            + " : recompense a recuperer ").withStyle(ChatFormatting.GOLD))
                    .append(link("[Recuperer]")));
        }
    }

    /** Fixe l'XP (administration). */
    public static void setXp(ServerPlayer player, long xp) {
        data(player).setXp(xp);
    }

    // -- Les recompenses ------------------------------------------------------------------

    /** Combien de niveaux atteints ont une recompense a recuperer. */
    public static int claimable(ServerPlayer player) {
        PassData data = data(player);
        if (data.isEmpty()) {
            return 0;
        }
        int reached = level(data);
        int count = 0;
        for (PassConfig.Reward reward : PassConfig.config().rewards()) {
            if (reward.level() <= reached && !data.claimed(reward.level())) {
                count++;
            }
        }
        return count;
    }

    /** Recupere la recompense d'un niveau. La raison du refus, ou {@code null}. */
    public static String claim(ServerPlayer player, int level) {
        PassData data = data(player);
        PassConfig.Reward reward = PassConfig.config().reward(level);
        if (reward == null) {
            return "Ce niveau n'a pas de recompense.";
        }
        if (level > level(data)) {
            return "Niveau " + level + " pas encore atteint.";
        }
        if (data.claimed(level)) {
            return "Recompense deja recuperee.";
        }
        data.claim(level);
        give(player, reward, data.season());
        return null;
    }

    /** Recupere tout ce qui est atteint ; combien de niveaux. */
    public static int claimAll(ServerPlayer player) {
        PassData data = data(player);
        int reached = level(data);
        int count = 0;
        for (PassConfig.Reward reward : PassConfig.config().rewards()) {
            if (reward.level() <= reached && !data.claimed(reward.level())) {
                data.claim(reward.level());
                give(player, reward, data.season());
                count++;
            }
        }
        return count;
    }

    static void give(ServerPlayer player, PassConfig.Reward reward, int season) {
        if (reward.money() > 0) {
            Economy.credit(player, reward.money());
        }
        for (PassConfig.Stack stack : reward.items()) {
            giveStack(player, stack(stack));
        }
        if (reward.relic()) {
            giveStack(player, DoHyPass.relic(relic(season), season));
        }
        DoHy.LOGGER.info("Pass : {} recupere le niveau {} de {}", player.getGameProfile().name(), reward.level(),
                PassSeason.label(season));
    }

    /** La pile d'une recompense ; une bouteille de metier prend son XP. */
    public static ItemStack stack(PassConfig.Stack spec) {
        Item item = BuiltInRegistries.ITEM.getOptional(spec.item()).orElse(Items.AIR);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, spec.count()));
        if (spec.jobXp() > 0 && item instanceof JobBottleItem) {
            stack.set(PalaDungeon.JOB_XP.get(), spec.jobXp());
        }
        return stack;
    }

    static void giveStack(ServerPlayer player, ItemStack stack) {
        int left = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            ItemStack part = stack.copyWithCount(Math.min(max, left));
            left -= part.getCount();
            player.getInventory().add(part);
            if (!part.isEmpty()) {
                player.drop(part, false);
            }
        }
    }

    /** Ce que donne une recompense, ligne par ligne. */
    public static List<String> describe(PassConfig.Reward reward, int season) {
        List<String> lines = new ArrayList<>();
        if (reward.money() > 0) {
            lines.add("+" + MoneyFormat.exact(reward.money()) + " $");
        }
        for (PassConfig.Stack spec : reward.items()) {
            ItemStack stack = stack(spec);
            if (stack.isEmpty()) {
                continue;
            }
            lines.add(stack.getCount() + " x " + stack.getHoverName().getString()
                    + (spec.jobXp() > 0 ? " (" + MoneyFormat.exact(spec.jobXp()) + " XP)" : ""));
        }
        if (reward.relic()) {
            lines.add("Relique : " + relic(season).title());
        }
        return lines;
    }

    /** L'icone d'une recompense : la relique, sinon le premier objet, sinon l'or. */
    public static ItemStack icon(PassConfig.Reward reward, int season) {
        if (reward.relic()) {
            return DoHyPass.relic(relic(season), season);
        }
        for (PassConfig.Stack spec : reward.items()) {
            ItemStack stack = stack(spec);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        long money = reward.money();
        return new ItemStack(money >= 100_000L ? Items.GOLD_BLOCK : money >= 20_000L ? Items.GOLD_INGOT
                : Items.GOLD_NUGGET);
    }
}
