package fr.atraxterra.dohy.farmbot;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bot de farming : il marche jusqu'aux cultures mures de sa zone, les recolte, les replante, et
 * vide sa recolte dans les coffres qu'on lui a lies.
 *
 * <p><b>Ce qu'il recolte</b> : les cultures a leur dernier stade (ble, carottes, pommes de terre,
 * betteraves, celles du PalaMod et de l'agriculture), les verrues du Nether, les cabosses de cacao,
 * les pasteques et les citrouilles nees d'une tige, la canne a sucre (il laisse le pied) et les
 * baies sucrees (il les cueille, le buisson reste). Il replante avec une des graines que rend la
 * recolte.
 *
 * <p><b>Comme a la main</b> : la recolte passe par la casse normale du bloc, au nom de son
 * proprietaire quand il est connecte. L'XP de Fermier, les contrats, les collections et les succes
 * la comptent donc comme une recolte faite a la main (choix du joueur). Proprietaire deconnecte, le
 * bot recolte quand meme, sans XP.
 *
 * <p><b>Ses limites</b> : il ne travaille que la ou le jeu tourne (zone chargee), jamais dans le
 * claim d'un autre, et s'arrete quand son sac (27 cases) est plein et ses coffres aussi : il ne
 * laisse rien tomber par terre. Il ne prend aucun degat ; son proprietaire le range depuis son menu.
 */
public class FarmBot extends PathfinderMob {

    public static final int SLOTS = 27;
    public static final int MAX_CHESTS = 6;
    /** Un coffre se lie a moins de tant de blocs de la zone. */
    public static final int CHEST_RANGE = 24;
    /** La zone se trace a moins de tant de blocs du bot. */
    public static final int ZONE_RANGE = 64;

    private static final int SCAN_PER_TICK = 1536;
    private static final int RESCAN_DELAY = 60;
    private static final double REACH = 1.8d;
    private static final double CHEST_REACH = 2.5d;
    private static final int STUCK_TICKS = 80;
    private static final int UNREACHABLE_TICKS = 1200;
    private static final int FULL_CHEST_TICKS = 200;
    /** Il va vider son sac a partir de tant de cases occupees, ou quand il n'a plus rien a recolter. */
    private static final int DEPOSIT_AT = 18;
    /** Et au moins toutes les cinq minutes s'il porte quelque chose. */
    private static final long DEPOSIT_EVERY = 6000L;
    private static final int HARVEST_PAUSE = 8;
    /** Sa vitesse de base, avant les technologies de l'Agriculture. */
    private static final double BASE_SPEED = 0.27d;
    private static final long WARN_EVERY = 6000L;
    private static final int REGISTRY_EVERY = 400;

    /** Le bot dont la recolte est en cours : ce qui tombe va dans son sac ({@link FarmBotEvents}). */
    static FarmBot harvesting;

    /** Ce que le bot sait recolter. */
    enum Kind { NONE, CROP, FRUIT, CANE, BERRIES }

    /** Ce que fait le bot, pour son menu. */
    public enum Status {
        WORKING("Recolte", 0xFF22E558),
        STORING("Range sa recolte", 0xFF27C4E8),
        WAITING("Attend que ca pousse", 0xFFFAC400),
        RETURNING("Rejoint sa zone", 0xFF27C4E8),
        FULL("Coffres pleins", 0xFFE5484D),
        NO_CHEST("Sac plein, aucun coffre", 0xFFE5484D),
        NO_ZONE("Pas de zone", 0xFFFAC400),
        PAUSED("En pause", 0xFF9A9AA5);

        private final String label;
        private final int color;

        Status(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public int color() {
            return color;
        }
    }

    private enum Mode { IDLE, HARVEST, DEPOSIT, RETURN }

    private UUID owner;
    private String ownerName = "";
    /**
     * La vitesse que la nation du proprietaire donne au bot, en pour cent (Agriculture III et V, lot
     * 23f) : relue quand il est la, gardee sinon.
     */
    private int speedBonus;
    private BotZone zone;
    private final List<BlockPos> chests = new ArrayList<>();
    private boolean paused;
    private final SimpleContainer inventory = new SimpleContainer(SLOTS);
    private long harvested;

