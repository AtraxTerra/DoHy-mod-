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
import net.minecraft.util.RandomSource;
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

    // -- Juin -----------------------------------------------------------------------------------

    /** {@code ItemRadio} : un air entrainant, et les joueurs proches dansent vingt secondes. */
    public static final DeferredItem<RadioItem> RADIO = seasonItem("radio", RadioItem::new,
            properties -> properties.durability(10));
    /** {@code ItemTrumpet} : un son tres agreable, une fois par minute. */
    public static final DeferredItem<TrumpetItem> TRUMPET = seasonItem("trumpet", TrumpetItem::new,
            properties -> properties.stacksTo(1));
    /** {@code ItemGoldRecord} : pose sur un juke-box, il change le sol dessous en or (5 x 5). */
    public static final DeferredItem<GoldRecordItem> GOLD_RECORD = seasonItem("gold_record", GoldRecordItem::new,
            properties -> properties.stacksTo(1));
    /** {@code ItemElectricGuitar} : une guitare qui frappe en musique. */
    public static final DeferredItem<GuitarItem> ELECTRIC_GUITAR = seasonItem("electric_guitar", GuitarItem::new,
            properties -> properties.sword(net.minecraft.world.item.ToolMaterial.IRON, 3.0f, -2.6f));
    /** {@code BlocksRegister.SHARK_PLUSH} : une peluche de requin. */
    public static final DeferredItem<Item> SHARK_PLUSH = seasonItem("shark_plush", Item::new,
            properties -> properties.stacksTo(16));
    /** {@code BlockBrightSound} : un clic, il s'allume et joue sa note une seconde. */
    public static final DeferredBlock<BrightSoundBlock> BRIGHT_SOUND = seasonBlock("bright_sound",
            BrightSoundBlock::new, properties -> properties.strength(1.0f).sound(SoundType.GLASS)
                    .lightLevel(state -> state.getValue(BrightSoundBlock.LIT) ? 12 : 0));

    // -- Juillet --------------------------------------------------------------------------------

    /** {@code BlockBarrel} : on le remplit de poudre ; une fleche ou du redstone, et il saute. */
    public static final DeferredBlock<PowderBarrelBlock> POWDER_BARREL = seasonBlock("powder_barrel",
            PowderBarrelBlock::new, properties -> properties.strength(2.0f).sound(SoundType.WOOD));
    /** {@code ItemSeaBottle} : on l'ouvre, un message seche dedans. */
    public static final DeferredItem<MessageItem> SEA_BOTTLE = seasonItem("sea_bottle",
            properties -> new MessageItem(properties, 0), properties -> properties.stacksTo(16));
    /** {@code ItemDryMessage} : illisible ; un passage au four le rend lisible. */
    public static final DeferredItem<MessageItem> DRY_MESSAGE = seasonItem("dry_message",
            properties -> new MessageItem(properties, 1), properties -> properties.stacksTo(16));
    /** {@code ItemPirateKingMessage} : le message du roi des pirates. */
    public static final DeferredItem<MessageItem> PIRATE_MESSAGE = seasonItem("pirate_message",
            properties -> new MessageItem(properties, 2), properties -> properties.stacksTo(16));
    /** {@code ItemsRegister.EYE_PATCH} : un cache-oeil, a porter. */
    public static final DeferredItem<Item> EYE_PATCH = seasonItem("eye_patch", Item::new,
            properties -> properties.humanoidArmor(PalaArmorMaterials.EYE_PATCH, ArmorType.HELMET));
    /** {@code ItemPipe} : une bouffee, dix secondes de regeneration ; cinq minutes entre deux. */
    public static final DeferredItem<PipeItem> PIPE = seasonItem("pipe", PipeItem::new,
            properties -> properties.stacksTo(1));
    /** {@code ItemOrangeConsumable} : riche en vitamines, il soigne le scorbut. */
    public static final DeferredItem<OrangeItem> ORANGE = seasonItem("orange", OrangeItem::new,
            properties -> properties.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(4)
                    .saturationModifier(0.3f).alwaysEdible().build()));
    /** {@code ItemSailorTooth} : une dent de marin ; huit poudres d'os a l'etabli. */
    public static final DeferredItem<Item> SAILOR_TOOTH = seasonItem("sailor_tooth", Item::new,
            properties -> properties);
    /** {@code ItemSkeletonSkull} : un crane auquel il manque cinq dents. */
    public static final DeferredItem<Item> TOOTH_SKELETON_SKULL = seasonItem("tooth_skeleton_skull", Item::new,
            properties -> properties.stacksTo(1));
    /** {@code ItemParrotSeeds} : un perroquet ami, dix minutes. */
    public static final DeferredItem<ParrotSeedsItem> PARROT_SEEDS = seasonItem("parrot_seeds", ParrotSeedsItem::new,
            properties -> properties.stacksTo(16));
    /** Les instruments des musiciens pirates. */
    public static final DeferredItem<InstrumentItem> DRUM = seasonItem("drum",
            properties -> new InstrumentItem(properties, "block.note_block.basedrum"), properties -> properties.stacksTo(1));
    public static final DeferredItem<InstrumentItem> ACCORDION = seasonItem("accordion",
            properties -> new InstrumentItem(properties, "block.note_block.flute"), properties -> properties.stacksTo(1));
    /** {@code ItemTreasureMap} : elle montre le chemin du tresor enterre. */
    public static final DeferredItem<TreasureMapItem> TREASURE_MAP = seasonItem("treasure_map", TreasureMapItem::new,
            properties -> properties.stacksTo(1));
    /** {@code ItemInvulnerabilityPotion} : soixante secondes sans effet negatif. */
    public static final DeferredItem<ImmunityPotionItem> INVULNERABILITY_POTION = seasonItem("invulnerability_potion",
            ImmunityPotionItem::new, properties -> properties.stacksTo(16).component(
                    net.minecraft.core.component.DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumable
                            .builder().consumeSeconds(1.6f).animation(net.minecraft.world.item.ItemUseAnimation.DRINK)
                            .sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false).build()));
    /** {@code BlockEncryptedComputer} : un ordinateur a decrypter. */
    public static final DeferredBlock<ComputerBlock> ENCRYPTED_COMPUTER = seasonBlock("encrypted_computer",
            properties -> new ComputerBlock(properties, false), properties -> properties.strength(2.0f)
                    .sound(SoundType.METAL));
    /** {@code BlockDecryptedComputer} : ses donnees top secretes (le code du coffre de juillet). */
    public static final DeferredBlock<ComputerBlock> DECRYPTED_COMPUTER = seasonBlock("decrypted_computer",
            properties -> new ComputerBlock(properties, true), properties -> properties.strength(2.0f)
                    .sound(SoundType.METAL).lightLevel(state -> 7));

    // -- Aout -----------------------------------------------------------------------------------

    /** {@code BlockLead} : du plomb ; au grand soleil, il finit parfois en or. */
    public static final DeferredBlock<LeadBlock> LEAD_BLOCK = seasonBlock("lead_block", LeadBlock::new,
            properties -> properties.strength(4.0f, 6.0f).sound(SoundType.METAL).randomTicks());
    /** {@code ItemIceCream} : une boule, puis deux ; un peu de vitesse avec. */
    public static final DeferredItem<IceCreamItem> ICE_CREAM = seasonItem("ice_cream",
            properties -> new IceCreamItem(properties, 1), properties -> properties.food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(1.2f)
                            .alwaysEdible().build()));
    public static final DeferredItem<IceCreamItem> DOUBLE_ICE_CREAM = seasonItem("double_ice_cream",
            properties -> new IceCreamItem(properties, 2), properties -> properties.food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(1.2f)
                            .alwaysEdible().build()));
    /** {@code ItemSolarCream} : contre les coups de soleil. */
    public static final DeferredItem<SolarCreamItem> SOLAR_CREAM = seasonItem("solar_cream", SolarCreamItem::new,
            properties -> properties.stacksTo(16));
    /** {@code ItemsRegister.VOLLEY_BALL} : un ballon a lancer, qui rebondit sur les joueurs. */
    public static final DeferredItem<VolleyBallItem> VOLLEY_BALL = seasonItem("volley_ball", VolleyBallItem::new,
            properties -> properties.stacksTo(16));
    /** {@code ItemPortableVolleyCourt} : un terrain de volley, cinq fois. */
    public static final DeferredItem<VolleyCourtItem> PORTABLE_VOLLEY_COURT = seasonItem("portable_volley_court",
            VolleyCourtItem::new, properties -> properties.durability(5));
    /** {@code BlockVentilator} : il souffle devant lui, un clic l'allume ou l'eteint. */
    public static final DeferredBlock<FanBlock> VENTILATOR = seasonBlock("ventilator", FanBlock::new,
            properties -> properties.strength(2.0f).sound(SoundType.METAL));
    /** {@code BlockRunningTreadmill} : monter dessus, c'est le defi du summer body. */
    public static final DeferredBlock<TreadmillBlock> TREADMILL = seasonBlock("treadmill", TreadmillBlock::new,
            properties -> properties.strength(2.0f).sound(SoundType.METAL));
    /** {@code BlocksRegister.QUICKSAND} : on s'y enfonce. */
    public static final DeferredBlock<QuicksandBlock> QUICKSAND = seasonBlock("quicksand", QuicksandBlock::new,
            properties -> properties.strength(0.5f).sound(SoundType.SAND).noCollision());

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
        if (projectile.level() instanceof ServerLevel volleyLevel && projectile.getPersistentData().contains(VOLLEY)) {
            // Le ballon de volley repousse ce qu'il touche, puis retombe ou il est.
            if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) {
                Vec3 push = projectile.getDeltaMovement().normalize().scale(0.8d);
                hit.getEntity().push(push.x, 0.4d, push.z);
                hit.getEntity().hurtMarked = true;
            }
            Vec3 at = event.getRayTraceResult().getLocation();
            LuckyWorld.drop(volleyLevel, at.x, at.y + 0.2d, at.z, new ItemStack(VOLLEY_BALL.get()));
            projectile.discard();
            event.setCanceled(true);
            return;
        }
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

    /** La radio : on danse autour. */
    public static class RadioItem extends Item {
        public RadioItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel server && player instanceof ServerPlayer owner) {
                for (ServerPlayer other : server.getEntitiesOfClass(ServerPlayer.class,
                        player.getBoundingBox().inflate(15.0d))) {
                    other.addEffect(new MobEffectInstance(SeasonEffects.DANCE, 400, 0));
                }
                JuneEvents.tune(owner, server, 20);
                player.getItemInHand(hand).hurtAndBreak(1, player, hand);
                player.getCooldowns().addCooldown(player.getItemInHand(hand), 400);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La trompette : une fois par minute. */
    public static class TrumpetItem extends Item {
        public TrumpetItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel server) {
                LuckyWorld.sound(server, player.getX(), player.getY(), player.getZ(), "item.goat_horn.sound.1",
                        SoundSource.PLAYERS, 2.0f, 1.2f);
                server.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.0d, player.getZ(), 8, 0.6d,
                        0.3d, 0.6d, 1.0d);
                player.getCooldowns().addCooldown(player.getItemInHand(hand), 1200);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Le disque d'or : sur un juke-box, l'etage du dessous devient de l'or. */
    public static class GoldRecordItem extends Item {
        public GoldRecordItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            Level level = context.getLevel();
            BlockPos jukebox = context.getClickedPos();
            if (!level.getBlockState(jukebox).is(Blocks.JUKEBOX)) {
                return InteractionResult.PASS;
            }
            if (!(context.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            BlockPos below = jukebox.below();
            List<BlockPos> layer = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(below.offset(-2, 0, -2), below.offset(2, 0, 2))) {
                BlockState state = server.getBlockState(pos);
                if (state.isAir()) {
                    LuckyWorld.tell(player, "Il manque des blocs sous ton juke-box (laisse une zone de 5 x 5 en"
                            + " dessous).", ChatFormatting.RED);
                    return InteractionResult.FAIL;
                }
                if (state.is(Blocks.BEDROCK)) {
                    LuckyWorld.tell(player, "Tu ne peux pas poser de disque d'or sur de la bedrock.", ChatFormatting.RED);
                    return InteractionResult.FAIL;
                }
                if (state.is(Blocks.GOLD_BLOCK)) {
                    LuckyWorld.tell(player, "Il y a deja de l'or ici.", ChatFormatting.RED);
                    return InteractionResult.FAIL;
                }
                if (!LuckyWorld.canEdit(player, server, pos.immutable())) {
                    LuckyWorld.tell(player, "Tu ne peux pas poser de disque d'or dans une zone protegee.",
                            ChatFormatting.RED);
                    return InteractionResult.FAIL;
                }
                layer.add(pos.immutable());
            }
            for (BlockPos pos : layer) {
                server.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            server.playSound(null, jukebox, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 0.8f);
            server.sendParticles(ParticleTypes.WAX_ON, jukebox.getX() + 0.5d, jukebox.getY() + 1.0d,
                    jukebox.getZ() + 0.5d, 30, 1.5d, 0.5d, 1.5d, 0.0d);
            context.getItemInHand().consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }

    /** La guitare electrique : chaque coup fait une note. */
    public static class GuitarItem extends Item {
        public GuitarItem(Properties properties) {
            super(properties);
        }

        @Override
        public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            super.hurtEnemy(stack, target, attacker);
            if (attacker.level() instanceof ServerLevel server) {
                LuckyWorld.sound(server, target.getX(), target.getY(), target.getZ(), "block.note_block.guitar",
                        SoundSource.PLAYERS, 1.0f, 0.5f + server.getRandom().nextFloat());
            }
        }
    }

    /** La bouteille a la mer et ses messages ({@code 0} bouteille, {@code 1} seche, {@code 2} lisible). */
    public static class MessageItem extends Item {
        private final int kind;

        public MessageItem(Properties properties, int kind) {
            super(properties);
            this.kind = kind;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            switch (kind) {
                case 0 -> {
                    player.getItemInHand(hand).consume(1, player);
                    SeasonKit.give(server, new ItemStack(DRY_MESSAGE.get()),
                            new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
                    LuckyWorld.tell(server, "Un message est roule dans la bouteille.", ChatFormatting.YELLOW);
                }
                case 1 -> LuckyWorld.tell(server, "Ce message semble illisible. Il pourrait peut-etre devenir lisible en"
                        + " etant rechauffe.", ChatFormatting.RED);
                default -> JulyEvents.readPirateMessage(server);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La pipe : regeneration et fumee. */
    public static class PipeItem extends Item {
        public PipeItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
                return InteractionResult.FAIL;
            }
            if (level instanceof ServerLevel server) {
                if (player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < 200) {
                    LuckyWorld.tell((ServerPlayer) player, "Tu ne peux pas utiliser cet objet en combat !",
                            ChatFormatting.RED);
                    return InteractionResult.FAIL;
                }
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getEyeY() + 0.2d,
                        player.getZ(), 6, 0.1d, 0.1d, 0.1d, 0.01d);
                player.getCooldowns().addCooldown(player.getItemInHand(hand), 6000);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** L'orange : elle soigne le scorbut. */
    public static class OrangeItem extends Item {
        public OrangeItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player && player.hasEffect(SeasonEffects.SCURVY)) {
                player.removeEffect(SeasonEffects.SCURVY);
                player.removeEffect(MobEffects.WEAKNESS);
                player.removeEffect(MobEffects.SLOWNESS);
                LuckyWorld.tell(player, "Tu as ete soigne du scorbut.", ChatFormatting.GREEN);
            }
            return left;
        }
    }

    /** Les graines de perroquet : un perroquet apprivoise pour dix minutes. */
    public static class ParrotSeedsItem extends Item {
        public ParrotSeedsItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                JulyEvents.flint(server, serverLevel, 12000);
                player.getItemInHand(hand).consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Un instrument : une note par clic. */
    public static class InstrumentItem extends Item {
        private final String sound;

        public InstrumentItem(Properties properties, String sound) {
            super(properties);
            this.sound = sound;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel server) {
                LuckyWorld.sound(server, player.getX(), player.getY(), player.getZ(), sound, SoundSource.RECORDS, 1.0f,
                        0.6f + server.getRandom().nextFloat() * 0.9f);
                server.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.0d, player.getZ(), 1, 0.2d,
                        0.1d, 0.2d, server.getRandom().nextFloat());
                player.getCooldowns().addCooldown(player.getItemInHand(hand), 5);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La carte au tresor : la direction et la distance du tresor enterre. */
    public static class TreasureMapItem extends Item {
        public TreasureMapItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer server) {
                JulyEvents.readMap(server, player.getItemInHand(hand));
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La potion d'invulnerabilite : soixante secondes a l'abri des effets negatifs. */
    public static class ImmunityPotionItem extends Item {
        public ImmunityPotionItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (entity instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(SeasonEffects.IMMUNE, 1200, 0));
            }
            return left;
        }
    }

    /** La glace : une ou deux boules, un peu de vitesse. */
    public static class IceCreamItem extends Item {
        private final int scoops;

        public IceCreamItem(Properties properties, int scoops) {
            super(properties);
            this.scoops = scoops;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            entity.addEffect(new MobEffectInstance(MobEffects.SPEED, 600 * scoops, scoops - 1));
            entity.removeEffect(SeasonEffects.SUNBURN);
            return left;
        }
    }

    /** La creme solaire : elle soigne le coup de soleil. */
    public static class SolarCreamItem extends Item {
        public SolarCreamItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            if (!player.hasEffect(SeasonEffects.SUNBURN)) {
                LuckyWorld.tell(server, "Tu ne peux pas utiliser cet objet : tu n'as pas d'insolation.", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            player.removeEffect(SeasonEffects.SUNBURN);
            player.getItemInHand(hand).consume(1, player);
            LuckyWorld.tell(server, "La creme solaire te protege.", ChatFormatting.GREEN);
            return InteractionResult.SUCCESS;
        }
    }

    /** Le ballon de volley : il se lance, et repousse ce qu'il touche. */
    public static class VolleyBallItem extends Item {
        public VolleyBallItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                var ball = new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(server, player,
                        stack.copyWithCount(1));
                ball.getPersistentData().putBoolean(VOLLEY, true);
                ball.shootFromRotation(player, player.getXRot(), player.getYRot(), -10.0f, 1.2f, 1.0f);
                server.addFreshEntity(ball);
            }
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }

    static final String VOLLEY = "dohy_volley";

    /** Le terrain de volley portatif : du sable, un filet, cinq fois. */
    public static class VolleyCourtItem extends Item {
        public VolleyCourtItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                if (AugustEvents.volleyCourt(server, serverLevel)) {
                    player.getItemInHand(hand).hurtAndBreak(1, player, hand);
                    LuckyWorld.tell(server, "Tu as bien pose le terrain de volley !", ChatFormatting.GREEN);
                } else {
                    LuckyWorld.tell(server, "Il n'a pas ete possible de poser le terrain.", ChatFormatting.RED);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    // -- Blocs ----------------------------------------------------------------------------------

    /** Le plomb : au soleil de midi, il lui arrive de devenir de l'or. */
    public static class LeadBlock extends Block {
        public LeadBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (level.isBrightOutside() && level.canSeeSky(pos.above()) && random.nextInt(40) == 0) {
                level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d, 10,
                        0.4d, 0.2d, 0.4d, 0.0d);
            }
        }
    }

    /** Le ventilateur : allume, il repousse ce qui est devant lui, sur six blocs. */
    public static class FanBlock extends Block {
        public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction>
                FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

        public FanBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH)
                    .setValue(LIT, true));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,
                BlockState> builder) {
            builder.add(FACING, LIT);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            if (lit) {
                level.scheduleTick(pos, this, 2);
            }
            if (player instanceof ServerPlayer server) {
                LuckyWorld.bar(server, lit ? "Ventilateur active" : "Ventilateur desactive",
                        lit ? ChatFormatting.GREEN : ChatFormatting.RED);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (state.getValue(LIT)) {
                level.scheduleTick(pos, this, 2);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (!state.getValue(LIT)) {
                return;
            }
            net.minecraft.core.Direction facing = state.getValue(FACING);
            AABB area = new AABB(pos).expandTowards(facing.getStepX() * 6.0d, 0.0d, facing.getStepZ() * 6.0d)
                    .inflate(0.2d, 1.0d, 0.2d);
            for (Entity entity : level.getEntities((Entity) null, area, entity -> !entity.isSpectator())) {
                entity.push(facing.getStepX() * 0.08d, 0.0d, facing.getStepZ() * 0.08d);
                entity.hurtMarked = true;
            }
            if (random.nextInt(4) == 0) {
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5d + facing.getStepX() * 0.7d, pos.getY() + 0.5d,
                        pos.getZ() + 0.5d + facing.getStepZ() * 0.7d, 1, 0.1d, 0.1d, 0.1d, 0.05d);
            }
            level.scheduleTick(pos, this, 2);
        }
    }

    /** Le tapis de course : poser le pied dessus lance le defi du summer body. */
    public static class TreadmillBlock extends Block {
        public TreadmillBlock(Properties properties) {
            super(properties);
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (entity instanceof ServerPlayer player && level instanceof ServerLevel server) {
                AugustEvents.treadmill(player, server, pos);
            }
        }
    }

    /** Le sable mouvant : on s'y englue et l'on s'enfonce. */
    public static class QuicksandBlock extends Block {
        public QuicksandBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                    net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean intersects) {
            entity.makeStuckInBlock(state, new Vec3(0.25d, 0.05d, 0.25d));
        }
    }

    /** Le baril de poudre : jusqu'a 64 poudres ; une fleche ou un signal de redstone, et il explose. */
    public static class PowderBarrelBlock extends Block {
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty POWDER =
                net.minecraft.world.level.block.state.properties.IntegerProperty.create("powder", 0, 64);

        public PowderBarrelBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(POWDER, 0));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,
                BlockState> builder) {
            builder.add(POWDER);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            if (!stack.is(net.minecraft.world.item.Items.GUNPOWDER)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (player instanceof ServerPlayer server) {
                int powder = state.getValue(POWDER);
                if (powder >= 64) {
                    LuckyWorld.tell(server, "Tu ne peux pas ajouter plus de poudre a ce baril !", ChatFormatting.RED);
                    return InteractionResult.FAIL;
                }
                int added = Math.min(64 - powder, player.isShiftKeyDown() ? stack.getCount() : 1);
                stack.consume(added, player);
                level.setBlock(pos, state.setValue(POWDER, powder + added), Block.UPDATE_ALL);
                LuckyWorld.bar(server, "Il y a actuellement " + (powder + added) + " poudres dans ce baril.",
                        ChatFormatting.YELLOW);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (player instanceof ServerPlayer server) {
                LuckyWorld.bar(server, "Il y a actuellement " + state.getValue(POWDER) + " poudres dans ce baril.",
                        ChatFormatting.YELLOW);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                       net.minecraft.world.level.redstone.Orientation orientation, boolean moved) {
            if (level instanceof ServerLevel server && level.hasNeighborSignal(pos)) {
                explode(server, pos, state);
            }
        }

        @Override
        protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit,
                                       net.minecraft.world.entity.projectile.Projectile projectile) {
            if (level instanceof ServerLevel server) {
                explode(server, hit.getBlockPos(), state);
            }
        }

        static void explode(ServerLevel level, BlockPos pos, BlockState state) {
            int powder = state.getValue(POWDER);
            level.removeBlock(pos, false);
            if (powder > 0) {
                level.explode(null, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, 1.0f + powder / 10.0f,
                        Level.ExplosionInteraction.TNT);
            }
        }
    }

    /** L'ordinateur crypte (et decrypte) du pirate informatique. */
    public static class ComputerBlock extends Block {
        private final boolean decrypted;

        public ComputerBlock(Properties properties, boolean decrypted) {
            super(properties);
            this.decrypted = decrypted;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
                if (decrypted) {
                    JulyEvents.secretFiles(server);
                } else {
                    JulyEvents.decrypt(server, serverLevel, pos);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Le bloc sonore : un clic, il s'allume et joue sa note ({@code JuneEvents} s'en sert au test de rythme). */
    public static class BrightSoundBlock extends Block {
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
                net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

        public BrightSoundBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,
                BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (level instanceof ServerLevel server) {
                ring(server, pos, 1.0f);
                if (player instanceof ServerPlayer owner) {
                    JuneEvents.padPressed(owner, server, pos);
                }
            }
            return InteractionResult.SUCCESS;
        }

        static void ring(ServerLevel level, BlockPos pos, float pitch) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BrightSoundBlock)) {
                return;
            }
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            level.scheduleTick(pos, state.getBlock(), 20);
            LuckyWorld.sound(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, "block.note_block.bell",
                    SoundSource.BLOCKS, 1.0f, pitch);
            level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5d, pos.getY() + 1.2d, pos.getZ() + 0.5d, 1, 0.0d,
                    0.0d, 0.0d, pitch / 2.0f);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            }
        }
    }

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
