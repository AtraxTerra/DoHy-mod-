package fr.atraxterra.dohy.earth.mine;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthBodies;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionVault;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Les mines de la Terre a capturer : les 245 gisements geants de la carte (mines,
 * cheminees de kimberlite, champs de petrole et de gaz, sables bitumineux).
 *
 * <p><b>La capture, sans combat</b> (choix du joueur) : une balise de concession posee
 * dans la mine, puis rester a moins de {@value #RANGE} blocs d'elle pendant deux a
 * cinq minutes selon le rang de la mine. S'eloigner met la capture en pause ;
 * casser la balise l'abandonne. La mine passe alors a la faction, et la nation de
 * sa region s'en souvient (un grief, comme pour un pays).
 *
 * <p><b>Les revenus</b> : chaque heure de temps reel, hors ligne compris (choix du
 * joueur), une mine verse de l'argent a la banque nationale et des minerais au
 * coffre de la banque ({@link FactionVault}). Une semaine d'absence au plus est
 * rattrapee.
 *
 * <p><b>La mine reste ouverte</b> : la capturer ne pose aucun claim ni aucune
 * protection ; on peut toujours y miner soi-meme.
 *
 * <p><b>La reprise</b> (choix du joueur) : une nation dont l'assaut reussit contre
 * la base reprend la derniere mine capturee dans sa region, comme le pays en jeu.
 */
public final class EarthMines {

    /** Une heure, en millisecondes. */
    static final long HOUR = 3_600_000L;
    /** Le temps de production qu'une mine garde a son proprietaire absent : une semaine. */
    public static final int MAX_HOURS = 168;
    /** A moins de tant de blocs de la balise, la capture avance. */
    public static final int RANGE = 16;

    /** Au-dela, le butin d'un versement est tire sur un echantillon de blocs, puis multiplie. */
    private static final int SAMPLE = 64;

    private static final Map<UUID, Capture> CAPTURES = new HashMap<>();
    /** Le prochain versement de chaque joueur connecte : un par heure, toutes mines ensemble. */
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    /** Une capture en cours : la balise, la mine, ce qui a ete tenu. */
    static final class Capture {
        final UUID player;
        final String mine;
        final ResourceKey<Level> dimension;
        final BlockPos beacon;
        final int need;
        final ServerBossEvent bar;
        int progress;

        Capture(UUID player, String mine, ResourceKey<Level> dimension, BlockPos beacon, int need,
                ServerBossEvent bar) {
            this.player = player;
            this.mine = mine;
            this.dimension = dimension;
            this.beacon = beacon;
            this.need = need;
            this.bar = bar;
        }

        int secondsLeft() {
            return Math.max(0, (need - progress + 19) / 20);
        }
    }

    private EarthMines() {
    }

    // -- Les mines -----------------------------------------------------------------------------

    /** Vrai si ce monde a une carte de la Terre et ses gisements geants. */
    public static boolean active() {
        return EarthData.get() != null && !EarthBodies.all().isEmpty();
    }

    public static EarthBodies.Body body(String id) {
        for (EarthBodies.Body body : EarthBodies.all()) {
            if (body.id().equals(id)) {
                return body;
            }
        }
        return null;
    }

    /** La mine sous ce point : celle dont l'ellipse le contient (la plus proche de son centre s'il y en a deux). */
    public static EarthBodies.Body at(double x, double z) {
        EarthBodies.Body best = null;
        double nearest = 1.0d;
        for (EarthBodies.Body body : EarthBodies.all()) {
            double flat = body.flat(x, z);
            if (flat <= nearest) {
                nearest = flat;
                best = body;
            }
        }
        return best;
    }

    /** Le rang mondial de la mine : 1 pour les plus grandes, 3 pour les plus petites. */
    public static int tier(EarthBodies.Body body) {
        return Math.max(1, Math.min(3, body.tier()));
    }

    /** L'argent qu'elle verse par heure ; un champ de petrole ou de gaz, qui ne rend pas d'objets, double. */
    public static long moneyPerHour(EarthBodies.Body body) {
        long base = switch (tier(body)) {
            case 1 -> 1_500L;
            case 2 -> 800L;
            default -> 400L;
        };
        return body.kind() == EarthBodies.Kind.POOL || body.kind() == EarthBodies.Kind.SAND ? base * 2L : base;
    }

    /** Les blocs de minerai qu'elle extrait par heure ; les diamants des cheminees sont plus rares. */
    public static int blocksPerHour(EarthBodies.Body body) {
        return switch (body.kind()) {
            case ORE -> switch (tier(body)) {
                case 1 -> 6;
                case 2 -> 3;
                default -> 2;
            };
            case PIPE -> tier(body) == 1 ? 2 : 1;
            default -> 0;
        };
    }

    /** Ce que dure la capture, en secondes passees pres de la balise. */
    public static int captureSeconds(EarthBodies.Body body) {
        return switch (tier(body)) {
            case 1 -> 300;
            case 2 -> 180;
            default -> 120;
        };
    }

    /** Le grief de la nation depossedee ; un pays pris en vaut 25. */
    static double grief(EarthBodies.Body body) {
        return switch (tier(body)) {
            case 1 -> 12.0d;
            case 2 -> 8.0d;
            default -> 5.0d;
        };
    }

    /** Le pays de la mine sur la grille ; 0 s'il n'y est pas. */
    public static int country(EarthBodies.Body body) {
        EarthData earth = EarthData.get();
        EarthData.Country country = earth == null ? null : earth.byCode(body.country());
        return country == null ? 0 : country.id();
    }

    public static String countryName(EarthBodies.Body body) {
        int country = country(body);
        return country == 0 ? body.country() : EarthNations.countryName(country);
    }

    /** La nation de la region de la mine, qui l'exploite tant qu'une faction ne l'a pas capturee ; -1 sans. */
    public static int nation(MinecraftServer server, EarthBodies.Body body) {
        int country = country(body);
        return country == 0 ? -1 : EarthNations.holder(server, country).rival();
    }

    public static String nationName(int nation) {
        List<EventRun.Rival> pool = EventRun.rivalPool();
        return nation < 0 || nation >= pool.size() ? "personne" : pool.get(nation).name();
    }

    /** Qui tient la mine, vu par ce joueur : « ta nation », la faction d'un autre joueur, ou une nation. */
    public static String holderName(ServerPlayer viewer, EarthBodies.Body body) {
        MinecraftServer server = viewer.level().getServer();
        MineLedger.Concession held = ledger(server).of(body.id());
        if (held != null) {
            return held.owner().equals(viewer.getUUID()) ? "ta nation" : held.faction();
        }
        return nationName(nation(server, body));
    }

    /** Vrai si la faction de ce joueur tient la mine. */
    public static boolean ownedBy(ServerPlayer player, EarthBodies.Body body) {
        MineLedger.Concession held = ledger(player.level().getServer()).of(body.id());
        return held != null && held.owner().equals(player.getUUID());
    }

    /** « fer, rang 1 ». */
    public static String label(EarthBodies.Body body) {
        return body.resource() + ", rang " + tier(body);
    }

    /** Ce qu'elle verse par heure, en une ligne : « 1 500 $ et 6 blocs de minerai par heure ». */
    public static String output(EarthBodies.Body body) {
        int blocks = blocksPerHour(body);
        return MoneyFormat.exact(moneyPerHour(body)) + " $" + (blocks > 0 ? " et " + blocks + " bloc"
                + (blocks > 1 ? "s" : "") + " de minerai" : "") + " par heure";
    }

    /** L'objet qui la represente a l'ecran : son minerai, ou un seau de petrole pour un champ. */
    public static ItemStack icon(EarthBodies.Body body) {
        if (body.kind() == EarthBodies.Kind.POOL || body.kind() == EarthBodies.Kind.SAND) {
            return new ItemStack(body.resource().contains("gaz") ? Items.GLASS_BOTTLE : Items.BUCKET);
        }
        ItemStack ore = new ItemStack(body.ore().getBlock().asItem());
        return ore.isEmpty() ? new ItemStack(Items.RAW_IRON) : ore;
    }

    /** Les mines les plus proches de ce joueur, la sienne comprise. */
    public static List<EarthBodies.Body> nearest(ServerPlayer player, int count) {
        List<EarthBodies.Body> all = new ArrayList<>(EarthBodies.all());
        double x = player.getX();
        double z = player.getZ();
        all.sort(Comparator.comparingDouble(body -> distanceSq(body, x, z)));
        return all.subList(0, Math.min(count, all.size()));
    }

    static double distanceSq(EarthBodies.Body body, double x, double z) {
        double dx = body.centerX() - x;
        double dz = body.centerZ() - z;
        return dx * dx + dz * dz;
    }

    /** La direction a prendre, le nord vers les z negatifs : « 412 blocs au nord ». */
    public static String direction(EarthBodies.Body body, double x, double z) {
        double dx = body.centerX() - x;
        double dz = body.centerZ() - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 8.0d) {
            return "tu y es";
        }
        String[] names = {"a l'est", "au sud-est", "au sud", "au sud-ouest", "a l'ouest", "au nord-ouest", "au nord",
                "au nord-est"};
        int index = (int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0d);
        return MoneyFormat.exact(Math.round(distance)) + " blocs " + names[Math.floorMod(index, 8)];
    }

    // -- Le registre ---------------------------------------------------------------------------

    private static ServerLevel earthLevel(MinecraftServer server) {
        EarthData earth = EarthData.get();
        return earth == null ? null : server.getLevel(earth.dimension());
    }

    public static MineLedger ledger(MinecraftServer server) {
        ServerLevel level = earthLevel(server);
        return level == null ? MineLedger.EMPTY : level.getData(DoHyMines.LEDGER.get());
    }

    private static void save(MinecraftServer server, MineLedger ledger) {
        ServerLevel level = earthLevel(server);
        if (level != null) {
            level.setData(DoHyMines.LEDGER.get(), ledger);
        }
    }

    /** Les mines de la faction de ce joueur, dans l'ordre des captures. */
    public static List<MineLedger.Concession> of(ServerPlayer player) {
        return active() ? ledger(player.level().getServer()).of(player.getUUID()) : List.of();
    }

    /** L'argent que versent toutes ses mines par heure. */
    public static long moneyPerHour(ServerPlayer player) {
        long total = 0L;
        for (MineLedger.Concession concession : of(player)) {
            EarthBodies.Body body = body(concession.mine());
            if (body != null) {
                total += moneyPerHour(body);
            }
        }
        return total;
    }

    // -- La capture ----------------------------------------------------------------------------

    /** La capture en cours de ce joueur, ou {@code null}. */
    static Capture capture(UUID player) {
        return CAPTURES.get(player);
    }

    /** La capture en cours, en clair (« kiruna 1200 / 2400 » ticks tenus), vide sans : pour les journaux. */
    public static String captureState(UUID player) {
        Capture capture = CAPTURES.get(player);
        return capture == null ? "" : capture.mine + " " + capture.progress + " / " + capture.need;
    }

    /** Ce qui empeche de poser une balise ici ; {@code null} si la capture peut commencer. */
    public static String refusal(ServerPlayer player, BlockPos pos) {
        EarthData earth = EarthData.get();
        if (earth == null || EarthBodies.all().isEmpty()) {
            return "Les mines n'existent que sur une carte de la Terre";
        }
        if (!earth.covers(player.level())) {
            return "Les mines sont sur la carte de la Terre";
        }
        if (!FactionApi.has(player)) {
            return "Il faut une nation pour tenir une mine : /f create";
        }
        EarthBodies.Body body = at(pos.getX() + 0.5d, pos.getZ() + 0.5d);
        if (body == null) {
            return "Pas de mine ici : /gisements donne les plus proches";
        }
        MinecraftServer server = player.level().getServer();
        MineLedger.Concession held = ledger(server).of(body.id());
        if (held != null) {
            return held.owner().equals(player.getUUID()) ? body.name() + " est deja a ta nation"
                    : body.name() + " est a la nation " + held.faction() + " : pas de guerre entre joueurs";
        }
        int country = country(body);
        if (country != 0) {
            EarthNations.Holder holder = EarthNations.holder(server, country);
            if (holder.player() && !holder.ownedBy(player.getUUID())) {
                return countryName(body) + " est a la nation " + holder.name() + " : pas de guerre entre joueurs";
            }
        }
        Capture current = CAPTURES.get(player.getUUID());
        if (current != null) {
            EarthBodies.Body other = body(current.mine);
            return "Une capture est deja en cours (" + (other == null ? current.mine : other.name())
                    + ") : finis-la ou casse sa balise";
        }
        for (Capture other : CAPTURES.values()) {
            if (other.mine.equals(body.id())) {
                return "Quelqu'un capture deja " + body.name();
            }
        }
        return null;
    }

    /** La balise est posee : la capture commence. */
    public static void start(ServerPlayer player, BlockPos pos) {
        EarthBodies.Body body = at(pos.getX() + 0.5d, pos.getZ() + 0.5d);
        if (body == null) {
            return;
        }
        int seconds = captureSeconds(body);
        ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Capture de " + body.name()),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setProgress(0.0f);
        bar.addPlayer(player);
        CAPTURES.put(player.getUUID(), new Capture(player.getUUID(), body.id(), player.level().dimension(),
                pos.immutable(), seconds * 20, bar));
        player.level().playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.2f);
        FactionApi.tell(player, "Capture de " + body.name() + " (" + label(body) + ") : reste a moins de " + RANGE
                + " blocs de la balise pendant " + minutes(seconds) + ". S'eloigner la met en pause.",
                ChatFormatting.YELLOW);
    }

    private static String minutes(int seconds) {
        return seconds % 60 == 0 ? seconds / 60 + " min" : seconds / 60 + " min " + seconds % 60 + " s";
    }

    /** Un clic droit sur une balise : ou en est la capture. */
    static void status(ServerPlayer player, BlockPos pos) {
        for (Capture capture : CAPTURES.values()) {
            if (capture.beacon.equals(pos) && capture.dimension.equals(player.level().dimension())) {
                EarthBodies.Body body = body(capture.mine);
                String name = body == null ? capture.mine : body.name();
                int seconds = capture.secondsLeft();
                player.sendSystemMessage(Component.literal("Capture de " + name + " : "
                        + Math.round(100.0d * capture.progress / capture.need) + " %, encore " + seconds / 60 + ":"
                        + String.format(Locale.ROOT, "%02d", seconds % 60) + ".").withStyle(ChatFormatting.YELLOW),
                        true);
                return;
            }
        }
        player.sendSystemMessage(Component.literal("Cette balise ne capture rien : casse-la pour la reprendre.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** Toutes les dix ticks : les captures avancent, se mettent en pause ou s'arretent. */
    static void tick(MinecraftServer server) {
        if (CAPTURES.isEmpty()) {
            return;
        }
        Iterator<Capture> iterator = CAPTURES.values().iterator();
        while (iterator.hasNext()) {
            Capture capture = iterator.next();
            ServerLevel level = server.getLevel(capture.dimension);
            ServerPlayer player = server.getPlayerList().getPlayer(capture.player);
            EarthBodies.Body body = body(capture.mine);
            if (level == null || body == null) {
                capture.bar.removeAllPlayers();
                iterator.remove();
                continue;
            }
            if (level.isLoaded(capture.beacon) && !level.getBlockState(capture.beacon).is(DoHyMines.BEACON.get())) {
                capture.bar.removeAllPlayers();
                iterator.remove();
                if (player != null) {
                    FactionApi.tell(player, "Balise cassee : la capture de " + body.name() + " est abandonnee.",
                            ChatFormatting.RED);
                }
                continue;
            }
            if (player == null) {
                continue;
            }
            boolean near = player.level() == level
                    && player.distanceToSqr(Vec3.atCenterOf(capture.beacon)) <= (double) RANGE * RANGE;
            if (near) {
                capture.progress = Math.min(capture.need, capture.progress + 10);
                if (!capture.bar.getPlayers().contains(player)) {
                    capture.bar.addPlayer(player);
                }
                int seconds = capture.secondsLeft();
                capture.bar.setName(Component.literal("Capture de " + body.name() + " · " + seconds / 60 + ":"
                        + String.format(Locale.ROOT, "%02d", seconds % 60)));
                capture.bar.setColor(BossEvent.BossBarColor.YELLOW);
            } else {
                capture.bar.setName(Component.literal("Capture de " + body.name()
                        + " · en pause : reviens pres de la balise"));
                capture.bar.setColor(BossEvent.BossBarColor.WHITE);
            }
            capture.bar.setProgress(Math.min(1.0f, capture.progress / (float) capture.need));
            if (capture.progress >= capture.need) {
                iterator.remove();
                capture.bar.removeAllPlayers();
                complete(player, level, capture, body);
            }
        }
    }

    /** La capture est finie : la mine passe a la faction, la balise s'eteint. */
    private static void complete(ServerPlayer player, ServerLevel level, Capture capture, EarthBodies.Body body) {
        MinecraftServer server = level.getServer();
        if (!FactionApi.has(player) || ledger(server).of(body.id()) != null) {
            FactionApi.tell(player, "La capture de " + body.name() + " n'a pas abouti.", ChatFormatting.RED);
            return;
        }
        long now = System.currentTimeMillis();
        String faction = FactionApi.data(player).name();
        save(server, ledger(server).with(new MineLedger.Concession(body.id(), player.getUUID(), faction, now, now)));
        BlockPos beacon = capture.beacon;
        level.removeBlock(beacon, false);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, beacon.getX() + 0.5d, beacon.getY() + 0.8d,
                beacon.getZ() + 0.5d, 30, 0.6d, 1.0d, 0.6d, 0.1d);
        level.playSound(null, beacon, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9f, 1.0f);

        int nation = nation(server, body);
        int country = country(body);
        boolean ownCountry = country != 0 && EarthNations.holder(server, country).ownedBy(player.getUUID());
        if (nation >= 0 && !ownCountry) {
            // La nation depossedee s'en souviendra, comme d'un pays pris (lot 22l).
            fr.atraxterra.dohy.faction.war.WarStrategy.grieve(player, nation, "mine prise : " + body.name(),
                    grief(body));
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(body.name())
                .withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Mine capturee · +"
                + MoneyFormat.exact(moneyPerHour(body)) + " $ par heure").withStyle(ChatFormatting.YELLOW)));
        String from = ownCountry || nation < 0 ? "" : " sur " + nationName(nation);
        server.getPlayerList().broadcastSystemMessage(FactionApi.prefix().append(Component.literal(body.name()
                + " : " + faction + " capture la mine (" + body.resource() + ")" + from + ".")
                .withStyle(ChatFormatting.YELLOW)), false);
        FactionApi.tell(player, "Elle verse " + output(body) + " a ta banque nationale, hors ligne compris. Tu peux"
                + " toujours y miner toi-meme.", ChatFormatting.GREEN);
        FactionApi.stat(player, "earth:mines", 1);
        DoHy.LOGGER.info("Mines de la Terre : {} capture {} ({})", faction, body.name(), body.id());
    }

    /** Le joueur se deconnecte : sa capture se met en pause, sa barre disparait. */
    static void loggedOut(ServerPlayer player) {
        NEXT.remove(player.getUUID());
        Capture capture = CAPTURES.get(player.getUUID());
        if (capture != null) {
            capture.bar.removePlayer(player);
        }
    }

    private static void cancel(ServerPlayer player) {
        Capture capture = CAPTURES.remove(player.getUUID());
        if (capture != null) {
            capture.bar.removeAllPlayers();
        }
    }

    static void forgetAll() {
        for (Capture capture : CAPTURES.values()) {
            capture.bar.removeAllPlayers();
        }
        CAPTURES.clear();
        NEXT.clear();
    }

    // -- Les revenus ---------------------------------------------------------------------------

    /**
     * Verse ce que les mines de ce joueur ont produit depuis le dernier versement, heure par
     * heure pleine, une semaine au plus : l'argent a la banque nationale, les minerais au coffre
     * de la banque. {@code login} : il revient, le message dit ce qui s'est passe en son absence.
     */
    public static void payout(ServerPlayer player, boolean login) {
        if (!active() || !FactionApi.has(player)) {
            return;
        }
        long clock = System.currentTimeMillis();
        Long next = NEXT.get(player.getUUID());
        if (!login && next != null && clock < next) {
            return;
        }
        NEXT.put(player.getUUID(), clock + HOUR);
        MinecraftServer server = player.level().getServer();
        MineLedger ledger = ledger(server);
        List<MineLedger.Concession> mines = ledger.of(player.getUUID());
        if (mines.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ServerLevel level = earthLevel(server);
        RandomSource random = player.getRandom();
        MineLedger updated = ledger;
        long money = 0L;
        long hours = 0L;
        int paying = 0;
        List<ItemStack> ores = new ArrayList<>();
        for (MineLedger.Concession concession : mines) {
            EarthBodies.Body body = body(concession.mine());
            long elapsed = now - concession.paid();
            if (body == null || elapsed < HOUR) {
                continue;
            }
            long due = elapsed / HOUR;
            long paid = Math.min(due, MAX_HOURS);
            money += paid * moneyPerHour(body);
            ores.addAll(extract(level, body, (int) Math.min(Integer.MAX_VALUE, paid * blocksPerHour(body)), random));
            long until = due > MAX_HOURS ? now - elapsed % HOUR : concession.paid() + due * HOUR;
            updated = updated.with(concession.paidUntil(until));
            hours = Math.max(hours, paid);
            paying++;
        }
        if (paying == 0) {
            return;
        }
        save(server, updated);
        if (money > 0L) {
            FactionApi.creditBank(player, money, "Mines de la Terre (" + hours + " h)");
        }
        int count = 0;
        for (ItemStack stack : ores) {
            count += stack.getCount();
        }
        int lost = ores.isEmpty() ? 0 : FactionVault.store(player, ores);
        StringBuilder text = new StringBuilder(login ? "Pendant ton absence (" + hours + " h), tes mines ont verse "
                : "Tes mines (" + paying + ") versent ");
        text.append(MoneyFormat.exact(money)).append(" $ a la banque nationale");
        if (count > 0) {
            text.append(" et ").append(MoneyFormat.exact(count - lost)).append(" minerai")
                    .append(count - lost > 1 ? "s" : "").append(" au coffre de la banque");
        }
        text.append('.');
        if (lost > 0) {
            text.append(" Coffre plein : ").append(MoneyFormat.exact(lost)).append(" perdu").append(lost > 1 ? "s" : "")
                    .append(", vide-le depuis l'onglet Banque.");
        }
        player.sendSystemMessage(Component.literal("[Mines] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text.toString()).withStyle(lost > 0 ? ChatFormatting.YELLOW
                        : ChatFormatting.GREEN)));
    }

    /**
     * Ce que rendent {@code blocks} blocs de la mine, casses a la pioche en diamant (sans fortune).
     * Au-dela de {@value #SAMPLE} blocs, un echantillon est tire puis multiplie.
     */
    static List<ItemStack> extract(ServerLevel level, EarthBodies.Body body, int blocks, RandomSource random) {
        List<ItemStack> out = new ArrayList<>();
        if (level == null || blocks <= 0) {
            return out;
        }
        int sample = Math.min(blocks, SAMPLE);
        BlockPos pos = BlockPos.containing(body.centerX(), body.y(), body.centerZ());
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        for (int index = 0; index < sample; index++) {
            if (body.rich() != null && random.nextDouble() < body.richChance()) {
                ItemStack rich = new ItemStack(body.rich().getBlock().asItem());
                if (!rich.isEmpty()) {
                    out.add(rich);
                    continue;
                }
            }
            BlockState state = body.ore();
            for (EarthBodies.Extra extra : body.extras()) {
                if (random.nextDouble() < extra.chance()) {
                    state = extra.ore();
                    break;
                }
            }
            try {
                out.addAll(Block.getDrops(state, level, pos, null, null, tool));
            } catch (RuntimeException error) {
                DoHy.LOGGER.warn("Mines de la Terre : butin illisible pour {}", state, error);
                break;
            }
        }
        List<ItemStack> merged = merge(out);
        if (sample == blocks) {
            return merged;
        }
        double scale = blocks / (double) sample;
        List<ItemStack> scaled = new ArrayList<>(merged.size());
        for (ItemStack stack : merged) {
            int count = (int) Math.round(stack.getCount() * scale);
            if (count > 0) {
                scaled.add(stack.copyWithCount(count));
            }
        }
        return scaled;
    }

    /** Les piles d'un meme objet reunies (sans limite de taille : le coffre les redecoupe). */
    static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean added = false;
            for (ItemStack existing : merged) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    added = true;
                    break;
                }
            }
            if (!added) {
                merged.add(stack.copy());
            }
        }
        return merged;
    }

    // -- La balise ------------------------------------------------------------------------------

    /** Achete une balise de concession a la Boutique ; la raison du refus, ou {@code null}. */
    public static String buyBeacon(ServerPlayer player) {
        if (!fr.atraxterra.dohy.economy.Economy.tryDebit(player, DoHyMines.BEACON_PRICE)) {
            return "Il te faut " + MoneyFormat.exact(DoHyMines.BEACON_PRICE) + " $ en liquide.";
        }
        ItemStack stack = new ItemStack(DoHyMines.BEACON_ITEM.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.literal("Balise de concession achetee : pose-la dans une mine de la Terre"
                + " (/gisements) et reste a cote.").withStyle(ChatFormatting.GREEN));
        return null;
    }

    // -- La reprise, l'abandon, la dissolution -------------------------------------------------

    /** La mine qu'une nation reprendrait sur un assaut reussi : la derniere capturee dans sa region ; null sans. */
    public static EarthBodies.Body stake(ServerPlayer player, int nation) {
        if (!active() || nation < 0) {
            return null;
        }
        MinecraftServer server = player.level().getServer();
        MineLedger.Concession last = null;
        for (MineLedger.Concession concession : ledger(server).of(player.getUUID())) {
            EarthBodies.Body body = body(concession.mine());
            if (body == null || nation(server, body) != nation) {
                continue;
            }
            if (last == null || concession.since() > last.since()) {
                last = concession;
            }
        }
        return last == null ? null : body(last.mine());
    }

    /** L'assaut de cette nation a reussi : elle reprend sa mine en jeu. Rend son nom, {@code null} sans. */
    public static String reclaim(ServerPlayer player, int nation) {
        EarthBodies.Body body = stake(player, nation);
        if (body == null) {
            return null;
        }
        MinecraftServer server = player.level().getServer();
        save(server, ledger(server).without(body.id()));
        String name = nationName(nation);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(body.name())
                .withStyle(ChatFormatting.RED)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Mine reprise par " + name)
                .withStyle(ChatFormatting.GRAY)));
        String faction = FactionApi.has(player) ? FactionApi.data(player).name() : player.getGameProfile().name();
        String verb = nation < fr.atraxterra.dohy.faction.war.NationTraits.count()
                ? fr.atraxterra.dohy.faction.war.NationTraits.of(nation).verb("reprend", "reprennent")
                : name + " reprend";
        server.getPlayerList().broadcastSystemMessage(FactionApi.prefix().append(Component.literal(body.name()
                + " : " + verb + " la mine " + EarthNations.a(faction) + ".").withStyle(ChatFormatting.RED)), false);
        FactionApi.stat(player, "earth:mines_lost", 1);
        DoHy.LOGGER.info("Mines de la Terre : {} reprend {} a {}", name, body.name(), faction);
        return body.name();
    }

    /**
     * La place forte de la nation qui l'avait reprise est tombee : la mine
     * revient a la faction (lot 22l-2), ses revenus repartant de maintenant.
     * Rend son nom, {@code null} si une autre faction la tient deja.
     */
    public static String restore(ServerPlayer player, String id) {
        EarthBodies.Body body = body(id);
        if (!active() || body == null || !FactionApi.has(player)) {
            return null;
        }
        MinecraftServer server = player.level().getServer();
        if (ledger(server).of(body.id()) != null) {
            return null;
        }
        long now = System.currentTimeMillis();
        String faction = FactionApi.data(player).name();
        save(server, ledger(server).with(new MineLedger.Concession(body.id(), player.getUUID(), faction, now, now)));
        int nation = nation(server, body);
        int country = country(body);
        boolean ownCountry = country != 0 && EarthNations.holder(server, country).ownedBy(player.getUUID());
        if (nation >= 0 && !ownCountry) {
            fr.atraxterra.dohy.faction.war.WarStrategy.grieve(player, nation, "mine prise : " + body.name(),
                    grief(body));
        }
        server.getPlayerList().broadcastSystemMessage(FactionApi.prefix().append(Component.literal(body.name()
                + " : " + faction + " reprend la mine (" + body.resource() + ").").withStyle(ChatFormatting.YELLOW)),
                false);
        DoHy.LOGGER.info("Mines de la Terre : {} reprend {} ({})", faction, body.name(), body.id());
        return body.name();
    }

    /** Rend une mine a la nation de sa region. */
    public static String abandon(ServerPlayer player, String id) {
        EarthBodies.Body body = body(id);
        if (body == null || !ownedBy(player, body)) {
            return "Ta nation ne tient pas cette mine";
        }
        MinecraftServer server = player.level().getServer();
        save(server, ledger(server).without(body.id()));
        FactionApi.tell(player, body.name() + " revient a " + nationName(nation(server, body)) + ".",
                ChatFormatting.GRAY);
        return null;
    }

    /** La faction est dissoute : ses mines reviennent aux nations, sa capture s'arrete. */
    public static void release(ServerPlayer player) {
        cancel(player);
        if (!active()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        MineLedger ledger = ledger(server);
        MineLedger kept = ledger;
        for (MineLedger.Concession concession : ledger.of(player.getUUID())) {
            kept = kept.without(concession.mine());
        }
        if (kept != ledger) {
            save(server, kept);
        }
    }

    /** Le nom de la faction a change : ses mines le suivent. */
    public static void renamed(ServerPlayer player) {
        if (!active() || !FactionApi.has(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        MineLedger ledger = ledger(server);
        MineLedger updated = ledger;
        String name = FactionApi.data(player).name();
        for (MineLedger.Concession concession : ledger.of(player.getUUID())) {
            updated = updated.with(concession.renamed(name));
        }
        if (updated != ledger) {
            save(server, updated);
        }
    }

    // -- Pour les operateurs -------------------------------------------------------------------

    /** Donne une mine a la faction du joueur, sans balise ni attente. */
    static String give(ServerPlayer player, String query) {
        if (!active()) {
            return "Pas de carte de la Terre";
        }
        if (!FactionApi.has(player)) {
            return "Il faut une nation";
        }
        EarthBodies.Body body = find(query);
        if (body == null) {
            return "Mine inconnue : " + query;
        }
        MinecraftServer server = player.level().getServer();
        long now = System.currentTimeMillis();
        save(server, ledger(server).with(new MineLedger.Concession(body.id(), player.getUUID(),
                FactionApi.data(player).name(), now, now)));
        return null;
    }

    /** Fait comme si ses mines avaient produit {@code hours} heures de plus, et verse. */
    static String advance(ServerPlayer player, int hours) {
        if (!active()) {
            return "Pas de carte de la Terre";
        }
        MinecraftServer server = player.level().getServer();
        MineLedger ledger = ledger(server);
        List<MineLedger.Concession> mines = ledger.of(player.getUUID());
        if (mines.isEmpty()) {
            return "Ta nation ne tient aucune mine";
        }
        MineLedger updated = ledger;
        for (MineLedger.Concession concession : mines) {
            updated = updated.with(concession.paidUntil(concession.paid() - hours * HOUR));
        }
        save(server, updated);
        payout(player, true);
        return null;
    }

    /** Une mine par son identifiant ou son nom, accents et casse ignores. */
    public static EarthBodies.Body find(String query) {
        String wanted = plain(query);
        for (EarthBodies.Body body : EarthBodies.all()) {
            if (body.id().equals(query) || plain(body.name()).equals(wanted)) {
                return body;
            }
        }
        return null;
    }

    static String plain(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
