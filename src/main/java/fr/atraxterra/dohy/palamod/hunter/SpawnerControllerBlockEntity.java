package fr.atraxterra.dohy.palamod.hunter;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.job.JobConfig;
import fr.atraxterra.dohy.job.JobGate;
import fr.atraxterra.dohy.job.JobXpEvents;
import fr.atraxterra.dohy.palamod.machine.MachineBlockEntity;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le controleur de spawners ({@code TileEntitySpawnerController}).
 *
 * <p>Une fois par seconde il verifie sa structure ({@link SpawnerBlueprint}),
 * recense les spawners customises qu'elle abrite et fait tomber les vagues :
 * <ul>
 *   <li><b>spawners</b> : le volume de 7 x 7 x 3 au-dessus du centre, la
 *       colonne du controleur exceptee, jusqu'au plafond du palier (27, 54, 81,
 *       108). Un spawner sous 100 ames compte dans ce plafond mais ne produit
 *       rien ; un spawner d'un palier superieur a la structure travaille au
 *       palier de la structure ;</li>
 *   <li><b>vague</b> : par palier, toutes les 12, 15, 15 ou 7 secondes, chaque
 *       spawner rend 2 a 3, 3 a 5, 5 a 7 ou 6 a 11 creatures — un seul tirage
 *       par groupe de spawners de la meme creature et du meme palier, multiplie
 *       par leur nombre ;</li>
 *   <li><b>mode manuel</b> : les creatures s'accumulent sur une creature
 *       immobile posee sur le controleur ; un coup en tue une (butin, XP,
 *       metier), un clic droit passe a la creature suivante ;</li>
 *   <li><b>mode automatique</b> : le butin va droit dans les coffres, sans XP.
 *       Sans coffre libre, la vague attend.</li>
 * </ul>
 * Les coffres : cinq cases en ligne au sol, contre la structure, sous le
 * controleur ({@code getInventoriesLocations}). Un seul controleur travaille par
 * troncon.
 *
 * <p>Les spawners recenses passent a l'etat {@code linked} : ils cessent de
 * faire apparaitre des creatures dans le monde ({@link CustomSpawnerBlock}).
 */
public class SpawnerControllerBlockEntity extends MachineBlockEntity implements MachineMenu.Buttons {

    public static final int SLOT_COUNT = 4;
    /** Les deux cases libres ; les deux autres attendent l'amelioration de capacite. */
    public static final int FREE_SLOTS = 2;
    public static final int BUTTON_MODE = 0;
    public static final int CHEST_SPOTS = 5;

    static final String HOLOGRAM_TAG = "palamod.spawner.hologram";

    /** {@code canVerify} : une verification par seconde. */
    private static final int PERIOD = 20;
    /** {@code hurtResistantTime = 11} : un coup toutes les dix ticks au plus. */
    private static final int HIT_COOLDOWN = 10;
    /** {@code 0.11d} : chances sur cent d'un slime, par amelioration et par creature. */
    private static final double SLIME_CHANCE = 0.11d;
    /** Demi-cote du volume des spawners, et sa hauteur. */
    private static final int REACH = 3;
    private static final int HEIGHT = 3;
    private static final int STOCK_CAP = 1_000_000_000;

    /** Un groupe de spawners : meme creature, meme palier effectif. */
    private record Group(EntityType<?> type, SpawnerTier tier, int count) {
    }

    private boolean automatic;
    /** Mode manuel : les creatures en attente, par type, dans l'ordre d'arrivee. */
    private final LinkedHashMap<Identifier, Integer> stock = new LinkedHashMap<>();
    private Identifier selected;
    /** Les niveaux de metier du dernier joueur a avoir ouvert ({@code putJobData}). */
    private final EnumMap<Job, Integer> ownerLevels = new EnumMap<>(Job.class);

    // -- Etat non sauvegarde ---------------------------------------------------
    private SpawnerBlueprint.Match match;
    private boolean verified;
    private final long[] lastWave = new long[SpawnerTier.values().length];
    private boolean timersStarted;
    private int spawnerCount;
    private int chestCount;
    private SpawnerTier shownTier;
    private Mob hologram;
    private final Map<EntityType<?>, LivingEntity> templates = new HashMap<>();
    private long lastHit;
    private int ticks;

