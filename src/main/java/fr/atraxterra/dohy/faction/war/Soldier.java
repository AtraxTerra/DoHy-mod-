package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.palamod.golem.GuardianGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Un soldat d'une nation rivale, venu assieger ta base (voir {@link Siege}).
 *
 * <p>Il n'existe que pour un siege : sans siege en cours a son nom, il
 * disparait. Le siege lui donne ses ordres — tenir un point, suivre le
 * chemin du plan jusqu'a une etape, aller a un endroit — et un
 * {@link OrderGoal} les execute, sous les buts de combat : un fantassin
 * accroche par le joueur se bat, puis reprend sa marche.
 *
 * <p>Le long du chemin, il passe ou le plan l'a dit. La navigation vanilla
 * relie les etapes (au plus huit pas d'ecart) ; aux echelles, il monte a la
 * main : il pousse contre le mur, et l'echelle le hisse.
 */
public class Soldier extends Monster implements RangedAttackMob {

    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NATION =
            SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);

    /** Le nombre de visages differents : une texture chacun. */
    public static final int VARIANTS = 4;

    /** Un ordre du siege. */
    record Order(int kind, BlockPos pos, int index, double speed, double radius) {
        static final int IDLE = 0;
        static final int HOLD = 1;
        static final int ROUTE = 2;
        static final int GOTO = 3;

        static final Order NONE = new Order(IDLE, BlockPos.ZERO, 0, 1.0d, 1.0d);
    }

    private UUID siege;
    private Order order = Order.NONE;
    /** L'etape du chemin la plus proche, -1 tant qu'on ne l'a pas cherchee. */
    int routeIndex = -1;
    private BlockPos navTarget;
    private int navCooldown;
    private Vec3 lastCheck = Vec3.ZERO;
    /** Les verifications de suite (toutes les 2 s) sans avancer alors qu'il a un ordre de marche. */
    int stuck;
    /**
     * Egare : tombe du chemin (pousse dans une cour, d'une echelle), il n'y retrouve plus de
     * passage. Il tient sa place et se bat si on vient le chercher ; il retente sa chance de
     * temps en temps.
     */
    boolean lost;
    int lostTicks;
    /** Arrive a l'etape que son ordre de marche visait : il attend, il n'est pas bloque. */
    boolean arrived;
    private boolean reported;
    private int healCooldown = 60;
    private boolean goalsInstalled;

    public Soldier(EntityType<? extends Soldier> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 6;
        if (getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
            ground.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0d)
                .add(Attributes.ATTACK_DAMAGE, 3.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.27d)
                .add(Attributes.FOLLOW_RANGE, 40.0d)
                .add(Attributes.SAFE_FALL_DISTANCE, 5.0d)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.5d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, 0);
        builder.define(DATA_NATION, 0);
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    // -- Identite --------------------------------------------------------------------------------

    public SoldierRole role() {
        return SoldierRole.of(entityData.get(DATA_ROLE));
    }

    public int nation() {
        return entityData.get(DATA_NATION);
    }

    public int variant() {
        return Math.floorMod(entityData.get(DATA_VARIANT), VARIANTS);
    }

    public UUID siegeId() {
        return siege;
    }

    Siege siege() {
        return siege == null ? null : Sieges.get(siege);
    }

    /** Fait du soldat un {@code role} de la nation, pour ce siege : sa vie, ses armes, son nom. */
    void enlist(UUID siegeId, SoldierRole role, int nation, RandomSource random) {
        this.siege = siegeId;
        entityData.set(DATA_ROLE, role.ordinal());
        entityData.set(DATA_NATION, nation);
        entityData.set(DATA_VARIANT, random.nextInt(VARIANTS));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(role.health());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(role.damage());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role.speed());
        if (role == SoldierRole.COMMANDANT) {
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6d);
        }
        setHealth(getMaxHealth());
        NationTraits traits = NationTraits.of(nation);
        equip(role, traits);
        setCustomName(Component.literal(role.label() + " " + traits.de()));
        setCustomNameVisible(role == SoldierRole.COMMANDANT || role == SoldierRole.TRESORIER);
        installGoals();
    }

    private void equip(SoldierRole role, NationTraits traits) {
        DyeColor color = traits.color();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
        switch (role) {
            case FANTASSIN -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
                setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(traits.doctrine() == NationTraits.Doctrine.CHOC
                        ? Items.IRON_AXE : Items.IRON_SWORD));
            }
            case ARCHER -> {
                setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, color));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
                setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, color));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
            case SAPEUR -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, color));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FLINT_AND_STEEL));
                // Un paquet de dynamite, pas un bloc de TNT : avec NTM, un porteur de TNT qui prend feu explose.
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(
                        fr.atraxterra.dohy.palamod.pillage.PalaPillage.BIG_DYNAMITE.get()));
            }
            case INGENIEUR -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LADDER));
            }
            case MEDECIN -> {
                setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, DyeColor.WHITE));
                setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, DyeColor.WHITE));
                setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
                setItemSlot(EquipmentSlot.MAINHAND, healing());
            }
            case COMMANDANT -> {
                setItemSlot(EquipmentSlot.HEAD, banner(level(), traits));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, color));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            }
            case TRESORIER -> {
                setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CHEST));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
                setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
                setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
            }
        }
    }

    private static ItemStack dyed(Item item, DyeColor color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor() & 0xFFFFFF));
        return stack;
    }

    private static ItemStack healing() {
        return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);
    }

    /** La banniere d'une nation : sa couleur, et deux motifs tires de son rang. */
    static ItemStack banner(Level level, NationTraits traits) {
        DyeColor base = traits.color();
        DyeColor ink = base == DyeColor.WHITE || base == DyeColor.YELLOW || base == DyeColor.LIGHT_BLUE
                ? DyeColor.BLACK : DyeColor.WHITE;
        ItemStack stack = new ItemStack(Items.BANNER.pick(base));
        var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        List<net.minecraft.resources.ResourceKey<BannerPattern>> shapes = List.of(BannerPatterns.CROSS,
                BannerPatterns.STRAIGHT_CROSS, BannerPatterns.TRIANGLE_TOP, BannerPatterns.RHOMBUS_MIDDLE,
                BannerPatterns.CIRCLE_MIDDLE, BannerPatterns.STRIPE_CENTER, BannerPatterns.HALF_HORIZONTAL,
                BannerPatterns.TRIANGLES_BOTTOM, BannerPatterns.SKULL, BannerPatterns.FLOWER,
                BannerPatterns.BORDER, BannerPatterns.GRADIENT);
        int index = Math.floorMod(traits.name().hashCode(), shapes.size());
        BannerPatternLayers layers = new BannerPatternLayers.Builder()
                .addIfRegistered(patterns, shapes.get(index), ink)
                .addIfRegistered(patterns, BannerPatterns.BORDER, ink)
                .build();
        stack.set(DataComponents.BANNER_PATTERNS, layers);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Etendard " + traits.de()));
        return stack;
    }

    /** Les buts selon le role. Appele a l'enrolement et au rechargement. */
    private void installGoals() {
        if (level().isClientSide() || goalsInstalled) {
            return;
        }
        goalsInstalled = true;
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        SoldierRole role = role();
        goalSelector.addGoal(0, new FloatGoal(this));
        if (role.shy()) {
            goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 4.5f, 1.1d, 1.3d,
                    target -> isFoe(target) && !holdingGround()));
        }
        switch (role) {
            case ARCHER -> {
                goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 3.5f, 1.0d, 1.2d,
                        this::isFoe));
                goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0d, 30, 18.0f));
            }
            case MEDECIN -> goalSelector.addGoal(2, new HealGoal());
            case FANTASSIN, COMMANDANT, INGENIEUR -> goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15d, false));
            default -> {
            }
        }
        goalSelector.addGoal(3, new OrderGoal());
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, Soldier.class).setAlertOthers(Soldier.class));
        if (role.fighter() || role == SoldierRole.ARCHER) {
            double reach = role == SoldierRole.ARCHER ? 20.0d : role == SoldierRole.COMMANDANT ? 10.0d : 16.0d;
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                    (target, level) -> isFoe(target) && distanceToSqr(target) < reach * reach));
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, GuardianGolem.class, 10, true, false,
                    (target, level) -> distanceToSqr(target) < 14.0d * 14.0d && defends(target)));
        }
    }

    /** Le joueur assiege, en jeu (ni createur ni spectateur). */
    boolean isFoe(LivingEntity target) {
        Siege current = siege();
        return target instanceof Player player && current != null && player.getUUID().equals(current.player)
                && !player.isCreative() && !player.isSpectator();
    }

    /** Un golem gardien du joueur assiege. */
    private boolean defends(LivingEntity target) {
        Siege current = siege();
        return current != null && target instanceof GuardianGolem golem
                && golem.owner().map(current.player::equals).orElse(true);
    }

    /** Un sapeur qui arme sa charge ne lache pas son poste. */
    private boolean holdingGround() {
        return getPose() == Pose.CROUCHING;
    }

    // -- Ordres ----------------------------------------------------------------------------------

    Order order() {
        return order;
    }

    void orderHold(BlockPos pos, double radius) {
        setOrder(new Order(Order.HOLD, pos, 0, 1.0d, radius));
    }

    void orderRoute(int index, double speed) {
        setOrder(new Order(Order.ROUTE, BlockPos.ZERO, index, speed, 1.5d));
    }

    void orderGoTo(BlockPos pos, double speed) {
        setOrder(new Order(Order.GOTO, pos, 0, speed, 1.2d));
    }

    private void setOrder(Order next) {
        if (!next.equals(order)) {
            order = next;
            navTarget = null;
            navCooldown = 0;
            arrived = false;
        }
    }

    /** Est-il a moins de {@code distance} blocs (horizontalement) de ce point, a 2 blocs pres en hauteur ? */
    boolean near(BlockPos pos, double distance) {
        double dx = getX() - (pos.getX() + 0.5d);
        double dz = getZ() - (pos.getZ() + 0.5d);
        return dx * dx + dz * dz <= distance * distance && Math.abs(getY() - pos.getY()) <= 2.5d;
    }

    /** L'etape du chemin la plus proche, cherchee autour de la derniere connue. */
    int locate(List<SiegePlanner.Step> steps) {
        int from = routeIndex < 0 ? 0 : Math.max(0, routeIndex - 8);
        int to = routeIndex < 0 ? steps.size() - 1 : Math.min(steps.size() - 1, routeIndex + 14);
        int best = routeIndex < 0 ? 0 : routeIndex;
        double bestDistance = Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            BlockPos pos = steps.get(i).pos();
            double d = distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
            if (d < bestDistance - 0.01d) {
                bestDistance = d;
                best = i;
            }
        }
        if (bestDistance > 64.0d && routeIndex >= 0) {
            // Tres loin de la ou on le croyait (repousse, ou il a poursuivi le joueur) : on cherche partout.
            routeIndex = -1;
            return locate(steps);
        }
        routeIndex = best;
        return best;
    }

    private void navigate(BlockPos pos, double speed) {
        if (pos.equals(navTarget) && navCooldown > 0 && !getNavigation().isDone()) {
            navCooldown--;
            return;
        }
        navTarget = pos;
        navCooldown = 20;
        if (!getNavigation().moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, speed)) {
            // Pas de chemin pour la navigation vanilla : tout droit, le mouvement fera le reste.
            getMoveControl().setWantedPosition(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, speed);
        }
    }

    /** Suit le chemin du plan jusqu'a l'etape {@code target}, dans un sens ou dans l'autre. */
    private void followRoute(Siege current, int target, double speed) {
        List<SiegePlanner.Step> steps = current.steps();
        if (steps.isEmpty()) {
            return;
        }
        target = Math.max(0, Math.min(steps.size() - 1, target));
        int at = locate(steps);
        arrived = Math.abs(at - target) <= 2 && near(steps.get(target).pos(), 3.0d);
        if (at == target && near(steps.get(target).pos(), 1.3d)) {
            getNavigation().stop();
            return;
        }
        if (target >= at) {
            int next = Math.min(target, at + 8);
            for (int k = at + 1; k <= next; k++) {
                if (steps.get(k).move() == SiegePlanner.M_LADDER) {
                    if (climbing(steps, k)) {
                        climb(steps.get(k).pos(), speed);
                        return;
                    }
                    if (k == at + 1 && near(steps.get(at).pos(), 0.9d)) {
                        BlockPos base = steps.get(k - 1).pos();
                        if (ladderBusy(current, base, base.getY(), steps.get(k).pos().getY())) {
                            // Un camarade est sur l'echelle : on attend son tour, a cote.
                            navigate(steps.get(Math.max(0, at - 2)).pos(), speed);
                            return;
                        }
                        climb(steps.get(k).pos(), speed);
                        return;
                    }
                    next = k - 1;
                    break;
                }
            }
            if (next == at && !near(steps.get(at).pos(), 0.9d)) {
                navigate(steps.get(at).pos(), speed);
                return;
            }
            navigate(steps.get(Math.max(next, at)).pos(), speed);
        } else {
            int next = Math.max(target, at - 8);
            for (int k = at; k > next; k--) {
                if (steps.get(k).move() == SiegePlanner.M_DROP
                        && steps.get(k - 1).pos().getY() - steps.get(k).pos().getY() >= 2) {
                    // Remonter une descente : l'echelle posee contre la falaise, un a la fois.
                    boolean on = climbingUp(steps, k);
                    if (k == at || on) {
                        BlockPos landing = steps.get(k).pos();
                        if (!on && ladderBusy(current, landing, landing.getY(), steps.get(k - 1).pos().getY())) {
                            navigate(steps.get(Math.min(steps.size() - 1, k + 2)).pos(), speed);
                            return;
                        }
                        climb(steps.get(k - 1).pos(), speed);
                        if (getY() >= steps.get(k - 1).pos().getY() - 0.2d) {
                            routeIndex = k - 1;
                        }
                        return;
                    }
                    next = k;
                    break;
                }
                if (steps.get(k).move() == SiegePlanner.M_LADDER) {
                    BlockPos top = steps.get(k).pos();
                    BlockPos base = steps.get(k - 1).pos();
                    if (k == at) {
                        // Redescendre : on enjambe le haut de l'echelle, elle freine la descente.
                        getNavigation().stop();
                        getMoveControl().setWantedPosition(base.getX() + 0.5d, top.getY(), base.getZ() + 0.5d,
                                speed);
                        if (getY() <= base.getY() + 0.6d) {
                            routeIndex = k - 1;
                        }
                        return;
                    }
                    next = k;
                    break;
                }
            }
            navigate(steps.get(next).pos(), speed);
        }
    }

    private boolean climbing(List<SiegePlanner.Step> steps, int ladder) {
        BlockPos base = steps.get(ladder - 1).pos();
        BlockPos top = steps.get(ladder).pos();
        double dx = getX() - (base.getX() + 0.5d);
        double dz = getZ() - (base.getZ() + 0.5d);
        return dx * dx + dz * dz < 0.8d && getY() > base.getY() + 0.4d && getY() < top.getY() + 0.5d;
    }

    /** Un autre soldat du siege est-il dans cette colonne d'echelle, entre ces hauteurs ? */
    private boolean ladderBusy(Siege current, BlockPos column, int bottom, int top) {
        for (Soldier other : current.alive()) {
            if (other == this) {
                continue;
            }
            double dx = other.getX() - (column.getX() + 0.5d);
            double dz = other.getZ() - (column.getZ() + 0.5d);
            if (dx * dx + dz * dz < 0.6d && other.getY() > bottom + 0.4d && other.getY() < top + 0.5d) {
                return true;
            }
        }
        return false;
    }

    /** Dans la colonne d'une echelle de descente, en train de la remonter. */
    private boolean climbingUp(List<SiegePlanner.Step> steps, int drop) {
        BlockPos landing = steps.get(drop).pos();
        BlockPos top = steps.get(drop - 1).pos();
        double dx = getX() - (landing.getX() + 0.5d);
        double dz = getZ() - (landing.getZ() + 0.5d);
        return dx * dx + dz * dz < 0.8d && getY() > landing.getY() + 0.3d && getY() < top.getY() + 0.5d;
    }

    /** Monte a l'echelle : pousse vers le haut du mur, l'echelle hisse. */
    private void climb(BlockPos top, double speed) {
        getNavigation().stop();
        getMoveControl().setWantedPosition(top.getX() + 0.5d, top.getY(), top.getZ() + 0.5d, speed);
        if (onClimbable() || horizontalCollision) {
            getJumpControl().jump();
        }
    }

    /** Execute l'ordre courant ; appele par {@link OrderGoal} a chaque tick. */
    private void drive() {
        Siege current = siege();
        if (current == null) {
            return;
        }
        switch (order.kind()) {
            case Order.HOLD -> {
                if (!near(order.pos(), order.radius())) {
                    navigate(order.pos(), 1.0d);
                } else if (!getNavigation().isDone()) {
                    getNavigation().stop();
                }
            }
            case Order.ROUTE -> followRoute(current, order.index(), order.speed());
            case Order.GOTO -> {
                if (!near(order.pos(), order.radius())) {
                    navigate(order.pos(), order.speed());
                } else {
                    getNavigation().stop();
                }
            }
            default -> {
            }
        }
    }

    // -- Le monde --------------------------------------------------------------------------------

    @Override
    protected void customServerAiStep(ServerLevel level) {
        Siege current = siege();
        if (current == null || current.finished()) {
            discard();
            return;
        }
        if (!goalsInstalled) {
            installGoals();
        }
        if (tickCount % 40 == 0) {
            Vec3 here = position();
            boolean moving = order.kind() == Order.ROUTE && !arrived || order.kind() == Order.GOTO
                    && !near(order.pos(), order.radius() + 0.5d)
                    || order.kind() == Order.HOLD && !near(order.pos(), order.radius() + 0.5d);
            stuck = moving && getTarget() == null && here.distanceToSqr(lastCheck) < 0.25d ? stuck + 1 : 0;
            lastCheck = here;
            if (stuck >= 10 && !lost) {
                lost = true;
                lostTicks = 0;
                stuck = 0;
                current.onLost(this, !reported);
                reported = true;
            }
        }
        if (lost && ++lostTicks > 600) {
            // Le terrain a pu changer (une echelle posee, une breche) : il retente.
            lost = false;
            routeIndex = -1;
        }
        super.customServerAiStep(level);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Les charges du siege : la troupe sait quand elles sautent, elle n'en prend que les eclats.
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.item.PrimedTnt tnt
                && tnt.entityTags().contains(Sieges.CHARGE_TAG)) {
            return super.hurtServer(level, source, amount * 0.1f);
        }
        // Pas de tir ami : les fleches des archers, les coups de leurs camarades.
        if (source.getEntity() instanceof Soldier other && siege != null && siege.equals(other.siege)) {
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
                return super.hurtServer(level, source, amount * 0.35f);
            }
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        Siege current = siege();
        if (current != null && !level().isClientSide()) {
            current.onDeath(this, source);
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        return 6;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack ammo = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, power, weapon);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333d) - arrow.getY();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (level() instanceof ServerLevel server) {
            Projectile.spawnProjectileUsingShoot(arrow, server, ammo, dx, dy + distance * 0.2d, dz, 1.6f,
                    (float) (12 - server.getDifficulty().getId() * 3));
        }
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("siege", siege == null ? "" : siege.toString());
        output.putInt("role", entityData.get(DATA_ROLE));
        output.putInt("nation", entityData.get(DATA_NATION));
        output.putInt("variant", entityData.get(DATA_VARIANT));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        String raw = input.getStringOr("siege", "");
        try {
            siege = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            siege = null;
        }
        entityData.set(DATA_ROLE, input.getIntOr("role", 0));
        entityData.set(DATA_NATION, input.getIntOr("nation", 0));
        entityData.set(DATA_VARIANT, input.getIntOr("variant", 0));
    }

    // -- Les buts --------------------------------------------------------------------------------

    /** Ce que le siege a ordonne : sous le combat, sauf pour ceux qui ne se battent pas. */
    private final class OrderGoal extends Goal {

        OrderGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return order.kind() != Order.IDLE && siege() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            drive();
        }

        @Override
        public void stop() {
            getNavigation().stop();
            navTarget = null;
        }
    }

    /** Le medecin : une potion de soin au blesse le plus atteint, a portee. */
    private final class HealGoal extends Goal {
        private Soldier patient;
        private int throwDelay;

        HealGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (--healCooldown > 0) {
                return false;
            }
            healCooldown = 20;
            Soldier worst = null;
            double ratio = 0.7d;
            for (Soldier other : level().getEntitiesOfClass(Soldier.class, getBoundingBox().inflate(12.0d),
                    other -> other.isAlive() && siege != null && siege.equals(other.siege))) {
                double health = other.getHealth() / other.getMaxHealth();
                if (health < ratio) {
                    ratio = health;
                    worst = other;
                }
            }
            patient = worst;
            throwDelay = 10;
            return patient != null;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && patient.getHealth() < patient.getMaxHealth() * 0.85d
                    && distanceToSqr(patient) < 16.0d * 16.0d;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(patient, 30.0f, 30.0f);
            if (distanceToSqr(patient) > 5.0d * 5.0d) {
                getNavigation().moveTo(patient, 1.15d);
                return;
            }
            getNavigation().stop();
            if (level() instanceof ServerLevel server && --throwDelay <= 0) {
                ItemStack potion = healing();
                Vec3 motion = patient.getDeltaMovement();
                double dx = patient.getX() + motion.x - getX();
                double dy = patient.getEyeY() - 1.1d - getY();
                double dz = patient.getZ() + motion.z - getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);
                Projectile.spawnProjectileUsingShoot(ThrownSplashPotion::new, server, potion, Soldier.this, dx,
                        dy + distance * 0.2d, dz, 0.75f, 4.0f);
                swing(InteractionHand.MAIN_HAND);
                playSound(SoundEvents.WITCH_THROW, 1.0f, 0.8f + getRandom().nextFloat() * 0.4f);
                healCooldown = 90;
                patient = null;
            }
        }

        @Override
        public void stop() {
            patient = null;
        }
    }
}
