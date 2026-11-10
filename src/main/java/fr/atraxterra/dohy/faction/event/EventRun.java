package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.boss.PalaBosses;
import fr.atraxterra.dohy.palamod.boss.RivalFighter;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un event de faction en cours (lot 22f) : ce que le Boss, le KOTH et le Totem
 * ont en commun.
 *
 * <ul>
 *   <li><b>Les factions rivales</b> : trois factions fictives par event, deux
 *       combattants chacune, renouveles toutes les 45 s. Au Boss elles visent le
 *       boss ; au KOTH et au Totem, c'est du PvP : elles se battent entre elles
 *       et contre le joueur.</li>
 *   <li><b>Le score par faction</b> (degats ou points) et <b>l'ELO par rang</b>,
 *       comme {@code ExponentialPool.distribute(n, 1.2, elo)} du PalaMod : chaque
 *       rang touche 1,2 fois la part du suivant.</li>
 *   <li><b>La fin</b> : bilan dans le chat, titre au vainqueur, retour des joueurs
 *       30 s plus tard, sbires et blocs poses effaces.</li>
 *   <li><b>Les blocs</b> : dans la Warzone, on ne casse que ce qu'on a pose
 *       pendant l'event (et les toiles d'Arachna).</li>
 * </ul>
 */
public abstract class EventRun {

    protected static final int RIVALS = 3;
    protected static final int WAVE_TICKS = 900;
    protected static final int RETURN_TICKS = 600;
    static final double ELO_FACTOR = 1.2d;

    private static final String[] NAMES = {"Les Corbeaux", "Nordheim", "Ombre Pourpre", "Les Loups Gris",
            "Valkyr", "Kraken", "Solaris", "Arcanum", "Les Titans", "Fer de Lance", "Basilic", "Aube Rouge"};
    private static final DyeColor[] COLORS = {DyeColor.BLACK, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.GRAY,
            DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.MAGENTA, DyeColor.ORANGE, DyeColor.WHITE,
            DyeColor.GREEN, DyeColor.RED};

    /** Une faction rivale fictive : son nom et sa couleur de banniere. */
    public record Rival(String name, DyeColor color) {
    }

    protected final UUID id = UUID.randomUUID();
    protected final FactionEventKind kind;
    protected final List<Rival> rivals = new ArrayList<>();
    protected final Map<String, Float> score = new LinkedHashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<BlockPos, Integer> temp = new HashMap<>();
    private final Set<BlockPos> placed = new HashSet<>();
    private final List<UUID> minions = new ArrayList<>();
    /** La barre du haut de l'ecran (KOTH, Totem) ; le Boss a la sienne. */
    protected ServerBossEvent bar;
    protected int ticks;
    protected int endedAt = -1;
    protected boolean done;