    // Le travail en cours : rien de cela n'est sauvegarde, tout se retrouve en quelques secondes.
    private Mode mode = Mode.IDLE;
    private Status status = Status.NO_ZONE;
    private BlockPos target;
    private int cooldown;
    private int scanIndex;
    private int scanWait;
    private List<BlockPos> found = new ArrayList<>();
    private List<BlockPos> targets = new ArrayList<>();
    private final Map<Long, Boolean> claims = new HashMap<>();
    private final Map<Long, Long> unreachable = new HashMap<>();
    private final Map<Long, Long> fullChests = new HashMap<>();
    private double bestDistance;
    private int stuckTicks;
    private long lastDeposit;
    private long lastWarning = -WARN_EVERY;

    public FarmBot(EntityType<? extends FarmBot> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0d)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.FOLLOW_RANGE, 64.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // -- Ce que le menu et la telecommande lisent ----------------------------------------------

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean ownedBy(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    void setOwner(ServerPlayer player) {
        this.owner = player.getUUID();
        this.ownerName = player.getName().getString();
    }

    public BotZone zone() {
        return zone;
    }

    public List<BlockPos> chests() {
        return List.copyOf(chests);
    }

    public boolean paused() {
        return paused;
    }

    public Status status() {
        return status;
    }

    public long harvested() {
        return harvested;
    }

    /** Les cultures mures que le dernier tour de la zone a trouvees. */
    public int ripe() {
        return targets.size();
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public int usedSlots() {
        int used = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Vrai si ce coffre est plein depuis le dernier passage du bot. */
    public boolean chestFull(BlockPos pos) {
        Long until = fullChests.get(pos.asLong());
        return until != null && until > level().getGameTime();
    }

    // -- Les reglages ----------------------------------------------------------------------------

    void setZone(BotZone value) {
        this.zone = value;
        resetWork();
    }

    void setPaused(boolean value) {
        this.paused = value;
        resetWork();
    }

    /** L'etat tout de suite, pour le menu qui se redessine avant le prochain tick du bot. */
    private void settle() {
        status = zone == null ? Status.NO_ZONE : paused ? Status.PAUSED : Status.WAITING;
    }

    /** Lie ou delie un coffre ; vrai s'il est lie apres l'appel. */
    boolean toggleChest(BlockPos pos) {
        BlockPos fixed = pos.immutable();
        if (chests.remove(fixed)) {
            return false;
        }
        chests.add(fixed);
        return true;
    }

    void unlink(int index) {
        if (index >= 0 && index < chests.size()) {
            chests.remove(index);
        }
    }

    BotSetup setup() {
        return new BotSetup(level().dimension().identifier().toString(), Optional.ofNullable(zone),
                List.copyOf(chests), paused);
    }

    /** Reprend le reglage d'un bot range, si on le repose dans la meme dimension, pres de sa zone. */
    void apply(BotSetup setup) {
        if (!setup.dimension().equals(level().dimension().identifier().toString())) {
            return;
        }
        BotZone saved = setup.zone().orElse(null);
        if (saved == null || saved.distance(getX(), getZ()) > ZONE_RANGE) {
            return;
        }
        this.zone = saved;
        this.chests.clear();
        this.chests.addAll(setup.chests());
        this.paused = setup.paused();
        resetWork();
    }

    /** Va vider son sac tout de suite. */
    void storeNow() {
        lastDeposit = Long.MIN_VALUE / 2;
        fullChests.clear();
        mode = Mode.IDLE;
        cooldown = 0;
    }

    private void resetWork() {
        settle();
        mode = Mode.IDLE;
        target = null;
        targets = new ArrayList<>();
        found = new ArrayList<>();
        scanIndex = 0;
        scanWait = 0;
        claims.clear();
        unreachable.clear();
        fullChests.clear();
        getNavigation().stop();
    }

    // -- Le travail ------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % REGISTRY_EVERY == 0) {
            FarmBots.update(level, this);
        }
        if (tickCount % 20 == 0) {
            ServerPlayer player = ownerOnline(level);
            if (player != null) {
                speedBonus = fr.atraxterra.dohy.faction.tech.TechPerks.of(player).agriSpeed();
            }
            net.minecraft.world.entity.ai.attributes.AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            double wanted = BASE_SPEED * (1.0d + speedBonus / 100.0d);
            if (speed != null && speed.getBaseValue() != wanted) {
                speed.setBaseValue(wanted);
            }
        }
        if (zone == null) {
            status = Status.NO_ZONE;
            idle();
            return;
        }
        if (paused) {
            status = Status.PAUSED;
            idle();
            return;
        }
        scan(level);
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        switch (mode) {
            case HARVEST -> stepHarvest(level);
            case DEPOSIT -> stepDeposit(level);
            case RETURN -> stepReturn();
            default -> decide(level);
        }
    }

    private void idle() {
        if (mode != Mode.IDLE) {
            mode = Mode.IDLE;
            target = null;
            getNavigation().stop();
        }
    }

    private void decide(ServerLevel level) {
        long now = level.getGameTime();
        int used = usedSlots();
        BlockPos crop = used >= SLOTS ? null : nextTarget(level, now);
        if (!chests.isEmpty() && used > 0
                && (used >= DEPOSIT_AT || crop == null || now - lastDeposit > DEPOSIT_EVERY)) {
            BlockPos chest = nearestChest(now);
            if (chest != null) {
                status = Status.STORING;
                start(Mode.DEPOSIT, chest);
                return;
            }
        }
        if (used >= SLOTS) {
            status = chests.isEmpty() ? Status.NO_CHEST : Status.FULL;
            warn(level, chests.isEmpty() ? "son sac est plein et il n'a aucun coffre : lie-lui un coffre a la"
                    + " telecommande." : "ses coffres sont pleins.");
            cooldown = 100;
            return;
        }
        if (crop != null) {
            status = Status.WORKING;
            start(Mode.HARVEST, crop);
            return;
        }
        if (zone.distance(getX(), getZ()) > 4.0d) {
            status = Status.RETURNING;
            start(Mode.RETURN, zone.center());
            return;
        }
        status = Status.WAITING;
        cooldown = 40;
    }

    private void start(Mode next, BlockPos pos) {
        mode = next;
        target = pos;
        bestDistance = Double.MAX_VALUE;
        stuckTicks = 0;
        walkTo(pos);
    }

    private void walkTo(BlockPos pos) {
        if (!getNavigation().moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 1.0d)) {
            stuckTicks += 20;
        }
    }