    public SpawnerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PalaHunter.SPAWNER_CONTROLLER_TYPE.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.spawner_controller"));
    }

    // -- Ameliorations ---------------------------------------------------------

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    int count(SpawnerUpgrade upgrade) {
        int count = 0;
        for (ItemStack stack : items) {
            if (SpawnerUpgrade.of(stack) == upgrade) {
                count++;
            }
        }
        return count;
    }

    /**
     * {@code isUpgradeValidForSlot} : sans amelioration de capacite, deux cases
     * et jamais deux fois la meme amelioration ; avec elle, les quatre cases et
     * les doublons — mais une seule capacite.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        SpawnerUpgrade upgrade = SpawnerUpgrade.of(stack);
        if (upgrade == null || !ownerMay(stack)) {
            return false;
        }
        boolean more = count(SpawnerUpgrade.MORE) > 0;
        if (more) {
            return upgrade != SpawnerUpgrade.MORE;
        }
        return slot < FREE_SLOTS && count(upgrade) == 0;
    }

    private boolean ownerMay(ItemStack stack) {
        JobConfig.Unlock unlock = JobGate.of(stack);
        if (unlock == null) {
            return true;
        }
        Job job = Job.byKey(unlock.job());
        return job == null || ownerLevels.getOrDefault(job, 0) >= unlock.level();
    }

    /** {@code removeInvalidSlots} : ce que le retrait de la capacite rend invalide tombe au sol. */
    private void ejectInvalid() {
        if (count(SpawnerUpgrade.MORE) > 0) {
            return;
        }
        List<SpawnerUpgrade> seen = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            SpawnerUpgrade upgrade = SpawnerUpgrade.of(stack);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot >= FREE_SLOTS || upgrade == null || seen.contains(upgrade)) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5d, worldPosition.getY() + 1.0d,
                        worldPosition.getZ() + 0.5d, stack);
                items.set(slot, ItemStack.EMPTY);
                setChanged();
            } else {
                seen.add(upgrade);
            }
        }
    }

    // -- Ouverture -------------------------------------------------------------

    /**
     * {@code onBlockActivated} : l'interface ne s'ouvre que sur une structure
     * valide, seule de son troncon. <b>Ajout</b> : quand la structure est
     * incomplete, le message dit quel bloc manque et ou, et l'endroit scintille.
     */
    @Override
    public boolean canOpen(ServerPlayer player) {
        ServerLevel server = (ServerLevel) player.level();
        if (!ownsChunk(server)) {
            player.sendSystemMessage(Component.translatable("message.palamod.spawner_controller.chunk")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        match = SpawnerBlueprint.find(server, worldPosition, match);
        if (match == null) {
            player.sendSystemMessage(Component.translatable("message.palamod.spawner_controller.invalid")
                    .withStyle(ChatFormatting.RED));
            SpawnerBlueprint.Missing missing = SpawnerBlueprint.missing(server, worldPosition);
            if (missing != null) {
                BlockPos at = missing.pos();
                player.sendSystemMessage(Component.translatable("message.palamod.spawner_controller.missing",
                        missing.tier().roman(), missing.placed(), missing.total(),
                        missing.expected().getName(), at.getX(), at.getY(), at.getZ())
                        .withStyle(ChatFormatting.GRAY));
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5d, at.getY() + 0.5d,
                        at.getZ() + 0.5d, 12, 0.3d, 0.3d, 0.3d, 0.0d);
            }
            return false;
        }
        for (Job job : Job.values()) {
            ownerLevels.put(job, player.isCreative() ? JobApi.maxLevel() : JobApi.level(player, job));
        }
        setChanged();
        return true;
    }

    /** {@code CSChangeSpawnerController} : changer de mode vide les creatures en attente. */
    @Override
    public boolean pressButton(ServerPlayer player, int id) {
        if (id != BUTTON_MODE) {
            return false;
        }
        automatic = !automatic;
        stock.clear();
        selected = null;
        dropHologram();
        setChanged();
        return true;
    }

    // -- La seconde ------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (hologram != null) {
            if (hologram.isAlive()) {
                // Un zombie ou un squelette au soleil : il ne doit pas flamber.
                hologram.clearFire();
            } else {
                hologram = null;
            }
        }
        if (++ticks % PERIOD != 0) {
            return;
        }
        verified = true;
        if (!ownsChunk(server)) {
            match = null;
        } else {
            match = SpawnerBlueprint.find(server, worldPosition, match);
        }
        if (match == null) {
            spawnerCount = 0;
            chestCount = 0;
            shownTier = null;
            dropHologram();
            return;
        }
        ejectInvalid();
        List<Group> groups = scan(server);
        List<Container> chests = chests(server);
        chestCount = chests.size();
        long now = server.getGameTime();
        if (!timersStarted) {
            java.util.Arrays.fill(lastWave, now);
            timersStarted = true;
        }
        int speed = count(SpawnerUpgrade.SPEED);
        int slime = count(SpawnerUpgrade.SLIME);
        int looting = count(SpawnerUpgrade.LOOTING);
        RandomSource random = server.getRandom();
        boolean[] fired = new boolean[lastWave.length];
        shownTier = groups.isEmpty() ? null : groups.get(0).tier();
        for (Group group : groups) {
            int tier = group.tier().ordinal();
            if (now - lastWave[tier] < group.tier().interval(speed)) {
                continue;
            }
            int amount = group.tier().amount(random) * group.count();
            int slimes = 0;
            for (int index = 0; slime > 0 && index < amount; index++) {
                if (random.nextDouble() * 100.0d <= SLIME_CHANCE * slime) {
                    slimes++;
                }
            }
            if (automatic) {
                if (!produce(server, chests, group.type(), amount, looting)) {
                    continue;
                }
                if (slimes > 0) {
                    produce(server, chests, EntityTypes.SLIME, slimes, 0);
                }
            } else {
                add(group.type(), amount);
                if (slimes > 0) {
                    add(EntityTypes.SLIME, slimes);
                }
            }
            fired[tier] = true;
        }
        for (int tier = 0; tier < fired.length; tier++) {
            if (fired[tier]) {
                lastWave[tier] = now;
            }
        }
        if (automatic) {
            dropHologram();
        } else {
            showHologram(server);
        }
    }

    /**
     * Un seul controleur par troncon ({@code func_145845_h} garde le premier de
     * la carte du troncon) : ici celui de plus petite position, pour que ce
     * soit toujours le meme.
     */
    private boolean ownsChunk(ServerLevel server) {
        for (BlockEntity other : server.getChunkAt(worldPosition).getBlockEntities().values()) {
            if (other != this && other instanceof SpawnerControllerBlockEntity && !other.isRemoved()
                    && other.getBlockPos().asLong() < worldPosition.asLong()) {
                return false;
            }
        }
        return true;
    }

    /** Vrai si ce spawner est dans le volume d'une structure valide de ce controleur. */
    boolean covers(BlockPos spawner) {
        if (!verified) {
            // Pas encore de verification depuis le chargement : on ne delie rien.
            return true;
        }
        if (match == null) {
            return false;
        }
        BlockPos center = match.center();
        int dy = spawner.getY() - center.getY();
        return Math.abs(spawner.getX() - center.getX()) <= REACH && Math.abs(spawner.getZ() - center.getZ()) <= REACH
                && dy >= 1 && dy <= HEIGHT
                && (spawner.getX() != worldPosition.getX() || spawner.getZ() != worldPosition.getZ());
    }

    /** {@code getSpawnersFromController}. */
    private List<Group> scan(ServerLevel server) {
        BlockPos center = match.center();
        int max = match.tier().maxSpawners();
        int counted = 0;
        Map<Long, Group> groups = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        scan:
        for (int dy = 1; dy <= HEIGHT; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (pos.getX() == worldPosition.getX() && pos.getZ() == worldPosition.getZ()) {
                        continue;
                    }
                    BlockState state = server.getBlockState(pos);
                    if (!(state.getBlock() instanceof CustomSpawnerBlock)
                            || !(server.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
                        continue;
                    }
                    if (counted >= max) {
                        break scan;
                    }
                    long link = spawner.getData(PalaHunter.SPAWNER_LINK.get());
                    if (link != CustomSpawnerBlock.NO_LINK && link != worldPosition.asLong()
                            && server.getBlockEntity(BlockPos.of(link)) instanceof SpawnerControllerBlockEntity) {
                        continue;
                    }
                    if (link != worldPosition.asLong()) {
                        spawner.setData(PalaHunter.SPAWNER_LINK.get(), worldPosition.asLong());
                        spawner.setChanged();
                    }
                    if (!state.getValue(CustomSpawnerBlock.LINKED)) {
                        server.setBlock(pos, state.setValue(CustomSpawnerBlock.LINKED, true), Block.UPDATE_CLIENTS);
                    }
                    counted++;
                    SpawnerSouls souls = spawner.getData(PalaHunter.SPAWNER_SOULS.get());
                    if (souls.tier() == 0 || souls.type().isEmpty()) {
                        continue;
                    }
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(souls.type().get()).orElse(null);
                    if (type == null) {
                        continue;
                    }
                    SpawnerTier tier = SpawnerTier.ofLevel(Math.min(souls.tier(), match.tier().level()));
                    long key = ((long) BuiltInRegistries.ENTITY_TYPE.getId(type) << 3) | tier.ordinal();
                    Group group = groups.get(key);
                    groups.put(key, new Group(type, tier, group == null ? 1 : group.count() + 1));
                }
            }
        }
        spawnerCount = counted;
        return new ArrayList<>(groups.values());
    }

    /**
     * {@code getInventoriesLocations} : cinq cases en ligne, au niveau du sol de
     * la structure, une case au-dela de son bord, du cote du controleur.
     */
    private List<Container> chests(ServerLevel server) {
        List<Container> chests = new ArrayList<>();
        Direction outward = match.outward();
        Direction side = outward.getClockWise();
        BlockPos middle = match.center().relative(outward, REACH + 1);
        for (int offset = -CHEST_SPOTS / 2; offset <= CHEST_SPOTS / 2; offset++) {
            Container container = HopperBlockEntity.getContainerAt(server, middle.relative(side, offset));
            if (container != null) {
                chests.add(container);
            }
        }
        return chests;
    }

    private static boolean hasRoom(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** {@code handleAutomaticMod} : faux sans coffre libre, et la vague attend. */
    private boolean produce(ServerLevel server, List<Container> chests, EntityType<?> type, int amount,
                            int looting) {
        if (chests.stream().noneMatch(SpawnerControllerBlockEntity::hasRoom)) {
            return false;
        }
        LivingEntity template = template(server, type);
        if (template == null) {
            return false;
        }
        store(server, chests, SpawnerLoot.roll(server, worldPosition, template, null, amount,
                type == EntityTypes.SLIME ? 0 : looting));
        return true;
    }

    /** Range dans les coffres, en partant d'un coffre au hasard ; rend ce qui n'a pas trouve de place. */
    private List<ItemStack> store(ServerLevel server, List<Container> chests, List<ItemStack> drops) {
        List<ItemStack> left = new ArrayList<>();
        int start = chests.isEmpty() ? 0 : server.getRandom().nextInt(chests.size());
        for (ItemStack drop : drops) {
            int remaining = drop.getCount();
            while (remaining > 0) {
                ItemStack part = drop.copyWithCount(Math.min(remaining, drop.getMaxStackSize()));
                remaining -= part.getCount();
                for (int index = 0; index < chests.size() && !part.isEmpty(); index++) {
                    Container chest = chests.get((start + index) % chests.size());
                    int before = part.getCount();
                    part = HopperBlockEntity.addItem(null, chest, part, Direction.UP);
                    if (part.getCount() != before) {
                        chest.setChanged();
                    }
                }
                if (!part.isEmpty()) {
                    left.add(part);
                }
            }
        }
        return left;
    }

    /** Le modele d'une creature : jamais ajoute au monde, il ne sert qu'au butin. */
    private LivingEntity template(ServerLevel server, EntityType<?> type) {
        LivingEntity template = templates.get(type);
        if (template == null && type.create(server, EntitySpawnReason.SPAWNER) instanceof LivingEntity created) {
            created.snapTo(worldPosition.getX() + 0.5d, worldPosition.getY() + 1.0d, worldPosition.getZ() + 0.5d,
                    0.0f, 0.0f);
            templates.put(type, created);
            template = created;
        }
        return template;
    }

    // -- Mode manuel -----------------------------------------------------------

    private void add(EntityType<?> type, int amount) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        stock.merge(id, amount, (a, b) -> (int) Math.min(STOCK_CAP, (long) a + b));
        setChanged();
    }

    private EntityType<?> selectedType() {
        if (stock.isEmpty()) {
            selected = null;
            return null;
        }
        if (selected == null || !stock.containsKey(selected)) {
            selected = stock.keySet().iterator().next();
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(selected).orElse(null);
    }

    /** {@code respawnLinkedEntity}, {@code updateEntityName}. */
    private void showHologram(ServerLevel server) {
        EntityType<?> type = selectedType();
        if (type == null) {
            dropHologram();
            return;
        }
        if (hologram == null || !hologram.isAlive() || hologram.getType() != type) {
            dropHologram();
            if (!(type.create(server, EntitySpawnReason.SPAWNER) instanceof Mob mob)) {
                return;
            }
            float yaw = match == null ? 0.0f : match.outward().toYRot();
            mob.snapTo(worldPosition.getX() + 0.5d, worldPosition.getY() + 1.0d, worldPosition.getZ() + 0.5d,
                    yaw, 0.0f);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setNoAi(true);
            mob.setSilent(true);
            mob.setInvulnerable(true);
            mob.setPersistenceRequired();
            mob.addTag(HOLOGRAM_TAG);
            mob.setCustomNameVisible(true);
            server.addFreshEntity(mob);
            hologram = mob;
        }
        hologram.setCustomName(Component.empty()
                .append(type.getDescription().copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" x" + stock.get(selected)).withStyle(ChatFormatting.YELLOW)));
    }

    private void dropHologram() {
        if (hologram != null) {
            hologram.discard();
            hologram = null;
        }
    }

    boolean owns(Entity entity) {
        return entity == hologram;
    }

    /** Clic droit sur la creature : la suivante ({@code nextOrPreviousMobData}). */
    void cycle() {
        if (stock.size() < 2 || !(level instanceof ServerLevel server)) {
            return;
        }
        List<Identifier> keys = new ArrayList<>(stock.keySet());
        selected = keys.get((keys.indexOf(selected) + 1) % keys.size());
        showHologram(server);
        setChanged();
    }

    /**
     * Un coup sur la creature ({@code EntitySpawner.attackEntityFrom}) : une
     * creature de moins, son butin dans un coffre ou aux pieds du joueur, son
     * XP en orbe, et la mort comptee pour les metiers.
     */
    void hit(ServerPlayer player) {
        if (automatic || !(level instanceof ServerLevel server) || match == null) {
            return;
        }
        long now = server.getGameTime();
        if (now - lastHit < HIT_COOLDOWN) {
            return;
        }
        EntityType<?> type = selectedType();
        if (type == null) {
            return;
        }
        lastHit = now;
        Identifier id = selected;
        int left = stock.get(id) - 1;
        if (left <= 0) {
            List<Identifier> keys = new ArrayList<>(stock.keySet());
            stock.remove(id);
            selected = stock.isEmpty() ? null : keys.get((keys.indexOf(id) + 1) % keys.size());
        } else {
            stock.put(id, left);
        }
        if (hologram != null && hologram.isAlive()) {
            server.broadcastDamageEvent(hologram, server.damageSources().playerAttack(player));
        }
        LivingEntity template = template(server, type);
        if (template != null) {
            List<ItemStack> drops = SpawnerLoot.roll(server, worldPosition, template, player, 1,
                    type == EntityTypes.SLIME ? 0 : count(SpawnerUpgrade.LOOTING));
            List<Container> chests = chests(server);
            for (ItemStack rest : chests.isEmpty() ? drops : store(server, chests, drops)) {
                int remaining = rest.getCount();
                while (remaining > 0) {
                    ItemStack part = rest.copyWithCount(Math.min(remaining, rest.getMaxStackSize()));
                    remaining -= part.getCount();
                    ItemEntity item = new ItemEntity(server, player.getX(), player.getY() + 0.25d, player.getZ(),
                            part);
                    item.setNoPickUpDelay();
                    server.addFreshEntity(item);
                }
            }
        }
        ExperienceOrb.award(server, player.position(), type == EntityTypes.SLIME ? 2 : 5);
        JobXpEvents.grantKill(player, id);
        showHologram(server);
        setChanged();
    }

    // -- Ecran -----------------------------------------------------------------

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaHunter.SPAWNER_CONTROLLER_MENU.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.SPAWNER_CONTROLLER;
    }

    /** Ticks ecoules depuis la derniere vague du premier groupe. */
    @Override
    public int work() {
        if (shownTier == null || level == null) {
            return 0;
        }
        return (int) Math.clamp(level.getGameTime() - lastWave[shownTier.ordinal()], 0L, (long) cycleDuration());
    }

    @Override
    public int cycleDuration() {
        return shownTier == null ? 0 : shownTier.interval(count(SpawnerUpgrade.SPEED));
    }

    @Override
    public int tankFill() {
        return chestCount;
    }

    @Override
    public int tankGoal() {
        return CHEST_SPOTS;
    }

    private int stockTotal() {
        long total = 0;
        for (int amount : stock.values()) {
            total += amount;
        }
        return (int) Math.min(STOCK_CAP, total);
    }

    /**
     * 0 : palier de la structure ; 1 : spawners recenses ; 2 : mode ; 3 : les
     * quinze bits bas des creatures en attente. Le reste est dans
     * {@link #selected()} : les entiers d'un menu voyagent sur seize bits.
     */
    @Override
    public int extra(int index) {
        return switch (index) {
            case 0 -> match == null ? 0 : match.tier().level();
            case 1 -> spawnerCount;
            case 2 -> automatic ? 1 : 0;
            default -> stockTotal() & 0x7FFF;
        };
    }

    @Override
    public int selected() {
        return stockTotal() >> 15;
    }

    // -- Sauvegarde ------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        automatic = input.getBooleanOr("Automatic", false);
        stock.clear();
        for (String entry : input.getStringOr("Stock", "").split(";")) {
            int cut = entry.lastIndexOf('=');
            Identifier id = cut <= 0 ? null : Identifier.tryParse(entry.substring(0, cut));
            if (id != null) {
                try {
                    stock.put(id, Math.clamp(Integer.parseInt(entry.substring(cut + 1)), 1, STOCK_CAP));
                } catch (NumberFormatException ignored) {
                    // Une entree illisible est abandonnee, pas le reste.
                }
            }
        }
        selected = Identifier.tryParse(input.getStringOr("Selected", ""));
        int[] levels = input.getIntArray("OwnerLevels").orElse(new int[0]);
        ownerLevels.clear();
        for (Job job : Job.values()) {
            if (job.ordinal() < levels.length) {
                ownerLevels.put(job, levels[job.ordinal()]);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("Automatic", automatic);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Identifier, Integer> entry : stock.entrySet()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        output.putString("Stock", text.toString());
        output.putString("Selected", selected == null ? "" : selected.toString());
        int[] levels = new int[Job.values().length];
        for (Job job : Job.values()) {
            levels[job.ordinal()] = ownerLevels.getOrDefault(job, 0);
        }
        output.putIntArray("OwnerLevels", levels);
    }

    /** Casse ou decharge : la creature du mode manuel ne lui survit pas. */
    @Override
    public void setRemoved() {
        dropHologram();
        super.setRemoved();
    }
}
