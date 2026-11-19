package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.equipment.PalaArmorMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Les objets et blocs des lucky blocks saisonniers ({@code registerItems} et
 * {@code registerBlocks} des modules du mois). Les images sont celles de PalaMod
 * ({@code tools/lucky_assets.py}, {@code SEASON_ITEMS}).
 *
 * <p>Beaucoup d'objets de PalaMod n'existaient que pour un evenement de serveur (un atelier
 * sur le serveur Event, un classement mensuel) : ici ils gardent l'usage qui se joue seul.
 */
@EventBusSubscriber
public final class SeasonItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PalaMod.NAMESPACE);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PalaMod.NAMESPACE);
    private static final List<DeferredItem<? extends Item>> ORDER = new ArrayList<>();
    /** La potion lancee : l'effet qu'elle porte ({@code EntityJupiterGravitySplashPotion}...). */
    static final String SPLASH = "dohy_season_splash";

    // -- Mars -----------------------------------------------------------------------------------

    /** {@code ItemStarHelmet} : casque en paladium ; il protege du manque d'oxygene. */
    public static final DeferredItem<Item> STAR_HELMET = seasonItem("star_helmet", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.STAR_HELMET, ArmorType.HELMET));
    /** {@code ItemGalacticPile} : repare l'unique objet abime de l'inventaire. */
    public static final DeferredItem<RepairItem> GALACTIC_PILE = seasonItem("galactic_pile", RepairItem::new,
            properties -> properties);
    public static final DeferredItem<Item> MOON_PIECE = seasonItem("moon_piece", Item::new, properties -> properties);
    /** {@code ItemSupersonicRocket} : on monte dessus et elle fonce droit devant. */
    public static final DeferredItem<RocketItem> SUPERSONIC_ROCKET = seasonItem("supersonic_rocket", RocketItem::new,
            properties -> properties.stacksTo(1));
    /** Les potions de gravite : saut 1, saut 3, plus de saut du tout, une minute, quatre blocs autour. */
    public static final DeferredItem<SplashItem> SPLASH_POTION_MARS_GRAVITY = seasonItem("splash_potion_mars_gravity",
            properties -> new SplashItem(properties, () -> MobEffects.JUMP_BOOST, 0), properties -> properties.stacksTo(16));
    public static final DeferredItem<SplashItem> SPLASH_POTION_MOON_GRAVITY = seasonItem("splash_potion_moon_gravity",
            properties -> new SplashItem(properties, () -> MobEffects.JUMP_BOOST, 2), properties -> properties.stacksTo(16));
    public static final DeferredItem<SplashItem> SPLASH_POTION_JUPITER_GRAVITY = seasonItem(
            "splash_potion_jupiter_gravity", properties -> new SplashItem(properties, () -> SeasonEffects.JUPITER_GRAVITY, 0),
            properties -> properties.stacksTo(16));
    /** {@code ItemSurvivingStone} : un abri de glace autour du joueur, quarante secondes. */
    public static final DeferredItem<ShelterItem> SURVIVING_STONE = seasonItem("surviving_stone", ShelterItem::new,
            properties -> properties.durability(3));
    /** {@code BlockTelescope} : on y regarde le ciel, un test sur le systeme solaire. */
    public static final DeferredBlock<TelescopeBlock> TELESCOPE = seasonBlock("telescope", TelescopeBlock::new,
            properties -> properties.strength(1.0f).sound(SoundType.METAL).noOcclusion());

    private SeasonItems() {
    }

    private static <I extends Item> DeferredItem<I> seasonItem(String name, Function<Item.Properties, I> factory,
                                                               UnaryOperator<Item.Properties> properties) {
        DeferredItem<I> item = ITEMS.registerItem(name, factory, properties);
        ORDER.add(item);
        return item;
    }

    private static <B extends Block> DeferredBlock<B> seasonBlock(String name,
            Function<BlockBehaviour.Properties, B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, properties);
        ORDER.add(ITEMS.registerSimpleBlockItem(block, item -> item));
        return block;
    }

    /** L'onglet PalaMod : les objets saisonniers, dans l'ordre d'inscription. */
    public static List<DeferredItem<? extends Item>> ordered() {
        return List.copyOf(ORDER);
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    // -- Objets ---------------------------------------------------------------------------------

    /** La pile galactique : un seul objet abime dans l'inventaire, et il est repare. */
    public static class RepairItem extends Item {
        public RepairItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            List<ItemStack> damaged = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isDamaged()) {
                    damaged.add(stack);
                }
            }
            if (damaged.size() != 1) {
                LuckyWorld.tell(server, damaged.isEmpty() ? "Tu n'as aucun objet a reparer dans ton inventaire."
                        : "Tu dois avoir UN SEUL objet reparable dans ton inventaire.", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            damaged.getFirst().setDamageValue(0);
            player.getItemInHand(hand).consume(1, player);
            LuckyWorld.tell(server, "Reparation : confirmee", ChatFormatting.GREEN);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
            return InteractionResult.SUCCESS;
        }
    }

    /** La fusee supersonique : elle part dans la direction du regard ; dix secondes entre deux vols. */
    public static class RocketItem extends Item {
        public RocketItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                Vec3 look = player.getLookAngle();
                LuckyTasks.repeat(server, 0, 2, 20, step -> {
                    server.setDeltaMovement(look.scale(1.6d));
                    server.hurtMarked = true;
                    server.resetFallDistance();
                    serverLevel.sendParticles(ParticleTypes.FIREWORK, server.getX(), server.getY(), server.getZ(), 6,
                            0.2d, 0.2d, 0.2d, 0.02d);
                });
                server.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                        SoundSource.PLAYERS, 1.0f, 0.6f);
                player.getCooldowns().addCooldown(player.getItemInHand(hand), 200);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Une potion a lancer : quatre blocs autour du point d'impact, une minute d'effet. */
    public static class SplashItem extends Item {
        private final Supplier<Holder<MobEffect>> effect;
        private final int amplifier;

        public SplashItem(Properties properties, Supplier<Holder<MobEffect>> effect, int amplifier) {
            super(properties);
            this.effect = effect;
            this.amplifier = amplifier;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                var potion = new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(server, player,
                        stack.copyWithCount(1));
                potion.getPersistentData().putString(SPLASH, net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(this).toString());
                potion.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0f, 0.6f, 1.0f);
                server.addFreshEntity(potion);
                server.playSound(null, player.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                        0.5f, 0.4f);
            }
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }

        void splash(ServerLevel level, Vec3 at) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f,
                    1.0f);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5d, at.z, 40, 1.5d, 0.5d, 1.5d, 0.1d);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(4.0d))) {
                living.addEffect(new MobEffectInstance(effect.get(), 1200, amplifier));
            }
        }
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || !projectile.getPersistentData().contains(SPLASH)) {
            return;
        }
        String id = projectile.getPersistentData().getStringOr(SPLASH, "");
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse(id));
        if (item instanceof SplashItem splash) {
            splash.splash(level, event.getRayTraceResult().getLocation());
        }
        projectile.discard();
        event.setCanceled(true);
    }

    /** La pierre de survie : des murs de glace tassee autour du joueur, quarante secondes. */
    public static class ShelterItem extends Item {
        public ShelterItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.SUCCESS;
            }
            BlockPos feet = player.blockPosition();
            List<BlockPos> placed = new ArrayList<>();
            BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 3, 2))) {
                int dx = Math.abs(pos.getX() - feet.getX());
                int dz = Math.abs(pos.getZ() - feet.getZ());
                boolean wall = dx == 2 || dz == 2 || pos.getY() == feet.getY() + 3;
                if (wall && serverLevel.getBlockState(pos).isAir()
                        && LuckyWorld.place(server, serverLevel, pos.immutable(), ice)) {
                    placed.add(pos.immutable());
                }
            }
            serverLevel.playSound(null, feet, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
            LuckyTasks.later(server, 800, () -> placed.forEach(pos -> {
                if (serverLevel.getBlockState(pos).is(Blocks.PACKED_ICE)) {
                    serverLevel.removeBlock(pos, false);
                }
            }));
            player.getItemInHand(hand).hurtAndBreak(1, player, hand);
            return InteractionResult.SUCCESS;
        }
    }

    // -- Blocs ----------------------------------------------------------------------------------

    /** Le telescope de « Par dela les etoiles » : un test sur le systeme solaire, une fois. */
    public static class TelescopeBlock extends Block {
        public TelescopeBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                MarchEvents.solarTest(server, serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                net.minecraft.world.level.BlockGetter level, BlockPos pos,
                net.minecraft.world.phys.shapes.CollisionContext context) {
            return Block.box(3, 0, 3, 13, 16, 13);
        }
    }

    /** Une ligne grise sous le nom de l'objet. */
    static void info(Consumer<Component> tooltip, String text) {
        tooltip.accept(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }
}