    private void stepHarvest(ServerLevel level) {
        if (target == null || !zone.contains(target)
                || kindOf(level, target, level.getBlockState(target)) == Kind.NONE) {
            targets.remove(target);
            mode = Mode.IDLE;
            return;
        }
        double dx = getX() - (target.getX() + 0.5d);
        double dz = getZ() - (target.getZ() + 0.5d);
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= REACH && Math.abs(getY() - target.getY()) <= 3.0d) {
            getNavigation().stop();
            getLookControl().setLookAt(target.getX() + 0.5d, target.getY() + 0.5d, target.getZ() + 0.5d);
            harvest(level, target);
            swing(InteractionHand.MAIN_HAND);
            mode = Mode.IDLE;
            target = null;
            cooldown = fr.atraxterra.dohy.faction.tech.Tech.faster(HARVEST_PAUSE, speedBonus);
            return;
        }
        progress(level, distance);
    }

    private void stepDeposit(ServerLevel level) {
        if (target == null || !chests.contains(target)) {
            mode = Mode.IDLE;
            return;
        }
        double distance = Math.sqrt(distanceToSqr(target.getX() + 0.5d, target.getY() + 0.5d, target.getZ() + 0.5d));
        if (distance <= CHEST_REACH) {
            getNavigation().stop();
            getLookControl().setLookAt(target.getX() + 0.5d, target.getY() + 0.5d, target.getZ() + 0.5d);
            long now = level.getGameTime();
            switch (deposit(level, target)) {
                case MISSING -> {
                    BlockPos lost = target;
                    chests.remove(lost);
                    warn(level, "le coffre en " + lost.getX() + " " + lost.getY() + " " + lost.getZ()
                            + " a disparu : il ne lui est plus lie.");
                }
                case PARTIAL -> fullChests.put(target.asLong(), now + FULL_CHEST_TICKS);
                default -> {
                }
            }
            lastDeposit = now;
            swing(InteractionHand.MAIN_HAND);
            mode = Mode.IDLE;
            target = null;
            cooldown = 10;
            return;
        }
        progress(level, distance);
    }

