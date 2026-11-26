package fr.atraxterra.dohy.faction.bcc;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionBranch;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.FactionNotation;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.base.EnemyBase;
import fr.atraxterra.dohy.faction.war.Soldier;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les regles du Base Claim Controller (wiki « Java | Base Claim Controller »),
 * lues en solo : la nation du joueur l'achete, le pose au coeur de sa base et en
 * tire de la gloire et de l'ELO chaque jour ; les nations rivales ont les leurs,
 * au coeur de leurs bases, a piller.
 *
 * <ul>
 *   <li><b>Achat</b> : 75 000 $ de la banque nationale, dans l'onglet Territoire,
 *       des que la nation a franchi le niveau 10 (dix paliers de l'arbre de
 *       technologie, toutes branches comprises).</li>
 *   <li><b>Pose</b> : au centre d'un claim d'au moins 3 x 3 chunks, a 1 500 blocs
 *       de tout autre BCC, les huit blocs autour et les neuf au-dessus vides ; un
 *       seul a la fois, quatorze jours entre deux poses. Une condition manquee : le
 *       BCC disparait, sans remboursement.</li>
 *   <li><b>Ce qu'il rapporte</b> : 120 a la pose, puis chaque jour 30 points de
 *       gloire et de l'ELO, de plus en plus jusqu'au cinquantieme jour. Un coffre de
 *       55 cases ; le nombre d'intrus a 200 blocs.</li>
 *   <li><b>Le revers</b> : une nation du podium de la notation est reperee. En solo,
 *       ce sont les rivales qui la voient : des pillards viennent tenter de piller
 *       le BCC quand son maitre est dans les parages, une fois par jour au plus.</li>
 *   <li><b>Le pillage</b> : deux minutes de clic droit sans lacher ni etre frappe.
 *       Le BCC prend feu, explose sans rien casser et lache son contenu ; son maitre
 *       perd de l'ELO, le pillard en gagne.</li>
 * </ul>
 */
@EventBusSubscriber
public final class Bccs {

    public static final long PRICE = 75_000L;
    /** Niveau de faction : plus de 10, lu en paliers de l'arbre de technologie. */
    public static final int LEVEL = 10;
    public static final int MIN_DISTANCE = 1_500;
    public static final long COOLDOWN_MS = 14L * 24L * 3600L * 1000L;
    public static final long DAY_MS = 24L * 3600L * 1000L;
    /** Deux minutes de clic droit. */
    public static final int LOOT_TICKS = 2 * 60 * 20;
    public static final long PLACE_ELO = 120L;
    public static final long DAILY_GLORY = 30L;
    /** L'ELO qui change de main au pillage. */
    public static final long LOOT_ELO = 300L;
    /** Une raid repoussee. */
    public static final long DEFENSE_ELO = 50L;
    static final String RAIDER_TAG = "dohy_bcc_raider";
    /** Le pillard abandonne au bout de dix minutes. */
    private static final int RAID_TICKS = 10 * 60 * 20;

    /** Un pillage en cours par un joueur. */
    private static final class Loot {
        final BlockPos pos;
        long last;
        int progress;

        Loot(BlockPos pos, long now) {
            this.pos = pos;
            this.last = now;
        }
    }

    /** Une raid de pillards contre le BCC d'un joueur. */
    private static final class Raid {
        final UUID owner;
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final String nation;
        final List<UUID> raiders = new ArrayList<>();
        UUID looter;
        int progress;
        int age;
        boolean interrupted;

        Raid(UUID owner, ResourceKey<Level> dimension, BlockPos pos, String nation) {
            this.owner = owner;
            this.dimension = dimension;
            this.pos = pos;
            this.nation = nation;
        }
    }

    private static final Map<UUID, Loot> LOOTING = new HashMap<>();
    private static final Map<UUID, Raid> RAIDS = new HashMap<>();

    private Bccs() {
    }

    public static BccData data(ServerPlayer player) {
        return player.getData(DoHyBcc.DATA.get());
    }

