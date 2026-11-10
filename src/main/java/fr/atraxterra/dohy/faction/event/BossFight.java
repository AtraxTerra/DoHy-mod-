package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.boss.BossKind;
import fr.atraxterra.dohy.palamod.boss.EventBoss;
import fr.atraxterra.dohy.palamod.boss.PalaBosses;
import fr.atraxterra.dohy.palamod.boss.RivalFighter;
import fr.atraxterra.dohy.util.MoneyFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Un event Boss en cours (lot 22f).
 *
 * <p>Le deroule du wiki : un des cinq boss apparait dans l'arene, les factions
 * le frappent, et a sa mort 700 ELO sont repartis entre elles <b>selon leur
 * rang aux degats</b> — le PalaMod passe par {@code ExponentialPool.distribute(n,
 * 1.2, elo)} : chaque rang touche 1,2 fois la part du suivant.
 *
 * <p><b>Solo</b> : trois factions rivales fictives envoient chacune deux
 * combattants, renouveles toutes les 45 s. Elles frappent le boss (a 30 %,
 * {@link EventBoss#RIVAL_FACTOR}) et prennent leur part du classement : seul
 * contre le boss, on touche les 700 ; avec elles, il faut faire plus de degats
 * qu'elles pour finir premier.
 *
 * <p>Le combat s'arrete quand le boss meurt, s'il n'a pas ete frappe depuis cinq
 * minutes, ou au bout de trente. Trente secondes plus tard, les joueurs encore
 * dans l'arene sont ramenes d'ou ils venaient.
 */
public final class BossFight {

    private static final int RIVALS = 3;
    private static final int FIGHTERS_PER_RIVAL = 2;
    private static final int WAVE_TICKS = 900;
    private static final int MAX_TICKS = 36_000;
    private static final int RETURN_TICKS = 600;
    private static final double ELO_FACTOR = 1.2d;

    private static final String[] NAMES = {"Les Corbeaux", "Nordheim", "Ombre Pourpre", "Les Loups Gris",
            "Valkyr", "Kraken", "Solaris", "Arcanum", "Les Titans", "Fer de Lance", "Basilic", "Aube Rouge"};
    private static final DyeColor[] COLORS = {DyeColor.BLACK, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.GRAY,
            DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.YELLOW, DyeColor.MAGENTA, DyeColor.ORANGE, DyeColor.WHITE,
            DyeColor.GREEN, DyeColor.RED};

    /** Une faction rivale fictive : son nom et sa couleur de banniere. */
    public record Rival(String name, DyeColor color) {
    }

    private final UUID id = UUID.randomUUID();
    private final BossKind kind;
    private final List<Rival> rivals = new ArrayList<>();
    private final Map<String, Float> damage = new LinkedHashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<BlockPos, Integer> temp = new HashMap<>();
    private final List<UUID> minions = new ArrayList<>();
    private UUID bossId;
    private int ticks;
    private int endedAt = -1;
    private boolean done;

    private BossFight(BossKind kind, RandomSource random) {
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

    /** Ouvre l'arene et lache le boss. Rend {@code null} si la Warzone manque. */
    static BossFight start(MinecraftServer server, BossKind kind) {
        ServerLevel level = EventArena.level(server);
        if (level == null) {
            return null;
        }
        EventArena.buildBossArena(level);
        EventArena.hold(level, true);
        BossFight fight = new BossFight(kind, level.getRandom());
        EventBoss boss = PalaBosses.type(kind).create(level, EntitySpawnReason.EVENT);
        if (boss == null) {
            EventArena.hold(level, false);
            return null;
        }
        Vec3 center = Vec3.atBottomCenterOf(EventArena.BOSS_CENTER.above());
        boss.snapTo(center.x, center.y, center.z, 180.0f, 0.0f);
        boss.bind(fight.id);
        level.addFreshEntity(boss);
        fight.bossId = boss.getUUID();
        fight.wave(level);
        return fight;
    }

    public UUID id() {
        return id;
    }

    public BossKind kind() {
        return kind;
    }

    public boolean ended() {
        return endedAt >= 0;
    }

    boolean done() {
        return done;
    }

    /** Secondes depuis le debut. */
    public int seconds() {
        return ticks / 20;
    }

    /** La part de vie du boss, de 0 a 1, ou -1 si on ne le voit pas. */
    public float health(MinecraftServer server) {
        ServerLevel level = EventArena.level(server);
        if (level != null && level.getEntity(bossId) instanceof EventBoss boss) {
            return boss.getHealth() / boss.getMaxHealth();
        }
        return -1.0f;
    }

    // -- Ce que le boss signale ------------------------------------------------

    /** La faction de celui qui frappe : le joueur pour la sienne, un rival pour la sienne. */
    public String keyOf(Entity attacker) {
        if (attacker instanceof ServerPlayer player && FactionApi.has(player)) {
            String key = "p:" + player.getUUID();
            names.put(key, FactionApi.data(player).name());
            return key;
        }
        if (attacker instanceof RivalFighter fighter && fighter.rival() >= 0) {
            return "r:" + fighter.rival();
        }
        return null;
    }

    public void addDamage(String key, float amount) {
        if (amount > 0.0f) {
            damage.merge(key, amount, Float::sum);
        }
    }

    /** Pose un bloc pour un temps (les toiles d'Arachna) ; il sera retire. */
    public void tempBlock(ServerLevel level, BlockPos pos, BlockState state, int duration) {
        if (level.getBlockState(pos).isAir() && !temp.containsKey(pos)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            temp.put(pos, ticks + duration);
        }
    }

    /** Un sbire du boss : marque, et efface a la fin. */
    public void minion(ServerLevel level, Mob mob) {
        mob.addTag(FactionEvents.MINION_TAG);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        minions.add(mob.getUUID());
    }

    public void bossKilled(ServerLevel level, EventBoss boss) {
        if (ended()) {
            return;
        }
        endedAt = ticks;
        distribute(level.getServer(), true);
    }

    // -- Deroulement -----------------------------------------------------------

    void tick(MinecraftServer server) {
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
                tellArena(level, "Retour dans 10 s.", ChatFormatting.GRAY);
            }
            if (ticks - endedAt >= RETURN_TICKS) {
                finish(level);
            }
            return;
        }
        EventBoss boss = level.getEntity(bossId) instanceof EventBoss found ? found : null;
        if (boss == null && ticks > 40) {
            // Hors de portee d'un chunk charge, ou disparu : on clot sans vainqueur.
            stop(level, "Le boss " + kind.label() + " s'est evanoui.");
            return;
        }
        if (boss != null && boss.idleTicks() > EventBoss.BORED_TICKS) {
            boss.discard();
            stop(level, "Le boss " + kind.label() + " repart : personne ne l'a frappe depuis 5 min.");
            return;
        }
        if (ticks > MAX_TICKS) {
            if (boss != null) {
                boss.discard();
            }
            stop(level, "Le boss " + kind.label() + " repart : 30 min sont ecoulees.");
            return;
        }
        if (ticks % WAVE_TICKS == 0) {
            wave(level);
        }
    }

    /** Arrete le combat sans mort du boss : pas d'ELO. */
    void stop(ServerLevel level, String reason) {
        if (ended()) {
            return;
        }
        endedAt = ticks;
        FactionEvents.broadcast(level.getServer(), Component.literal(reason).withStyle(ChatFormatting.RED));
        if (level.getEntity(bossId) instanceof EventBoss boss) {
            boss.discard();
        }
    }

    /** Fin : on ramene les joueurs, on efface sbires, rivaux et toiles. */
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
        for (BlockPos pos : temp.keySet()) {
            if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        temp.clear();
        EventArena.hold(level, false);
        done = true;
    }

    /** Complete chaque faction rivale a deux combattants. */
    private void wave(ServerLevel level) {
        AABB arena = new AABB(EventArena.BOSS_CENTER).inflate(EventArena.BOSS_RADIUS + 4, 20, EventArena.BOSS_RADIUS + 4);
        int[] alive = new int[RIVALS];
        for (RivalFighter fighter : level.getEntitiesOfClass(RivalFighter.class, arena, RivalFighter::isAlive)) {
            if (fighter.rival() >= 0 && fighter.rival() < RIVALS) {
                alive[fighter.rival()]++;
            }
        }
        for (int i = 0; i < RIVALS; i++) {
            for (int n = alive[i]; n < FIGHTERS_PER_RIVAL; n++) {
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
        Vec3 gate = EventArena.rivalGate(index);
        RandomSource random = level.getRandom();
        fighter.snapTo(gate.x + random.nextInt(3) - 1, gate.y, gate.z + random.nextInt(3) - 1,
                random.nextFloat() * 360.0f, 0.0f);
        fighter.finalizeSpawn(level, level.getCurrentDifficultyAt(fighter.blockPosition()), EntitySpawnReason.EVENT,
                null);
        fighter.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        fighter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.BANNER.pick(rival.color())));
        fighter.setCustomName(Component.literal(rival.name()).withStyle(Style.EMPTY.withColor(
                rival.color().getTextColor())));
        fighter.setCustomNameVisible(true);
        fighter.bind(id, index);
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

    private String nameOf(String key) {
        if (key.startsWith("r:")) {
            int index = Integer.parseInt(key.substring(2));
            return index < rivals.size() ? rivals.get(index).name() : "???";
        }
        return names.getOrDefault(key, "???");
    }

    private void distribute(MinecraftServer server, boolean killed) {
        List<Map.Entry<String, Float>> ranking = new ArrayList<>(damage.entrySet());
        ranking.removeIf(entry -> entry.getValue() <= 0.0f);
        ranking.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        long[] elo = shares(ranking.size(), ELO_FACTOR, FactionEventKind.BOSS.elo());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("----------------------------------------")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));
        lines.add(Component.literal("Le boss " + kind.label() + " a succombe.").withStyle(ChatFormatting.RED));
        for (int i = 0; i < Math.min(ranking.size(), 5); i++) {
            Map.Entry<String, Float> entry = ranking.get(i);
            lines.add(Component.literal("#" + (i + 1) + " ").withStyle(i == 0 ? ChatFormatting.GREEN
                            : ChatFormatting.YELLOW)
                    .append(Component.literal(nameOf(entry.getKey())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" · " + Math.round(entry.getValue()) + " degats · +"
                            + MoneyFormat.exact(elo[i]) + " ELO").withStyle(ChatFormatting.GRAY)));
        }
        lines.add(Component.literal("----------------------------------------")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));

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
            MutableComponent mine = Component.literal("Ta faction » ").withStyle(ChatFormatting.GRAY);
            if (place < 0) {
                player.sendSystemMessage(mine.append(Component.literal("non classee").withStyle(ChatFormatting.RED)));
                continue;
            }
            player.sendSystemMessage(mine.append(Component.literal((place == 0 ? "1re" : (place + 1) + "e")
                    + " place").withStyle(ChatFormatting.YELLOW)));
            FactionApi.addElo(player, elo[place], "Boss " + kind.label());
            FactionApi.stat(player, "event:boss", 1);
        }
    }

    // -- Joueurs ---------------------------------------------------------------

    private static void tellArena(ServerLevel level, String text, ChatFormatting color) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal(text).withStyle(color));
        }
    }

    /** Les noms des factions rivales, pour l'ecran des events. */
    public List<Rival> rivals() {
        return List.copyOf(rivals);
    }

    /** Les degats de la faction du joueur, pour l'ecran des events. */
    public float damageOf(ServerPlayer player) {
        return damage.getOrDefault("p:" + player.getUUID(), 0.0f);
    }

    /** Le rang provisoire du joueur (1 = premier), 0 s'il n'a rien fait. */
    public int rankOf(ServerPlayer player) {
        float mine = damageOf(player);
        if (mine <= 0.0f) {
            return 0;
        }
        int rank = 1;
        for (float other : damage.values()) {
            if (other > mine) {
                rank++;
            }
        }
        return rank;
    }
}