    private void stepReturn() {
        if (zone.distance(getX(), getZ()) <= 1.0d) {
            mode = Mode.IDLE;
            getNavigation().stop();
            return;
        }
        double distance = Math.sqrt(distanceToSqr(target.getX() + 0.5d, target.getY(), target.getZ() + 0.5d));
        if (distance < bestDistance - 0.3d) {
            bestDistance = distance;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_TICKS * 2) {
            // Il ne trouve pas de chemin vers sa zone : il attend, et reessaiera.
            mode = Mode.IDLE;
            cooldown = 100;
            return;
        }
        if (getNavigation().isDone()) {
            walkTo(target);
        }
    }

    /** Avance vers la cible ; une cible qu'il n'arrive pas a approcher est laissee de cote un moment. */
    private void progress(ServerLevel level, double distance) {
        if (distance < bestDistance - 0.3d) {
            bestDistance = distance;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_TICKS) {
            long until = level.getGameTime() + (mode == Mode.DEPOSIT ? FULL_CHEST_TICKS : UNREACHABLE_TICKS);
            if (mode == Mode.DEPOSIT) {
                fullChests.put(target.asLong(), until);
            } else {
                unreachable.put(target.asLong(), until);
            }
            mode = Mode.IDLE;
            target = null;
            getNavigation().stop();
            return;
        }
        if (getNavigation().isDone()) {
            walkTo(target);
        }
    }

    // -- Le tour de la zone ----------------------------------------------------------------------

    /** Parcourt la zone par morceaux, et garde les cultures mures du dernier tour complet. */
    private void scan(ServerLevel level) {
        if (scanWait > 0) {
            scanWait--;
            return;
        }
        int total = zone.volume();
        int end = Math.min(total, scanIndex + SCAN_PER_TICK);
        for (int index = scanIndex; index < end; index++) {
            BlockPos pos = zone.at(index);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (kindOf(level, pos, level.getBlockState(pos)) != Kind.NONE && allowed(level, pos)) {
                found.add(pos);
            }
        }
        scanIndex = end;
        if (scanIndex >= total) {
            targets = found;
            found = new ArrayList<>();
            scanIndex = 0;
            scanWait = RESCAN_DELAY;
            claims.clear();
            long now = level.getGameTime();
            unreachable.values().removeIf(until -> until <= now);
        }
    }

