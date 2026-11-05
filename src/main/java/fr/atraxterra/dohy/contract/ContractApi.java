package fr.atraxterra.dohy.contract;

import fr.atraxterra.dohy.DoHyAttachments;
import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.economy.price.PriceEntry;
import fr.atraxterra.dohy.economy.price.Prices;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Contrats, demande du jour et niveau global.
 *
 * <h2>Le role de ces systemes</h2>
 *
 * <p>Ils existent pour une raison precise, tiree du rythme de jeu vise : deux
 * heures en semaine doivent suffire a franchir quelque chose. Les trois contrats
 * quotidiens sont calibres pour ca, le contrat hebdomadaire est volontairement
 * infaisable en une soiree, et la demande du jour donne un objectif immediat des
 * la connexion.
 *
 * <p>Les cycles tournent en <b>temps reel</b>, a heure fixe : les contrats du
 * jour se renouvellent a l'heure de bascule du datapack, et non vingt-quatre
 * heures apres le dernier tirage. Se connecter deux soirs de suite donne donc
 * bien deux series de contrats, ce qu'un compteur en temps de jeu ne faisait
 * pas — vingt-quatre heures de jeu, a deux heures par soiree, font douze jours.
 *
 * <p>Aucun de ces systemes ne se casse si on saute un soir : sauter trois jours
 * ne produit pas trois tirages de rattrapage, juste un seul. C'est la raison
 * pour laquelle il n'y a pas de serie a entretenir.
 */
public final class ContractApi {

    /** Croissance de la recompense avec le niveau global, par niveau. */
    private static final double REWARD_PER_GLOBAL_LEVEL = 0.35d;

    /** XP necessaire pour passer au niveau global suivant, par niveau. */
    private static final long GLOBAL_XP_PER_LEVEL = 500L;

    /** Recompense accordee tous les cinq niveaux globaux. */
    private static final long GLOBAL_MILESTONE_MONEY = 50_000L;

    private ContractApi() {
    }

    public static ContractData data(ServerPlayer player) {
        return player.getData(DoHyAttachments.CONTRACTS);
    }

    public static int globalLevel(ServerPlayer player) {
        return (int) (data(player).globalXp() / GLOBAL_XP_PER_LEVEL);
    }

    public static long globalXpPerLevel() {
        return GLOBAL_XP_PER_LEVEL;
    }

    /** XP acquise dans le niveau global en cours. */
    public static long globalXpIntoLevel(ServerPlayer player) {
        return data(player).globalXp() % GLOBAL_XP_PER_LEVEL;
    }

    /**
     * Recompense actuelle d'un contrat, niveau global compris.
     *
     * <p>Expose pour que l'interface annonce le montant exact qui sera verse,
     * et non une estimation : un ecran qui promet un chiffre different de celui
     * du paiement est pire que pas d'ecran.
     */
    public static long rewardFor(ServerPlayer player, ActiveContract contract) {
        ContractConfig.Pool pool = contract.weekly()
                ? ContractConfig.config().weekly()
                : ContractConfig.config().daily();
        return Math.round(pool.moneyPerLevel()
                * (1.0d + globalLevel(player) * REWARD_PER_GLOBAL_LEVEL));
    }