    /** « les Titans » ou « les Vautours » : les noms des rivales portent parfois leur article. */
    static String les(String nation) {
        return nation.startsWith("Les ") ? "les " + nation.substring(4) : "les " + nation;
    }

    /** « des Titans », « des Vautours ». */
    static String des(String nation) {
        return nation.startsWith("Les ") ? "des " + nation.substring(4) : "des " + nation;
    }

    static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[BCC] " + text).withStyle(color));
    }

    // -- Les jours, la gloire et l'ELO ---------------------------------------------------------

    /** Les jours entiers depuis la pose. */
    public static long daysHeld(BccData data) {
        return data.placed() ? Math.max(0L, (System.currentTimeMillis() - data.placedAt) / DAY_MS) : 0L;
    }

    /** L'ELO du jour {@code day} : 10 au premier, x1,05 par jour jusqu'au cinquantieme, puis 109. */
    public static long eloForDay(long day) {
        long capped = Math.max(1L, Math.min(50L, day));
        return Math.round(10.0d * Math.pow(1.05d, capped - 1L));
    }

    /** Le niveau de la nation, lu dans l'arbre : la somme des paliers de ses quatre branches. */
    public static int nationLevel(FactionData data) {
        int level = 0;
        for (FactionBranch branch : FactionBranch.values()) {
            level += data.tier(branch.key());
        }
        return level;
    }

    /** Ce qui manque pour acheter ; {@code null} si tout est la. */
    public static String cannotBuy(ServerPlayer player) {
        FactionData nation = FactionApi.data(player);
        BccData data = data(player);
        if (!nation.exists()) {
            return "Il faut une nation.";
        }
        if (nationLevel(nation) <= LEVEL) {
            return "Ta nation doit passer le niveau " + LEVEL + " (" + nationLevel(nation) + " paliers d'arbre).";
        }
        if (data.placed()) {
            return "Ta nation a deja un BCC pose.";
        }
        if (data.bought > 0 || hasItem(player)) {
            return "Ton BCC t'attend dans ton inventaire.";
        }
        long wait = data.lastPlaced + COOLDOWN_MS - System.currentTimeMillis();
        if (data.lastPlaced > 0L && wait > 0L) {
            return "Encore " + days(wait) + " avant de pouvoir en poser un autre.";
        }
        if (nation.bank() < PRICE) {
            return "Il faut " + MoneyFormat.exact(PRICE) + " $ dans la banque nationale.";
        }
        return null;
    }

    private static boolean hasItem(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(DoHyBcc.BCC_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    static String days(long millis) {
        long hours = Math.max(1L, (millis + 3_599_999L) / 3_600_000L);
        return hours < 48L ? hours + " h" : (hours / 24L) + " jours";
    }

    /** L'achat, depuis l'onglet Territoire : rend le refus, ou {@code null}. */
    public static String buy(ServerPlayer player) {
        String refusal = cannotBuy(player);
        if (refusal != null) {
            return refusal;
        }
        if (!FactionApi.payFromBank(player, PRICE, "Base Claim Controller")) {
            return "La banque nationale n'a pas " + MoneyFormat.exact(PRICE) + " $.";
        }
        data(player).bought = 1;
        ItemStack stack = new ItemStack(DoHyBcc.BCC_ITEM.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        tell(player, "Base Claim Controller achete (" + MoneyFormat.exact(PRICE) + " $). Pose-le au centre d'un "
                + "claim de 3 x 3 chunks : une condition manquee et il disparait.", ChatFormatting.GOLD);
        return null;
    }

    // -- La pose -------------------------------------------------------------------------------

    /** Ce qui empeche la pose ici ; {@code null} si elle est possible. */
    static String cannotPlace(ServerPlayer player, ServerLevel level, BlockPos pos) {
        FactionData nation = FactionApi.data(player);
        BccData data = data(player);
        if (!nation.exists()) {
            return "il faut une nation";
        }
        if (nationLevel(nation) <= LEVEL) {
            return "ta nation n'a pas passe le niveau " + LEVEL;
        }
        if (data.placed()) {
            return "ta nation a deja un BCC";
        }
        if (data.lastPlaced > 0L && System.currentTimeMillis() - data.lastPlaced < COOLDOWN_MS) {
            return "quatorze jours entre deux BCC";
        }
        ChunkPos center = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos probe = new BlockPos(((center.x() + dx) << 4) + 8, pos.getY(), ((center.z() + dz) << 4) + 8);
                ClaimOwner owner = FactionApi.claimAt(level, probe);
                if (!owner.ownedBy(player.getUUID())) {
                    return "il faut ses neuf chunks (3 x 3) a ta nation autour de lui";
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !level.getBlockState(pos.offset(dx, 0, dz)).isAir()) {
                    return "les huit blocs autour doivent etre vides";
                }
                if (!level.getBlockState(pos.offset(dx, 1, dz)).isAir()) {
                    return "les blocs au-dessus doivent etre vides";
                }
            }
        }
        EnemyBase rival = player.getData(Factions.ENEMY_BASE.get());
        if (rival.state() == EnemyBase.ACTIVE && rival.center().distSqr(pos) < (double) MIN_DISTANCE * MIN_DISTANCE) {
            return "un autre BCC est a moins de " + MIN_DISTANCE + " blocs";
        }
        return null;
    }

    /** Le bloc vient d'etre pose : il reste, ou il disparait (sans remboursement). */
    static void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BccBlockEntity bcc)) {
            return;
        }
        BccData data = data(player);
        data.bought = 0;
        String refusal = cannotPlace(player, level, pos);
        if (refusal != null) {
            level.removeBlock(pos, false);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, 20,
                    0.3d, 0.3d, 0.3d, 0.02d);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.8f);
            tell(player, "Le BCC disparait : " + refusal + ". L'argent n'est pas rendu.", ChatFormatting.RED);
            return;
        }
        long now = System.currentTimeMillis();
        bcc.setOwner(player.getUUID(), player.getGameProfile().name());
        data.dimension = level.dimension().identifier().toString();
        data.pos = pos.asLong();
        data.placedAt = now;
        data.lastPlaced = now;
        data.paidDays = 0L;
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        tell(player, "Base Claim Controller pose en " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + ". Chaque jour : " + DAILY_GLORY + " de gloire et de l'ELO.", ChatFormatting.GOLD);
        FactionApi.addElo(player, PLACE_ELO, "Base Claim Controller pose");
        data.elo += PLACE_ELO;
        FactionApi.stat(player, "bcc:placed", 1);
    }

    /** Le bloc s'en va : le maitre l'oublie ; un BCC rival efface ne lache rien. */
    static void forget(BccBlockEntity bcc) {
        if (!(bcc.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!bcc.rival().isEmpty()) {
            bcc.clearContent();
            return;
        }
        if (bcc.owner() == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bcc.owner());
        if (owner != null) {
            BccData data = data(owner);
            if (data.placed() && data.pos == bcc.getBlockPos().asLong()) {
                data.clearPlaced();
            }
        }
    }

    // -- Le revenu de chaque jour ---------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameTime() % 600L != 17L) {
            return;
        }
        BccData data = data(player);
        if (!data.placed()) {
            return;
        }
        long days = daysHeld(data);
        while (data.paidDays < days) {
            data.paidDays++;
            long elo = eloForDay(data.paidDays);
            data.glory += DAILY_GLORY;
            data.elo += elo;
            FactionApi.addElo(player, elo, "BCC, jour " + data.paidDays);
            FactionApi.stat(player, "bcc:glory", DAILY_GLORY);
            tell(player, "Jour " + data.paidDays + " du BCC : +" + DAILY_GLORY + " de gloire (" + data.glory
                    + " en tout).", ChatFormatting.GOLD);
        }
        maybeRaid(player, data);
    }

    // -- Les intrus ------------------------------------------------------------------------------

    /** Ce que le BCC compte a 200 blocs : les autres joueurs, et les soldats et pillards rivaux. */
    static boolean intruder(BccBlockEntity bcc, LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isSpectator() && (!bcc.rival().isEmpty() || bcc.owner() == null
                    || !bcc.owner().equals(player.getUUID()));
        }
        if (!bcc.rival().isEmpty()) {
            return false;
        }
        return entity instanceof Soldier || entity.entityTags().contains(RAIDER_TAG);
    }

    // -- Le pillage par un joueur ---------------------------------------------------------------

    /** Un clic droit (repete tant qu'il est tenu) d'un autre que le maitre. */
    static void loot(ServerPlayer player, BccBlockEntity bcc) {
        ServerLevel level = player.level();
        long now = level.getServer().getTickCount();
        Loot loot = LOOTING.get(player.getUUID());
        if (loot == null || !loot.pos.equals(bcc.getBlockPos()) || now - loot.last > 10L) {
            loot = new Loot(bcc.getBlockPos().immutable(), now);
            LOOTING.put(player.getUUID(), loot);
            String who = bcc.rival().isEmpty() ? "de " + bcc.ownerName() : des(bcc.rival());
            tell(player, "Pillage du BCC " + who + " : garde le clic droit deux minutes, sans te faire toucher.",
                    ChatFormatting.YELLOW);
        } else {
            loot.progress += (int) (now - loot.last);
            loot.last = now;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Pillage du BCC : "
                + clock(loot.progress) + " / " + clock(LOOT_TICKS) + ", ne lache pas").withStyle(ChatFormatting.GOLD)));
        if (loot.progress < LOOT_TICKS) {
            return;
        }
        LOOTING.remove(player.getUUID());
        String rival = bcc.rival();
        UUID owner = bcc.owner();
        String ownerName = bcc.ownerName();
        explode(level, bcc);
        if (!rival.isEmpty()) {
            FactionApi.addElo(player, LOOT_ELO, "pillage du BCC " + des(rival));
            FactionApi.stat(player, "bcc:looted", 1);
            tell(player, "Le BCC " + des(rival) + " part en fumee : son tresor est a terre.", ChatFormatting.GOLD);
        } else if (owner != null) {
            FactionApi.addElo(player, LOOT_ELO, "pillage du BCC de " + ownerName);
            ServerPlayer victim = level.getServer().getPlayerList().getPlayer(owner);
            if (victim != null) {
                FactionApi.removeElo(victim, LOOT_ELO, "BCC pille par " + player.getGameProfile().name());
                tell(victim, "Ton BCC a ete pille par " + player.getGameProfile().name() + ".", ChatFormatting.RED);
            }
        }
    }

    static String clock(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** Frappe pendant le pillage : tout est a refaire. Le pillard d'une raid aussi. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() != null
                && event.getSource().getEntity() != player && LOOTING.remove(player.getUUID()) != null) {
            tell(player, "Pillage interrompu : tu as ete frappe. Tout est a refaire.", ChatFormatting.RED);
            return;
        }
        for (Raid raid : RAIDS.values()) {
            if (event.getEntity().getUUID().equals(raid.looter) && raid.progress > 0) {
                raid.progress = 0;
                raid.interrupted = true;
            }
        }
    }

    /**
     * Le pillage abouti : le BCC prend feu et saute sans rien casser autour, son
     * contenu tombe, le bloc disparait.
     */
    static void explode(ServerLevel level, BccBlockEntity bcc) {
        BlockPos pos = bcc.getBlockPos();
        Containers.dropContents(level, pos, bcc);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5d, pos.getY() + 0.6d, pos.getZ() + 0.5d, 40, 0.4d,
                0.5d, 0.4d, 0.03d);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5d, pos.getY() + 0.5d,
                pos.getZ() + 0.5d, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0f, 0.9f);
        level.removeBlock(pos, false);
    }

    // -- Les raids des nations rivales -----------------------------------------------------------

    /** La nation est-elle reperee ? Sur le podium de la derniere notation. */
    public static boolean revealed(ServerPlayer player) {
        FactionData nation = FactionApi.data(player);
        int rank = FactionNotation.lastRank(nation);
        return nation.exists() && FactionNotation.hasLast(nation) && rank >= 1 && rank <= 3;
    }

    /** Toutes les 30 s pres de son BCC : une raid de temps en temps, une par jour au plus. */
    private static void maybeRaid(ServerPlayer player, BccData data) {
        if (RAIDS.containsKey(player.getUUID()) || !revealed(player)
                || System.currentTimeMillis() - data.lastRaid < DAY_MS
                || !player.level().dimension().identifier().toString().equals(data.dimension)
                || player.blockPosition().distSqr(data.pos()) > (double) BccBlockEntity.WATCH * BccBlockEntity.WATCH
                || player.getRandom().nextFloat() >= 0.08f) {
            return;
        }
        startRaid(player, data);
    }

    /** Quatre pillards d'une nation rivale, a 30-45 blocs du BCC. */
    public static boolean startRaid(ServerPlayer player, BccData data) {
        ServerLevel level = player.level();
        BlockPos target = data.pos();
        if (!(level.getBlockEntity(target) instanceof BccBlockEntity)) {
            return false;
        }
        RandomSource random = level.getRandom();
        var pool = fr.atraxterra.dohy.faction.event.EventRun.rivalPool();
        String nation = pool.isEmpty() ? "Corbeaux" : pool.get(random.nextInt(pool.size())).name();
        BlockPos spot = null;
        for (int attempt = 0; attempt < 16 && spot == null; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            int distance = 30 + random.nextInt(16);
            int x = target.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = target.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getBlockState(ground.below()).getFluidState().isEmpty()) {
                spot = ground;
            }
        }
        if (spot == null) {
            return false;
        }
        Raid raid = new Raid(player.getUUID(), level.dimension(), target.immutable(), nation);
        for (int index = 0; index < 4; index++) {
            Mob mob = index < 2 ? EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT)
                    : EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
            if (mob == null) {
                continue;
            }
            mob.snapTo(spot.getX() + 0.5d + index % 2, spot.getY(), spot.getZ() + 0.5d + index / 2, 0.0f, 0.0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
            mob.setPersistenceRequired();
            mob.addTag(RAIDER_TAG);
            mob.setCustomName(Component.literal(index == 0 ? "Pillard " + des(nation) + " (BCC)"
                    : "Pillard " + des(nation)));
            if (index == 0) {
                mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            } else {
                mob.setTarget(player);
            }
            if (level.addFreshEntity(mob)) {
                raid.raiders.add(mob.getUUID());
                if (index == 0) {
                    raid.looter = mob.getUUID();
                }
            }
        }
        if (raid.looter == null) {
            return false;
        }
        data.lastRaid = System.currentTimeMillis();
        RAIDS.put(player.getUUID(), raid);
        tell(player, capital(les(nation)) + " ont repere ton BCC : des pillards arrivent ! Arrete celui au casque d'or "
                + "avant qu'il ne tienne deux minutes au contact.", ChatFormatting.RED);
        level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0f, 1.0f);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RAIDS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Raid>> iterator = RAIDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Raid raid = iterator.next().getValue();
            if (tickRaid(server, raid)) {
                iterator.remove();
            }
        }
    }

    /** Un tick de raid ; vrai quand elle est finie. */
    private static boolean tickRaid(MinecraftServer server, Raid raid) {
        ServerLevel level = server.getLevel(raid.dimension);
        ServerPlayer owner = server.getPlayerList().getPlayer(raid.owner);
        Entity entity = level == null ? null : level.getEntity(raid.looter);
        raid.age++;
        boolean bccThere = level != null && level.getBlockEntity(raid.pos) instanceof BccBlockEntity;
        if (!(entity instanceof Mob looter) || !looter.isAlive() || !bccThere) {
            if (owner != null && bccThere) {
                tell(owner, "Raid repoussee : " + les(raid.nation) + " repartent bredouilles.", ChatFormatting.GREEN);
                FactionApi.addElo(owner, DEFENSE_ELO, "raid " + des(raid.nation) + " repoussee");
            }
            disband(level, raid);
            return true;
        }
        if (raid.age > RAID_TICKS || owner == null) {
            if (owner != null) {
                tell(owner, "Les pillards " + des(raid.nation) + " renoncent.", ChatFormatting.GREEN);
            }
            disband(level, raid);
            return true;
        }
        looter.setTarget(null);
        Vec3 center = Vec3.atCenterOf(raid.pos);
        if (looter.distanceToSqr(center) > 2.6d * 2.6d) {
            if (raid.age % 20 == 0) {
                looter.getNavigation().moveTo(center.x, raid.pos.getY(), center.z, 1.0d);
            }
            return false;
        }
        looter.getNavigation().stop();
        looter.getLookControl().setLookAt(center);
        if (raid.interrupted) {
            raid.interrupted = false;
            if (owner.distanceToSqr(center) < 64.0d * 64.0d) {
                owner.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(
                        "Le pillard est interrompu !").withStyle(ChatFormatting.GREEN)));
            }
        }
        raid.progress++;
        if (raid.progress % 20 == 0 && owner.distanceToSqr(center) < 96.0d * 96.0d) {
            owner.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Ton BCC est pille : "
                    + clock(raid.progress) + " / " + clock(LOOT_TICKS) + ", frappe le pillard au casque d'or")
                    .withStyle(ChatFormatting.RED)));
        }
        if (raid.progress >= LOOT_TICKS && level.getBlockEntity(raid.pos) instanceof BccBlockEntity bcc) {
            explode(level, bcc);
            data(owner).clearPlaced();
            FactionApi.removeElo(owner, LOOT_ELO, "BCC pille par " + les(raid.nation));
            tell(owner, capital(les(raid.nation)) + " ont pille ton BCC : il a saute et son contenu est a terre.",
                    ChatFormatting.RED);
            disband(level, raid);
            return true;
        }
        return false;
    }

    /** Les pillards encore la s'en vont. */
    private static void disband(ServerLevel level, Raid raid) {
        if (level == null) {
            return;
        }
        for (UUID id : raid.raiders) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0d, entity.getZ(), 10, 0.3d,
                        0.5d, 0.3d, 0.02d);
                entity.discard();
            }
        }
    }

    // -- Les BCC des nations rivales ------------------------------------------------------------

    /**
     * Une base rivale (base ou forteresse) garde son tresor dans un BCC, pose au
     * centre de son coeur ; rend vrai s'il a pu etre pose.
     */
    public static boolean placeRival(ServerLevel level, EnemyBase base) {
        if (base.tier() < 2) {
            return false;
        }
        BlockPos spot = rivalSpot(level, base);
        if (spot == null) {
            return false;
        }
        level.setBlock(spot, DoHyBcc.BCC.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(spot) instanceof BccBlockEntity bcc)) {
            return false;
        }
        bcc.setRival(base.name());
        LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(
                Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("palamod", "chests/enemy_coffre_fort")));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(spot))
                .create(LootContextParamSets.CHEST);
        int[] slot = {0};
        for (int round = 0; round < base.tier(); round++) {
            table.getRandomItems(params, stack -> {
                if (slot[0] < BccBlockEntity.SLOTS && !stack.isEmpty()) {
                    bcc.setItem(slot[0]++, stack);
                }
            });
        }
        bcc.setChanged();
        return true;
    }

    /** Une case d'air sur un sol, au plus pres du centre du coeur de la base. */
    private static BlockPos rivalSpot(ServerLevel level, EnemyBase base) {
        int cx = (base.coreMin().getX() + base.coreMax().getX()) / 2;
        int cz = (base.coreMin().getZ() + base.coreMax().getZ()) / 2;
        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int y = base.coreMin().getY(); y <= base.coreMax().getY(); y++) {
                        BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                                && level.getBlockState(pos.below()).isSolidRender()
                                && !level.getBlockState(pos.below()).is(Blocks.BEDROCK)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }
}
