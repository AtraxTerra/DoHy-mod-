package fr.atraxterra.dohy.agri;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Le tracteur.
 *
 * <p>On y monte d'un clic et on le conduit comme on marche : il tourne vers ou
 * l'on regarde, avance et recule. Il brule du carburant a la distance, deux
 * fois plus quand son outil travaille. Un seul outil a la fois, large de trois
 * blocs, qui agit derriere lui : la charrue laboure, le semoir seme les
 * semences chargees, l'epandeur epand l'engrais, la moissonneuse coupe les
 * cereales mures. Un outil attele travaille ; la touche de saut, tenue un
 * instant, l'arrete et le remet en marche (pour traverser sans labourer).
 *
 * <p>Ce qu'il moissonne reste dans sa tremie. Elle se vide toute seule dans
 * un silo des qu'il passe a cote ; a la main, c'est pile par pile, depuis son
 * ecran. Les semences que rend la moisson retournent au semoir.
 *
 * <p>Gestes, a pied : clic pour monter ; clic avec un outil pour l'atteler,
 * avec du carburant, des semences ou de la poudre d'os pour les charger ;
 * accroupi + clic pour ouvrir son ecran ; accroupi + coup pour le demonter (il
 * rend alors tout ce qu'il porte, et garde son carburant).
 *
 * <p>La recolte passe par la casse normale d'un bloc, au nom du conducteur :
 * l'experience de fermier, les contrats et les collections la comptent comme
 * une recolte a la main.
 */
public class Tractor extends PathfinderMob implements PlayerRideableJumping, HasCustomInventoryScreen {

    private static final EntityDataAccessor<Integer> TOOL =
            SynchedEntityData.defineId(Tractor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUEL =
            SynchedEntityData.defineId(Tractor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WORKING =
            SynchedEntityData.defineId(Tractor.class, EntityDataSerializers.BOOLEAN);

    private static final Codec<Map<Identifier, Integer>> STOCK = Codec.unboundedMap(Identifier.CODEC, Codec.INT);
    /** L'outil agit a cette distance derriere le centre du tracteur. */
    private static final double TOOL_REACH = 2.4d;
    /** Degres par tick : le tracteur ne pivote pas sur place comme un cheval. */
    private static final float TURN = 6.0f;
    /**
     * Les dernieres cases epandues : l'epandeur passe plusieurs ticks sur la meme case et ne
     * doit pas y vider sa cuve. Huit blocs de memoire : un second passage, lui, compte.
     */
    private static final int MEMORY = 24;

    /** Le tracteur en train de moissonner : ce que lache le bloc casse lui revient ({@link TractorEvents}). */
    static Tractor harvesting;

    private final Map<Item, Integer> seeds = new LinkedHashMap<>();
    private final Map<Item, Integer> crops = new LinkedHashMap<>();
    private final LinkedHashSet<Long> done = new LinkedHashSet<>();
    private int fertilizer;
    private Item sowing;
    private Vec3 last;
    private double burn;
    private int quiet;

    public Tractor(EntityType<? extends Tractor> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.2d)
                .add(Attributes.STEP_HEIGHT, 1.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TOOL, 0);
        builder.define(FUEL, 0);
        builder.define(WORKING, false);
    }

    @Override
    protected void registerGoals() {
    }

    // -- Etat ----------------------------------------------------------------------

    public TractorTool tool() {
        return TractorTool.byId(entityData.get(TOOL));
    }

    /** Attelle un outil, en marche : c'est l'arreter qui demande un geste, pas s'en servir. */
    public void setTool(TractorTool tool) {
        entityData.set(TOOL, tool.ordinal());
        entityData.set(WORKING, tool != TractorTool.NONE);
        done.clear();
    }

    public int fuel() {
        return entityData.get(FUEL);
    }

    public void setFuel(int fuel) {
        entityData.set(FUEL, Mth.clamp(fuel, 0, tank()));
    }

    public static int tank() {
        return AgriConfig.config().farming().tank();
    }

    public boolean working() {
        return entityData.get(WORKING);
    }

    public void setWorking(boolean working) {
        entityData.set(WORKING, working && tool() != TractorTool.NONE);
        done.clear();
    }

    public Map<Item, Integer> seeds() {
        return seeds;
    }

    public Map<Item, Integer> crops() {
        return crops;
    }

    public int fertilizer() {
        return fertilizer;
    }

    /** La semence que le semoir seme d'abord ; {@code null} : la premiere qui pousse ici. */
    public Item sowing() {
        return sowing;
    }

    public void setSowing(Item item) {
        sowing = item;
    }

    private static int total(Map<Item, Integer> stock) {
        int sum = 0;
        for (int count : stock.values()) {
            sum += count;
        }
        return sum;
    }

    public int seedCount() {
        return total(seeds);
    }

    public int cropCount() {
        return total(crops);
    }

    /** La culture qu'une semence donne, ou {@code null} si ce n'en est pas une que le semoir connait. */
    static Block cropOf(Item item) {
        if (item == Items.WHEAT_SEEDS) {
            return Blocks.WHEAT;
        }
        for (Cereal cereal : Cereal.values()) {
            if (DoHyAgri.seeds(cereal) == item) {
                return DoHyAgri.crop(cereal);
            }
        }
        return null;
    }

    // -- Charger, reprendre --------------------------------------------------------

    /** Ajoute des semences ; rend ce qui a ete pris. */
    public int addSeeds(Item item, int count) {
        int room = AgriConfig.config().farming().seedCapacity() - seedCount();
        int taken = cropOf(item) == null ? 0 : Math.max(0, Math.min(room, count));
        if (taken > 0) {
            seeds.merge(item, taken, Integer::sum);
        }
        return taken;
    }

    /** Ajoute de l'engrais ; rend ce qui a ete pris. */
    public int addFertilizer(int count) {
        int taken = Math.max(0, Math.min(AgriConfig.config().farming().fertilizerCapacity() - fertilizer, count));
        fertilizer += taken;
        return taken;
    }

    /** Retire jusqu'a {@code count} semences de cette sorte ; rend ce qui a ete retire. */
    public int removeSeeds(Item item, int count) {
        int taken = Math.min(count, seeds.getOrDefault(item, 0));
        if (taken > 0) {
            int left = seeds.get(item) - taken;
            if (left > 0) {
                seeds.put(item, left);
            } else {
                seeds.remove(item);
                if (sowing == item) {
                    sowing = null;
                }
            }
        }
        return taken;
    }

    public int removeFertilizer(int count) {
        int taken = Math.min(count, fertilizer);
        fertilizer -= taken;
        return taken;
    }

    /**
     * Ramasse un objet lache par une culture moissonnee : le grain va a la
     * tremie, la semence au semoir. Rend ce qui n'a pas trouve de place.
     */
    int collect(ItemStack stack) {
        AgriConfig.Farming config = AgriConfig.config().farming();
        Item item = stack.getItem();
        int count = stack.getCount();
        if (cropOf(item) != null) {
            return count - addSeeds(item, count);
        }
        if (stack.is(DoHyAgri.CEREALS)) {
            int taken = Math.max(0, Math.min(config.cropCapacity() - cropCount(), count));
            if (taken > 0) {
                crops.merge(item, taken, Integer::sum);
            }
            return count - taken;
        }
        return count;
    }

    // -- Tick ----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    private void serverTick(ServerLevel server) {
        AgriConfig.Farming config = AgriConfig.config().farming();
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getBaseValue() != config.speed()) {
            speed.setBaseValue(config.speed());
        }
        if (quiet > 0) {
            quiet--;
        }
        Vec3 now = position();
        double moved = last == null ? 0.0d : Math.hypot(now.x - last.x, now.z - last.z);
        last = now;
        if (moved > 2.0d) {
            // une teleportation n'est pas un trajet
            moved = 0.0d;
        }
        boolean busy = working() && tool() != TractorTool.NONE;
        if (moved > 0.001d && fuel() > 0) {
            burn += moved * config.moveCost() * (busy ? config.workFactor() : 1.0d);
            int whole = (int) burn;
            if (whole > 0) {
                burn -= whole;
                setFuel(fuel() - whole);
            }
            if (busy) {
                work(server, config);
            }
            if (fuel() <= 0) {
                setWorking(false);
                tell("Reservoir vide.", ChatFormatting.RED);
            }
        }
        if (tickCount % 20 == 0 && !crops.isEmpty()) {
            unload(server, config);
        }
    }

    /** Un message au conducteur, au-dessus de sa barre d'objets ; pas plus d'un par seconde. */
    private void tell(String text, ChatFormatting color) {
        if (quiet == 0 && getControllingPassenger() instanceof ServerPlayer driver) {
            quiet = 20;
            driver.sendSystemMessage(Component.literal(text).withStyle(color), true);
        }
    }

    /** Les trois cases derriere le tracteur, une fois chacune. */
    private void work(ServerLevel server, AgriConfig.Farming config) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yaw);
        double forwardZ = Mth.cos(yaw);
        double baseX = getX() - forwardX * TOOL_REACH;
        double baseZ = getZ() - forwardZ * TOOL_REACH;
        TractorTool tool = tool();
        for (int side = -1; side <= 1; side++) {
            BlockPos ground = BlockPos.containing(baseX - forwardZ * side, getY() - 0.5d, baseZ + forwardX * side);
            // Seul l'epandeur a besoin de se souvenir : les autres outils lisent l'etat du bloc,
            // et repasser sur une case deja faite n'y change rien.
            if (tool == TractorTool.SPREADER) {
                if (!done.add(ground.asLong())) {
                    continue;
                }
                if (done.size() > MEMORY) {
                    done.remove(done.iterator().next());
                }
            }
            BlockPos above = ground.above();
            switch (tool) {
                case PLOW -> plow(server, ground, above);
                case SEEDER -> sow(server, ground, above);
                case SPREADER -> spread(server, above);
                case HARVESTER -> reap(server, above, config);
                default -> {
                }
            }
        }
    }

    private void plow(ServerLevel server, BlockPos ground, BlockPos above) {
        BlockState soil = server.getBlockState(ground);
        if (!soil.is(Blocks.GRASS_BLOCK) && !soil.is(Blocks.DIRT) && !soil.is(Blocks.COARSE_DIRT)
                && !soil.is(Blocks.DIRT_PATH)) {
            return;
        }
        BlockState over = server.getBlockState(above);
        if (!over.isAir()) {
            if (!over.canBeReplaced()) {
                return;
            }
            server.destroyBlock(above, false);
        }
        server.setBlock(ground, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
        server.playSound(null, ground, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 0.5f, 1.0f);
    }

    private void sow(ServerLevel server, BlockPos ground, BlockPos above) {
        if (!(server.getBlockState(ground).getBlock() instanceof FarmlandBlock) || !server.getBlockState(above).isAir()) {
            return;
        }
        if (seeds.isEmpty()) {
            tell("Semoir vide : charge des semences.", ChatFormatting.RED);
            return;
        }
        Item seed = null;
        if (sowing != null && seeds.containsKey(sowing) && growsHere(server, above, sowing)) {
            seed = sowing;
        } else if (sowing == null) {
            for (Item candidate : seeds.keySet()) {
                if (growsHere(server, above, candidate)) {
                    seed = candidate;
                    break;
                }
            }
        }
        if (seed == null) {
            tell("Aucune de ces semences ne pousse dans ce climat.", ChatFormatting.RED);
            return;
        }
        server.setBlock(above, cropOf(seed).defaultBlockState(), Block.UPDATE_ALL);
        server.playSound(null, above, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.5f, 1.0f);
        removeSeeds(seed, 1);
    }

    private static boolean growsHere(ServerLevel server, BlockPos pos, Item seed) {
        Block crop = cropOf(seed);
        if (crop == null || !crop.defaultBlockState().canSurvive(server, pos)) {
            return false;
        }
        return !(crop instanceof AgriCropBlock cereal) || cereal.cereal().growsAt(server, pos);
    }

    private void spread(ServerLevel server, BlockPos above) {
        BlockState state = server.getBlockState(above);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isValidBonemealTarget(server, above, state)) {
            return;
        }
        if (fertilizer <= 0) {
            tell("Epandeur vide : charge de la poudre d'os.", ChatFormatting.RED);
            return;
        }
        fertilizer--;
        if (crop.isBonemealSuccess(server, server.getRandom(), above, state)) {
            crop.performBonemeal(server, server.getRandom(), above, state);
        }
        server.levelEvent(1505, above, 15);
    }

    private void reap(ServerLevel server, BlockPos above, AgriConfig.Farming config) {
        BlockState state = server.getBlockState(above);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)
                || !(state.is(Blocks.WHEAT) || crop instanceof AgriCropBlock)) {
            return;
        }
        if (cropCount() >= config.cropCapacity()) {
            tell("Tremie pleine : va au silo.", ChatFormatting.RED);
            return;
        }
        harvesting = this;
        try {
            server.destroyBlock(above, true, getControllingPassenger());
        } finally {
            harvesting = null;
        }
    }

    /** Vide la tremie dans le premier silo a portee. */
    private void unload(ServerLevel server, AgriConfig.Farming config) {
        int range = config.siloRange();
        BlockPos centre = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-range, -2, -range), centre.offset(range, 3, range))) {
            if (server.getBlockEntity(pos) instanceof SiloBlockEntity silo) {
                int moved = silo.depositAll(crops);
                if (moved > 0) {
                    server.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8f, 0.8f);
                    quiet = 0;
                    tell("Silo : " + moved + " cereales deposees.", ChatFormatting.GREEN);
                    return;
                }
            }
        }
    }

    // -- Gestes ----------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            TractorMenu.INSTANCE.openFor(server, this);
        } else if (held.getItem() instanceof TractorToolItem item) {
            attach(server, item.tool());
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        } else if (cropOf(held.getItem()) != null) {
            int taken = addSeeds(held.getItem(), held.getCount());
            held.shrink(taken);
            say(server, taken > 0 ? "Semences chargees : " + seedCount() + "." : "Le semoir est plein.", taken > 0);
        } else if (held.is(Items.BONE_MEAL)) {
            int taken = addFertilizer(held.getCount());
            held.shrink(taken);
            say(server, taken > 0 ? "Engrais charge : " + fertilizer + "." : "L'epandeur est plein.", taken > 0);
        } else if (TractorFuel.isFuel(player, hand)) {
            int gained = TractorFuel.drain(player, hand, tank() - fuel());
            setFuel(fuel() + gained);
            say(server, gained > 0 ? "Carburant : " + fuel() + " / " + tank() + "." : "Le reservoir est plein.",
                    gained > 0);
        } else if (!isVehicle()) {
            if (!allowed(server)) {
                return InteractionResult.SUCCESS;
            }
            server.startRiding(this);
            say(server, "Carburant : " + fuel() + " / " + tank() + (tool() == TractorTool.NONE ? "."
                    : "  ·  " + tool().label() + (working() ? " en marche" : " a l'arret") + " (saut)."), true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Vrai si ce joueur a le niveau de Fermier du tracteur ; sinon il l'apprend.
     * Le creatif en est dispense, comme pour les verrous de metier.
     */
    static boolean allowed(ServerPlayer player) {
        int needed = AgriConfig.config().tractorLevel();
        if (player.isCreative() || JobApi.level(player, Job.FARMER) >= needed) {
            return true;
        }
        say(player, "Tracteur : il faut etre Fermier niveau " + needed + ".", false);
        return false;
    }

    private static void say(ServerPlayer player, String text, boolean good) {
        player.sendSystemMessage(Component.literal(text).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED),
                true);
    }

    /** Attelle un outil ; celui qui etait attele revient au joueur. */
    public void attach(ServerPlayer player, TractorTool tool) {
        detach(player);
        setTool(tool);
        level().playSound(null, blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.8f, 0.8f);
        say(player, "Outil attele : " + tool.label() + ".", true);
    }

    /** Detelle l'outil et le rend au joueur. */
    public void detach(ServerPlayer player) {
        TractorTool current = tool();
        if (current != TractorTool.NONE) {
            setTool(TractorTool.NONE);
            AgriInv.give(player, DoHyAgri.tool(current), 1);
        }
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (player instanceof ServerPlayer server) {
            TractorMenu.INSTANCE.openFor(server, this);
        }
    }

    /**
     * Rien ne l'abime. Un coup de joueur accroupi (ou en creatif) le demonte :
     * il rend le tracteur avec son carburant, l'outil et tout ce qu'il porte.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        if (source.getEntity() instanceof ServerPlayer player && !hasPassenger(player)) {
            if (player.isShiftKeyDown() || player.isCreative()) {
                dismantle(level);
            } else {
                say(player, "Accroupi + coup : demonter le tracteur.", false);
            }
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) {
            dismantle(server);
        }
    }

    private void dismantle(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        ejectPassengers();
        ItemStack item = new ItemStack(DoHyAgri.TRACTOR_ITEM.get());
        if (fuel() > 0) {
            item.set(DoHyAgri.FUEL.get(), fuel());
        }
        spawnAtLocation(level, item);
        if (tool() != TractorTool.NONE) {
            spawnAtLocation(level, new ItemStack(DoHyAgri.tool(tool())));
        }
        seeds.forEach((seed, count) -> drop(level, seed, count));
        crops.forEach((crop, count) -> drop(level, crop, count));
        drop(level, Items.BONE_MEAL, fertilizer);
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 0.7f);
        discard();
    }

    private void drop(ServerLevel level, Item item, int count) {
        int max = new ItemStack(item).getMaxStackSize();
        for (int left = count; left > 0; left -= max) {
            spawnAtLocation(level, new ItemStack(item, Math.min(left, max)));
        }
    }

    // -- Conduite --------------------------------------------------------------------

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    /**
     * Il tourne vers le regard du conducteur, sans pivoter d'un coup, et seulement
     * quand il roule : regarder autour de soi a l'arret ne le fait pas tourner sur place.
     */
    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        if (player.zza != 0.0f && fuel() > 0) {
            float turn = Mth.wrapDegrees(player.getYRot() - getYRot());
            setRot(getYRot() + Mth.clamp(turn, -TURN, TURN), 0.0f);
        }
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Avant et arriere seulement, la marche arriere au ralenti ; sans carburant, rien. */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        if (fuel() <= 0) {
            return Vec3.ZERO;
        }
        float forward = player.zza;
        return new Vec3(0.0d, 0.0d, forward < 0.0f ? forward * 0.4f : forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        double speed = getAttributeValue(Attributes.MOVEMENT_SPEED);
        boolean busy = working() && tool() != TractorTool.NONE;
        return (float) (busy ? speed * AgriConfig.config().farming().workSpeed() : speed);
    }

    // La touche de saut ne fait pas sauter un tracteur : elle embraye l'outil.

    @Override
    public void onPlayerJump(int strength) {
    }

    @Override
    public boolean canJump() {
        return tool() != TractorTool.NONE;
    }

    @Override
    public void handleStartJump(int strength) {
        if (level().isClientSide() || tool() == TractorTool.NONE) {
            return;
        }
        if (fuel() <= 0) {
            quiet = 0;
            tell("Reservoir vide.", ChatFormatting.RED);
            return;
        }
        setWorking(!working());
        quiet = 0;
        tell(tool().label() + (working() ? " : en marche." : " : a l'arret."),
                working() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    @Override
    public void handleStopJump() {
    }

    // -- Ce qu'un tracteur n'est pas ---------------------------------------------------

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
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
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
    public ItemStack getPickResult() {
        return new ItemStack(DoHyAgri.TRACTOR_ITEM.get());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    // -- Sauvegarde --------------------------------------------------------------------

    private static Map<Identifier, Integer> ids(Map<Item, Integer> stock) {
        Map<Identifier, Integer> map = new LinkedHashMap<>();
        stock.forEach((item, count) -> map.put(BuiltInRegistries.ITEM.getKey(item), count));
        return map;
    }

    private static void items(Map<Identifier, Integer> saved, Map<Item, Integer> stock) {
        stock.clear();
        saved.forEach((id, count) -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
            if (count > 0) {
                stock.put(item, count);
            }
        }));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Fuel", fuel());
        output.putInt("Tool", tool().ordinal());
        output.putBoolean("Working", working());
        output.putInt("Fertilizer", fertilizer);
        output.store("Seeds", STOCK, ids(seeds));
        output.store("Crops", STOCK, ids(crops));
        if (sowing != null) {
            output.store("Sowing", Identifier.CODEC, BuiltInRegistries.ITEM.getKey(sowing));
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(TOOL, TractorTool.byId(input.getIntOr("Tool", 0)).ordinal());
        entityData.set(WORKING, tool() != TractorTool.NONE && input.getBooleanOr("Working", true));
        setFuel(input.getIntOr("Fuel", 0));
        fertilizer = Math.max(0, input.getIntOr("Fertilizer", 0));
        items(input.read("Seeds", STOCK).orElse(Map.of()), seeds);
        items(input.read("Crops", STOCK).orElse(Map.of()), crops);
        sowing = input.read("Sowing", Identifier.CODEC).flatMap(BuiltInRegistries.ITEM::getOptional).orElse(null);
    }
}
