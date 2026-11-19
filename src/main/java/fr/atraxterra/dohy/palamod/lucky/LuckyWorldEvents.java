package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce que les evenements laissent derriere eux, cote serveur
 * ({@code EventsManager} de PalaMod) : les gardes du corps, le lapin aux
 * minerais, le coffre piege, les faux creepers, le faux bloc de paladium, le vol
 * de SuperMan, l'armure arc-en-ciel et les enclumes a dollars.
 */
@EventBusSubscriber
public final class LuckyWorldEvents {

    /** Un golem de « Body Guard », et le joueur qu'il garde. */
    static final String GUARD_TAG = "palamod.body_guard";
    static final String OWNER_TAG = "palamod.owner=";
    /** Le lapin de « Ruee vers les minerais », son minerai et ses sauts. */
    static final String MINERAL_TAG = "palamod.mineral_rush";
    static final String MINERAL_ITEM_TAG = "palamod.mineral=";
    private static final String JUMPS_TAG = "palamod.jumps=";
    /** Les creepers de « Pssssssss ». */
    static final String FAKE_CREEPER_TAG = "palamod.fake_creeper";
    /** Les sauts du lapin avant de disparaitre, et ce qu'il lache tue. */
    private static final int RABBIT_JUMPS = 10;
    private static final int RABBIT_LOOT = 10;
    /** Ce que rapporte une enclume de « Pret a tout » ({@code depositEconomy}). */
    private static final long ANVIL_MONEY = 1_000L;
    /** {@code getEntitiesWithinAABB(EntityPlayer, 20 blocs)} : SuperMan ne vole pas pres d'un autre joueur. */
    private static final double SUPER_MAN_ALONE = 20.0d;
    /** Les coffres de « C'est un traquenard ! ». En memoire, comme {@code UsersManager.getTraps}. */
    private static final Set<GlobalPos> TRAPS = ConcurrentHashMap.newKeySet();

    private LuckyWorldEvents() {
    }

    static void trap(ServerLevel level, BlockPos pos) {
        TRAPS.add(GlobalPos.of(level.dimension(), pos.immutable()));
    }

    /** La couleur de l'armure arc-en-ciel : un tour de teinte toutes les trois secondes ({@code HSBtoRGB}). */
    static int rainbow(long gameTime) {
        return java.awt.Color.HSBtoRGB((gameTime % 60L) / 60.0f, 0.8f, 0.8f) & 0xFFFFFF;
    }

    // -- Body Guard -----------------------------------------------------------

