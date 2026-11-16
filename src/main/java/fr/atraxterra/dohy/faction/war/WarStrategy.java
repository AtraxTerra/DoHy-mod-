package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.absence.AbsenceJournal;
import fr.atraxterra.dohy.faction.base.EnemyBaseTier;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'IA strategique des nations rivales, et la diplomatie.
 *
 * <p><b>Ce que chaque nation pese</b>, a chacune de ses reflexions (toutes les
 * demi-heures que tu passes en ligne, decalees d'une nation a l'autre) :
 *
 * <ul>
 *   <li>l'attrait de ta banque, selon son avidite (le butin grandit comme le
 *       logarithme de la banque : dix fois plus d'argent ne fait pas dix fois
 *       plus envie) ;</li>
 *   <li>ta croissance — tes claims —, qui fait de toi un rival a abattre pour
 *       une nation belliqueuse ;</li>
 *   <li>ses griefs : ce que tu lui as pris, ses soldats tombes chez toi, un
 *       tribut refuse. Ils s'effacent avec les jours, lentement chez les
 *       rancuniers ;</li>
 *   <li>sa confiance, la taille de son armee ;</li>
 *   <li>et ce qui la retient : tes defenses telles qu'elle les a vues a son
 *       dernier assaut, ta force (armure, vie), et la crainte que tu lui
 *       inspires — d'autant plus qu'elle est prudente.</li>
 * </ul>
 *
 * <p>De quoi tirer une humeur cible ; son hostilite s'en approche par tiers,
 * pas d'un coup. Au-dela de 58, avec une armee et de quoi la payer, elle passe
 * a l'acte : un ultimatum (un tribut, sinon la guerre) si elle a de l'honneur
 * ou si tu as deja paye, une declaration de guerre directe sinon.
 *
 * <p><b>Son economie</b> tourne meme quand tu n'es pas la : son tresor grandit
 * de ses terres (les pays qu'elle tient encore sur la carte de la Terre), son
 * armee se reconstitue en recrutant. Les pertes d'un assaut se paient
 * longtemps.
 *
 * <p><b>Jamais en ton absence</b> : un ultimatum qui expire hors ligne est
 * prolonge, un assaut dont l'heure passe hors ligne part cinq minutes apres
 * ton retour.
 *
 * <p><b>La reconquete</b> : un assaut reussi (le tresorier rentre au camp
 * avec ta banque) rend aussi a la nation le dernier pays que tu lui as pris
 * sur la carte de la Terre ({@link EarthNations#reclaim}). Ce pays en jeu est
 * annonce a l'ultimatum, a la declaration et au depart de l'armee ; celui de
 * ta capitale ne l'est jamais.
 *
 * <p><b>Les missiles</b> : devant une base imprenable, une nation qui en a les
 * moyens (et NTM NEXT) tire un missile au lieu de lever le siege
 * ({@link Strikes}). Le terrain revient cinq heures apres l'impact ; tes
 * conteneurs et tes machines sont epargnes.
 */
public final class WarStrategy {

    static final long THINK_MS = 30L * 60_000L;
    static final long ULTIMATUM_MS = 24L * 3_600_000L;
    static final long WINDOW_OPEN_MS = 30L * 60_000L;
    static final long WINDOW_MS = 48L * 3_600_000L;
    static final long CHOOSE_MS = 24L * 3_600_000L;
    static final long RETURN_DELAY_MS = 5L * 60_000L;
    static final long DAY_MS = 86_400_000L;
    /** Une faction plus pauvre n'interesse personne. */
    static final long MIN_BANK = 2_000L;
    static final double MENACE_AT = 58.0d;
    /** Apres une guerre, un jour de calme avant qu'une autre nation ne s'en prenne a toi. */
    static final long CALM_MS = DAY_MS;
    /** Ce que coute un soldat engage dans un assaut, a sa nation. */
    static final long SOLDIER_COST = 120L;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAYS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    /** Les rappels deja donnes pour l'assaut fixe : 30, 10 et 1 minute avant. */
    private static final Map<UUID, Integer> REMINDED = new HashMap<>();

    private WarStrategy() {
    }

    static WarData data(ServerPlayer player) {
        return player.getData(Factions.WAR.get());
    }

    // -- Le temps ----------------------------------------------------------------------------------

    /** Chaque seconde, pour chaque joueur en ligne qui a une faction. */
    static void tick(ServerPlayer player, long now) {
        if (!FactionApi.has(player)) {
            return;
        }
        WarData data = data(player);
        for (int nation = 0; nation < NationTraits.count(); nation++) {
            data.relation(nation, now);
        }
        data.seen = now;
        Siege siege = Sieges.of(player.getUUID());
        for (WarData.Relation relation : data.relations) {
            switch (relation.stance) {
                case MENACE -> {
                    if (now >= relation.deadline) {
                        relation.grieve("silence", 6.0d, now);
                        declare(player, data, relation, now, true);
                    }
                }
                case GUERRE -> {
                    if (relation.slot == 0L && siege == null && now >= relation.since + CHOOSE_MS) {
                        relation.slot = Math.max(now + RETURN_DELAY_MS, relation.deadline);
                        say(player, relation, "Tu n'as pas choisi : nous viendrons " + when(relation.slot, now) + ".",
                                ChatFormatting.RED);
                    }
                    if (relation.slot > 0L && siege == null) {
                        remind(player, relation, now);
                        if (now >= relation.slot) {
                            launch(player, data, relation, now);
                            siege = Sieges.of(player.getUUID());
                        }
                    }
                }
                case TREVE -> {
                    if (now >= relation.truce) {
                        relation.stance(WarData.Stance.PAIX, now);
                        say(player, relation, "La treve prend fin.", ChatFormatting.GRAY);
                    }
                }
                default -> {
                }
            }
        }
        // Une seule reflexion par seconde : les nations ne pensent pas toutes au meme instant.
        if (siege == null) {
            for (WarData.Relation relation : data.relations) {
                if (now - relation.thought >= THINK_MS) {
                    think(player, data, relation, now);
                    break;
                }
            }
        }
    }

    private static void remind(ServerPlayer player, WarData.Relation relation, long now) {
        long left = relation.slot - now;
        int stage = left <= 60_000L ? 3 : left <= 10L * 60_000L ? 2 : left <= 30L * 60_000L ? 1 : 0;
        int done = REMINDED.getOrDefault(player.getUUID(), 0);
        if (stage > done) {
            REMINDED.put(player.getUUID(), stage);
            String text = switch (stage) {
                case 1 -> "L'assaut part dans 30 minutes.";
                case 2 -> "L'assaut part dans 10 minutes. Garnis tes murs.";
                default -> "L'assaut part dans une minute !";
            };
            say(player, relation, text, ChatFormatting.RED);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f,
                    stage == 3 ? 0.6f : 1.0f);
        }
    }

    /** A la connexion : l'economie des nations a tourne, et rien ne s'est passe sans toi. */
    static void login(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            return;
        }
        WarData data = data(player);
        long now = System.currentTimeMillis();
        REMINDED.remove(player.getUUID());
        if (data.refund > 0L && Sieges.of(player.getUUID()) == null) {
            long amount = data.refund;
            data.refund = 0L;
            FactionApi.creditBank(player, amount, "Butin d'un siege interrompu");
            player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Un siege a ete coupe par un arret "
                    + "du serveur : " + MoneyFormat.exact(amount) + " $ reviennent a ta banque.")
                    .withStyle(ChatFormatting.GREEN)));
        }
        if (data.seen <= 0L) {
            data.seen = now;
            return;
        }
        double hours = Math.min(14.0d * 24.0d, Math.max(0L, now - data.seen) / 3_600_000.0d);
        MinecraftServer server = player.level().getServer();
        for (WarData.Relation relation : data.relations) {
            economy(server, relation, hours);
            relation.thought = Math.max(relation.thought, now - THINK_MS + 5L * 60_000L);
            switch (relation.stance) {
                case MENACE -> {
                    if (relation.deadline < now + 2L * 3_600_000L) {
                        relation.deadline = now + 2L * 3_600_000L;
                        say(player, relation, "Nous attendions ta reponse. Il te reste deux heures.",
                                ChatFormatting.RED);
                    }
                }
                case GUERRE -> {
                    if (relation.slot > 0L && relation.slot < now + RETURN_DELAY_MS) {
                        relation.slot = now + RETURN_DELAY_MS;
                        say(player, relation, "Tu etais absent a l'heure dite. Nous attaquons dans 5 minutes.",
                                ChatFormatting.DARK_RED);
                    } else if (relation.slot == 0L) {
                        say(player, relation, "La guerre continue : choisis l'heure de l'assaut.", ChatFormatting.RED);
                    }
                    if (relation.deadline < now + WINDOW_OPEN_MS) {
                        relation.deadline = now + CHOOSE_MS;
                    }
                }
                default -> {
                }
            }
        }
        data.seen = now;
    }

    /** Le tresor et l'armee d'une nation, apres {@code hours} heures. */
    private static void economy(MinecraftServer server, WarData.Relation relation, double hours) {
        if (hours <= 0.0d) {
            return;
        }
        NationTraits traits = relation.traits();
        double share = EarthNations.active() ? EarthNations.rivalShares(server)[relation.nation] : 1.0d;
        double income = traits.treasury() * 0.01d * (0.5d + share);
        relation.treasury = Math.min(traits.treasury() * 10L, relation.treasury + Math.round(income * hours));
        int room = traits.maxArmy() - relation.army;
        if (room > 0) {
            int recruits = (int) Math.min(room, Math.floor(hours * (1.5d + 3.0d * traits.aggression())));
            long cost = recruits * 80L;
            if (recruits > 0 && relation.treasury >= cost) {
                relation.army += recruits;
                relation.treasury -= cost;
            }
        }
        relation.fear = 20.0d + (relation.fear - 20.0d) * Math.exp(-hours / 72.0d);
    }

    // -- La reflexion ------------------------------------------------------------------------------

    /** Ta force, de 0 (sans armure) a 1 (armure complete et solide, vie pleine). */
    static double power(ServerPlayer player) {
        double armor = Math.min(1.0d, player.getArmorValue() / 20.0d);
        double toughness = Math.min(1.0d, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) / 12.0d);
        double health = Math.min(1.0d, player.getMaxHealth() / 40.0d);
        return 0.55d * armor + 0.25d * toughness + 0.2d * health;
    }

    static long expedition(WarData.Relation relation) {
        return force(relation) * SOLDIER_COST;
    }

    /** Les soldats qu'elle engagerait : une part de son armee, plus grande chez les belliqueux. */
    static int force(WarData.Relation relation) {
        NationTraits traits = relation.traits();
        return (int) Math.max(8, Math.min(24, Math.round(relation.army * (0.35d + 0.3d * traits.aggression()))));
    }

    /** L'humeur qu'elle tend a avoir envers toi aujourd'hui. */
    static double mood(ServerPlayer player, WarData data, WarData.Relation relation, long now) {
        NationTraits traits = relation.traits();
        FactionData faction = FactionApi.data(player);
        double bank = Math.max(0L, faction.bank());
        double prize = traits.greed() * Math.log10(1.0d + bank / 1000.0d) * 18.0d;
        double envy = traits.aggression() * Math.min(15.0d, faction.claims().size() * 0.8d);
        double revenge = traits.grudge() * Math.min(40.0d, relation.grievance(now));
        double nerve = relation.army / (double) Math.max(1, traits.maxArmy()) * 12.0d - 6.0d;
        double deterrence = traits.caution() * (relation.defense * 25.0d + power(player) * 15.0d)
                + relation.fear * 0.35d * (1.0d - traits.aggression() * 0.5d);
        double base = 18.0d + 25.0d * traits.aggression();
        return Math.max(0.0d, Math.min(100.0d, base + prize + envy + revenge + nerve - deterrence));
    }

    static void think(ServerPlayer player, WarData data, WarData.Relation relation, long now) {
        MinecraftServer server = player.level().getServer();
        double hours = Math.max(0L, now - relation.thought) / 3_600_000.0d;
        relation.thought = now;
        economy(server, relation, Math.min(hours, 2.0d));
        NationTraits traits = relation.traits();
        double target = mood(player, data, relation, now);
        double noise = (player.getRandom().nextDouble() - 0.5d) * 8.0d;
        relation.hostility = Math.max(0.0d, Math.min(100.0d,
                relation.hostility + (target + noise - relation.hostility) * 0.33d));
        if (relation.stance != WarData.Stance.PAIX) {
            if (relation.stance == WarData.Stance.GUERRE && relation.slot == 0L && relation.hostility < 28.0d) {
                relation.stance(WarData.Stance.TREVE, now);
                relation.truce = now + 2L * DAY_MS;
                say(player, relation, "Cette guerre ne vaut plus la peine. Nous la suspendons.", ChatFormatting.GREEN);
                log(player, data, relation, now, traits.verb("renonce", "renoncent") + " a la guerre");
            }
            return;
        }
        long bank = FactionApi.data(player).bank();
        boolean calm = data.relations.stream().anyMatch(other -> other.stance == WarData.Stance.TREVE
                && now - other.since < CALM_MS);
        boolean busy = calm || data.conflict().isPresent() || Sieges.of(player.getUUID()) != null;
        if (busy || bank < MIN_BANK || relation.hostility < MENACE_AT || relation.army < 10
                || relation.treasury < expedition(relation)) {
            return;
        }
        if (traits.honor() >= 0.45d || relation.memory.paid() > 0L) {
            ultimatum(player, data, relation, now);
        } else {
            declare(player, data, relation, now, false);
        }
    }

    // -- La diplomatie -----------------------------------------------------------------------------

    private static long round(long amount) {
        return Math.max(100L, (amount + 50L) / 100L * 100L);
    }

    static void ultimatum(ServerPlayer player, WarData data, WarData.Relation relation, long now) {
        NationTraits traits = relation.traits();
        long bank = FactionApi.data(player).bank();
        long tribute = round(Math.max(500L, Math.round(bank * (0.08d + 0.17d * traits.greed()))));
        relation.stance(WarData.Stance.MENACE, now);
        relation.tribute = tribute;
        relation.deadline = now + ULTIMATUM_MS;
        String sum = MoneyFormat.exact(tribute) + " $";
        String limit = when(relation.deadline, now);
        String text = switch (traits.voice()) {
            case "brutale" -> "Ta faction grossit trop pres de nos terres. " + sum + " de tribut avant " + limit
                    + ", ou nous venons les prendre nous-memes.";
            case "courtoise" -> "Nous preferons la paix. Un tribut de " + sum + " avant " + limit
                    + " la garantira ; a defaut, nous serions contraints a la guerre.";
            case "mielleuse" -> "Une si belle banque merite protection... " + sum + " avant " + limit
                    + ", et nous veillerons sur toi. Sinon, qui sait ?";
            default -> sum + " avant " + limit + ". Il n'y aura pas d'autre avertissement.";
        };
        say(player, relation, text, ChatFormatting.RED);
        player.sendSystemMessage(Component.literal("   ")
                .append(button("[Payer " + sum + "]", "/guerre payer", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(button("[Negocier]", "/guerre negocier", ChatFormatting.YELLOW))
                .append(Component.literal(" "))
                .append(button("[Refuser]", "/guerre refuser", ChatFormatting.RED))
                .append(Component.literal(" "))
                .append(button("[Details]", "/guerre", ChatFormatting.GRAY)));
        stakeLine(player, relation);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE,
                0.6f, 1.4f);
        log(player, data, relation, now, traits.verb("exige", "exigent") + " " + sum + " de tribut");
        DoHy.LOGGER.info("Guerre : {} menace {} (hostilite {}, tribut {})", traits.name(),
                player.getGameProfile().name(), Math.round(relation.hostility), tribute);
    }

    static void declare(ServerPlayer player, WarData data, WarData.Relation relation, long now, boolean warned) {
        NationTraits traits = relation.traits();
        relation.stance(WarData.Stance.GUERRE, now);
        relation.deadline = now + WINDOW_MS;
        relation.slot = 0L;
        relation.hostility = Math.min(100.0d, relation.hostility + 5.0d);
        String text = switch (traits.voice()) {
            case "brutale" -> "C'est la guerre ! Choisis l'heure de l'assaut, nous viendrons.";
            case "courtoise" -> "Nous te declarons la guerre, a regret. Fixe l'heure de la bataille : nous la respecterons.";
            case "mielleuse" -> "La guerre, donc. Choisis ton heure... on ne voudrait pas te prendre au depourvu.";
            default -> "La guerre est declaree. Fixe l'heure de l'assaut.";
        };
        say(player, relation, (warned ? "" : "Sans sommation : ") + text, ChatFormatting.DARK_RED);
        player.sendSystemMessage(Component.literal("   ")
                .append(button("[Choisir l'heure]", "/guerre", ChatFormatting.GOLD))
                .append(Component.literal(" Entre " + when(now + WINDOW_OPEN_MS, now) + " et "
                        + when(relation.deadline, now) + ".").withStyle(ChatFormatting.GRAY)));
        stakeLine(player, relation);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE,
                1.0f, 0.8f);
        log(player, data, relation, now, traits.verb("declare", "declarent") + " la guerre");
        DoHy.LOGGER.info("Guerre : {} declare la guerre a {}", traits.name(), player.getGameProfile().name());
    }

    /** Payer le tribut exige. */
    static String pay(ServerPlayer player) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.conflict().filter(r -> r.stance == WarData.Stance.MENACE).orElse(null);
        if (relation == null) {
            return "Personne ne te reclame de tribut";
        }
        long tribute = relation.tribute;
        if (!FactionApi.payFromBank(player, tribute, "Tribut " + relation.traits().de())) {
            return "Ta banque de faction n'a que " + MoneyFormat.exact(FactionApi.data(player).bank()) + " $";
        }
        NationTraits traits = relation.traits();
        relation.memory = relation.memory.withPaid(tribute);
        relation.hostility = Math.max(0.0d, relation.hostility - (35.0d + 15.0d * traits.honor()));
        relation.treasury += tribute;
        relation.stance(WarData.Stance.TREVE, now);
        relation.truce = now + Math.round((3.0d + 4.0d * traits.honor()) * DAY_MS);
        say(player, relation, traits.honor() >= 0.6d ? "Tribut recu. Nous tiendrons parole : la paix jusqu'a "
                + when(relation.truce, now) + "." : "Sage decision. Pour l'instant.", ChatFormatting.GREEN);
        log(player, data, relation, now, "tribut de " + MoneyFormat.exact(tribute) + " $ paye");
        return null;
    }

    /** Refuser le tribut : la guerre. */
    static String refuse(ServerPlayer player) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.conflict().filter(r -> r.stance == WarData.Stance.MENACE).orElse(null);
        if (relation == null) {
            return "Personne ne te reclame de tribut";
        }
        relation.grieve("tribut refuse", 12.0d, now);
        declare(player, data, relation, now, true);
        return null;
    }

    /** Proposer la moitie : une nation peu avide, ou qui te craint, accepte plus volontiers. */
    static String negotiate(ServerPlayer player) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.conflict().filter(r -> r.stance == WarData.Stance.MENACE).orElse(null);
        if (relation == null) {
            return "Personne ne te reclame de tribut";
        }
        if (relation.negotiated) {
            return relation.traits().name() + " a deja repondu a ta contre-offre";
        }
        relation.negotiated = true;
        NationTraits traits = relation.traits();
        double chance = Math.max(0.05d, Math.min(0.85d, 0.2d + 0.45d * (1.0d - traits.greed())
                + 0.35d * relation.fear / 100.0d + 0.15d * traits.honor() - (relation.hostility - 60.0d) / 120.0d));
        if (player.getRandom().nextDouble() < chance) {
            relation.tribute = round(relation.tribute / 2L);
            relation.deadline = Math.max(relation.deadline, now + 2L * 3_600_000L);
            say(player, relation, "Soit. " + MoneyFormat.exact(relation.tribute) + " $, et n'en parlons plus.",
                    ChatFormatting.YELLOW);
            player.sendSystemMessage(Component.literal("   ").append(button("[Payer "
                    + MoneyFormat.exact(relation.tribute) + " $]", "/guerre payer", ChatFormatting.GREEN)));
        } else {
            relation.hostility = Math.min(100.0d, relation.hostility + 4.0d);
            long left = relation.deadline - now;
            relation.deadline = now + Math.max(3_600_000L, left * 3L / 4L);
            say(player, relation, "Tu marchandes ? Le prix reste le meme, et notre patience raccourcit : "
                    + when(relation.deadline, now) + ".", ChatFormatting.RED);
        }
        return null;
    }

    /** Le prix de la paix pendant une guerre : une part de ta banque, plus forte chez les avides. */
    static long peacePrice(ServerPlayer player, WarData.Relation relation) {
        NationTraits traits = relation.traits();
        long bank = FactionApi.data(player).bank();
        return round(Math.max(1_000L, Math.round(bank * (0.10d + 0.15d * traits.greed())
                * (0.6d + relation.hostility / 100.0d))));
    }

    /** Les rancuniers tres hostiles ne veulent pas d'argent : ils veulent l'assaut. */
    static boolean refusesPeace(WarData.Relation relation, long now) {
        NationTraits traits = relation.traits();
        return traits.grudge() * relation.hostility / 100.0d > 0.62d || relation.grievance(now) > 45.0d;
    }

    static String peace(ServerPlayer player) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.conflict().filter(r -> r.stance == WarData.Stance.GUERRE).orElse(null);
        if (relation == null) {
            return "Tu n'es en guerre avec personne";
        }
        if (Sieges.of(player.getUUID()) != null) {
            return "On ne negocie pas pendant un assaut";
        }
        if (refusesPeace(relation, now)) {
            relation.hostility = Math.min(100.0d, relation.hostility + 3.0d);
            say(player, relation, "Pas de paix. Pas avec toi.", ChatFormatting.DARK_RED);
            return null;
        }
        long price = peacePrice(player, relation);
        if (!FactionApi.payFromBank(player, price, "Paix avec " + relation.traits().name())) {
            return "La paix coute " + MoneyFormat.exact(price) + " $ : ta banque n'en a que "
                    + MoneyFormat.exact(FactionApi.data(player).bank());
        }
        NationTraits traits = relation.traits();
        relation.memory = relation.memory.withPaid(price);
        relation.treasury += price;
        relation.hostility = Math.max(0.0d, relation.hostility - 30.0d);
        relation.stance(WarData.Stance.TREVE, now);
        relation.truce = now + Math.round((2.0d + 3.0d * traits.honor()) * DAY_MS);
        say(player, relation, "Marche conclu. La paix jusqu'a " + when(relation.truce, now) + ".", ChatFormatting.GREEN);
        log(player, data, relation, now, "paix achetee " + MoneyFormat.exact(price) + " $");
        return null;
    }

    /** Un cadeau adoucit une nation ; le meme cadeau compte moins pour un joueur riche. */
    static String gift(ServerPlayer player, int nation, long amount) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        if (amount < 100L) {
            return "Un cadeau de 100 $ au moins";
        }
        WarData.Relation relation = data.relation(nation, now);
        if (relation.stance == WarData.Stance.GUERRE) {
            return "En guerre, un cadeau passe pour de la faiblesse : demande la paix";
        }
        if (!FactionApi.payFromBank(player, amount, "Cadeau " + EarthNations.a(relation.traits().name()))) {
            return "Ta banque de faction n'a que " + MoneyFormat.exact(FactionApi.data(player).bank()) + " $";
        }
        long bank = Math.max(0L, FactionApi.data(player).bank());
        double effect = Math.min(25.0d, amount / (bank / 20.0d + 500.0d) * 10.0d);
        relation.hostility = Math.max(0.0d, relation.hostility - effect);
        relation.memory = relation.memory.withPaid(amount);
        relation.treasury += amount;
        say(player, relation, effect >= 10.0d ? "Voila qui nous rapproche." : "Nous en prenons note.",
                ChatFormatting.GREEN);
        log(player, data, relation, now, "cadeau de " + MoneyFormat.exact(amount) + " $");
        return null;
    }

    // -- L'heure de l'assaut -----------------------------------------------------------------------

    /** Les heures qu'on peut choisir : la fenetre ouverte par la declaration. */
    static List<Long> slots(WarData.Relation relation, long now) {
        List<Long> out = new ArrayList<>();
        long from = Math.max(now, relation.since) + WINDOW_OPEN_MS;
        long to = relation.deadline;
        for (long delay : new long[] {0L, 30L, 90L, 210L}) {
            long at = from + delay * 60_000L;
            if (at <= to) {
                out.add(at - at % 60_000L);
            }
        }
        ZonedDateTime base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), RealClock.zone());
        for (int day = 0; day <= 2; day++) {
            for (int hour : new int[] {18, 21}) {
                long at = base.toLocalDate().plusDays(day).atTime(hour, 0).atZone(RealClock.zone())
                        .toInstant().toEpochMilli();
                if (at >= from && at <= to && out.stream().noneMatch(t -> Math.abs(t - at) < 30L * 60_000L)) {
                    out.add(at);
                }
            }
        }
        out.sort(Long::compare);
        return out.size() > 9 ? out.subList(0, 9) : out;
    }

    static String choose(ServerPlayer player, long at) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.conflict().filter(r -> r.stance == WarData.Stance.GUERRE).orElse(null);
        if (relation == null) {
            return "Tu n'es en guerre avec personne";
        }
        if (Sieges.of(player.getUUID()) != null) {
            return "L'assaut a deja commence";
        }
        long from = Math.max(now, relation.since) + WINDOW_OPEN_MS - 60_000L;
        if (at < from || at > relation.deadline) {
            return "Choisis entre " + when(from + 60_000L, now) + " et " + when(relation.deadline, now);
        }
        relation.slot = at;
        REMINDED.remove(player.getUUID());
        say(player, relation, "Entendu : nous viendrons " + when(at, now) + ".", ChatFormatting.RED);
        log(player, data, relation, now, "assaut fixe " + when(at, now));
        return null;
    }

    /** Lit « 21:00 », « 21h », « demain 18:30 », « apres-demain 21 ». */
    static long parse(String text, long now) {
        String raw = text.trim().toLowerCase(java.util.Locale.ROOT);
        int days = 0;
        if (raw.startsWith("apres-demain") || raw.startsWith("apresdemain")) {
            days = 2;
            raw = raw.substring(raw.indexOf("demain") + 6).trim();
        } else if (raw.startsWith("demain")) {
            days = 1;
            raw = raw.substring(6).trim();
        }
        raw = raw.replace('h', ':');
        String[] parts = raw.split(":");
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = parts.length > 1 && !parts[1].isBlank() ? Integer.parseInt(parts[1].trim()) : 0;
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1L;
            }
            LocalDate date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), RealClock.zone()).toLocalDate()
                    .plusDays(days);
            long at = date.atTime(hour, minute).atZone(RealClock.zone()).toInstant().toEpochMilli();
            if (days == 0 && at < now) {
                at += DAY_MS;
            }
            return at;
        } catch (NumberFormatException invalid) {
            return -1L;
        }
    }

    // -- L'assaut ----------------------------------------------------------------------------------

    /** Ou est ton coffre : celui que tu as designe, sinon le cœur de ton claim le plus garni. */
    static Optional<Map.Entry<ServerLevel, BlockPos>> vault(ServerPlayer player, WarData data) {
        MinecraftServer server = player.level().getServer();
        if (data.vault.isPresent()) {
            WarData.Vault vault = data.vault.get();
            for (ServerLevel level : server.getAllLevels()) {
                if (FactionApi.dimension(level).equals(vault.dimension())
                        && FactionApi.claimAt(level, vault.pos()).ownedBy(player.getUUID())) {
                    return Optional.of(Map.entry(level, vault.pos()));
                }
            }
        }
        FactionData faction = FactionApi.data(player);
        ServerLevel best = null;
        BlockPos center = null;
        int most = -1;
        for (FactionData.Claim claim : faction.claims()) {
            for (ServerLevel level : server.getAllLevels()) {
                if (!FactionApi.dimension(level).equals(claim.dimension())) {
                    continue;
                }
                var chunk = level.getChunkSource().getChunkNow(claim.x(), claim.z());
                int count = chunk == null ? 0 : chunk.getBlockEntities().size();
                if (count > most) {
                    most = count;
                    best = level;
                    int x = claim.x() * 16 + 8;
                    int z = claim.z() * 16 + 8;
                    center = new BlockPos(x, 0, z);
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        best.getChunk(center.getX() >> 4, center.getZ() >> 4);
        int y = best.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        return Optional.of(Map.entry(best, new BlockPos(center.getX(), y, center.getZ())));
    }

    static String setVault(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            return "Tu n'as pas de faction";
        }
        if (Sieges.of(player.getUUID()) != null || Strikes.striking(player.getUUID())) {
            return "On ne deplace pas son coffre en plein siege";
        }
        if (!FactionApi.claimAt(player.level(), player.blockPosition()).ownedBy(player.getUUID())) {
            return "Ton coffre se pose dans un de tes claims";
        }
        WarData data = data(player);
        data.vault = Optional.of(new WarData.Vault(FactionApi.dimension(player.level()), player.blockPosition()));
        return null;
    }

    /** L'heure est venue : l'armee marche. */
    static void launch(ServerPlayer player, WarData data, WarData.Relation relation, long now) {
        if (Sieges.of(player.getUUID()) != null) {
            return;
        }
        relation.slot = 0L;
        Optional<Map.Entry<ServerLevel, BlockPos>> vault = vault(player, data);
        if (vault.isEmpty()) {
            relation.stance(WarData.Stance.TREVE, now);
            relation.truce = now + DAY_MS;
            say(player, relation, "Rien a piller chez toi : nous rentrons.", ChatFormatting.GRAY);
            return;
        }
        int force = force(relation);
        relation.treasury = Math.max(0L, relation.treasury - force * SOLDIER_COST);
        Sieges.start(player, relation, vault.get().getKey(), vault.get().getValue(), force, now);
        int country = stake(player, relation.nation);
        say(player, relation, "Notre armee marche sur ta base : " + force + " soldats."
                + (country > 0 ? " Si nous l'emportons, " + EarthNations.countryName(country) + " nous reviendra." : ""),
                ChatFormatting.DARK_RED);
        log(player, data, relation, now, "assaut lance (" + force + " soldats)");
    }

    /** Le siege est fini : la nation en tire ses lecons. */
    static void concluded(Siege siege) {
        ServerPlayer player = siege.target();
        if (player == null) {
            return;
        }
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.relation(siege.nation, now);
        NationTraits traits = relation.traits();
        data.assaults++;
        relation.army = Math.max(0, relation.army - siege.fallen);
        relation.memory = relation.memory.withKilled(siege.fallen);
        if (siege.plan != null) {
            relation.defense = relation.defense * 0.3d + siege.plan.hardness() * 0.7d;
        }
        String line;
        switch (siege.result) {
            case "pillage" -> {
                relation.memory = relation.memory.withWin(siege.drained);
                relation.treasury += siege.drained;
                relation.hostility = Math.max(0.0d, relation.hostility - 15.0d);
                relation.fear = Math.max(0.0d, relation.fear - 10.0d);
                relation.stance(WarData.Stance.TREVE, now);
                relation.truce = now + Math.round((4.0d - traits.greed() * 2.0d) * DAY_MS);
                data.refund = Math.max(0L, data.refund - siege.drained);
                line = "assaut reussi : " + MoneyFormat.exact(siege.drained) + " $ pilles";
                String retaken = reconquer(player, relation);
                if (!retaken.isEmpty()) {
                    line += " ; " + retaken;
                }
            }
            case "imprenable" -> {
                relation.defense = 1.0d;
                Strike strike = Strikes.plan(player, relation, siege.level, siege.vault);
                if (strike != null) {
                    // Pas d'entree : elle bombarde. La treve tient le temps du tir, puis struck() la fixe.
                    relation.treasury = Math.max(0L, relation.treasury - strike.cost);
                    relation.memory = relation.memory.withStrike();
                    relation.stance(WarData.Stance.TREVE, now);
                    relation.truce = now + DAY_MS;
                    say(player, relation, bombard(traits, strike.warhead), ChatFormatting.DARK_RED);
                    line = "base imprenable : tir (" + strike.warhead.label() + ")";
                } else {
                    relation.fear = Math.min(100.0d, relation.fear + 20.0d);
                    relation.hostility = Math.max(0.0d, relation.hostility - 10.0d);
                    relation.stance(WarData.Stance.TREVE, now);
                    relation.truce = now + 7L * DAY_MS;
                    say(player, relation, "Ta base est imprenable. Nous levons le siege.", ChatFormatting.GREEN);
                    line = "siege leve : base imprenable";
                }
            }
            case "suspendu" -> {
                relation.stance(WarData.Stance.GUERRE, relation.since);
                relation.slot = 0L;
                relation.deadline = now + WINDOW_MS;
                relation.hostility = Math.min(100.0d, relation.hostility + 5.0d);
                line = "assaut interrompu";
            }
            default -> {
                relation.memory = relation.memory.withLoss();
                double fear = 12.0d + (siege.commanderDead ? 10.0d : 0.0d) + (siege.paymasterDead ? 8.0d : 0.0d)
                        + (siege.bannerDown ? 6.0d : 0.0d);
                relation.fear = Math.min(100.0d, relation.fear + fear);
                relation.grieve("soldats tombes", Math.min(30.0d, siege.fallen * 1.2d), now);
                relation.hostility = Math.max(0.0d, relation.hostility - 20.0d + 12.0d * traits.grudge());
                relation.stance(WarData.Stance.TREVE, now);
                relation.truce = now + Math.round((4.0d + 3.0d * traits.caution()) * DAY_MS);
                long reparations = 0L;
                if (traits.honor() >= 0.6d && (siege.commanderDead || "deroute".equals(siege.result))) {
                    reparations = Math.min(4_000L, Math.round(relation.treasury * 0.06d));
                    if (reparations > 0L) {
                        relation.treasury -= reparations;
                        FactionApi.creditBank(player, reparations, "Reparations " + traits.de());
                    }
                }
                line = "assaut brise (" + siege.fallen + " soldats tombes)";
                player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Victoire ! L'assaut "
                        + traits.de() + " est brise : " + siege.fallen + " soldat(s) tombe(s)"
                        + (siege.bounty > 0 ? ", " + MoneyFormat.exact(siege.bounty) + " $ de primes" : "")
                        + (reparations > 0 ? ", " + MoneyFormat.exact(reparations) + " $ de reparations" : "")
                        + ".").withStyle(ChatFormatting.GOLD)));
                FactionApi.addElo(player, 40L + siege.fallen * 4L, "assaut brise");
            }
        }
        log(player, data, relation, now, line);
        DoHy.LOGGER.info("Guerre : fin du siege de {} contre {} : {} ({} morts, {} $ pilles)", traits.name(),
                player.getGameProfile().name(), siege.result, siege.fallen, siege.drained);
    }

    /**
     * L'assaut a reussi : la nation reprend le pays en jeu, s'il y en a un, et le
     * grief qu'il portait s'eteint. Rend la ligne du journal (« Pologne repris »),
     * vide si elle n'avait rien a reprendre.
     */
    static String reconquer(ServerPlayer player, WarData.Relation relation) {
        int country = EarthNations.active() ? EarthNations.reclaim(player, relation.nation) : 0;
        if (country <= 0) {
            return "";
        }
        String name = EarthNations.countryName(country);
        String kind = "pays pris : " + name;
        List<WarData.Grievance> kept = new ArrayList<>();
        for (WarData.Grievance grievance : relation.memory.grievances()) {
            if (!grievance.kind().equals(kind)) {
                kept.add(grievance);
            }
        }
        relation.memory = relation.memory.withGrievances(kept);
        String text = switch (relation.traits().voice()) {
            case "brutale" -> name + " est de nouveau a nous. N'y remets plus les pieds.";
            case "courtoise" -> "Nous reprenons " + name + ". Nous en prendrons soin, sois-en sur.";
            case "mielleuse" -> "Merci de nous rendre " + name + "... c'etait si aimable a toi.";
            default -> "Nous reprenons " + name + ".";
        };
        say(player, relation, text, ChatFormatting.DARK_RED);
        return name + " repris";
    }

    /** Ce qu'elle dit en pointant un missile sur ta base. */
    static String bombard(NationTraits traits, NtmArsenal.Warhead warhead) {
        return switch (traits.voice()) {
            case "brutale" -> "Imprenable ? Alors nous la raserons. " + warhead.sentence() + " part vers ta base.";
            case "courtoise" -> "Tes murs resistent a nos soldats : nous allons les bombarder. Mets tes gens a l'abri.";
            case "mielleuse" -> "Une si belle forteresse... ce serait dommage qu'" + warhead.one()
                    + " tombe dessus. Leve les yeux.";
            default -> "Base imprenable. Frappe autorisee : " + warhead.label() + ".";
        };
    }

    /**
     * La frappe est finie (ou n'aura pas lieu) : la nation en tire ses lecons.
     *
     * <ul>
     *   <li>{@code frappe} : sa colere retombe, une treve de deux a quatre jours ;</li>
     *   <li>{@code intercepte} : abattu en vol, elle te craint, une treve plus longue,
     *       et de l'ELO pour toi ;</li>
     *   <li>{@code annule} : tu t'es deconnecte avant l'impact, le missile lui est
     *       rendu et la guerre continue ;</li>
     *   <li>{@code rate} : le tir n'est pas parti, le missile lui est rendu.</li>
     * </ul>
     */
    static void struck(ServerPlayer player, int nation, String result, NtmArsenal.Warhead warhead, long cost) {
        if (!FactionApi.has(player)) {
            return;
        }
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.relation(nation, now);
        NationTraits traits = relation.traits();
        String line;
        switch (result) {
            case "frappe" -> {
                relation.hostility = Math.max(0.0d, relation.hostility - 8.0d - 10.0d * traits.aggression());
                relation.fear = Math.min(100.0d, relation.fear + 5.0d);
                relation.stance(WarData.Stance.TREVE, now);
                relation.truce = now + Math.round((2.0d + 2.0d * traits.caution()) * DAY_MS);
                String text = switch (traits.voice()) {
                    case "brutale" -> "Voila ce qu'il en coute de nous defier.";
                    case "courtoise" -> "Nous regrettons d'en etre arrives la. La treve est a toi, si tu la veux.";
                    case "mielleuse" -> "Oh... ta jolie base. Quel dommage.";
                    default -> "Frappe executee.";
                };
                say(player, relation, text, ChatFormatting.DARK_RED);
                line = "frappe sur ta base (" + warhead.label() + ")";
            }
            case "intercepte" -> {
                relation.fear = Math.min(100.0d, relation.fear + 18.0d);
                relation.hostility = Math.min(100.0d, relation.hostility + 6.0d * traits.grudge());
                relation.stance(WarData.Stance.TREVE, now);
                relation.truce = now + Math.round((4.0d + 3.0d * traits.caution()) * DAY_MS);
                String text = switch (traits.voice()) {
                    case "brutale" -> "Tu as abattu notre missile. Le prochain ne te ratera pas.";
                    case "courtoise" -> "Ta defense antimissile est remarquable. Nous en prenons note.";
                    case "mielleuse" -> "Quelle adresse... tu as abattu notre petit cadeau.";
                    default -> "Missile perdu. Defense antimissile confirmee.";
                };
                say(player, relation, text, ChatFormatting.GRAY);
                player.sendSystemMessage(FactionApi.prefix().append(Component.literal("Missile abattu en vol ! "
                        + traits.verb("recule", "reculent") + ".").withStyle(ChatFormatting.GOLD)));
                FactionApi.addElo(player, 25L, "missile abattu");
                line = "tir abattu en vol (" + warhead.label() + ")";
            }
            case "annule" -> {
                relation.treasury += cost;
                relation.stance(WarData.Stance.GUERRE, now);
                relation.slot = 0L;
                relation.deadline = now + WINDOW_MS;
                relation.hostility = Math.min(100.0d, relation.hostility + 5.0d);
                line = "tir annule";
            }
            default -> {
                relation.treasury += cost;
                relation.stance(WarData.Stance.TREVE, now);
                relation.truce = now + DAY_MS;
                say(player, relation, "Notre missile n'est pas parti. Ce n'est que partie remise.",
                        ChatFormatting.GRAY);
                line = "tir rate";
            }
        }
        log(player, data, relation, now, line);
        DoHy.LOGGER.info("Guerre : frappe {} contre {} : {} ({})", traits.de(), player.getGameProfile().name(),
                result, warhead.label());
    }

    /** Ce que la chute de sa place forte te rapporte : le butin verse a ta banque, les soldats qu'elle perd. */
    record Spoils(long loot, int lost) {
    }

    /**
     * Sa place forte est tombee ({@link Strongholds}) : rasee par tes missiles
     * ({@code missiles}) ou pillee ({@code pillage}). La nation recule : 30 % de
     * son tresor passent a ta banque de faction (10 % de plus partent en
     * fumee), la moitie de son armee tombe, elle te craint, et une treve de
     * sept jours commence. Sur la carte de la Terre, {@code country} (s'il y en
     * a un) passe a ta faction.
     */
    static Spoils razed(ServerPlayer player, int nation, String how, EnemyBaseTier tier, int country) {
        WarData data = data(player);
        long now = System.currentTimeMillis();
        WarData.Relation relation = data.relation(nation, now);
        NationTraits traits = relation.traits();
        if (country > 0) {
            EarthNations.conquer(player, country, EarthNations.holder(player.level().getServer(), country));
        }
        long loot = relation.treasury >= 1_000L ? round(Math.round(relation.treasury * 0.30d)) : 0L;
        long burnt = Math.round(relation.treasury * 0.10d);
        relation.treasury = Math.max(0L, relation.treasury - loot - burnt);
        int lost = (relation.army + 1) / 2;
        relation.army -= lost;
        relation.memory = relation.memory.withLoss().withKilled(lost);
        relation.fear = Math.min(100.0d, relation.fear + 30.0d);
        relation.hostility = Math.max(0.0d, relation.hostility - 15.0d);
        relation.stance(WarData.Stance.TREVE, now);
        relation.truce = now + 7L * DAY_MS;
        relation.slot = 0L;
        if (loot > 0L) {
            FactionApi.creditBank(player, loot, "Butin de la place forte " + traits.de());
        }
        FactionApi.addElo(player, tier.elo(), "place forte " + traits.de());
        FactionApi.stat(player, "war:stronghold", 1);
        boolean pillage = "pillage".equals(how);
        String text = switch (traits.voice()) {
            case "brutale" -> pillage ? "Tu as fouille notre place forte. Profite de la treve : elle ne durera pas."
                    : "Tu as rase notre place forte. Nous reculons... pour cette fois.";
            case "courtoise" -> "Notre place forte est tombee. Nous saluons ta victoire : la treve est a toi.";
            case "mielleuse" -> "Notre jolie place forte... tu n'aurais pas du. Faisons une petite pause, veux-tu ?";
            default -> "Place forte perdue. Retrait. Treve de sept jours.";
        };
        say(player, relation, text, ChatFormatting.GRAY);
        log(player, data, relation, now, "place forte " + (pillage ? "pillee" : "rasee") + " : "
                + MoneyFormat.exact(loot) + " $ de butin, " + lost + " soldats perdus"
                + (country > 0 ? ", " + EarthNations.countryName(country) + " perdu" : ""));
        DoHy.LOGGER.info("Guerre : place forte {} {} par {} : {} $ de butin, {} soldats perdus, pays {}", traits.de(),
                pillage ? "pillee" : "rasee", player.getGameProfile().name(), loot, lost, country);
        return new Spoils(loot, lost);
    }

    /** Une partie du prix du missile revient a la nation (une charge plus legere que prevu). */
    static void refund(ServerPlayer player, int nation, long amount) {
        WarData.Relation relation = data(player).relation(nation, System.currentTimeMillis());
        relation.treasury += amount;
    }

    /** L'argent en route avec le tresorier : rendu au demarrage si le serveur tombe. */
    static void transit(ServerPlayer player, long delta) {
        WarData data = data(player);
        data.refund = Math.max(0L, data.refund + delta);
    }

    /** Un grief venu d'ailleurs : un pays pris sur la carte de la Terre. */
    public static void grieve(ServerPlayer player, int nation, String kind, double weight) {
        if (nation < 0 || nation >= NationTraits.count() || !FactionApi.has(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        WarData data = data(player);
        WarData.Relation relation = data.relation(nation, now);
        relation.grieve(kind, weight, now);
        relation.hostility = Math.min(100.0d, relation.hostility + weight * relation.traits().grudge() * 0.5d);
        log(player, data, relation, now, "grief : " + kind);
    }

    /** Le pays en jeu face a cette nation : celui qu'elle reprendrait si son assaut reussissait ; 0 sans. */
    static int stake(ServerPlayer player, int nation) {
        return EarthNations.active() ? EarthNations.stake(player, nation) : 0;
    }

    /** La ligne de l'enjeu, sous un ultimatum ou une declaration de guerre. */
    private static void stakeLine(ServerPlayer player, WarData.Relation relation) {
        int country = stake(player, relation.nation);
        if (country > 0) {
            player.sendSystemMessage(Component.literal("   Enjeu : " + EarthNations.countryName(country)
                    + ", repris si leur assaut reussit.").withStyle(ChatFormatting.GOLD));
        }
    }

    // -- Les mots ----------------------------------------------------------------------------------

    static MutableComponent button(String label, String command, ChatFormatting color) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    static void say(ServerPlayer player, WarData.Relation relation, String text, ChatFormatting color) {
        NationTraits traits = relation.traits();
        player.sendSystemMessage(Component.literal("[" + traits.name() + "] ")
                .withStyle(Style.EMPTY.withColor(traits.color().getTextColor()).withBold(true))
                .append(Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(false))));
    }

    private static void log(ServerPlayer player, WarData data, WarData.Relation relation, long now, String text) {
        data.log(now, relation.nation, text);
        AbsenceJournal.post(player.level().getServer(), AbsenceJournal.NATIONS,
                relation.traits().name() + " : " + text, Items.IRON_SWORD);
    }

    /** « dans 45 min », « ce soir 21:00 », « demain 18:00 », « jeudi 21:00 ». */
    static String when(long at, long now) {
        long left = at - now;
        if (left < 0L) {
            return "maintenant";
        }
        if (left < 60L * 60_000L) {
            return "dans " + Math.max(1L, left / 60_000L) + " min";
        }
        ZonedDateTime then = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), RealClock.zone());
        LocalDate today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), RealClock.zone()).toLocalDate();
        String clock = then.format(CLOCK);
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, then.toLocalDate());
        if (days == 0L) {
            return (then.getHour() >= 18 ? "ce soir " : "aujourd'hui ") + clock;
        }
        if (days == 1L) {
            return "demain " + clock;
        }
        if (days == 2L) {
            return "apres-demain " + clock;
        }
        return DAYS[then.getDayOfWeek().getValue() - 1] + " " + clock;
    }
}