    /** Jamais dans le claim d'un autre (ni dans une base ennemie). */
    private boolean allowed(ServerLevel level, BlockPos pos) {
        long chunk = ((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL);
        return claims.computeIfAbsent(chunk, key -> {
            ClaimOwner claim = FactionApi.claimAt(level, pos);
            return !claim.claimed() || (owner != null && claim.ownedBy(owner) && !claim.enemy());
        });
    }

    /** La culture mure la plus proche qu'il n'a pas renonce a atteindre. */
    private BlockPos nextTarget(ServerLevel level, long now) {
        for (int attempt = 0; attempt < 32 && !targets.isEmpty(); attempt++) {
            BlockPos best = null;
            double bestSq = Double.MAX_VALUE;
            for (BlockPos pos : targets) {
                Long until = unreachable.get(pos.asLong());
                if (until != null && until > now) {
                    continue;
                }
                double sq = distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
                if (sq < bestSq) {
                    bestSq = sq;
                    best = pos;
                }
            }
            if (best == null) {
                return null;
            }
            if (kindOf(level, best, level.getBlockState(best)) != Kind.NONE) {
                return best;
            }
            targets.remove(best);
        }
        return null;
    }

    static Kind kindOf(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? Kind.CROP : Kind.NONE;
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? Kind.CROP : Kind.NONE;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE ? Kind.CROP : Kind.NONE;
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2 ? Kind.BERRIES : Kind.NONE;
        }
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            return grownOnStem(level, pos) ? Kind.FRUIT : Kind.NONE;
        }
        if (state.is(Blocks.SUGAR_CANE)) {
            // le deuxieme bloc d'une tige : on coupe a partir de la, le pied repousse
            return level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)
                    && !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE) ? Kind.CANE : Kind.NONE;
        }
        return Kind.NONE;
    }

    /** Une pasteque ou une citrouille vers laquelle une tige est tournee : un fruit, pas une deco. */
    private static boolean grownOnStem(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState next = level.getBlockState(pos.relative(side));
            if (next.getBlock() instanceof AttachedStemBlock
                    && next.getValue(AttachedStemBlock.FACING) == side.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    // -- La recolte ------------------------------------------------------------------------------

    private void harvest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        switch (kindOf(level, pos, state)) {
            case CROP -> {
                breakBlock(level, pos);
                replant(level, pos, state);
            }
            case FRUIT -> breakBlock(level, pos);
            case CANE -> {
                int top = pos.getY();
                while (level.getBlockState(pos.atY(top + 1)).is(Blocks.SUGAR_CANE)) {
                    top++;
                }
                for (int y = top; y >= pos.getY(); y--) {
                    breakBlock(level, pos.atY(y));
                }
            }
            case BERRIES -> pickBerries(level, pos, state);
            default -> {
                return;
            }
        }
        harvested++;
        targets.remove(pos);
    }

    /**
     * La casse normale du bloc, au nom du proprietaire s'il est connecte : l'XP de Fermier, les
     * contrats et les collections la comptent. Ce qui tombe va dans le sac du bot.
     */
    private void breakBlock(ServerLevel level, BlockPos pos) {
        ServerPlayer player = ownerOnline(level);
        harvesting = this;
        try {
            level.destroyBlock(pos, true, player != null ? player : this);
        } finally {
            harvesting = null;
        }
    }

    /** Replante la meme culture, avec une graine de la recolte. */
    private void replant(ServerLevel level, BlockPos pos, BlockState old) {
        Block block = old.getBlock();
        Item seed = block.asItem();
        if (seed == Items.AIR || !level.getBlockState(pos).isAir()) {
            return;
        }
        BlockState fresh = block instanceof CocoaBlock ? old.setValue(CocoaBlock.AGE, 0) : block.defaultBlockState();
        if (!fresh.canSurvive(level, pos) || !take(seed)) {
            return;
        }
        level.setBlock(pos, fresh, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.4f, 1.0f);
    }

    /** Les baies se cueillent comme a la main : le buisson reste, a son deuxieme stade. */
    private void pickBerries(ServerLevel level, BlockPos pos, BlockState state) {
        int age = state.getValue(SweetBerryBushBlock.AGE);
        int count = 1 + level.getRandom().nextInt(2) + (age >= 3 ? 1 : 0);
        ItemStack berries = new ItemStack(Items.SWEET_BERRIES, count);
        int left = collect(berries);
        if (left > 0) {
            Block.popResource(level, pos, berries.copyWithCount(left));
        }
        level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f,
                0.8f + level.getRandom().nextFloat() * 0.4f);
    }

    /** Met une pile dans le sac ; rend ce qui n'y tient pas. */
    int collect(ItemStack stack) {
        ItemStack left = inventory.addItem(stack.copy());
        return left.getCount();
    }

    private boolean take(Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    private ServerPlayer ownerOnline(ServerLevel level) {
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    // -- Les coffres -----------------------------------------------------------------------------

    private enum Deposit { DONE, PARTIAL, MISSING }

    private BlockPos nearestChest(long now) {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos pos : chests) {
            Long until = fullChests.get(pos.asLong());
            if (until != null && until > now) {
                continue;
            }
            double sq = distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
            if (sq < bestSq) {
                bestSq = sq;
                best = pos;
            }
        }
        return best;
    }

    /** Vide le sac dans ce coffre, par tout ce qui le laisse entrer (coffre, baril, tiroir, silo...). */
    private Deposit deposit(ServerLevel level, BlockPos pos) {
        ResourceHandler<ItemResource> handler;
        try {
            handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
        } catch (RuntimeException error) {
            handler = null;
        }
        if (handler == null) {
            return Deposit.MISSING;
        }
        boolean left = false;
        int moved = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(ItemResource.of(stack), stack.getCount(), transaction);
                if (inserted > 0) {
                    transaction.commit();
                    stack.shrink(inserted);
                    moved += inserted;
                }
            }
            if (!stack.isEmpty()) {
                left = true;
            }
        }
        inventory.setChanged();
        if (moved > 0) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.7f);
        }
        return left ? Deposit.PARTIAL : Deposit.DONE;
    }

    /** Un mot au proprietaire, pas plus d'une fois toutes les cinq minutes. */
    private void warn(ServerLevel level, String text) {
        long now = level.getGameTime();
        if (now - lastWarning < WARN_EVERY) {
            return;
        }
        lastWarning = now;
        ServerPlayer player = ownerOnline(level);
        if (player != null) {
            BlockPos at = blockPosition();
            player.sendSystemMessage(Component.literal("[Bot de farming] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("En " + at.getX() + " " + at.getY() + " " + at.getZ() + ", " + text)
                            .withStyle(ChatFormatting.GOLD)));
        }
    }

    // -- Les gestes ------------------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!ownedBy(player)) {
            server.sendSystemMessage(Component.literal("Ce bot appartient a " + ownerName + ".")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof BotRemoteItem) {
            BotRemoteItem.select(server, held, this);
            return InteractionResult.SUCCESS;
        }
        FarmBotMenu.INSTANCE.openFor(server, this);
        return InteractionResult.SUCCESS;
    }

    /** Le proprietaire le range : il revient en objet avec son reglage, et son sac revient aussi. */
    void pickUp(ServerPlayer player) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.removeItemNoUpdate(slot);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        ItemStack item = asItem();
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        if (level() instanceof ServerLevel level) {
            FarmBots.unregister(level.getServer(), getUUID());
            level.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6f, 1.0f);
        }
        discard();
    }

    /** L'objet du bot, avec son reglage. */
    ItemStack asItem() {
        ItemStack item = new ItemStack(DoHyFarmBots.FARM_BOT_ITEM.get());
        item.set(DoHyFarmBots.SETUP.get(), setup());
        return item;
    }

    /** Ne prend pas de degats : seul ce qui passe outre l'invulnerabilite (le vide, /kill) l'atteint. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        return false;
    }

    /** Mort quand meme (le vide, /kill) : il rend son sac et redevient un objet, rien ne se perd. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    spawnAtLocation(level, stack);
                }
            }
            spawnAtLocation(level, asItem());
            FarmBots.unregister(level.getServer(), getUUID());
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(DoHyFarmBots.FARM_BOT_ITEM.get());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    // -- Sauvegarde ------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (owner != null) {
            output.store("Owner", UUIDUtil.CODEC, owner);
        }
        output.putString("OwnerName", ownerName);
        if (zone != null) {
            output.store("Zone", BotZone.CODEC, zone);
        }
        output.store("Chests", BlockPos.CODEC.listOf(), List.copyOf(chests));
        output.putBoolean("Paused", paused);
        output.putLong("Harvested", harvested);
        output.putInt("SpeedBonus", speedBonus);
        ContainerHelper.saveAllItems(output, inventory.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("OwnerName", "");
        zone = input.read("Zone", BotZone.CODEC).orElse(null);
        chests.clear();
        chests.addAll(input.read("Chests", BlockPos.CODEC.listOf()).orElse(List.of()));
        paused = input.getBooleanOr("Paused", false);
        harvested = Math.max(0L, input.getLongOr("Harvested", 0L));
        speedBonus = Math.max(0, Math.min(200, input.getIntOr("SpeedBonus", 0)));
        NonNullList<ItemStack> items = inventory.getItems();
        ContainerHelper.loadAllItems(input, items);
        resetWork();
    }
}