    protected EventRun(FactionEventKind kind, RandomSource random) {
        this.kind = kind;
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            picks.add(i);
        }
        Collections.shuffle(picks, new java.util.Random(random.nextLong()));
        for (int i = 0; i < RIVALS; i++) {
            rivals.add(new Rival(NAMES[picks.get(i)], COLORS[picks.get(i)]));
        }
    }

    // -- Identite --------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public FactionEventKind kind() {
        return kind;
    }

    public boolean ended() {
        return endedAt >= 0;
    }

    boolean done() {
        return done;
    }

    public int seconds() {
        return ticks / 20;
    }

    public List<Rival> rivals() {
        return List.copyOf(rivals);
    }

    // -- Ce que chaque event precise -------------------------------------------

    /** Le centre de l'arene, au niveau du sol. */
    protected abstract BlockPos center();

    /** La ou le joueur arrive. */
    public abstract Vec3 entry();

    /** La ou entrent les combattants de la faction rivale {@code index}. */
    protected abstract Vec3 gate(int index);

    /** L'inclinaison du regard a l'arrivee : droit devant, sauf au Totem ou l'on regarde l'escalier. */
    public float entryPitch() {
        return 0.0f;
    }

    /** Le titre de la carte « EN COURS ». */
    public abstract String title();

    public abstract Item icon();

    /** Deux lignes pour la carte « EN COURS ». */
    public abstract List<String> status(ServerPlayer player, MinecraftServer server);

    /** Une barre de progression (0 a 1) pour la carte, ou -1. */
    public float progress(MinecraftServer server) {
        return -1.0f;
    }

    protected abstract void tickRunning(ServerLevel level);

    /** Le mot des scores : « degats », « points ». */
    protected abstract String unit();

    /** Rend vrai si le PvP est de mise : les rivaux attaquent le joueur. */
    protected abstract boolean pvp();

    protected void onFinish(ServerLevel level) {
    }

    // -- Scores ----------------------------------------------------------------

    /** La faction de celui qui agit : le joueur pour la sienne, un rival pour la sienne. */
    public String keyOf(Entity entity) {
        if (entity instanceof ServerPlayer player && FactionApi.has(player)) {
            String key = "p:" + player.getUUID();
            names.put(key, FactionApi.data(player).name());
            return key;
        }
        if (entity instanceof RivalFighter fighter && fighter.rival() >= 0) {
            return "r:" + fighter.rival();
        }
        return null;
    }

    public String nameOf(String key) {
        if (key.startsWith("r:")) {
            int index = Integer.parseInt(key.substring(2));
            return index < rivals.size() ? rivals.get(index).name() : "???";
        }
        return names.getOrDefault(key, "???");
    }

    public void addScore(String key, float amount) {
        if (key != null && amount > 0.0f) {
            score.merge(key, amount, Float::sum);
        }
    }

    public float scoreOf(ServerPlayer player) {
        return score.getOrDefault("p:" + player.getUUID(), 0.0f);
    }

    /** Le rang provisoire du joueur (1 = premier), 0 s'il n'a rien. */
    public int rankOf(ServerPlayer player) {
        float mine = scoreOf(player);
        if (mine <= 0.0f) {
            return 0;
        }
        int rank = 1;
        for (float other : score.values()) {
            if (other > mine) {
                rank++;
            }
        }
        return rank;
    }

    /** La faction en tete, ou {@code null}. */
    protected String leader() {
        String best = null;
        float top = 0.0f;
        for (Map.Entry<String, Float> entry : score.entrySet()) {
            if (entry.getValue() > top) {
                top = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    // -- Blocs et sbires -------------------------------------------------------

    /** Pose un bloc pour un temps (les toiles d'Arachna) ; il sera retire. */
    public void tempBlock(ServerLevel level, BlockPos pos, BlockState state, int duration) {
        if (level.getBlockState(pos).isAir() && !temp.containsKey(pos)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            temp.put(pos, ticks + duration);
        }
    }

    /** Un bloc pose par un joueur pendant l'event : on pourra le casser, et il partira a la fin. */
    void placed(BlockPos pos) {
        placed.add(pos.immutable());
    }

    /** Ce bloc de la Warzone peut-il etre casse ? */
    boolean breakable(BlockPos pos) {
        return placed.contains(pos) || temp.containsKey(pos);
    }

    void forget(BlockPos pos) {
        placed.remove(pos);
        temp.remove(pos);
    }

    /** Un sbire du boss : marque, et efface a la fin. */
    public void minion(ServerLevel level, Mob mob) {
        mob.addTag(FactionEvents.MINION_TAG);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        minions.add(mob.getUUID());
    }

    // -- Deroulement -----------------------------------------------------------

    final void tick(MinecraftServer server) {
        ticks++;
        ServerLevel level = EventArena.level(server);
        if (level == null) {
            done = true;
            return;
        }
        Iterator<Map.Entry<BlockPos, Integer>> blocks = temp.entrySet().iterator();
        while (blocks.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = blocks.next();
            if (entry.getValue() <= ticks) {
                if (level.getBlockState(entry.getKey()).is(Blocks.COBWEB)) {
                    level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                blocks.remove();
            }
        }
        if (ended()) {
            if (ticks - endedAt == RETURN_TICKS - 200) {
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(Component.literal("Retour dans 10 s.").withStyle(ChatFormatting.GRAY));
                }
            }
            if (ticks - endedAt >= RETURN_TICKS) {
                finish(level);
            }
            return;
        }
        tickRunning(level);
        if (ended()) {
            return;
        }
        if (ticks % WAVE_TICKS == 0) {
            wave(level);
        }
        if (bar != null && ticks % 10 == 0) {
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
                if (player.level() != level) {
                    bar.removePlayer(player);
                }
            }
            for (ServerPlayer player : level.players()) {
                bar.addPlayer(player);
            }
        }
    }

    /** Arrete l'event sans vainqueur : pas d'ELO. */
    void stop(ServerLevel level, String reason) {
        if (ended()) {
            return;
        }
        endedAt = ticks;
        FactionEvents.broadcast(level.getServer(), Component.literal(reason).withStyle(ChatFormatting.RED));
    }

    /** Fin : on ramene les joueurs, on efface sbires, rivaux, blocs poses et toiles. */
    void finish(ServerLevel level) {
        for (ServerPlayer player : List.copyOf(level.players())) {
            FactionEvents.sendBack(player);
        }
        for (UUID minion : minions) {
            Entity entity = level.getEntity(minion);
            if (entity != null) {
                entity.discard();
            }
        }
        for (RivalFighter fighter : fighters(level)) {
            fighter.discard();
        }
        for (BlockPos pos : temp.keySet()) {
            if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos pos : placed) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        temp.clear();
        placed.clear();
        if (bar != null) {
            bar.removeAllPlayers();
        }
        onFinish(level);
        EventArena.hold(level, center(), false);
        done = true;
    }

    // -- Factions rivales ------------------------------------------------------

    protected List<RivalFighter> fighters(ServerLevel level) {
        AABB arena = new AABB(center()).inflate(48, 24, 48);
        return level.getEntitiesOfClass(RivalFighter.class, arena, RivalFighter::isAlive);
    }

    /** Complete chaque faction rivale a deux combattants. */
    protected void wave(ServerLevel level) {
        int[] alive = new int[RIVALS];
        for (RivalFighter fighter : fighters(level)) {
            if (fighter.rival() >= 0 && fighter.rival() < RIVALS) {
                alive[fighter.rival()]++;
            }
        }
        for (int i = 0; i < RIVALS; i++) {
            for (int n = alive[i]; n < 2; n++) {
                spawnFighter(level, i);
            }
        }
    }

    private void spawnFighter(ServerLevel level, int index) {
        RivalFighter fighter = PalaBosses.RIVAL_FIGHTER.get().create(level, EntitySpawnReason.EVENT);
        if (fighter == null) {
            return;
        }
        Rival rival = rivals.get(index);
        Vec3 gate = gate(index);
        RandomSource random = level.getRandom();
        fighter.snapTo(gate.x + random.nextInt(3) - 1, gate.y, gate.z + random.nextInt(3) - 1,
                random.nextFloat() * 360.0f, 0.0f);
        fighter.finalizeSpawn(level, level.getCurrentDifficultyAt(fighter.blockPosition()), EntitySpawnReason.EVENT,
                null);
        // Contre le boss, la hache ; contre des joueurs, l'epee de pierre : six haches de fer
        // sur un joueur seul, c'est la mort en trois coups.
        fighter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(pvp() ? Items.STONE_SWORD : Items.IRON_AXE));
        fighter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.BANNER.pick(rival.color())));
        fighter.setCustomName(Component.literal(rival.name()).withStyle(Style.EMPTY.withColor(
                rival.color().getTextColor())));
        fighter.setCustomNameVisible(true);
        fighter.bind(id, index, pvp());
        level.addFreshEntity(fighter);
    }

    // -- Classement et ELO -----------------------------------------------------

    /** Les parts d'un pot reparti par rang : chaque rang touche 1,2 fois le suivant. */
    static long[] shares(int count, double factor, long pool) {
        long[] out = new long[count];
        if (count == 0) {
            return out;
        }
        double total = 0.0d;
        for (int i = 0; i < count; i++) {
            total += Math.pow(factor, count - 1 - i);
        }
        long given = 0L;
        for (int i = 0; i < count; i++) {
            out[i] = (long) Math.floor(pool * Math.pow(factor, count - 1 - i) / total);
            given += out[i];
        }
        out[0] += pool - given;
        return out;
    }

    /** Clot l'event : classement, ELO, titre et bilan pour chaque joueur en faction. */
    protected void conclude(MinecraftServer server, String headline) {
        if (ended()) {
            return;
        }
        endedAt = ticks;
        List<Map.Entry<String, Float>> ranking = new ArrayList<>(score.entrySet());
        ranking.removeIf(entry -> entry.getValue() <= 0.0f);
        ranking.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        long[] elo = shares(ranking.size(), ELO_FACTOR, kind.elo());

        List<Component> lines = new ArrayList<>();
        Component rule = Component.literal("----------------------------------------")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
        lines.add(rule);
        lines.add(Component.literal(headline).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < Math.min(ranking.size(), 10); i++) {
            Map.Entry<String, Float> entry = ranking.get(i);
            lines.add(Component.literal("#" + (i + 1) + " ").withStyle(i == 0 ? ChatFormatting.GREEN
                            : ChatFormatting.YELLOW)
                    .append(Component.literal(nameOf(entry.getKey())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" · " + Math.round(entry.getValue()) + " " + unit() + " · +"
                            + MoneyFormat.exact(elo[i]) + " ELO").withStyle(ChatFormatting.GRAY)));
        }
        if (ranking.isEmpty()) {
            lines.add(Component.literal("Personne n'a marque.").withStyle(ChatFormatting.GRAY));
        }
        lines.add(rule);
        String winner = ranking.isEmpty() ? null : nameOf(ranking.get(0).getKey());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!FactionApi.has(player)) {
                continue;
            }
            for (Component line : lines) {
                player.sendSystemMessage(line);
            }
            String key = "p:" + player.getUUID();
            int place = -1;
            for (int i = 0; i < ranking.size(); i++) {
                if (ranking.get(i).getKey().equals(key)) {
                    place = i;
                }
            }
            String where = place < 0 ? "non classee" : (place == 0 ? "1re" : (place + 1) + "e") + " place";
            if (winner != null) {
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(winner)
                        .withStyle(place == 0 ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                        kind.label() + " · ta faction : " + where).withStyle(ChatFormatting.GRAY)));
            }
            MutableComponent mine = Component.literal("Ta faction » ").withStyle(ChatFormatting.GRAY);
            player.sendSystemMessage(mine.append(Component.literal(where)
                    .withStyle(place < 0 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
            if (place >= 0) {
                FactionApi.addElo(player, elo[place], kind.label());
                FactionApi.stat(player, "event:" + kind.id(), 1);
            }
        }
    }

    /** « 12:34 » */
    protected static String clock(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
