package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une partie : un groupe, un donjon, un palier.
 *
 * <p>Trois salles par palier. La premiere est un combat ; a la fin de chaque
 * salle, des portes proposent la suivante — une a trois au choix entre
 * combat, enigme, boost et marchande, sauf la troisieme d'un palier en 5 ou
 * en 0, qui est celle du mini-boss ou du boss. Apres la troisieme, la porte
 * de sortie : le palier est reussi.
 *
 * <p>Une salle de combat compte deux vagues, trois a partir du palier 50 ;
 * {@code 3 + palier / 25} monstres par vague, deux de plus par joueur
 * au-dela du premier (« plus de joueurs, plus d'ennemis »). Les monstres ont
 * {@code 1 + 0,06 (palier - 1)} fois leurs points de vie et infligent
 * {@code 1 + 0,03 (palier - 1)} fois leurs degats.
 */
final class DungeonRun {

    enum Phase { FIGHT, PUZZLE, TRAP, OPEN, ENDED }

    /** Pourquoi un joueur quitte la partie. */
    enum Leave { DEATH, QUIT, LOGOUT, ESCAPED }

    static final String TAG = "palamod.dungeon";
    static final String SPLIT_TAG = "palamod.dungeon.split";
    static final String MERCHANT_TAG = "palamod.dungeon.merchant";

    /** Ecart entre deux salles, le long de +z. */
    private static final int ROOM_SPACING = 80;
    /** Points de vie maximum d'un monstre vanilla (le plafond de l'attribut). */
    private static final double HEALTH_CAP = 1024.0d;
    private static final int BOOST_TICKS = 20 * 60 * 10;

    final int id;
    final Dungeon dungeon;
    final int palier;
    /** Facile, Normal ou Difficile ; toujours Normal pour un boss de metier. */
    final DungeonMode mode;
    /** Le boss de metier de cette partie, ou {@code null} pour un palier. */
    final JobBosses.Fight jobBoss;
    final UUID leader;
    final BlockPos origin;
    final RandomSource random = RandomSource.create();
    private final ServerLevel level;

    final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    final Map<UUID, List<ItemStack>> bags = new HashMap<>();
    private final Map<UUID, Integer> capacities = new HashMap<>();
    private final Map<UUID, List<Holder<MobEffect>>> boosts = new HashMap<>();

    private int roomIndex;
    DungeonRoom room;
    private final List<DungeonRoom> built = new ArrayList<>();
    private List<DungeonRoom.Door> doors = List.of();
    Phase phase = Phase.FIGHT;

    /**
     * Les monstres vivants de la salle, et leur type. On garde les entites
     * elles-memes, pas leurs UUID : une entite tout juste ajoutee n'est pas
     * encore visible de {@code ServerLevel.getEntity}, et la croire morte
     * finirait la salle avant le combat.
     */
    final Map<Mob, MobKind> enemies = new LinkedHashMap<>();
    final List<Mob> bosses = new ArrayList<>();
    /** Tout ce que la partie a fait apparaitre, pour l'effacer a coup sur. */
    private final List<Entity> spawned = new ArrayList<>();
    private BossSpec bossSpec;
    private ServerBossEvent bar;
    private int abilityCooldown;
    private int wave;
    private int waves;
    private int nextWaveIn;

    /** Le coffre de la salle finie ({@link DungeonProps}), ou {@code null}. */
    Mob chest;
    /** Les jarres cassees, et le tick ou leurs eclats disparaissent. */
    private final Map<Entity, Integer> brokenJars = new HashMap<>();
    final Set<UUID> looted = new HashSet<>();
    private int buttonsTotal;
    List<Boost> boostOptions = List.of();
    final Set<UUID> boosted = new HashSet<>();
    private Villager merchant;
    private int ticks;

    /** Ce qui fait la note : degats infliges, vie perdue, monstres tues. */
    static final class Stats {
        float dealt;
        float taken;
        int kills;
    }

    private final Map<UUID, Stats> stats = new HashMap<>();
    /** Les salles de combat et de boss terminees. */
    private int fights;

    // -- Salles et boss des modes (DoHy) ------------------------------------------

    /** Un boss Dark est passe dans sa seconde phase ; un boss de metier, enrage. */
    private boolean secondPhase;
    /** Les dalles de la salle piegee, par rangee, et ce qu'elles etaient. */
    private List<List<BlockPos>> trapRows = List.of();
    private final Map<BlockPos, BlockState> trapFloor = new HashMap<>();
    private final Set<BlockPos> spikes = new HashSet<>();
    private BlockPos trapButton;
    /** La salle de survie : ticks restants, sur combien. */
    private int survivalLeft;
    private int survivalTotal;
    /** Une zone nocive au sol (nuage toxique) : centre, rayon, tick de fin. */
    private record Hazard(Vec3 center, double radius, int until) {
    }

    private final List<Hazard> hazards = new ArrayList<>();
    /** Des blocs poses pour un temps (toiles), et le tick ou ils partent. */
    private final Map<BlockPos, Integer> temporary = new HashMap<>();
    /** Les ombres d'un boss Dark, et le tick ou elles s'effacent. */
    private final Map<Mob, Integer> shadows = new HashMap<>();

    DungeonRun(int id, ServerLevel level, Dungeon dungeon, int palier, ServerPlayer leader, List<ServerPlayer> party,
               BlockPos origin, DungeonMode mode, JobBosses.Fight jobBoss) {
        this.id = id;
        this.level = level;
        this.dungeon = dungeon;
        this.palier = palier;
        this.mode = mode;
        this.jobBoss = jobBoss;
        this.leader = leader.getUUID();
        this.origin = origin;
        for (ServerPlayer player : party) {
            members.add(player.getUUID());
            names.put(player.getUUID(), player.getGameProfile().name());
            bags.put(player.getUUID(), new ArrayList<>());
            capacities.put(player.getUUID(), DungeonBackpackItem.capacityOf(player));
            boosts.put(player.getUUID(), new ArrayList<>());
        }
    }

    ServerLevel level() {
        return level;
    }

    // -- Chiffres ---------------------------------------------------------------

    float damageFactor() {
        return (1.0f + 0.03f * (palier - 1)) * mode.mobDamage();
    }

    double healthFactor() {
        return (1.0d + 0.06d * (palier - 1)) * mode.mobHealth();
    }

    Band band() {
        return Band.of(palier);
    }

    // -- Membres ----------------------------------------------------------------

    List<ServerPlayer> onlineMembers(ServerLevel ignored) {
        return onlineMembers();
    }

    List<ServerPlayer> onlineMembers() {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID uuid : members) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && player.level() == level && player.isAlive()) {
                players.add(player);
            }
        }
        return players;
    }

    boolean insideRoom(Vec3 position) {
        return room != null && position.x > room.interior.minX + 0.5d && position.x < room.interior.maxX - 0.5d
                && position.z > room.interior.minZ + 0.5d && position.z < room.interior.maxZ - 0.5d;
    }

    private ServerPlayer nearest(Entity from) {
        ServerPlayer best = null;
        double distance = Double.MAX_VALUE;
        for (ServerPlayer player : onlineMembers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            double candidate = player.distanceToSqr(from);
            if (candidate < distance) {
                distance = candidate;
                best = player;
            }
        }
        return best;
    }

    void broadcast(ChatFormatting color, String key, Object... args) {
        for (ServerPlayer player : onlineMembers()) {
            DungeonManager.message(player, color, key, args);
        }
    }

    private void title(Component title, Component subtitle) {
        for (ServerPlayer player : onlineMembers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    // -- Deroulement --------------------------------------------------------------

    /** Construit la premiere salle et y conduit le groupe. */
    void begin(List<ServerPlayer> party) {
        room = DungeonRoom.build(level, dungeon, jobBoss != null ? RoomType.BOSS : RoomType.COMBAT, roomCenter(0),
                random);
        built.add(room);
        Vec3 entrance = room.entrance();
        int index = 0;
        for (ServerPlayer player : party) {
            Vec3 spot = entrance.add((index++ - (party.size() - 1) / 2.0d) * 1.5d, 0.0d, 0.0d);
            player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, spot, Vec3.ZERO,
                    0.0f, 0.0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        }
        if (jobBoss != null) {
            title(Component.literal(jobBoss.spec().name()).withStyle(dungeon.color(), ChatFormatting.BOLD),
                    Component.literal("Boss de metier · niveau " + jobBoss.level()).withStyle(ChatFormatting.GRAY));
        } else {
            title(Component.literal(dungeon.display()).withStyle(dungeon.color(), ChatFormatting.BOLD),
                    Component.literal("Palier " + palier + " — " + band().label() + " · " + mode.label())
                            .withStyle(band().color()));
        }
        enter();
    }

    /** Un message en toutes lettres a tout le groupe. */
    void say(ChatFormatting color, String text) {
        for (ServerPlayer player : onlineMembers()) {
            player.sendSystemMessage(Component.literal(text).withStyle(color));
        }
    }

    private BlockPos roomCenter(int index) {
        return origin.offset(0, 0, index * ROOM_SPACING);
    }

    /** Ce que la salle courante contient, a l'arrivee. */
    private void enter() {
        enemies.clear();
        bosses.clear();
        doors = List.of();
        chest = null;
        looted.clear();
        brokenJars.clear();
        boostOptions = List.of();
        boosted.clear();
        merchant = null;
        switch (room.type) {
            case COMBAT -> {
                placeJars(3 + random.nextInt(3));
                phase = Phase.FIGHT;
                wave = 0;
                waves = palier >= 50 ? 3 : 2;
                nextWaveIn = 30;
            }
            case PUZZLE -> {
                placeJars(2 + random.nextInt(3));
                phase = Phase.PUZZLE;
                room.placeButtons(level, dungeon.palette(), random, 3 + random.nextInt(3));
                buttonsTotal = room.buttons.size();
                broadcast(ChatFormatting.BLUE, "puzzle", buttonsTotal);
            }
            case BOOST -> {
                List<Boost> all = new ArrayList<>(List.of(Boost.values()));
                Collections.shuffle(all, new java.util.Random(random.nextLong()));
                boostOptions = List.copyOf(all.subList(0, 3));
                broadcast(ChatFormatting.GREEN, "boost");
                openDoors();
            }
            case MERCHANT -> {
                spawnMerchant();
                broadcast(ChatFormatting.YELLOW, "merchant");
                openDoors();
            }
            case BOSS, ELITE -> {
                placeJars(room.type == RoomType.BOSS ? 4 + random.nextInt(3) : 2 + random.nextInt(2));
                phase = Phase.FIGHT;
                spawnBosses();
            }
            case TRAP -> {
                phase = Phase.TRAP;
                trapRows = room.trapRows();
                trapFloor.clear();
                spikes.clear();
                for (List<BlockPos> row : trapRows) {
                    for (BlockPos tile : row) {
                        trapFloor.put(tile, level.getBlockState(tile));
                    }
                }
                trapButton = room.placeTrapButton(level, dungeon.palette());
                say(ChatFormatting.DARK_GRAY, "Salle piegee : le sol s'embrase par rangees. Traverse jusqu'au bouton"
                        + " du fond.");
            }
            case SURVIVAL -> {
                placeJars(2 + random.nextInt(2));
                phase = Phase.FIGHT;
                survivalTotal = 20 * (40 + Math.min(20, palier / 5));
                survivalLeft = survivalTotal;
                nextWaveIn = 0;
                bar = new ServerBossEvent(UUID.randomUUID(), survivalTitle(), BossEvent.BossBarColor.PINK,
                        BossEvent.BossBarOverlay.PROGRESS);
                for (ServerPlayer player : onlineMembers()) {
                    bar.addPlayer(player);
                }
                say(ChatFormatting.LIGHT_PURPLE, "Salle de survie : tiens " + survivalTotal / 20
                        + " secondes, les renforts ne s'arretent pas.");
                level.playSound(null, room.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 0.8f);
            }
            case EXIT -> {
            }
        }
    }

    private Component survivalTitle() {
        return Component.literal("Survie · " + Math.max(0, (survivalLeft + 19) / 20) + " s")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    void tick() {
        if (phase == Phase.ENDED) {
            return;
        }
        ticks++;
        if (!brokenJars.isEmpty()) {
            sweepJars();
        }
        if (ticks % 20 == 0) {
            keepInside();
        }
        if (!hazards.isEmpty() && ticks % 10 == 0) {
            tickHazards();
        }
        if (!temporary.isEmpty() && ticks % 5 == 0) {
            sweepTemporary();
        }
        if (!shadows.isEmpty() && ticks % 10 == 0) {
            sweepShadows();
        }
        if (phase == Phase.TRAP) {
            tickTrap();
            return;
        }
        if (phase == Phase.FIGHT && room.type == RoomType.SURVIVAL) {
            tickSurvival();
            return;
        }
        if (phase == Phase.FIGHT) {
            if (nextWaveIn > 0 && --nextWaveIn == 0) {
                spawnWave();
            }
            if (ticks % 10 == 0 && nextWaveIn == 0) {
                checkFight();
            }
            if (ticks % 40 == 0) {
                retarget();
            }
            if (ticks % 60 == 0) {
                magePulse();
            }
            if (!bosses.isEmpty()) {
                tickBosses();
            }
        } else if (phase == Phase.OPEN && ticks % 5 == 0) {
            checkDoors();
        }
    }

    /** Un joueur sorti de la salle (perle, chorus, toit) y est ramene. */
    private void keepInside() {
        AABB box = room.outer();
        for (ServerPlayer player : onlineMembers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (!box.contains(player.position()) || player.getY() > room.center.getY() + room.height) {
                Vec3 entrance = room.entrance();
                player.teleportTo(entrance.x, entrance.y, entrance.z);
                player.fallDistance = 0.0f;
            }
        }
    }

    // -- Salle piegee ------------------------------------------------------------

    /** Une periode du sol piege : alerte, puis piques, puis repos. */
    private static final int TRAP_PERIOD = 60;
    private static final int TRAP_WARN = 15;
    private static final int TRAP_ACTIVE = 35;

    /**
     * Les rangees s'embrasent l'une apres l'autre, decalees de douze ticks : une
     * vague qui avance vers le fond. En alerte, la dalle devient du magma ; puis
     * des piques en sortent et blessent qui s'y tient ; puis le sol revient.
     */
    private void tickTrap() {
        for (int index = 0; index < trapRows.size(); index++) {
            int local = Math.floorMod(ticks + index * 12, TRAP_PERIOD);
            List<BlockPos> row = trapRows.get(index);
            if (local == 0) {
                for (BlockPos tile : row) {
                    level.setBlock(tile, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                level.playSound(null, row.get(row.size() / 2), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS,
                        0.4f, 1.6f);
            } else if (local == TRAP_WARN) {
                for (BlockPos tile : row) {
                    BlockPos above = tile.above();
                    if (level.getBlockState(above).isAir() && !occupied(above)) {
                        // l'etat par defaut est une pointe ; on la tourne vers le haut
                        level.setBlock(above, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP), Block.UPDATE_CLIENTS);
                        spikes.add(above);
                    }
                }
                level.playSound(null, row.get(row.size() / 2), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS,
                        0.8f, 0.8f);
            } else if (local == TRAP_ACTIVE) {
                lowerRow(row);
            }
            if (local >= TRAP_WARN && local < TRAP_ACTIVE && ticks % 5 == 0) {
                BlockPos middle = row.get(row.size() / 2);
                level.sendParticles(ParticleTypes.FLAME, middle.getX() + 0.5d, middle.getY() + 1.1d,
                        middle.getZ() + 0.5d, 6, room.half / 2.0d, 0.05d, 0.2d, 0.01d);
            }
        }
        if (ticks % 5 == 0) {
            for (ServerPlayer player : onlineMembers()) {
                if (player.isSpectator() || player.isCreative()) {
                    continue;
                }
                BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2d, player.getZ());
                int rowIndex = trapRowOf(below);
                if (rowIndex >= 0) {
                    int local = Math.floorMod(ticks + rowIndex * 12, TRAP_PERIOD);
                    if (local >= TRAP_WARN && local < TRAP_ACTIVE) {
                        float damage = 4.0f * (1.0f + palier / 50.0f) * mode.mobDamage();
                        player.hurtServer(level, level.damageSources().generic(), damage);
                        player.setDeltaMovement(player.getDeltaMovement().x, 0.45d, player.getDeltaMovement().z);
                        player.hurtMarked = true;
                    }
                }
            }
        }
    }

    private int trapRowOf(BlockPos floor) {
        for (int index = 0; index < trapRows.size(); index++) {
            List<BlockPos> row = trapRows.get(index);
            BlockPos first = row.get(0);
            if (floor.getZ() == first.getZ() && floor.getY() == first.getY() && floor.getX() >= first.getX()
                    && floor.getX() <= row.get(row.size() - 1).getX()) {
                return index;
            }
        }
        return -1;
    }

    private boolean occupied(BlockPos pos) {
        AABB box = new AABB(pos);
        for (ServerPlayer player : onlineMembers()) {
            if (player.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    /** Une rangee revient a son sol : piques rentres, dalles d'origine. */
    private void lowerRow(List<BlockPos> row) {
        for (BlockPos tile : row) {
            BlockPos above = tile.above();
            if (spikes.remove(above) && level.getBlockState(above).is(Blocks.POINTED_DRIPSTONE)) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockState original = trapFloor.get(tile);
            if (original != null) {
                level.setBlock(tile, original, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Le sol piege s'eteint pour de bon : la salle est traversee. */
    private void disarmTrap() {
        for (List<BlockPos> row : trapRows) {
            lowerRow(row);
        }
        trapRows = List.of();
        trapFloor.clear();
        spikes.clear();
        if (trapButton != null
                && level.getBlockState(trapButton).getBlock() instanceof net.minecraft.world.level.block.ButtonBlock) {
            level.setBlock(trapButton, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        trapButton = null;
    }

    // -- Salle de survie ------------------------------------------------------------

    /**
     * Des renforts toutes les trois secondes, douze monstres au plus en meme
     * temps, jusqu'a la fin du compte a rebours : alors ils s'effacent.
     */
    private void tickSurvival() {
        survivalLeft--;
        if (ticks % 10 == 0) {
            enemies.keySet().removeIf(mob -> {
                if (mob.isRemoved() || !mob.isAlive()) {
                    DungeonManager.untrackMob(mob.getUUID());
                    return true;
                }
                return false;
            });
        }
        if (ticks % 40 == 0) {
            retarget();
        }
        if (ticks % 60 == 0) {
            magePulse();
            int alive = enemies.size();
            int count = Math.max(1, 2 + palier / 40 + Math.max(0, onlineMembers().size() - 1) + mode.extraMobs());
            for (int i = 0; i < count && alive + i < 12; i++) {
                spawnMob(room.spawnPoint(level, random), pickKind(), false);
            }
        }
        if (bar != null && ticks % 10 == 0) {
            bar.setName(survivalTitle());
            bar.setProgress(Math.clamp((float) survivalLeft / Math.max(1, survivalTotal), 0.0f, 1.0f));
            for (ServerPlayer player : onlineMembers()) {
                if (!bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                }
            }
        }
        if (survivalLeft <= 0) {
            for (Mob mob : enemies.keySet()) {
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5d, mob.getZ(), 10, 0.3d, 0.5d,
                        0.3d, 0.02d);
            }
            clearEnemies();
            hideBar();
            say(ChatFormatting.LIGHT_PURPLE, "Tu as tenu : les renforts reculent.");
            roomCleared();
        }
    }

    /** Un type de monstre tire au poids, parmi ceux du palier ; deux mages au plus par appel. */
    private MobKind pickKind() {
        List<MobKind> open = new ArrayList<>();
        int total = 0;
        for (MobKind kind : MobKind.values()) {
            if (palier >= kind.from() && kind != MobKind.MAGE) {
                open.add(kind);
                total += kind.weight();
            }
        }
        int pick = random.nextInt(Math.max(1, total));
        for (MobKind candidate : open) {
            pick -= candidate.weight();
            if (pick < 0) {
                return candidate;
            }
        }
        return MobKind.MELEE;
    }

    // -- Effets de boss poses dans la salle -----------------------------------------

    /** Un nuage toxique : poison a qui s'y tient, jusqu'au tick dit. */
    void addHazard(Vec3 center, double radius, int duration, boolean ignored) {
        hazards.add(new Hazard(center, radius, ticks + duration));
    }

    private void tickHazards() {
        hazards.removeIf(hazard -> ticks >= hazard.until());
        for (Hazard hazard : hazards) {
            level.sendParticles(ParticleTypes.SNEEZE, hazard.center().x, hazard.center().y + 0.3d, hazard.center().z,
                    14, hazard.radius() * 0.6d, 0.2d, hazard.radius() * 0.6d, 0.01d);
            for (ServerPlayer player : onlineMembers()) {
                if (player.position().distanceTo(hazard.center()) <= hazard.radius()) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                }
            }
        }
    }

    /** Pose un bloc pour un temps, s'il y a de l'air ; il part tout seul. */
    void placeTemporary(BlockPos pos, BlockState state, int duration) {
        if (room == null || !insideRoom(Vec3.atBottomCenterOf(pos)) || !level.getBlockState(pos).isAir()) {
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        temporary.put(pos.immutable(), ticks + duration);
    }

    private void sweepTemporary() {
        temporary.entrySet().removeIf(entry -> {
            if (ticks < entry.getValue()) {
                return false;
            }
            if (level.getBlockState(entry.getKey()).is(Blocks.COBWEB)) {
                level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            return true;
        });
    }

    /**
     * Les ombres d'un boss Dark : deux copies plus petites, a 15 % de sa vie,
     * qui s'effacent au bout de vingt secondes si personne ne les abat.
     */
    void spawnShadows(Mob boss) {
        float health = Math.max(20.0f, boss.getMaxHealth() * 0.15f);
        for (int i = 0; i < 2; i++) {
            Mob shadow = bossSpec.type().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (shadow == null) {
                continue;
            }
            Vec3 at = boss.position().add(i == 0 ? -2.5d : 2.5d, 0.0d, 0.0d);
            if (!insideRoom(at)) {
                at = boss.position();
            }
            shadow.snapTo(at.x, at.y, at.z, boss.getYRot(), 0.0f);
            shadow.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()),
                    EntitySpawnReason.MOB_SUMMONED, null);
            if (shadow instanceof Zombie zombie) {
                zombie.setBaby(false);
            }
            base(shadow, Attributes.MAX_HEALTH, health);
            base(shadow, Attributes.SCALE, Math.max(0.6d, bossSpec.scale() * 0.6d));
            base(shadow, Attributes.FOLLOW_RANGE, 48.0d);
            shadow.setHealth(shadow.getMaxHealth());
            shadow.setPersistenceRequired();
            shadow.addTag(TAG);
            shadow.setCustomName(Component.literal("Ombre").withStyle(ChatFormatting.DARK_PURPLE));
            shadow.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
            ServerPlayer target = nearest(shadow);
            if (target != null) {
                shadow.setTarget(target);
            }
            level.addFreshEntity(shadow);
            track(shadow, MobKind.ASSASSIN, 1.0f);
            shadows.put(shadow, ticks + 400);
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0d, at.z, 20, 0.4d, 0.8d, 0.4d, 0.05d);
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0f,
                0.7f);
    }

    private void sweepShadows() {
        shadows.entrySet().removeIf(entry -> {
            Mob shadow = entry.getKey();
            if (shadow.isRemoved() || !shadow.isAlive()) {
                return true;
            }
            if (ticks < entry.getValue()) {
                return false;
            }
            level.sendParticles(ParticleTypes.SQUID_INK, shadow.getX(), shadow.getY() + 1.0d, shadow.getZ(),
                    12, 0.3d, 0.6d, 0.3d, 0.02d);
            enemies.remove(shadow);
            DungeonManager.untrackMob(shadow.getUUID());
            shadow.discard();
            return true;
        });
    }

    // -- Combat -----------------------------------------------------------------

    private void spawnWave() {
        wave++;
        List<ServerPlayer> players = onlineMembers();
        int count = Math.max(2, 3 + palier / 25 + 2 * Math.max(0, players.size() - 1) + mode.extraMobs());
        List<MobKind> open = new ArrayList<>();
        int total = 0;
        for (MobKind kind : MobKind.values()) {
            if (palier >= kind.from()) {
                open.add(kind);
                total += kind.weight();
            }
        }
        int mages = 0;
        for (int i = 0; i < count; i++) {
            int pick = random.nextInt(total);
            MobKind kind = open.get(0);
            for (MobKind candidate : open) {
                pick -= candidate.weight();
                if (pick < 0) {
                    kind = candidate;
                    break;
                }
            }
            if (kind == MobKind.MAGE && ++mages > 2) {
                kind = MobKind.MELEE;
            }
            spawnMob(room.spawnPoint(level, random), kind, false);
        }
        broadcast(ChatFormatting.RED, "wave", wave, waves);
        level.playSound(null, room.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    /** Un monstre de la salle, depuis une capacite de boss. */
    void spawnMinion(ServerLevel ignored, Vec3 near, MobKind kind) {
        BlockPos pos = BlockPos.containing(near.x + random.nextInt(5) - 2, room.center.getY(),
                near.z + random.nextInt(5) - 2);
        if (!insideRoom(Vec3.atBottomCenterOf(pos)) || !level.getBlockState(pos).isAir()) {
            pos = room.spawnPoint(level, random);
        }
        spawnMob(pos, kind, false);
    }

    Mob spawnMob(BlockPos pos, MobKind kind, boolean baby) {
        EntityType<? extends Mob> type = baby ? EntityTypes.DROWNED : dungeon.mob(kind);
        Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null) {
            return null;
        }
        mob.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, random.nextFloat() * 360.0f, 0.0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(baby);
        }
        double health = kind.health() * healthFactor() * (baby ? 0.4d : 1.0d);
        base(mob, Attributes.MAX_HEALTH, Math.min(HEALTH_CAP, health));
        base(mob, Attributes.FOLLOW_RANGE, 48.0d);
        if (kind == MobKind.TANK) {
            base(mob, Attributes.SCALE, 1.35d);
            base(mob, Attributes.KNOCKBACK_RESISTANCE, 0.9d);
            base(mob, Attributes.ARMOR, 8.0d);
        }
        if (kind == MobKind.MAGE) {
            mob.setNoAi(true);
        }
        if (kind == MobKind.RANGED && dungeon == Dungeon.MANELIOS) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        }
        if (kind == MobKind.MELEE && dungeon == Dungeon.MANELIOS && !baby) {
            mob.addTag(SPLIT_TAG);
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        mob.setCustomName(Component.literal(dungeon.mobName(kind)).withStyle(band().color()));
        RpgSkins.dress(mob, dungeon, kind, baby, band());
        ServerPlayer target = nearest(mob);
        if (target != null && kind != MobKind.MAGE) {
            mob.setTarget(target);
        }
        level.addFreshEntity(mob);
        RpgSkins.playSpawn(mob);
        track(mob, kind, 1.0f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.5d, mob.getZ(),
                8, 0.3d, 0.5d, 0.3d, 0.01d);
        return mob;
    }

    /** Fait d'un monstre un monstre de la partie : compte, facteur de degats. */
    void track(Mob mob, MobKind kind, float taken) {
        enemies.put(mob, kind);
        spawned.add(mob);
        DungeonManager.trackMob(mob.getUUID(), this, taken);
    }

    private static void base(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void checkFight() {
        Iterator<Map.Entry<Mob, MobKind>> iterator = enemies.entrySet().iterator();
        while (iterator.hasNext()) {
            Mob mob = iterator.next().getKey();
            if (mob.isRemoved() || !mob.isAlive()) {
                iterator.remove();
                DungeonManager.untrackMob(mob.getUUID());
            }
        }
        if (room.type == RoomType.BOSS || room.type == RoomType.ELITE) {
            bosses.removeIf(boss -> !enemies.containsKey(boss));
            if (bar != null) {
                updateBar();
            }
            if (bosses.isEmpty()) {
                broadcast(ChatFormatting.GOLD, "boss_dead", bossSpec.name());
                clearEnemies();
                hideBar();
                if (jobBoss != null) {
                    // Le gardien tombe : niveau, XP de metier et argent tout de suite, le coffre au centre.
                    for (ServerPlayer player : onlineMembers()) {
                        fr.atraxterra.dohy.jobboss.JobBossApi.defeated(player, jobBoss.job(), jobBoss.level(),
                                player.getUUID().equals(leader));
                    }
                }
                roomCleared();
            }
            return;
        }
        if (!enemies.isEmpty()) {
            return;
        }
        if (wave < waves) {
            nextWaveIn = 40;
        } else {
            roomCleared();
        }
    }

    /** Les monstres retrouvent leur cible : un joueur du groupe. */
    private void retarget() {
        for (Mob mob : enemies.keySet()) {
            if (mob.isAlive() && !mob.isNoAi()) {
                LivingEntity current = mob.getTarget();
                if (current == null || !current.isAlive() || !(current instanceof Player)) {
                    ServerPlayer target = nearest(mob);
                    if (target != null) {
                        mob.setTarget(target);
                    }
                }
            }
        }
    }

    /**
     * Le mage « ne fait aucun degat » : il renforce les monstres proches
     * (force et vitesse) ou gene les joueurs proches (lenteur et faiblesse),
     * en alternance.
     */
    private void magePulse() {
        boolean buff = (ticks / 60) % 2 == 0;
        for (Map.Entry<Mob, MobKind> entry : enemies.entrySet()) {
            Mob mage = entry.getKey();
            if (entry.getValue() != MobKind.MAGE || !mage.isAlive()) {
                continue;
            }
            if (buff) {
                for (Mob ally : enemies.keySet()) {
                    if (ally != mage && ally.isAlive() && ally.distanceTo(mage) <= 10.0f) {
                        ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
                        ally.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0));
                    }
                }
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mage.getX(), mage.getY() + 2.0d, mage.getZ(),
                        6, 0.5d, 0.3d, 0.5d, 0.0d);
            } else {
                for (ServerPlayer player : onlineMembers()) {
                    if (player.distanceTo(mage) <= 10.0f) {
                        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
                        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                    }
                }
                level.sendParticles(ParticleTypes.WITCH, mage.getX(), mage.getY() + 1.0d, mage.getZ(),
                        20, 2.0d, 0.5d, 2.0d, 0.0d);
            }
        }
    }

    private void clearEnemies() {
        for (Mob mob : new ArrayList<>(enemies.keySet())) {
            mob.discard();
            DungeonManager.untrackMob(mob.getUUID());
        }
        enemies.clear();
    }

    // -- Boss -------------------------------------------------------------------

    private void spawnBosses() {
        boolean mini;
        double health;
        if (jobBoss != null) {
            bossSpec = jobBoss.spec();
            mini = false;
            health = JobBosses.health(jobBoss.level());
        } else if (room.type == RoomType.ELITE) {
            List<BossSpec> elites = dungeon.elites();
            bossSpec = elites.get(random.nextInt(elites.size()));
            mini = true;
            health = 120.0d * (1.0d + 0.05d * (palier - 1)) * mode.bossHealth();
        } else {
            bossSpec = dungeon.bossFor(palier);
            if (bossSpec == null) {
                roomCleared();
                return;
            }
            mini = palier % 10 != 0;
            if (!mini && mode.darkBosses()) {
                bossSpec = bossSpec.darkForm();
            }
            health = (mini ? 120.0d : 250.0d) * (1.0d + 0.05d * (palier - 1)) * mode.bossHealth()
                    * (bossSpec.dark() ? 1.4d : 1.0d);
        }
        secondPhase = false;
        int players = Math.max(1, onlineMembers().size());
        health *= 1.0d + 0.6d * (players - 1);
        ChatFormatting nameColor = bossSpec.dark() ? ChatFormatting.DARK_PURPLE : dungeon.color();
        int count = bossSpec.duo() ? 2 : 1;
        if (bossSpec.duo()) {
            health *= 0.6d;
        }
        for (int i = 0; i < count; i++) {
            BlockPos pos = room.center.offset(count == 1 ? 0 : (i == 0 ? -4 : 4), 0, room.half / 2);
            Mob boss = bossSpec.type().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (boss == null) {
                continue;
            }
            boss.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 180.0f, 0.0f);
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            if (boss instanceof Zombie zombie) {
                zombie.setBaby(false);
            }
            // Au-dela du plafond de 1024 points de vie, le boss encaisse moins.
            double capped = Math.min(HEALTH_CAP, health);
            base(boss, Attributes.MAX_HEALTH, capped);
            base(boss, Attributes.SCALE, bossSpec.scale());
            base(boss, Attributes.FOLLOW_RANGE, 64.0d);
            base(boss, Attributes.KNOCKBACK_RESISTANCE, 0.6d);
            boss.setHealth(boss.getMaxHealth());
            boss.setPersistenceRequired();
            boss.addTag(TAG);
            boss.setCustomName(Component.literal(bossSpec.name()).withStyle(nameColor, ChatFormatting.BOLD));
            boss.setCustomNameVisible(true);
            RpgSkins.dressBoss(boss, bossSpec.skin(), bossSpec.dark() ? Band.BLACK : band());
            equip(boss, bossSpec);
            ServerPlayer target = nearest(boss);
            if (target != null) {
                boss.setTarget(target);
            }
            level.addFreshEntity(boss);
            RpgSkins.playSpawn(boss);
            track(boss, MobKind.TANK, (float) (capped / health));
            bosses.add(boss);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        BossEvent.BossBarColor color = jobBoss != null ? switch (jobBoss.job()) {
            case FARMER -> BossEvent.BossBarColor.GREEN;
            case MINER -> BossEvent.BossBarColor.WHITE;
            case HUNTER -> BossEvent.BossBarColor.RED;
            case ALCHEMIST -> BossEvent.BossBarColor.BLUE;
        } : bossSpec.dark() ? BossEvent.BossBarColor.PURPLE : room.type == RoomType.ELITE
                ? BossEvent.BossBarColor.YELLOW : mini ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE;
        bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(bossSpec.name()).withStyle(nameColor), color,
                bossSpec.dark() ? BossEvent.BossBarOverlay.NOTCHED_20 : BossEvent.BossBarOverlay.NOTCHED_10);
        for (ServerPlayer player : onlineMembers()) {
            bar.addPlayer(player);
        }
        abilityCooldown = 100;
        String kind = jobBoss != null ? "Boss de metier · niveau " + jobBoss.level()
                : room.type == RoomType.ELITE ? "Elite" : bossSpec.dark() ? "Boss Dark" : mini ? "Mini-boss" : "Boss";
        title(Component.literal(bossSpec.name()).withStyle(nameColor, ChatFormatting.BOLD),
                Component.literal(kind).withStyle(bossSpec.dark() ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY));
        broadcast(ChatFormatting.DARK_PURPLE, "boss", bossSpec.name());
        level.playSound(null, room.center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 1.0f);
    }

    /** Ce que porte un elite pour se reconnaitre : une tete de citrouille, des outils. */
    private static void equip(Mob boss, BossSpec spec) {
        switch (spec.name()) {
            case "Epouvantail" -> boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            case "Faucheuse" -> boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_HOE));
            case "Contremaitre" -> {
                boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            }
            case "Sirene" -> boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            default -> {
            }
        }
    }

    private void tickBosses() {
        if (ticks % 5 == 0 && bar != null) {
            updateBar();
        }
        List<Mob> alive = new ArrayList<>();
        for (Mob boss : bosses) {
            if (boss.isAlive() && !boss.isRemoved()) {
                alive.add(boss);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        if (bossSpec.dark() && ticks % 10 == 0) {
            for (Mob boss : alive) {
                level.sendParticles(ParticleTypes.SMOKE, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5d,
                        boss.getZ(), 8, boss.getBbWidth() * 0.6d, boss.getBbHeight() * 0.4d, boss.getBbWidth() * 0.6d,
                        0.01d);
                level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + 0.2d, boss.getZ(), 2,
                        boss.getBbWidth() * 0.6d, 0.1d, boss.getBbWidth() * 0.6d, 0.02d);
            }
        }
        if (!secondPhase && ticks % 10 == 0) {
            checkSecondPhase(alive);
        }
        if (ticks % 20 == 0) {
            for (Mob boss : alive) {
                if (!insideRoom(boss.position())) {
                    boss.teleportTo(room.center.getX() + 0.5d, room.center.getY(), room.center.getZ() + 0.5d);
                }
                ServerPlayer target = nearest(boss);
                if (target != null && boss.getTarget() != target) {
                    boss.setTarget(target);
                }
            }
        }
        if (--abilityCooldown > 0) {
            return;
        }
        abilityCooldown = Math.max(70, 140 - palier / 2) + random.nextInt(40);
        if (secondPhase) {
            abilityCooldown = Math.round(abilityCooldown * (bossSpec.dark() ? 0.6f : 0.7f));
        }
        Mob boss = alive.get(random.nextInt(alive.size()));
        ServerPlayer target = nearest(boss);
        if (target != null && !bossSpec.abilities().isEmpty()) {
            BossAbility ability = bossSpec.abilities().get(random.nextInt(bossSpec.abilities().size()));
            ability.perform(this, boss, level, target);
        }
    }

    /**
     * La seconde phase : un boss Dark sous la moitie de sa vie s'eveille
     * (resistance, vitesse, capacites plus frequentes) ; un boss de metier sous
     * le tiers s'enrage (force, capacites plus frequentes).
     */
    private void checkSecondPhase(List<Mob> alive) {
        if (!bossSpec.dark() && jobBoss == null) {
            return;
        }
        float health = 0.0f;
        float max = 0.0f;
        for (Mob boss : bosses) {
            if (boss.isAlive() && !boss.isRemoved()) {
                health += boss.getHealth();
                max += boss.getMaxHealth();
            }
        }
        float threshold = bossSpec.dark() ? 0.5f : 0.35f;
        if (max <= 0.0f || health / max > threshold) {
            return;
        }
        secondPhase = true;
        for (Mob boss : alive) {
            if (bossSpec.dark()) {
                boss.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60 * 30, 0));
                boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60 * 30, 0));
            } else {
                boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 60 * 30, 0));
            }
            level.sendParticles(ParticleTypes.SONIC_BOOM, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5d,
                    boss.getZ(), 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        if (bossSpec.dark()) {
            title(Component.literal("Forme Dark").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                    Component.literal(bossSpec.name() + " s'eveille").withStyle(ChatFormatting.GRAY));
            say(ChatFormatting.DARK_PURPLE, bossSpec.name() + " s'eveille : plus vite, plus dur, plus souvent.");
            level.playSound(null, room.center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 0.6f);
        } else {
            say(ChatFormatting.RED, bossSpec.name() + " entre en rage !");
            level.playSound(null, room.center, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
    }

    private void updateBar() {
        float health = 0.0f;
        float max = 0.0f;
        for (Mob boss : bosses) {
            if (boss.isAlive() && !boss.isRemoved()) {
                health += boss.getHealth();
                max += boss.getMaxHealth();
            }
        }
        bar.setProgress(max <= 0.0f ? 0.0f : Math.clamp(health / max, 0.0f, 1.0f));
        for (ServerPlayer player : onlineMembers()) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    private void hideBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }

    // -- Enigme -----------------------------------------------------------------

    /** Un bouton presse ; vrai s'il etait de l'enigme. */
    boolean pressButton(BlockPos pos) {
        if (phase == Phase.TRAP && pos.equals(trapButton)) {
            disarmTrap();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                    16, 0.4d, 0.4d, 0.4d, 0.0d);
            say(ChatFormatting.GREEN, "Le piege s'eteint.");
            roomCleared();
            return true;
        }
        if (phase != Phase.PUZZLE || !room.buttons.remove(pos)) {
            return false;
        }
        int found = buttonsTotal - room.buttons.size();
        broadcast(ChatFormatting.AQUA, "puzzle_progress", found, buttonsTotal);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                10, 0.3d, 0.3d, 0.3d, 0.0d);
        if (room.buttons.isEmpty()) {
            roomCleared();
        }
        return true;
    }

    // -- Boost et marchande -------------------------------------------------------

    /** Applique un boost ; faux si le joueur en a deja pris un dans cette salle. */
    boolean takeBoost(ServerPlayer player, Boost boost) {
        if (room.type != RoomType.BOOST || !boostOptions.contains(boost) || !boosted.add(player.getUUID())) {
            return false;
        }
        player.addEffect(new MobEffectInstance(boost.effect(), BOOST_TICKS, boost.amplifier()));
        boosts.computeIfAbsent(player.getUUID(), uuid -> new ArrayList<>()).add(boost.effect());
        return true;
    }

    private void removeBoosts(ServerPlayer player) {
        List<Holder<MobEffect>> taken = boosts.get(player.getUUID());
        if (taken != null) {
            for (Holder<MobEffect> effect : taken) {
                player.removeEffect(effect);
            }
            taken.clear();
        }
    }

    private void spawnMerchant() {
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (villager == null) {
            return;
        }
        BlockPos pos = room.center.offset(0, 0, 2);
        villager.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 180.0f, 0.0f);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setPersistenceRequired();
        villager.addTag(TAG);
        villager.addTag(MERCHANT_TAG);
        // Les deux marchands de PalaRPG, au hasard : le villageois n'est plus que leur support.
        boolean mammon = random.nextBoolean();
        villager.setSilent(true);
        villager.setData(PalaDungeon.SKIN.get(),
                new RpgSkin(mammon ? "merchant_mammon" : "merchant_mephisto", "default", 1.0f, "spawn>idle"));
        villager.setCustomName(Component.literal(mammon ? "Mammon" : "Mephisto")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        villager.setCustomNameVisible(true);
        level.addFreshEntity(villager);
        merchant = villager;
        spawned.add(villager);
    }

    // -- Fin de salle, coffre et portes ---------------------------------------------

    private void roomCleared() {
        if (room.type == RoomType.COMBAT || room.type == RoomType.BOSS || room.type == RoomType.ELITE
                || room.type == RoomType.SURVIVAL) {
            fights++;
        }
        // XP RPG (wiki : « chaque salle terminee ») ; le triple pour un boss, le double pour un elite.
        if (room.type != RoomType.BOOST && room.type != RoomType.MERCHANT && room.type != RoomType.EXIT) {
            long xp = (10L + 2L * palier) * (room.type == RoomType.BOSS ? 3L : room.type == RoomType.ELITE ? 2L : 1L);
            for (ServerPlayer player : onlineMembers()) {
                // Arbre de technologie, branche Globale : +5 a +20 % d'XP de donjon.
                float bonus = fr.atraxterra.dohy.faction.FactionApi.perks(player).dungeonXp();
                Rpg.grantXp(player, Math.round(xp * (1.0d + bonus) * mode.rewards()));
            }
        }
        if (room.type.loot()) {
            BlockPos at = room.chestPos();
            chest = DungeonProps.spawn(level, at.getX() + 0.5d, at.getY(), at.getZ() + 0.5d, 180.0f,
                    new RpgSkin("dungeon_chest", chestTier(), 1.0f, "idle"), 1.25d, DungeonProps.CHEST_TAG);
            if (chest != null) {
                spawned.add(chest);
                DungeonProps.sound(level, chest, "dungeon.loot.spawn", 1.0f);
            }
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5d, at.getY() + 1.0d,
                    at.getZ() + 0.5d, 30, 0.4d, 0.6d, 0.4d, 0.2d);
            broadcast(ChatFormatting.GOLD, "chest");
        }
        level.playSound(null, room.center, SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 1.0f);
        broadcast(ChatFormatting.GREEN, "room_cleared");
        openDoors();
    }

    private void openDoors() {
        phase = Phase.OPEN;
        doors = room.openDoors(level, nextOptions());
    }

    /**
     * Les portes de la salle suivante : une a trois, tirees au poids parmi les
     * salles ouvertes au palier — l'elite des le palier 3, le piege des le 2, la
     * survie des le 8 (DoHy). Un boss de metier n'a que la sortie.
     */
    private List<RoomType> nextOptions() {
        if (jobBoss != null || roomIndex >= 2) {
            return List.of(RoomType.EXIT);
        }
        if (roomIndex == 1 && dungeon.bossFor(palier) != null) {
            return List.of(RoomType.BOSS);
        }
        Map<RoomType, Integer> weights = new LinkedHashMap<>();
        weights.put(RoomType.COMBAT, 30);
        weights.put(RoomType.PUZZLE, 15);
        weights.put(RoomType.BOOST, 15);
        weights.put(RoomType.MERCHANT, 14);
        if (palier >= 3) {
            weights.put(RoomType.ELITE, 12);
        }
        if (palier >= 2) {
            weights.put(RoomType.TRAP, 12);
        }
        if (palier >= 8) {
            weights.put(RoomType.SURVIVAL, 10);
        }
        int wanted = 1 + random.nextInt(3);
        List<RoomType> chosen = new ArrayList<>();
        while (chosen.size() < wanted && !weights.isEmpty()) {
            int total = 0;
            for (int weight : weights.values()) {
                total += weight;
            }
            int pick = random.nextInt(total);
            for (Map.Entry<RoomType, Integer> entry : weights.entrySet()) {
                pick -= entry.getValue();
                if (pick < 0) {
                    chosen.add(entry.getKey());
                    weights.remove(entry.getKey());
                    break;
                }
            }
        }
        Collections.shuffle(chosen, new java.util.Random(random.nextLong()));
        return List.copyOf(chosen);
    }

    // -- Jarres -------------------------------------------------------------------

    /** Des jarres le long des murs : a casser pour un petit butin. */
    private void placeJars(int count) {
        for (BlockPos spot : room.jarSpots(level, random, count)) {
            Mob jar = DungeonProps.spawn(level, spot.getX() + 0.5d, spot.getY(), spot.getZ() + 0.5d,
                    random.nextInt(4) * 90.0f, new RpgSkin("dungeon_jar", "default", 1.0f, "idle"), 0.95d,
                    DungeonProps.JAR_TAG);
            if (jar != null) {
                spawned.add(jar);
            }
        }
    }

    /** Un joueur frappe une jarre : elle se brise, et une fois sur quatre lache un tirage du donjon. */
    void breakJar(ServerPlayer player, Entity jar) {
        if (!members.contains(player.getUUID()) || brokenJars.containsKey(jar) || jar.isRemoved()) {
            return;
        }
        brokenJars.put(jar, ticks + 50);
        DungeonProps.pose(jar, "breaking>destroyed");
        DungeonProps.sound(level, jar, "dungeon.jar.break", 0.9f);
        List<ItemStack> loot = DungeonRewards.jar(level, random, dungeon, palier);
        if (!loot.isEmpty()) {
            DungeonManager.message(player, ChatFormatting.GOLD, "jar_loot", describe(loot));
            addToBag(player, loot);
        }
    }

    private void sweepJars() {
        brokenJars.entrySet().removeIf(entry -> {
            if (ticks < entry.getValue()) {
                return false;
            }
            entry.getKey().discard();
            return true;
        });
    }

    /**
     * La couleur du coffre : legendaire pour un boss, rare pour un mini-boss
     * ou une enigme, commun sinon. Elle ne change pas son contenu.
     */
    private String chestTier() {
        if (room.type == RoomType.BOSS) {
            return jobBoss != null || palier % 10 == 0 ? "legendary" : "rare";
        }
        return room.type == RoomType.PUZZLE || room.type == RoomType.ELITE || room.type == RoomType.TRAP
                || room.type == RoomType.SURVIVAL ? "rare" : "default";
    }

    /**
     * Le butin du coffre de la salle, pour un joueur : la reserve du donjon,
     * un tirage de plus ou de moins selon le mode, et la table des boss pour un
     * boss (deux tirages apres un boss de palier en 0 ou un boss de metier, un
     * de plus pour un Dark), un elite (un tirage), un piege ou une survie (un
     * tirage de reserve en plus). Un boss Dark ajoute sa rune et son essence.
     */
    private List<ItemStack> roomLoot() {
        int extra = mode.extraRolls();
        int bossRolls = 0;
        switch (room.type) {
            case BOSS -> bossRolls = jobBoss != null || palier % 10 == 0 ? 2 : 1;
            case ELITE -> bossRolls = 1;
            case TRAP, SURVIVAL -> extra++;
            default -> {
            }
        }
        boolean dark = room.type == RoomType.BOSS && bossSpec != null && bossSpec.dark();
        if (dark) {
            bossRolls++;
        }
        List<ItemStack> loot = DungeonRewards.chest(level, random, dungeon, palier, bossRolls, extra);
        if (dark) {
            loot.addAll(DungeonRewards.dark(level, random, dungeon, palier));
        }
        return loot;
    }

    /** Le coffre de la salle, pour un joueur : son butin, une fois. */
    boolean openChest(ServerPlayer player, Entity target) {
        if (chest == null || chest != target) {
            return false;
        }
        if (!members.contains(player.getUUID())) {
            return true;
        }
        if (!looted.add(player.getUUID())) {
            DungeonManager.message(player, ChatFormatting.GRAY, "chest_done");
            return true;
        }
        List<ItemStack> loot = roomLoot();
        if (looted.size() == 1) {
            // Le premier l'ouvre ; il reste ouvert pour les suivants.
            DungeonProps.pose(chest, "open>open_idle");
        }
        String tier = RpgSkins.skin(chest).variant();
        DungeonProps.sound(level, chest, "dungeon.loot." + (tier.equals("default") ? "common" : tier), 1.0f);
        DungeonManager.message(player, ChatFormatting.GOLD, "chest_loot", describe(loot));
        addToBag(player, loot);
        return true;
    }

    /** Qui n'a pas ouvert le coffre en partant le recoit quand meme. */
    private void collectChest() {
        if (chest == null) {
            return;
        }
        for (ServerPlayer player : onlineMembers()) {
            if (looted.add(player.getUUID())) {
                List<ItemStack> loot = roomLoot();
                DungeonManager.message(player, ChatFormatting.GOLD, "chest_auto", describe(loot));
                addToBag(player, loot);
            }
        }
    }

    private void addToBag(ServerPlayer player, List<ItemStack> loot) {
        List<ItemStack> bag = bags.computeIfAbsent(player.getUUID(), uuid -> new ArrayList<>());
        int capacity = capacities.getOrDefault(player.getUUID(), DungeonBackpackItem.BASE_CAPACITY);
        List<ItemStack> lost = new ArrayList<>();
        for (ItemStack stack : loot) {
            ItemStack rest = stack.copy();
            for (ItemStack held : bag) {
                if (rest.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(held, rest) && held.getCount() < held.getMaxStackSize()) {
                    int moved = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(moved);
                    rest.shrink(moved);
                }
            }
            if (rest.isEmpty()) {
                continue;
            }
            if (bag.size() < capacity) {
                bag.add(rest);
            } else {
                lost.add(rest);
            }
        }
        if (!lost.isEmpty()) {
            DungeonManager.message(player, ChatFormatting.RED, "bag_full", describe(lost));
        }
    }

    static Component describe(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return Component.literal("rien").withStyle(ChatFormatting.GRAY);
        }
        MutableComponent line = Component.empty();
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            ItemStack stack = stacks.get(i);
            line.append(Component.literal(stack.getCount() + "x ").withStyle(ChatFormatting.WHITE))
                    .append(stack.getHoverName().copy().withStyle(ChatFormatting.YELLOW));
        }
        return line;
    }

    int capacity(UUID player) {
        return capacities.getOrDefault(player, DungeonBackpackItem.BASE_CAPACITY);
    }

    private void checkDoors() {
        for (ServerPlayer player : onlineMembers()) {
            if (player.isSpectator()) {
                continue;
            }
            for (DungeonRoom.Door door : doors) {
                if (door.zone().contains(player.position())) {
                    choose(player, door.type());
                    return;
                }
            }
        }
    }

    /**
     * Essai ({@code /donjon admin finir}) : la salle en cours est reussie comme
     * si on l'avait finie — monstres et boss abattus, piege desarme, compte a
     * rebours ecoule, boutons trouves.
     */
    void finishRoom() {
        if (phase == Phase.TRAP && trapButton != null) {
            pressButton(trapButton);
            return;
        }
        if (phase == Phase.PUZZLE) {
            room.buttons.clear();
            roomCleared();
            return;
        }
        if (phase != Phase.FIGHT) {
            return;
        }
        if (room.type == RoomType.SURVIVAL) {
            survivalLeft = Math.min(survivalLeft, 1);
            return;
        }
        wave = waves;
        nextWaveIn = 0;
        for (Mob mob : new ArrayList<>(enemies.keySet())) {
            if (mob.isAlive()) {
                mob.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            }
        }
    }

    /**
     * Essai ({@code /donjon admin salle}) : la salle en cours s'arrete et la
     * suivante est celle demandee.
     */
    void forceNext(ServerPlayer chooser, RoomType type) {
        clearEnemies();
        hideBar();
        if (phase == Phase.TRAP) {
            disarmTrap();
        }
        bosses.clear();
        if (roomIndex >= 2) {
            roomIndex = 1;
        }
        choose(chooser, type);
    }

    private void choose(ServerPlayer chooser, RoomType type) {
        collectChest();
        broadcast(ChatFormatting.AQUA, "door", chooser.getDisplayName(),
                Component.literal(type.label()).withStyle(type.color()));
        if (type == RoomType.EXIT) {
            succeed();
            return;
        }
        DungeonRoom previous = room;
        for (Entity entity : spawned) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        spawned.clear();
        discardIn(previous.outer());
        roomIndex++;
        room = DungeonRoom.build(level, dungeon, type, roomCenter(roomIndex), random);
        built.add(room);
        Vec3 entrance = room.entrance();
        int index = 0;
        List<ServerPlayer> players = onlineMembers();
        for (ServerPlayer player : players) {
            Vec3 spot = entrance.add((index++ - (players.size() - 1) / 2.0d) * 1.5d, 0.0d, 0.0d);
            player.teleportTo(spot.x, spot.y, spot.z);
            player.setYRot(0.0f);
            player.fallDistance = 0.0f;
        }
        previous.erase(level);
        enter();
    }

    private void discardIn(AABB box) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, entity -> !(entity instanceof Player))) {
            DungeonManager.untrackMob(entity.getUUID());
            entity.discard();
        }
    }

    // -- Note ---------------------------------------------------------------------

    private Stats stats(UUID player) {
        return stats.computeIfAbsent(player, uuid -> new Stats());
    }

    void recordDealt(ServerPlayer player, float amount) {
        if (members.contains(player.getUUID()) && amount > 0.0f) {
            stats(player.getUUID()).dealt += amount;
        }
    }

    void recordTaken(ServerPlayer player, float amount) {
        if (members.contains(player.getUUID()) && amount > 0.0f) {
            stats(player.getUUID()).taken += amount;
        }
    }

    void recordKill(ServerPlayer player) {
        if (members.contains(player.getUUID())) {
            stats(player.getUUID()).kills++;
        }
    }

    /** La note d'un joueur, et ses trois parts, en pour cent. */
    private record Mark(Grade grade, int damage, int survival, int involvement) {
    }

    private Mark mark(ServerPlayer player, List<ServerPlayer> party) {
        float totalDealt = 0.0f;
        int totalKills = 0;
        for (ServerPlayer member : party) {
            totalDealt += stats(member.getUUID()).dealt;
            totalKills += stats(member.getUUID()).kills;
        }
        Stats own = stats(player.getUUID());
        int size = party.size();
        double damage = size <= 1 || totalDealt <= 0.0f ? 1.0d : Math.min(1.0d, own.dealt / totalDealt * size);
        double involvement = size <= 1 ? (own.kills > 0 ? 1.0d : 0.5d)
                : totalKills <= 0 ? 0.5d : Math.min(1.0d, (double) own.kills / totalKills * size);
        double survival = Math.clamp(1.0d - own.taken / (40.0d * Math.max(1, fights)), 0.0d, 1.0d);
        Grade grade = Grade.of(0.35d * damage + 0.45d * survival + 0.20d * involvement);
        return new Mark(grade, (int) Math.round(damage * 100), (int) Math.round(survival * 100),
                (int) Math.round(involvement * 100));
    }

    // -- Fin de partie ------------------------------------------------------------

    /** La porte de sortie : sacs livres, XP de metier, progression. */
    private void succeed() {
        phase = Phase.ENDED;
        long jobXp = Math.round((600L + 40L * palier) * mode.rewards());
        List<ServerPlayer> party = onlineMembers();
        for (ServerPlayer player : party) {
            List<ItemStack> delivered = new ArrayList<>(bags.getOrDefault(player.getUUID(), List.of()));
            if (jobBoss == null) {
                for (ItemStack unique : DungeonRewards.unique(dungeon, palier, random)) {
                    if (random.nextFloat() < mode.unique()) {
                        DungeonManager.message(player, ChatFormatting.LIGHT_PURPLE, "unique", unique.getHoverName());
                        delivered.add(unique);
                    }
                }
            }
            Mark mark = mark(player, party);
            List<ItemStack> graded = DungeonRewards.gradeChest(level, random, dungeon, palier, mark.grade());
            DungeonManager.message(player, mark.grade().color(), "grade",
                    Component.literal(mark.grade().name()).withStyle(mark.grade().color(), ChatFormatting.BOLD),
                    mark.damage(), mark.survival(), mark.involvement());
            if (!graded.isEmpty()) {
                DungeonManager.message(player, ChatFormatting.GOLD, "grade_chest", describe(graded));
                delivered.addAll(graded);
            }
            for (ItemStack stack : delivered) {
                ItemStack copy = stack.copy();
                if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                    player.drop(copy, false);
                }
            }
            if (jobBoss != null) {
                player.sendSystemMessage(Component.literal("Butin du gardien livre : " + delivered.size()
                        + " pile(s).").withStyle(ChatFormatting.GOLD));
            } else {
                DungeonManager.message(player, ChatFormatting.GREEN, "success", palier, dungeon.display());
                DungeonManager.message(player, ChatFormatting.GOLD, "delivered", delivered.size(), jobXp,
                        dungeon.job().display());
                JobApi.grant(player, dungeon.job(), jobXp);
                DungeonManager.recordCleared(player, dungeon, palier, mode);
            }
            removeBoosts(player);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(jobBoss != null
                    ? Component.literal(jobBoss.spec().name() + " vaincu !").withStyle(ChatFormatting.GOLD,
                    ChatFormatting.BOLD)
                    : Component.literal("Palier " + palier + " reussi !").withStyle(ChatFormatting.GREEN,
                    ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal((jobBoss != null ? "Boss de metier" : dungeon.display() + " · "
                                    + mode.label()) + " — note ").withStyle(dungeon.color())
                            .append(Component.literal(mark.grade().name())
                                    .withStyle(mark.grade().color(), ChatFormatting.BOLD))));
            DungeonManager.sendBack(player);
        }
        members.clear();
        DungeonManager.finish(this);
    }

    /**
     * Un joueur quitte la partie : mort, sortie, deconnexion, ou fuite hors
     * de la dimension. Son sac est perdu ; sans plus personne, le palier est
     * echoue.
     */
    void leave(ServerPlayer player, Leave reason) {
        if (!members.remove(player.getUUID())) {
            return;
        }
        bags.remove(player.getUUID());
        removeBoosts(player);
        if (bar != null) {
            bar.removePlayer(player);
        }
        if (jobBoss != null && (reason == Leave.DEATH || reason == Leave.QUIT)) {
            boolean beaten = bosses.isEmpty() && phase != Phase.FIGHT;
            player.sendSystemMessage(Component.literal(beaten
                    ? "Tu quittes l'arene : le butin du coffre est perdu, la victoire reste."
                    : reason == Leave.DEATH ? jobBoss.spec().name() + " t'a mis a terre. Il t'attend encore : reviens"
                    + " par l'autel quand tu veux." : "Tu quittes l'arene. " + jobBoss.spec().name()
                    + " t'attend encore.").withStyle(ChatFormatting.RED));
        } else {
            switch (reason) {
                case DEATH -> DungeonManager.message(player, ChatFormatting.RED, "death");
                case QUIT -> DungeonManager.message(player, ChatFormatting.RED, "quit");
                default -> {
                }
            }
        }
        if (reason != Leave.LOGOUT && reason != Leave.ESCAPED) {
            DungeonManager.sendBack(player);
        }
        if (reason == Leave.ESCAPED) {
            player.setData(PalaDungeon.RETURN.get(), ReturnPoint.NONE);
        }
        broadcast(ChatFormatting.GRAY, "member_left", Component.literal(names.getOrDefault(player.getUUID(),
                player.getGameProfile().name())));
        if (members.isEmpty()) {
            phase = Phase.ENDED;
            DungeonManager.finish(this);
        }
    }

    /** Efface tout : salles, monstres, barre. */
    void cleanup() {
        phase = Phase.ENDED;
        hideBar();
        for (Mob mob : enemies.keySet()) {
            DungeonManager.untrackMob(mob.getUUID());
        }
        enemies.clear();
        for (Entity entity : spawned) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        spawned.clear();
        if (room != null) {
            discardIn(room.outer().inflate(8.0d));
        }
        for (DungeonRoom candidate : built) {
            candidate.erase(level);
        }
        built.clear();
    }

    boolean isMerchant(Entity entity) {
        return merchant != null && merchant == entity;
    }

    /**
     * Le marchand salue qui lui parle. La pose change de nom a chaque fois —
     * la meme, c'est ne rien changer, et les clients ne la rejoueraient pas.
     */
    void greet(Entity entity) {
        String pose = RpgSkins.skin(entity).pose();
        DungeonProps.pose(entity, pose.equals("interact>idle") ? "interact>idle>idle" : "interact>idle");
    }

    String leaderName() {
        return names.getOrDefault(leader, "?");
    }
}
