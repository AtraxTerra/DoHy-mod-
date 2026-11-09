package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
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

    enum Phase { FIGHT, PUZZLE, OPEN, ENDED }

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

    /** Les monstres vivants de la salle, et leur type. */
    final Map<UUID, MobKind> enemies = new LinkedHashMap<>();
    final List<UUID> bosses = new ArrayList<>();
    private BossSpec bossSpec;
    private ServerBossEvent bar;
    private int abilityCooldown;
    private int wave;
    private int waves;
    private int nextWaveIn;

    BlockPos chest;
    final Set<UUID> looted = new HashSet<>();
    private int buttonsTotal;
    List<Boost> boostOptions = List.of();
    final Set<UUID> boosted = new HashSet<>();
    private UUID merchant;
    private int ticks;

    DungeonRun(int id, ServerLevel level, Dungeon dungeon, int palier, ServerPlayer leader, List<ServerPlayer> party,
               BlockPos origin) {
        this.id = id;
        this.level = level;
        this.dungeon = dungeon;
        this.palier = palier;
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
        return 1.0f + 0.03f * (palier - 1);
    }

    double healthFactor() {
        return 1.0d + 0.06d * (palier - 1);
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
        room = DungeonRoom.build(level, dungeon, RoomType.COMBAT, roomCenter(0), random);
        built.add(room);
        Vec3 entrance = room.entrance();
        int index = 0;
        for (ServerPlayer player : party) {
            Vec3 spot = entrance.add((index++ - (party.size() - 1) / 2.0d) * 1.5d, 0.0d, 0.0d);
            player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, spot, Vec3.ZERO,
                    0.0f, 0.0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        }
        title(Component.literal(dungeon.display()).withStyle(dungeon.color(), ChatFormatting.BOLD),
                Component.literal("Palier " + palier + " — " + band().label()).withStyle(band().color()));
        enter();
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
        boostOptions = List.of();
        boosted.clear();
        merchant = null;
        switch (room.type) {
            case COMBAT -> {
                phase = Phase.FIGHT;
                wave = 0;
                waves = palier >= 50 ? 3 : 2;
                nextWaveIn = 30;
            }
            case PUZZLE -> {
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
            case BOSS -> {
                phase = Phase.FIGHT;
                spawnBosses();
            }
            case EXIT -> {
            }
        }
    }

    void tick() {
        if (phase == Phase.ENDED) {
            return;
        }
        ticks++;
        if (ticks % 20 == 0) {
            keepInside();
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

    // -- Combat -----------------------------------------------------------------

    private void spawnWave() {
        wave++;
        List<ServerPlayer> players = onlineMembers();
        int count = 3 + palier / 25 + 2 * Math.max(0, players.size() - 1);
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
        ServerPlayer target = nearest(mob);
        if (target != null && kind != MobKind.MAGE) {
            mob.setTarget(target);
        }
        level.addFreshEntity(mob);
        track(mob, kind, 1.0f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.5d, mob.getZ(),
                8, 0.3d, 0.5d, 0.3d, 0.01d);
        return mob;
    }

    /** Fait d'un monstre un monstre de la partie : compte, facteur de degats. */
    void track(Mob mob, MobKind kind, float taken) {
        enemies.put(mob.getUUID(), kind);
        DungeonManager.trackMob(mob.getUUID(), this, taken);
    }

    private static void base(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void checkFight() {
        Iterator<Map.Entry<UUID, MobKind>> iterator = enemies.entrySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next().getKey();
            Entity entity = level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                DungeonManager.untrackMob(uuid);
            }
        }
        if (room.type == RoomType.BOSS) {
            bosses.removeIf(uuid -> !enemies.containsKey(uuid));
            if (bar != null) {
                updateBar();
            }
            if (bosses.isEmpty()) {
                broadcast(ChatFormatting.GOLD, "boss_dead", bossSpec.name());
                clearEnemies();
                hideBar();
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
        for (UUID uuid : enemies.keySet()) {
            if (level.getEntity(uuid) instanceof Mob mob && !mob.isNoAi()) {
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
        for (Map.Entry<UUID, MobKind> entry : enemies.entrySet()) {
            if (entry.getValue() != MobKind.MAGE || !(level.getEntity(entry.getKey()) instanceof Mob mage)) {
                continue;
            }
            if (buff) {
                for (UUID other : enemies.keySet()) {
                    if (level.getEntity(other) instanceof Mob ally && ally != mage && ally.distanceTo(mage) <= 10.0f) {
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
        for (UUID uuid : new ArrayList<>(enemies.keySet())) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
            DungeonManager.untrackMob(uuid);
        }
        enemies.clear();
    }

    // -- Boss -------------------------------------------------------------------

    private void spawnBosses() {
        bossSpec = dungeon.bossFor(palier);
        if (bossSpec == null) {
            roomCleared();
            return;
        }
        boolean mini = palier % 10 != 0;
        int players = Math.max(1, onlineMembers().size());
        double health = (mini ? 120.0d : 250.0d) * (1.0d + 0.05d * (palier - 1)) * (1.0d + 0.6d * (players - 1));
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
            boss.setCustomName(Component.literal(bossSpec.name()).withStyle(dungeon.color(), ChatFormatting.BOLD));
            boss.setCustomNameVisible(true);
            ServerPlayer target = nearest(boss);
            if (target != null) {
                boss.setTarget(target);
            }
            level.addFreshEntity(boss);
            track(boss, MobKind.TANK, (float) (capped / health));
            bosses.add(boss.getUUID());
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0d, boss.getZ(),
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(bossSpec.name()).withStyle(dungeon.color()),
                mini ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        for (ServerPlayer player : onlineMembers()) {
            bar.addPlayer(player);
        }
        abilityCooldown = 100;
        title(Component.literal(bossSpec.name()).withStyle(dungeon.color(), ChatFormatting.BOLD),
                Component.literal(mini ? "Mini-boss" : "Boss").withStyle(ChatFormatting.GRAY));
        broadcast(ChatFormatting.DARK_PURPLE, "boss", bossSpec.name());
        level.playSound(null, room.center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 1.0f);
    }

    private void tickBosses() {
        if (ticks % 5 == 0 && bar != null) {
            updateBar();
        }
        List<Mob> alive = new ArrayList<>();
        for (UUID uuid : bosses) {
            if (level.getEntity(uuid) instanceof Mob boss && boss.isAlive()) {
                alive.add(boss);
            }
        }
        if (alive.isEmpty()) {
            return;
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
        Mob boss = alive.get(random.nextInt(alive.size()));
        ServerPlayer target = nearest(boss);
        if (target != null && !bossSpec.abilities().isEmpty()) {
            BossAbility ability = bossSpec.abilities().get(random.nextInt(bossSpec.abilities().size()));
            ability.perform(this, boss, level, target);
        }
    }

    private void updateBar() {
        float health = 0.0f;
        float max = 0.0f;
        for (UUID uuid : bosses) {
            if (level.getEntity(uuid) instanceof LivingEntity boss && boss.isAlive()) {
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
        villager.setCustomName(Component.literal("Marchande").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        villager.setCustomNameVisible(true);
        level.addFreshEntity(villager);
        merchant = villager.getUUID();
    }

    // -- Fin de salle, coffre et portes ---------------------------------------------

    private void roomCleared() {
        if (room.type.loot()) {
            chest = room.chestPos();
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, chest.getX() + 0.5d, chest.getY() + 1.0d,
                    chest.getZ() + 0.5d, 30, 0.4d, 0.6d, 0.4d, 0.2d);
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

    private List<RoomType> nextOptions() {
        if (roomIndex >= 2) {
            return List.of(RoomType.EXIT);
        }
        if (roomIndex == 1 && dungeon.bossFor(palier) != null) {
            return List.of(RoomType.BOSS);
        }
        List<RoomType> pool = new ArrayList<>(List.of(RoomType.COMBAT, RoomType.PUZZLE, RoomType.BOOST,
                RoomType.MERCHANT));
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        return List.copyOf(pool.subList(0, 1 + random.nextInt(3)));
    }

    /** Le coffre de la salle, pour un joueur : son butin, une fois. */
    boolean openChest(ServerPlayer player, BlockPos pos) {
        if (chest == null || !chest.equals(pos)) {
            return false;
        }
        if (!members.contains(player.getUUID())) {
            return true;
        }
        if (!looted.add(player.getUUID())) {
            DungeonManager.message(player, ChatFormatting.GRAY, "chest_done");
            return true;
        }
        List<ItemStack> loot = DungeonRewards.chest(level, random, dungeon, palier, room.type == RoomType.BOSS);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 1.0f);
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
                List<ItemStack> loot = DungeonRewards.chest(level, random, dungeon, palier,
                        room.type == RoomType.BOSS);
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

    private void choose(ServerPlayer chooser, RoomType type) {
        collectChest();
        broadcast(ChatFormatting.AQUA, "door", chooser.getDisplayName(),
                Component.literal(type.label()).withStyle(type.color()));
        if (type == RoomType.EXIT) {
            succeed();
            return;
        }
        DungeonRoom previous = room;
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

    // -- Fin de partie ------------------------------------------------------------

    /** La porte de sortie : sacs livres, XP de metier, progression. */
    private void succeed() {
        phase = Phase.ENDED;
        long jobXp = 600L + 40L * palier;
        for (ServerPlayer player : onlineMembers()) {
            List<ItemStack> delivered = new ArrayList<>(bags.getOrDefault(player.getUUID(), List.of()));
            for (ItemStack unique : DungeonRewards.unique(dungeon, palier)) {
                if (random.nextBoolean()) {
                    DungeonManager.message(player, ChatFormatting.LIGHT_PURPLE, "unique", unique.getHoverName());
                    delivered.add(unique);
                }
            }
            for (ItemStack stack : delivered) {
                ItemStack copy = stack.copy();
                if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                    player.drop(copy, false);
                }
            }
            DungeonManager.message(player, ChatFormatting.GREEN, "success", palier, dungeon.display());
            DungeonManager.message(player, ChatFormatting.GOLD, "delivered", delivered.size(), jobXp,
                    dungeon.job().display());
            JobApi.grant(player, dungeon.job(), jobXp);
            DungeonManager.recordCleared(player, dungeon, palier);
            removeBoosts(player);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal("Palier " + palier + " reussi !").withStyle(ChatFormatting.GREEN,
                            ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal(dungeon.display()).withStyle(dungeon.color())));
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
        switch (reason) {
            case DEATH -> DungeonManager.message(player, ChatFormatting.RED, "death");
            case QUIT -> DungeonManager.message(player, ChatFormatting.RED, "quit");
            default -> {
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
        for (UUID uuid : enemies.keySet()) {
            DungeonManager.untrackMob(uuid);
        }
        enemies.clear();
        if (room != null) {
            discardIn(room.outer().inflate(8.0d));
        }
        for (DungeonRoom candidate : built) {
            candidate.erase(level);
        }
        built.clear();
    }

    boolean isMerchant(Entity entity) {
        return merchant != null && merchant.equals(entity.getUUID());
    }

    String leaderName() {
        return names.getOrDefault(leader, "?");
    }
}
