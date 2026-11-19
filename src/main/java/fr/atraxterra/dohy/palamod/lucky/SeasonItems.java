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

    // -- Paques ---------------------------------------------------------------------------------

    /** {@code ItemChocolateEgg} : toute la faim, et un effet au hasard pendant une minute. */
    public static final DeferredItem<ChocolateEggItem> CHOCOLATE_EGG = seasonItem("chocolate_egg",
            properties -> new ChocolateEggItem(properties, false), properties -> properties.food(eggFood()));
    /** {@code ItemFakeChocolateEgg} : le meme oeuf, perime (poison). */
    public static final DeferredItem<ChocolateEggItem> FAKE_CHOCOLATE_EGG = seasonItem("fake_chocolate_egg",
            properties -> new ChocolateEggItem(properties, true), properties -> properties.food(eggFood()));
    /** {@code ItemGoldenEgg} : il se mange (mal) ou se change en or a l'etabli. */
    public static final DeferredItem<GoldenEggItem> GOLDEN_EGG = seasonItem("golden_egg", GoldenEggItem::new,
            properties -> properties.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2)
                    .saturationModifier(0.2f).alwaysEdible().build()));
    /** {@code ItemAprilFish} : mange, ou donne a un autre (clic droit) ; un poisson d'avril pour 24 heures. */
    public static final DeferredItem<AprilFishItem> APRIL_FISH = seasonItem("april_fish", AprilFishItem::new,
            properties -> properties.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2)
                    .saturationModifier(0.1f).alwaysEdible().build()));
    /** {@code ItemRainbowLeather} : avec une piece d'armure en cuir, elle devient arc-en-ciel. */
    public static final DeferredItem<Item> RAINBOW_LEATHER = seasonItem("rainbow_leather", Item::new,
            properties -> properties);
    /** {@code ItemRainbowColoring} : sur un mouton, il devient arc-en-ciel ({@code jeb_}). */
    public static final DeferredItem<RainbowColoringItem> RAINBOW_COLORING = seasonItem("rainbow_coloring",
            RainbowColoringItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> EASTER_BUNNY_HELMET = seasonItem("easter_bunny_helmet", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.EASTER_BUNNY, ArmorType.HELMET));
    public static final DeferredItem<Item> EASTER_BUNNY_CHESTPLATE = seasonItem("easter_bunny_chestplate", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.EASTER_BUNNY, ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> EASTER_BUNNY_LEGGINGS = seasonItem("easter_bunny_leggings", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.EASTER_BUNNY, ArmorType.LEGGINGS));
    public static final DeferredItem<Item> EASTER_BUNNY_BOOTS = seasonItem("easter_bunny_boots", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.EASTER_BUNNY, ArmorType.BOOTS));
    /** {@code BlockEasterGift} : pose, il s'ouvre et donne une recompense. */
    public static final DeferredBlock<EasterGiftBlock> EASTER_GIFT = seasonBlock("easter_gift",
            properties -> new EasterGiftBlock(properties, false), properties -> properties.strength(0.5f)
                    .sound(SoundType.WOOL).noOcclusion());
    /** {@code BlockFakeEasterGift} : le meme, mais un trou noir en sort. */
    public static final DeferredBlock<EasterGiftBlock> FAKE_EASTER_GIFT = seasonBlock("fake_easter_gift",
            properties -> new EasterGiftBlock(properties, true), properties -> properties.strength(0.5f)
                    .sound(SoundType.WOOL).noOcclusion());
    /** {@code BlockBlueEasterEgg} : les oeufs de la chasse aux oeufs. */
    public static final DeferredBlock<EasterEggBlock> BLUE_EASTER_EGG = seasonBlock("blue_easter_egg",
            properties -> new EasterEggBlock(properties, false), properties -> properties.instabreak()
                    .sound(SoundType.STONE).noOcclusion());
    /** {@code BlockYellowEasterEgg} : casse, il donne une recompense. */
    public static final DeferredBlock<EasterEggBlock> YELLOW_EASTER_EGG = seasonBlock("yellow_easter_egg",
            properties -> new EasterEggBlock(properties, true), properties -> properties.instabreak()
                    .sound(SoundType.STONE).noOcclusion());

    // -- Mai ------------------------------------------------------------------------------------

    /** {@code BlockStoneHearth} : trois coeurs rendus, une fois par minute et par foyer. */
    public static final DeferredBlock<HearthBlock> STONE_HEARTH = seasonBlock("stone_hearth", HearthBlock::new,
            properties -> properties.strength(2.0f).sound(SoundType.STONE).lightLevel(state -> 10));
    /** {@code FAKE_GOLD_BLOCK} : de l'or pour les yeux ; casse, il ne rend rien. */
    public static final DeferredBlock<Block> FAKE_GOLD_BLOCK = seasonBlock("fake_gold_block", Block::new,
            properties -> properties.strength(3.0f, 6.0f).sound(SoundType.METAL));
    /** {@code ItemPlayerSaddle} : un clic droit sur un joueur, ou ici sur toute creature, et on monte dessus. */
    public static final DeferredItem<SaddleItem> PLAYER_SADDLE = seasonItem("player_saddle", SaddleItem::new,
            properties -> properties.stacksTo(1));
    /** {@code ItemGhostCape} : portee, deux minutes d'invisibilite, puis une heure de recharge. */
    public static final DeferredItem<GhostCapeItem> GHOST_CAPE = seasonItem("ghost_cape", GhostCapeItem::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.GHOST_CAPE, ArmorType.CHESTPLATE));
    /** {@code ItemGhostlySword} : une epee en paladium, d'apparence spectrale. */
    public static final DeferredItem<Item> GHOSTLY_SWORD = seasonItem("ghostly_sword", Item::new,
            properties -> properties.sword(fr.atraxterra.dohy.palamod.equipment.PalaToolMaterials.PALADIUM, 3.0f,
                    -2.4f));

    private SeasonItems() {
    }

    private static net.minecraft.world.food.FoodProperties eggFood() {
        return new net.minecraft.world.food.FoodProperties.Builder().nutrition(20).saturationModifier(0.6f)
                .alwaysEdible().build();
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

    /** L'oeuf en chocolat, vrai ou perime. */
    public static class ChocolateEggItem extends Item {
        private final boolean fake;

        public ChocolateEggItem(Properties properties, boolean fake) {
            super(properties);
            this.fake = fake;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (level instanceof ServerLevel && entity instanceof ServerPlayer player) {
                if (fake) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                    LuckyWorld.tell(player, "Tu as mange un oeuf perime.", ChatFormatting.RED);
                } else {
                    List<Holder<MobEffect>> effects = List.of(MobEffects.BLINDNESS, MobEffects.NAUSEA, MobEffects.HASTE,
                            MobEffects.NIGHT_VISION, MobEffects.REGENERATION, MobEffects.POISON, MobEffects.SPEED,
                            MobEffects.WITHER);
                    player.addEffect(new MobEffectInstance(effects.get(player.getRandom().nextInt(effects.size())), 1200,
                            0));
                }
            }
            return left;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    net.minecraft.world.item.component.TooltipDisplay display,
                                    Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            info(tooltip, "Restaure toute la barre de nourriture" + (fake ? "." : ""));
            if (!fake) {
                info(tooltip, "et donne un effet au hasard pendant une minute.");
            }
            info(tooltip, "N'en mange pas trop, cela te ferait mal au ventre.");
        }
    }

    /** L'oeuf d'or : se manger fait mal ; il vaut surtout son or. */
    public static class GoldenEggItem extends Item {
        public GoldenEggItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                player.hurtServer(server, server.damageSources().magic(), 2.0f);
            }
            return left;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    net.minecraft.world.item.component.TooltipDisplay display,
                                    Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            info(tooltip, "Peut etre transforme en lingots d'or a l'etabli.");
            info(tooltip, "Mais il est si joli, tu veux vraiment le faire ?");
        }
    }

    /** Le poisson d'avril : mange, ou glisse a une creature (clic droit). */
    public static class AprilFishItem extends Item {
        public AprilFishItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player) {
                fish(player);
            }
            return left;
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                      InteractionHand hand) {
            if (!(target instanceof ServerPlayer other) || !(player instanceof ServerPlayer server)) {
                return InteractionResult.PASS;
            }
            fish(other);
            LuckyWorld.tell(server, "Tu as transforme " + other.getName().getString() + " en poisson pour 24 heures !",
                    ChatFormatting.YELLOW);
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }

        static void fish(ServerPlayer player) {
            player.addEffect(new MobEffectInstance(SeasonEffects.APRIL_FISH, 1_728_000, 0));
            LuckyWorld.tell(player, "Tu as ete transforme en poisson pour 24 heures !", ChatFormatting.YELLOW);
        }
    }

    /** Le colorant arc-en-ciel : un mouton nomme {@code jeb_} change de couleur sans arret. */
    public static class RainbowColoringItem extends Item {
        public RainbowColoringItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                      InteractionHand hand) {
            if (target.getType() != net.minecraft.world.entity.EntityTypes.SHEEP) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                target.setCustomName(Component.literal("jeb_"));
                LuckyWorld.tell(server, "Le mouton est maintenant arc-en-ciel !", ChatFormatting.GREEN);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La selle de joueur : on monte sur la creature visee. */
    public static class SaddleItem extends Item {
        public SaddleItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                      InteractionHand hand) {
            if (player.isPassenger() || target.isVehicle()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer) {
                player.startRiding(target);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La cape fantome : chargee, elle rend invisible deux minutes ; puis il faut une heure. */
    public static class GhostCapeItem extends Item {
        private static final String READY = "dohy_ghost_ready";

        public GhostCapeItem(Properties properties) {
            super(properties);
        }

        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity,
                                  net.minecraft.world.entity.EquipmentSlot slot) {
            if (slot != net.minecraft.world.entity.EquipmentSlot.CHEST || !(entity instanceof ServerPlayer player)
                    || player.tickCount % 20 != 0) {
                return;
            }
            long now = level.getGameTime();
            var data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            if (now >= data.getLongOr(READY, 0L)) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2400, 0, false, false, true));
                data.putLong(READY, now + 72_000L);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(data));
                LuckyWorld.bar(player, "La cape fantome te rend invisible pendant 2 minutes.", ChatFormatting.AQUA);
            }
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                                    net.minecraft.world.item.component.TooltipDisplay display,
                                    Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
            info(tooltip, "Cet objet te permet de devenir invisible");
            info(tooltip, "pendant 2 minutes s'il est completement recharge.");
            info(tooltip, "Temps de rechargement : 1 heure.");
        }
    }

    // -- Blocs ----------------------------------------------------------------------------------

    /** Le foyer de pierre : trois coeurs, puis une minute d'attente. */
    public static class HearthBlock extends Block {
        private static final java.util.Map<BlockPos, Long> LAST = new java.util.HashMap<>();

        public HearthBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            long now = level.getGameTime();
            long last = LAST.getOrDefault(pos.immutable(), -1200L);
            if (now - last < 1200L) {
                long left = (1200L - (now - last)) / 20L;
                LuckyWorld.tell(server, "Tu dois attendre encore " + left / 60L + " min " + left % 60L + " s.",
                        ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
            LAST.put(pos.immutable(), now);
            server.heal(6.0f);
            LuckyWorld.tell(server, "Tu as ete soigne de 3 coeurs !", ChatFormatting.GREEN);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d,
                        6, 0.3d, 0.2d, 0.3d, 0.0d);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Le cadeau de Paques : pose, il s'ouvre deux secondes plus tard. */
    public static class EasterGiftBlock extends Block {
        private final boolean fake;

        public EasterGiftBlock(Properties properties, boolean fake) {
            super(properties);
            this.fake = fake;
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (!(level instanceof ServerLevel server) || !(placer instanceof ServerPlayer player)) {
                return;
            }
            LuckyTasks.later(player, 35, () -> {
                if (!server.getBlockState(pos).is(this)) {
                    return;
                }
                server.removeBlock(pos, false);
                if (fake) {
                    EasterEvents.blackHole(player, server, Vec3.atBottomCenterOf(pos));
                    return;
                }
                ItemStack reward = switch (server.getRandom().nextInt(4)) {
                    case 0 -> new ItemStack(CHOCOLATE_EGG.get(), 8);
                    case 1 -> new ItemStack(EASTER_BUNNY_CHESTPLATE.get());
                    case 2 -> new ItemStack(EASTER_BUNNY_LEGGINGS.get());
                    default -> new ItemStack(EASTER_BUNNY_BOOTS.get());
                };
                LuckyWorld.drop(server, pos, reward);
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5d, pos.getY() + 0.5d,
                        pos.getZ() + 0.5d, 15, 0.4d, 0.4d, 0.4d, 0.0d);
            });
        }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                net.minecraft.world.level.BlockGetter level, BlockPos pos,
                net.minecraft.world.phys.shapes.CollisionContext context) {
            return Block.box(2, 0, 2, 14, 13, 14);
        }
    }

    /** Un oeuf de Paques pose : bleu pour la chasse, jaune pour une recompense. */
    public static class EasterEggBlock extends Block {
        private final boolean yellow;

        public EasterEggBlock(Properties properties, boolean yellow) {
            super(properties);
            this.yellow = yellow;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                found(server, serverLevel, pos);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                found(server, serverLevel, pos);
            }
        }

        private void found(ServerPlayer player, ServerLevel level, BlockPos pos) {
            if (!level.getBlockState(pos).is(this)) {
                return;
            }
            level.removeBlock(pos, false);
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(yellow ? 0xF2D54A : 0x63B4F0, 1.5f),
                    pos.getX() + 0.5d, pos.getY() + 0.4d, pos.getZ() + 0.5d, 20, 0.3d, 0.3d, 0.3d, 0.0d);
            level.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 1.0f, 1.2f);
            if (yellow) {
                LuckyWorld.drop(level, pos, EasterEvents.yellowReward(level.getRandom()));
            } else {
                EasterEvents.blueEggFound(player);
            }
        }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                net.minecraft.world.level.BlockGetter level, BlockPos pos,
                net.minecraft.world.phys.shapes.CollisionContext context) {
            return Block.box(4, 0, 4, 12, 11, 12);
        }
    }


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