    /**
     * Le garde frappe celui qui frappe son joueur, et ce que son joueur frappe
     * ({@code onBodyGuardMustProtect}, {@code onBodyGuardMustAttack}).
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (victim instanceof ServerPlayer owner && attacker instanceof LivingEntity enemy && enemy != owner
                && !guards(owner, enemy)) {
            for (IronGolem golem : guards(owner)) {
                golem.setTarget(enemy);
            }
        }
        if (attacker instanceof ServerPlayer owner && victim != owner && !guards(owner, victim)) {
            for (IronGolem golem : guards(owner)) {
                golem.setTarget(victim);
            }
        }
        // SuperMan perd ses pouvoirs au premier coup ({@code onSuperManDamaged}).
        if (victim instanceof ServerPlayer player && player.hasEffect(PalaLucky.SUPER_MAN)) {
            player.removeEffect(PalaLucky.SUPER_MAN);
        }
        // Pret a tout : une enclume recue rapporte.
        if (victim instanceof ServerPlayer player && player.hasEffect(PalaLucky.ANVIL_MONEY)
                && event.getSource().is(DamageTypes.FALLING_ANVIL)) {
            Economy.credit(player, ANVIL_MONEY);
            LuckyWorld.bar(player, "+1 000 $", ChatFormatting.GOLD);
        }
    }

    private static boolean guards(ServerPlayer owner, Entity entity) {
        return entity.entityTags().contains(GUARD_TAG) && entity.entityTags().contains(OWNER_TAG + owner.getUUID());
    }

    private static List<IronGolem> guards(ServerPlayer owner) {
        String tag = OWNER_TAG + owner.getUUID();
        return owner.level().getEntitiesOfClass(IronGolem.class, owner.getBoundingBox().inflate(32.0d),
                golem -> golem.isAlive() && golem.entityTags().contains(GUARD_TAG) && golem.entityTags().contains(tag));
    }

    // -- Ruee vers les minerais ---------------------------------------------

    /** Chaque saut du lapin lache un minerai ; au onzieme, il disparait. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity rabbit = event.getEntity();
        if (!(rabbit.level() instanceof ServerLevel level) || !rabbit.entityTags().contains(MINERAL_TAG)) {
            return;
        }
        int jumps = 1;
        for (String tag : Set.copyOf(rabbit.entityTags())) {
            if (tag.startsWith(JUMPS_TAG)) {
                jumps = parse(tag.substring(JUMPS_TAG.length())) + 1;
                rabbit.removeTag(tag);
            }
        }
        rabbit.addTag(JUMPS_TAG + jumps);
        if (jumps > RABBIT_JUMPS) {
            level.sendParticles(ParticleTypes.POOF, rabbit.getX(), rabbit.getY() + 0.3d, rabbit.getZ(), 12, 0.2d,
                    0.2d, 0.2d, 0.02d);
            rabbit.discard();
            return;
        }
        Item mineral = mineral(rabbit);
        if (mineral != null) {
            LuckyWorld.drop(level, rabbit.getX(), rabbit.getY() + 1.0d, rabbit.getZ(), new ItemStack(mineral));
        }
    }

    /** Tue, le lapin lache dix minerais. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity rabbit = event.getEntity();
        if (rabbit.level() instanceof ServerLevel level && rabbit.entityTags().contains(MINERAL_TAG)) {
            Item mineral = mineral(rabbit);
            if (mineral != null) {
                LuckyWorld.drop(level, rabbit.getX(), rabbit.getY() + 1.0d, rabbit.getZ(),
                        new ItemStack(mineral, RABBIT_LOOT));
            }
        }
    }

    private static Item mineral(Entity rabbit) {
        for (String tag : rabbit.entityTags()) {
            if (tag.startsWith(MINERAL_ITEM_TAG)) {
                Identifier id = Identifier.tryParse(tag.substring(MINERAL_ITEM_TAG.length()));
                return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            }
        }
        return null;
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException wrong) {
            return 0;
        }
    }

    // -- C'est un traquenard ! -----------------------------------------------

    /** Ouvert, le coffre piege disparait et une TNT s'allume a sa place. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().isShiftKeyDown() || TRAPS.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!TRAPS.remove(GlobalPos.of(level.dimension(), pos)) || !level.getBlockState(pos).is(Blocks.CHEST)) {
            return;
        }
        event.setCanceled(true);
        level.removeBlock(pos, false);
        LuckyWorld.tnt(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 80);
    }

    // -- Pssssssss ----------------------------------------------------------

    /** Le faux creeper charge saute sans rien casser ni blesser : un bruit et un nuage. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source == null || !source.entityTags().contains(FAKE_CREEPER_TAG)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.setCanceled(true);
        Vec3 at = event.getExplosion().center();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        LuckyWorld.sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), 4.0f, 1.0f);
    }

    // -- Ouaiche t'es relou -------------------------------------------------

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof FakePaladiumBlock) || !FakePaladiumBlock.jumps(state)
                || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.setCanceled(true);
        FakePaladiumBlock.jump(player, level, event.getPos(), state);
    }

    // -- SuperMan et Arc en armure -------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance flight = player.getEffect(PalaLucky.SUPER_MAN);
        if (flight != null) {
            superMan(player, flight);
        }
        if (player.tickCount % 2 == 0) {
            rainbow(player);
        }
    }

    /**
     * Le vol de SuperMan ({@code onSuperManUpdate}) : on vole tant qu'on est seul
     * a vingt blocs, dans le monde, et qu'il reste plus de quatre secondes.
     */
    private static void superMan(ServerPlayer player, MobEffectInstance flight) {
        boolean outside = player.getY() < player.level().getMinY() || player.getY() > player.level().getMaxY();
        boolean ending = !flight.isInfiniteDuration() && flight.getDuration() < 80;
        boolean crowded = !player.level().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(SUPER_MAN_ALONE), other -> other != player).isEmpty();
        if (outside || ending || crowded) {
            player.removeEffect(PalaLucky.SUPER_MAN);
            return;
        }
        Abilities abilities = player.getAbilities();
        if (!abilities.mayfly || !abilities.flying) {
            abilities.mayfly = true;
            abilities.flying = true;
            player.onUpdateAbilities();
        }
    }

    /** L'effet fini ou retire (lait, coup, fin) : plus de vol, et la chute ne compte pas. */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().value() == PalaLucky.SUPER_MAN.get() && event.getEntity() instanceof ServerPlayer player) {
            land(player);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().value() == PalaLucky.SUPER_MAN.get()
                && event.getEntity() instanceof ServerPlayer player) {
            land(player);
        }
    }

    private static void land(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        Abilities abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        player.resetFallDistance();
        player.onUpdateAbilities();
    }

    /** L'armure de cuir « rainbow » portee change de couleur ({@code onRainbowArmor}). */
    private static void rainbow(ServerPlayer player) {
        int color = -1;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty() && Boolean.TRUE.equals(armor.get(PalaLucky.RAINBOW.get()))) {
                if (color < 0) {
                    color = rainbow(player.level().getGameTime());
                }
                armor.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
            }
        }
    }
}
