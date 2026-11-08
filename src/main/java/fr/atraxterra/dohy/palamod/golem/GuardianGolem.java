package fr.atraxterra.dohy.palamod.golem;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code EntityGolem} : le golem gardien.
 *
 * <p>Il appartient a un joueur, monte jusqu'au niveau 40 en mangeant des
 * metaux, gagne un point de capacite par niveau et le depense dans un arbre a
 * trois voies : utilitaire, guerriere, farmeur. Il attaque tout joueur a 40
 * blocs, hors son proprietaire et sa liste blanche.
 *
 * <p>Statistiques : {@code 200 + 125 x niveau} PV, {@code 6 + 0,25 x niveau}
 * de degats, recul encaisse a moitie, vitesse 0,25.
 *
 * <p><b>Ecarts</b>, des corrections :
 * <ul>
 *   <li>la phase de chargement (30 s, vie a moitie) ne revient plus a chaque
 *       rechargement du chunk, seulement a l'apparition ;</li>
 *   <li>les golems d'un meme proprietaire ne se battent plus entre eux ;</li>
 *   <li>Wither hunter, choix 2 : le golem ne devient plus passif ;</li>
 *   <li>Desynchronisation ne frappe plus le proprietaire ni la liste
 *       blanche ;</li>
 *   <li>Magicien, flamme : 5 secondes comme le dit sa description, pas 100.</li>
 * </ul>
 */
public class GuardianGolem extends PathfinderMob {

    public static final int MAX_LEVEL = 40;
    /** {@code golemFeedTick = 9600} : une croquette toutes les 8 minutes. */
    public static final int FEED_COOLDOWN = 9600;
    private static final int LOADING_SECONDS = 30;
    private static final int COMBAT_SECONDS = 300;
    private static final int BOSS_BAR_RANGE = 32;
    public static final int STORAGE_SLOTS = 16;
    public static final double MENU_RANGE = 10.0d;