    /** XP de jalon. Les paliers de niveau global sont annonces et payes ici. */
    public static void grantGlobalXp(ServerPlayer player, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        ContractData data = data(player);
        int before = globalLevel(player);
        data.addGlobalXp(amount);
        int after = globalLevel(player);

        for (int level = before + 1; level <= after; level++) {
            if (level % 5 != 0) {
                continue;
            }
            long money = GLOBAL_MILESTONE_MONEY * (level / 5L);
            long balance = Economy.credit(player, money);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.7f, 0.8f);
            player.sendSystemMessage(Component.literal("Niveau global " + level)
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("  +" + MoneyFormat.exact(money))
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("  · solde " + MoneyFormat.shortForm(balance))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (after > before && after % 5 != 0) {
            player.sendSystemMessage(Component.literal("Niveau global " + after + " — " + reason)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    /** La cle du jour courant, bascule du datapack comprise. */
    public static long today() {
        return RealClock.dayKey(ContractConfig.config().reset().dailyHour());
    }

    /** La cle de la semaine courante. */
    public static long thisWeek() {
        ContractConfig.Reset reset = ContractConfig.config().reset();
        return RealClock.weekKey(reset.dailyHour(), RealClock.parseDay(reset.weeklyDay()));
    }

    /** Minutes reelles avant le prochain renouvellement quotidien. */
    public static long minutesUntilDailyReset() {
        return RealClock.minutesUntilNextDay(ContractConfig.config().reset().dailyHour());
    }

    /** Minutes reelles avant le prochain renouvellement hebdomadaire. */
    public static long minutesUntilWeeklyReset() {
        ContractConfig.Reset reset = ContractConfig.config().reset();
        return RealClock.minutesUntilNextWeek(reset.dailyHour(),
                RealClock.parseDay(reset.weeklyDay()));
    }

    /**
     * Tire de nouveaux contrats si la periode a change.
     *
     * <p>Une comparaison de cles, pas un compte a rebours : sauter trois jours
     * ne declenche donc qu'un seul tirage, et non trois de rattrapage.
     */
    public static void rollIfDue(ServerPlayer player) {
        ContractData data = data(player);
        ContractConfig.Config config = ContractConfig.config();

        long today = today();
        if (data.dailyPeriod() != today) {
            data.setDaily(pick(config.daily(), false));
            data.setDailyPeriod(today);
            announce(player, data.daily(), "Contrats du jour");
        }

        long week = thisWeek();
        if (data.weeklyPeriod() != week) {
            data.setWeekly(pick(config.weekly(), true));
            data.setWeeklyPeriod(week);
            announce(player, data.weekly(), "Contrat de la semaine");
        }
    }

    private static List<ActiveContract> pick(ContractConfig.Pool pool, boolean weekly) {
        List<ContractConfig.Entry> candidates = new ArrayList<>(pool.pool());
        Collections.shuffle(candidates);
        List<ActiveContract> chosen = new ArrayList<>();
        for (ContractConfig.Entry entry : candidates) {
            if (chosen.size() >= pool.count()) {
                break;
            }
            if (ContractType.byName(entry.type()) == ContractType.UNKNOWN) {
                continue;
            }
            chosen.add(ActiveContract.of(entry, weekly));
        }
        return chosen;
    }

    private static void announce(ServerPlayer player, List<ActiveContract> contracts, String title) {
        if (contracts.isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.literal(title).withStyle(ChatFormatting.GOLD));
        contracts.forEach(contract -> player.sendSystemMessage(
                Component.literal("  " + describe(contract)).withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Fait avancer les contrats de ce type dont la cible satisfait le test.
     *
     * <p>Le test est fourni par l'appelant et non resolu ici, parce que seul
     * l'appelant dispose du sujet : un bloc sait tester ses propres tags, un
     * compteur de distance n'a pas de cible du tout. Un contrat sans cible
     * accepte toujours.
     */
    public static void progress(ServerPlayer player, ContractType type,
                                Predicate<String> targetTest, long delta) {
        if (delta <= 0) {
            return;
        }
        ContractData data = data(player);
        advance(player, data, data.daily(), false, type, targetTest, delta);
        advance(player, data, data.weekly(), true, type, targetTest, delta);
    }

    private static void advance(ServerPlayer player, ContractData data, List<ActiveContract> list,
                                boolean weekly, ContractType type,
                                Predicate<String> targetTest, long delta) {
        for (int index = 0; index < list.size(); index++) {
            ActiveContract contract = list.get(index);
            if (contract.done() || contract.kind() != type) {
                continue;
            }
            if (contract.target().isPresent() && !targetTest.test(contract.target().get())) {
                continue;
            }
            ActiveContract advanced = contract.advance(delta);
            data.replace(weekly, index, advanced);
            if (advanced.done()) {
                complete(player, advanced);
            }
        }
    }

    private static void complete(ServerPlayer player, ActiveContract contract) {
        ContractConfig.Pool pool = contract.weekly()
                ? ContractConfig.config().weekly()
                : ContractConfig.config().daily();

        long money = rewardFor(player, contract);
        long balance = Economy.credit(player, money);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 1.0f);
        player.sendSystemMessage(Component.literal("Contrat termine — ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(describe(contract)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  +" + MoneyFormat.exact(money))
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  · solde " + MoneyFormat.shortForm(balance))
                        .withStyle(ChatFormatting.DARK_GRAY)));

        grantGlobalXp(player, pool.globalXp(), "contrat termine");
    }

    /**
     * L'item en demande aujourd'hui, ou {@code null}.
     *
     * <p>Seule source de verite : la donnee stockee ne vaut que pour la periode
     * ou elle a ete tiree.
     */
    public static Identifier demandItem(ServerPlayer player) {
        ContractData data = data(player);
        return data.demandPeriod() == today() ? data.rawDemandItem() : null;
    }

    /** Vrai si le bonus de premiere vente n'a pas encore servi aujourd'hui. */
    public static boolean firstSaleAvailable(ServerPlayer player) {
        return data(player).firstSalePeriod() != today();
    }

    public static void consumeFirstSale(ServerPlayer player) {
        data(player).setFirstSalePeriod(today());
    }

    /** Tire la demande du jour si celle du fichier n'est plus celle du jour. */
    public static void refreshDemand(ServerPlayer player) {
        ContractData data = data(player);
        if (demandItem(player) != null) {
            return;
        }

        ContractConfig.Demand demand = ContractConfig.config().demand();
        List<Identifier> candidates = new ArrayList<>();
        Prices.book().items().forEach((id, entry) -> {
            if (demand.eligibleTiers().stream().anyMatch(t -> t.equalsIgnoreCase(entry.tier()))
                    && entry.sellable()) {
                candidates.add(id);
            }
        });
        if (candidates.isEmpty()) {
            return;
        }

        Identifier chosen = candidates.get(player.getRandom().nextInt(candidates.size()));
        data.setDemand(chosen, today());

        Item item = BuiltInRegistries.ITEM.getOptional(chosen).orElse(null);
        Component name = item == null
                ? Component.literal(chosen.getPath())
                : new ItemStack(item).getHoverName();
        player.sendSystemMessage(Component.literal("Demande du jour : ")
                .withStyle(ChatFormatting.AQUA)
                .append(name.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  x" + demand.multiplier() + " a la vente")
                        .withStyle(ChatFormatting.GOLD)));
    }

    /**
     * Intitule d'un contrat, pour le chat.
     *
     * <p>Delegue a {@link ContractDisplay} : le chat et l'interface doivent
     * nommer un contrat de la meme facon, sinon le joueur croit a deux contrats
     * differents.
     */
    public static String describe(ActiveContract contract) {
        return ContractDisplay.label(contract)
                + "  [" + MoneyFormat.exact(contract.progress()) + " / "
                + MoneyFormat.exact(contract.amount()) + "]";
    }

    public static PriceEntry entryOf(Identifier id) {
        return Prices.book().entry(id).orElse(null);
    }
}