    private UUID owner;
    private String ownerName = "";
    private int level;
    private int subXp;
    private int points;
    private final EnumSet<GolemUpgrade> upgrades = EnumSet.noneOf(GolemUpgrade.class);
    private final EnumMap<GolemChoice, Integer> choices = new EnumMap<>(GolemChoice.class);
    private final List<String> whitelist = new ArrayList<>();
    private int feedCooldown;
    private int durability = GolemData.MINI_GOLEM_DURABILITY;
    private int loadingElapsed = LOADING_SECONDS;
    private int combatSeconds;
    private int ticker;
    private int attackAnimationTick;
    private int magicianTick;
    private boolean saved;
    private final GolemStorage storage = new GolemStorage(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(UUID.randomUUID(), Component.empty(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    public GuardianGolem(EntityType<? extends GuardianGolem> type, Level world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0d)
                .add(Attributes.ATTACK_DAMAGE, 6.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5d)
                .add(Attributes.MOVEMENT_SPEED, 0.25d)
                .add(Attributes.FOLLOW_RANGE, 40.0d);
    }

    // -- Etat ------------------------------------------------------------------

    public boolean has(GolemUpgrade upgrade) {
        return upgrades.contains(upgrade);
    }

    public int choice(GolemChoice choice) {
        return choices.getOrDefault(choice, choice.fallback());
    }

    public void setChoice(GolemChoice choice, int value) {
        if (has(choice.gate()) && value >= 1 && value <= choice.options()) {
            choices.put(choice, value);
        }
    }

    public int golemLevel() {
        return level;
    }

    public int subXp() {
        return subXp;
    }

    public int points() {
        return points;
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public String ownerName() {
        return ownerName;
    }

    public List<String> whitelist() {
        return List.copyOf(whitelist);
    }

    public GolemStorage storage() {
        return storage;
    }

    public boolean isLoading() {
        return loadingElapsed < LOADING_SECONDS;
    }

    public int feedCooldown() {
        return feedCooldown;
    }

    /** {@code getXPForLevel} : 1 000, puis 5 % de plus a chaque niveau, arrondi vers le bas. */
    public static int xpForLevel(int target) {
        int value = 1000;
        for (int step = 1; step < target; step++) {
            value = (int) (value + value * 0.05f);
        }
        return value;
    }

    /** {@code GuardianUtils.registerFood} : l'XP d'un objet, 0 s'il ne se mange pas. */
    public int foodXp(ItemStack stack) {
        Item item = stack.getItem();
        if (item == PalaItems.AMETHYST_INGOT.get()) {
            return 1;
        }
        if (item == PalaBlocks.AMETHYST_BLOCK.get().asItem()) {
            return 9;
        }
        if (item == PalaItems.TITANE_INGOT.get()) {
            return 2;
        }
        if (item == PalaBlocks.TITANE_BLOCK.get().asItem()) {
            return 18;
        }
        if (item == PalaItems.PALADIUM_INGOT.get()) {
            return 30;
        }
        if (item == PalaBlocks.PALADIUM_BLOCK.get().asItem()) {
            return 270;
        }
        if (has(GolemUpgrade.INNOVATIVE) && item == PalaItems.FINDIUM.get()) {
            return 70;
        }
        if (has(GolemUpgrade.INNOVATIVE) && item == PalaBlocks.FINDIUM_BLOCK.get().asItem()) {
            return 630;
        }
        return 0;
    }

    /** {@code addXP} : un point de capacite par niveau, rien au-dela de 40. */
    public void addXp(int amount) {
        if (level >= MAX_LEVEL) {
            subXp = 0;
            return;
        }
        subXp += amount;
        while (level < MAX_LEVEL && subXp >= xpForLevel(level + 1)) {
            subXp -= xpForLevel(level + 1);
            level++;
            points++;
            playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        if (level >= MAX_LEVEL) {
            subXp = 0;
        }
    }

    /** {@code addUpgrade} : points, parent, et Guerriere ou Farmeur, pas les deux. */
    public boolean canBuy(GolemUpgrade upgrade) {
        if (has(upgrade) || points < upgrade.cost()) {
            return false;
        }
        if (upgrade.parent() != null && !has(upgrade.parent())) {
            return false;
        }
        return !(upgrade == GolemUpgrade.WAR && has(GolemUpgrade.FARMER))
                && !(upgrade == GolemUpgrade.FARMER && has(GolemUpgrade.WAR));
    }

    public boolean buy(GolemUpgrade upgrade) {
        if (!canBuy(upgrade)) {
            return false;
        }
        points -= upgrade.cost();
        upgrades.add(upgrade);
        if (upgrade == GolemUpgrade.MAD_GOLEM) {
            setTarget(null);
        }
        return true;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Proprietaire ou operateur : peut ouvrir, acheter, frapper au marteau. */
    public boolean canManage(ServerPlayer player) {
        return isOwner(player) || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)
                .test(player.createCommandSourceStack());
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getName().getString();
    }

    /** L'apparition : 30 secondes de chargement, a mi-vie. */
    public void startLoading() {
        loadingElapsed = 0;
    }

    // -- Pierre ------------------------------------------------------------

    public GolemData toData() {
        return new GolemData(owner(), ownerName, level, subXp, points, List.copyOf(upgrades), Map.copyOf(choices),
                List.copyOf(whitelist), feedCooldown, durability, Optional.empty());
    }

    public void load(GolemData data) {
        owner = data.owner().orElse(null);
        ownerName = data.ownerName();
        level = Math.clamp(data.level(), 0, MAX_LEVEL);
        subXp = data.subXp();
        points = data.points();
        upgrades.clear();
        upgrades.addAll(data.upgrades());
        choices.clear();
        choices.putAll(data.choices());
        whitelist.clear();
        whitelist.addAll(data.whitelist());
        feedCooldown = data.feedCooldown();
        durability = data.durability();
        updateAttributes();
        setHealth(getMaxHealth());
    }

    // -- IA ----------------------------------------------------------------

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GolemAttackGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                (target, world) -> isHostileTo(target)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WitherBoss.class, true,
                (target, world) -> isHostileTo(target)));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, GuardianGolem.class, true,
                (target, world) -> isHostileTo(target)));
    }

    /** {@code EntityAIGuardian.shouldExecute}, et qui le golem fou attaque : tout le monde. */
    public boolean isHostileTo(LivingEntity target) {
        if (target == this) {
            return false;
        }
        if (has(GolemUpgrade.MAD_GOLEM)) {
            return true;
        }
        if (has(GolemUpgrade.PACIFICATION) || isLoading()) {
            return false;
        }
        if (target instanceof Player player) {
            if (isOwner(player)) {
                return false;
            }
            String name = player.getName().getString();
            return whitelist.stream().noneMatch(name::equalsIgnoreCase);
        }
        if (target instanceof GuardianGolem other) {
            return owner == null || other.owner == null || !owner.equals(other.owner);
        }
        if (target instanceof WitherBoss) {
            return huntsWithers();
        }
        return true;
    }

    /** Wither hunter, choix 1 : il chasse les withers et frappe plus fort les monstres. */
    private boolean huntsWithers() {
        return has(GolemUpgrade.WITHER_HUNTER) && choice(GolemChoice.WITHER) == 1;
    }

    /** Wither hunter, choix 2 : les withers le laissent (voir {@link GolemEvents}). */
    public boolean ignoredByWithers() {
        return has(GolemUpgrade.WITHER_HUNTER) && choice(GolemChoice.WITHER) == 2;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && isHostileTo(target);
    }

    @Override
    public boolean doHurtTarget(ServerLevel world, Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (huntsWithers() && (target instanceof Enemy || target instanceof WitherBoss)) {
            damage *= 1.3f;
        }
        DamageSource source = damageSources().mobAttack(this);
        boolean hit = target.hurtServer(world, source, damage);
        attackAnimationTick = 10;
        world.broadcastEntityEvent(this, (byte) 4);
        if (hit && target instanceof LivingEntity living) {
            setLastHurtMob(living);
            if (has(GolemUpgrade.DESYNCHRONIZATION)) {
                float second = damage;
                world.getEntitiesOfClass(Player.class, getBoundingBox().inflate(3.0d),
                                candidate -> candidate != target && candidate.isAlive() && hasLineOfSight(candidate)
                                        && !candidate.isCreative() && !candidate.isSpectator()
                                        && isHostileTo(candidate))
                        .stream().min(Comparator.comparingDouble(this::distanceToSqr))
                        .ifPresent(extra -> extra.hurtServer(world, source, second));
            }
        }
        playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 1.0f);
        return hit;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            attackAnimationTick = 10;
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 1.0f);
        } else {
            super.handleEntityEvent(id);
        }
    }

    public int attackAnimationTick() {
        return attackAnimationTick;
    }

    // -- Degats --------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        combatSeconds = isLoading() ? 5 : COMBAT_SECONDS;
        if (has(GolemUpgrade.SHIELD) && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        float damage = amount;
        if (has(GolemUpgrade.MAD_GOLEM)) {
            damage *= 0.4f;
        }
        if (isLoading()) {
            damage *= 0.8f;
        }
        if (has(GolemUpgrade.ADVANCED_RESISTANCE)) {
            boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
            switch (choice(GolemChoice.ADVANCED_RESISTANCE)) {
                case 1 -> damage *= projectile ? 1.33f : 0.8f;
                case 3 -> damage *= projectile ? 0.7f : 1.2f;
                default -> {
                }
            }
        }
        if (has(GolemUpgrade.SPECIALIZED_RESISTANCE)) {
            switch (choice(GolemChoice.SPECIALIZED_RESISTANCE)) {
                case 1 -> damage *= source.is(DamageTypeTags.IS_EXPLOSION) ? 0.3f : 1.0f;
                case 2 -> damage *= source.is(DamageTypeTags.IS_FIRE) ? 0.3f : 1.0f;
                case 3 -> damage *= source.is(DamageTypeTags.WITCH_RESISTANT_TO) ? 0.4f : 1.0f;
                case 4 -> damage *= source.is(DamageTypeTags.IS_DROWNING) ? 0.3f : 1.0f;
                default -> {
                }
            }
        }
        float before = getHealth() / getMaxHealth();
        boolean hurt = super.hurtServer(world, source, damage);
        if (hurt && isAlive() && has(GolemUpgrade.LAST_BREATH)
                && before > 0.1f && getHealth() / getMaxHealth() <= 0.1f) {
            lastBreath(source);
        }
        return hurt;
    }

    /** Dernier souffle, a 10 % de vie. */
    private void lastBreath(DamageSource source) {
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        switch (choice(GolemChoice.LAST_BREATH)) {
            case 1 -> addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 3));
            case 2 -> addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2));
            case 3 -> {
                if (attacker != null) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2), this);
                    attacker.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 1), this);
                }
            }
            case 4 -> {
                if (attacker != null) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1), this);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel world && !saved) {
            String cause = source.getMsgId();
            String killer = source.getEntity() != null ? source.getEntity().getName().getString() : "";
            saveAndDrop(world, Optional.of(new GolemData.Death(cause, killer, Instant.now().getEpochSecond(),
                    blockPosition())));
        }
        super.die(source);
    }

    /**
     * La mort ou le marteau : le stockage tombe, la pierre part au gardien du
     * proprietaire. Le golem fou ne revient pas ; un gardien plein le perd.
     */
    public void saveAndDrop(ServerLevel world, Optional<GolemData.Death> death) {
        if (saved) {
            return;
        }
        saved = true;
        Containers.dropContents(world, this, storage);
        bossEvent.removeAllPlayers();
        if (has(GolemUpgrade.MAD_GOLEM) || owner == null) {
            return;
        }
        ItemStack stone = new ItemStack(PalaGolem.GUARDIAN_STONE.get());
        stone.set(PalaGolem.GOLEM_DATA.get(), toData().withDeath(death));
        boolean kept = GolemKeeper.store(world.getServer(), owner, stone);
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(kept
                    ? "message.palamod.golem.kept" : "message.palamod.golem.lost").withStyle(kept
                    ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
    }

    /** Le marteau du gardien : le golem disparait, sa pierre part au gardien. */
    public void hammer(ServerLevel world) {
        saveAndDrop(world, Optional.empty());
        discard();
    }

    // -- Interaction ---------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server) || !(level() instanceof ServerLevel world)) {
            return InteractionResult.SUCCESS;
        }
        if (has(GolemUpgrade.MAD_GOLEM)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        // Croquette : un maker la change en metal, toutes les 8 minutes.
        if (held.is(PalaGolem.CROQUETTE.get()) && feedCooldown <= 0 && toData().bestMaker() >= 0) {
            ItemStack metal = GolemMetal.of(toData().bestMaker()).yield();
            spawnAtLocation(world, metal);
            held.consume(1, player);
            feedCooldown = FEED_COOLDOWN;
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.0d, getZ(), 14,
                    0.6d, 0.6d, 0.6d, 0.0d);
            return InteractionResult.SUCCESS;
        }

        int xp = foodXp(held);
        if (xp > 0) {
            if (level >= MAX_LEVEL) {
                server.sendSystemMessage(Component.translatable("message.palamod.golem.max_level")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            int multiplier = has(GolemUpgrade.GREEDY) ? 2 : 1;
            int count = player.isShiftKeyDown() ? held.getCount() : 1;
            addXp(xp * multiplier * count);
            held.consume(count, player);
            world.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.0d, getZ(), 14,
                    0.6d, 0.6d, 0.6d, 0.0d);
            return InteractionResult.SUCCESS;
        }

        if (!canManage(server)) {
            server.sendSystemMessage(Component.translatable("message.palamod.golem.not_yours")
                    .withStyle(ChatFormatting.DARK_RED));
            return InteractionResult.SUCCESS;
        }
        if (isOwner(player)) {
            ownerName = player.getName().getString();
        }
        if (held.is(PalaGolem.GUARDIAN_HAMMER.get())) {
            if (isOwner(player)) {
                held.hurtAndBreak(1, player, hand);
            }
            hammer(world);
            return InteractionResult.SUCCESS;
        }
        if (held.is(PalaGolem.WHITELIST.get())) {
            whitelist.clear();
            whitelist.addAll(held.getOrDefault(PalaGolem.WHITELIST_NAMES.get(), List.of()));
            server.sendSystemMessage(Component.translatable("message.palamod.golem.whitelist", whitelist.size())
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }
        GolemMenus.openMain(server, this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // -- Tick ------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (attackAnimationTick > 0) {
            attackAnimationTick--;
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel world) {
        super.customServerAiStep(world);
        updateAttributes();
        if (feedCooldown > 0) {
            feedCooldown--;
        }
        if (++ticker >= 20) {
            ticker = 0;
            everySecond(world);
        }
        if (has(GolemUpgrade.HOUSEWORK) && has(GolemUpgrade.STOCKAGE)) {
            for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.0d, 0.0d, 1.0d),
                    ItemEntity::isAlive)) {
                ItemStack rest = storage.addItem(item.getItem());
                if (rest.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(rest);
                }
            }
        }
        LivingEntity target = getTarget();
        if (has(GolemUpgrade.MAGICIAN) && target != null && !isLoading()) {
            if (magicianTick <= 20) {
                magic(target);
                magicianTick = 600;
            } else {
                magicianTick--;
            }
        }
    }

    /** {@code updateCombat}, {@code checkHeal}, {@code checkLoading}, la barre de vie. */
    private void everySecond(ServerLevel world) {
        if (combatSeconds > 0) {
            combatSeconds--;
        }
        if (isLoading()) {
            if (loadingElapsed == 0) {
                setHealth(getMaxHealth() / 2.0f);
            }
            if (combatSeconds <= 0) {
                heal(getMaxHealth() / 2.0f / LOADING_SECONDS);
            }
            loadingElapsed++;
        } else if (combatSeconds <= 0) {
            heal(1.0f);
        }
        updateBossBar(world);
    }

    /** Magicien : un effet sur la cible, toutes les 29 secondes environ. */
    private void magic(LivingEntity target) {
        switch (choice(GolemChoice.MAGICIAN)) {
            case 1 -> target.knockback(1.0d, getX() - target.getX(), getZ() - target.getZ(),
                    damageSources().mobAttack(this), 0.0f);
            case 2 -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), this);
            case 3 -> target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 1), this);
            case 4 -> target.igniteForSeconds(5.0f);
            default -> {
            }
        }
    }

    /**
     * {@code setAttributes}, a chaque tick : le niveau, les ameliorations, et
     * le chargement qui fige tout.
     */
    private void updateAttributes() {
        double health = (has(GolemUpgrade.BERSERKER) ? 220.0d : 200.0d) + 125.0d * level;
        double damage = (has(GolemUpgrade.FIGHTER2) ? 7.8d : has(GolemUpgrade.FIGHTER) ? 6.6d : 6.0d) + 0.25d * level;
        double speed = has(GolemUpgrade.ACCELERATION) ? 0.2875d : 0.25d;
        double follow = 40.0d;
        double knockback = 0.5d;
        if (has(GolemUpgrade.PACIFICATION)) {
            speed = 0.0d;
            damage = 0.0d;
        }
        if (isLoading()) {
            speed = 0.0d;
            follow = 0.0d;
            damage = 0.0d;
            knockback = 1.0d;
        }
        base(Attributes.MAX_HEALTH, health);
        base(Attributes.ATTACK_DAMAGE, damage);
        base(Attributes.MOVEMENT_SPEED, speed);
        base(Attributes.FOLLOW_RANGE, follow);
        base(Attributes.KNOCKBACK_RESISTANCE, knockback);
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                      double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    // -- Barre de vie --------------------------------------------------------

    public Component displayTitle() {
        Component name = hasCustomName() ? getCustomName() : Component.translatable("entity.palamod.guardian_golem");
        Component title = Component.empty().append(name)
                .append(Component.translatable("entity.palamod.guardian_golem.level", level));
        if (isLoading()) {
            title = title.copy().append(Component.translatable("entity.palamod.guardian_golem.loading",
                    LOADING_SECONDS - loadingElapsed).withStyle(ChatFormatting.GRAY));
        }
        return title;
    }

    private void updateBossBar(ServerLevel world) {
        bossEvent.setName(displayTitle());
        bossEvent.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));
        boolean visible = !has(GolemUpgrade.DISCREET);
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (!visible || !player.isAlive() || player.level() != world
                    || player.distanceToSqr(this) > BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
                bossEvent.removePlayer(player);
            }
        }
        if (visible) {
            for (ServerPlayer player : world.players()) {
                if (player.distanceToSqr(this) <= BOSS_BAR_RANGE * BOSS_BAR_RANGE) {
                    bossEvent.addPlayer(player);
                }
            }
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    // -- Sauvegarde ----------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Golem", GolemData.CODEC, toData());
        output.putInt("LoadingElapsed", loadingElapsed);
        output.putInt("CombatSeconds", combatSeconds);
        output.putBoolean("Saved", saved);
        storage.save(output.child("Storage"));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        float health = getHealth();
        input.read("Golem", GolemData.CODEC).ifPresent(this::load);
        setHealth(health);
        loadingElapsed = input.getIntOr("LoadingElapsed", LOADING_SECONDS);
        combatSeconds = input.getIntOr("CombatSeconds", 0);
        saved = input.getBooleanOr("Saved", false);
        storage.load(input.childOrEmpty("Storage"));
    }

    /** {@code GolemMenu} : le nom affiche dans les interfaces. */
    public String describeOwner() {
        return ownerName.isEmpty() ? owner().map(UUID::toString).orElse("?") : ownerName;
    }

    /** {@code EntityAIGuardian} : l'attaque au corps a corps, une fois par seconde, deux avec Carnage. */
    static final class GolemAttackGoal extends MeleeAttackGoal {

        private final GuardianGolem golem;

        GolemAttackGoal(GuardianGolem golem) {
            super(golem, 1.0d, false);
            this.golem = golem;
        }

        @Override
        public boolean canUse() {
            return !golem.has(GolemUpgrade.PACIFICATION) && !golem.isLoading() && super.canUse();
        }

        @Override
        protected int getAttackInterval() {
            return adjustedTickDelay(golem.has(GolemUpgrade.CARNAGE) ? 10 : 20);
        }
    }
}
