package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le lucky block d'aout ({@code monthly/modules/august}) : l'ete. Dix-neuf evenements.
 *
 * <p>Ecarts : les vacances, la piscine et le chateau de sable se construisent autour du
 * joueur, au lieu du serveur Event ; la soif de « Reste hydrate » se calme avec une bouteille
 * d'eau ; la valise de « Fais tes valises » est un sac a dos du donjon.
 */
final class AugustEvents {

    private static final Set<UUID> TREADMILL = new HashSet<>();

    private AugustEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.AUGUST, "blazing_sun", "Soleil de plomb", "august/blazing_sun", false, 250,
                (player, level, pos) -> {
                    SeasonKit.give(player, new ItemStack(SeasonItems.LEAD_BLOCK.get()), new ItemStack(Items.GOLD_INGOT, 3));
                    SeasonKit.say(player, "Des alchimistes cherchaient a changer le plomb en or... Pose-le au soleil !");
                });
        c.season(LuckyType.AUGUST, "dont_make_mess", "Faut pas en faire tout un foin", "august/dont_make_mess", true,
                250, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.MESS, 600, 0));
                    LuckyTasks.repeat(player, 0, 10, 60, step -> level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM,
                            player.getX(), player.getY() + 2.5d, player.getZ(), 6, 1.0d, 0.5d, 1.0d, 0.0d));
                });
        c.season(LuckyType.AUGUST, "mosquito", "Moustique", "august/mosquito", true, 250, AugustEvents::mosquito);
        c.season(LuckyType.AUGUST, "sunburn", "Coup de soleil", "august/sunburn", true, 250, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(SeasonEffects.SUNBURN, 2400, 0));
            SeasonKit.say(player, "Coup de soleil ! Mets-toi a l'ombre, ou passe de la creme solaire.");
            SeasonKit.give(player, new ItemStack(SeasonItems.SOLAR_CREAM.get()));
        });
        c.season(LuckyType.AUGUST, "little_ice_cream", "Une petite glace", "august/little_ice_cream", false, 300,
                AugustEvents::iceCreamSeller);
        c.season(LuckyType.AUGUST, "armor_bake", "Tu vas cuire dans cette armure !", "august/armor_bake", true, 350,
                (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.BAKING, 180, 0));
                    SeasonKit.fail(player, "Tu vas cuire dans cette armure ! Enleve-la vite !");
                });
        c.season(LuckyType.AUGUST, "stay_hydrated", "Reste hydrate", "august/stay_hydrated", true, 350,
                AugustEvents::stayHydrated);
        c.season(LuckyType.AUGUST, "storm", "De l'orage dans l'air", "august/storm", false, 350, AugustEvents::storm);
        c.season(LuckyType.AUGUST, "summer_sale", "Soldes d'ete", "august/summer_sales", false, 350,
                AugustEvents::summerSale);
        c.season(LuckyType.AUGUST, "money_pool", "Piscine de billets", "august/money_pool", false, 400,
                AugustEvents::moneyPool);
        c.season(LuckyType.AUGUST, "quick_sand", "Sable mouvant", "august/quicksand", true, 400, AugustEvents::quicksand);
        c.season(LuckyType.AUGUST, "summer_body_dilemma", "Le dilemme du summer body", "august/summer_body_dilemma",
                false, 400, (player, level, pos) -> {
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                            new ItemStack(SeasonItems.TREADMILL.get()));
                    SeasonKit.say(player, "Pose ce tapis de course et monte dessus pour debloquer ton summer body !");
                });
        c.season(LuckyType.AUGUST, "august_chest", "Coffre d'aout", "august/august_chest", false, 450,
                (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre d'aout", new int[]{9, 7, 4},
                        List.of(SeasonKit.stack("palamod:titane_block", 8), SeasonKit.stack("palamod:paladium_block", 1),
                                new ItemStack(Items.GOLD_BLOCK, 15), SeasonKit.stack("palamod:green_paladium_block", 1))));
        c.season(LuckyType.AUGUST, "beach_volley", "Beach Volley", "august/beach_volley", false, 450,
                (player, level, pos) -> {
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                            new ItemStack(SeasonItems.VOLLEY_BALL.get(), 4));
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                            new ItemStack(SeasonItems.PORTABLE_VOLLEY_COURT.get()));
                });
        c.season(LuckyType.AUGUST, "summer_winter", "Team ete ou team hiver ?", "august/summer_winter", false, 500,
                AugustEvents::summerWinter);
        c.season(LuckyType.AUGUST, "sandcastle", "La vie de chateau (de sable)", "august/sand_castle_life", false, 550,
                AugustEvents::sandcastle);
        c.season(LuckyType.AUGUST, "pack_your_bags", "Fais tes valises", "august/pack_your_bags", false, 700,
                AugustEvents::packYourBags);
        c.season(LuckyType.AUGUST, "powerful_fan", "Un peu puissant ce ventilateur", "august/powerful_fan", true, 700,
                AugustEvents::powerfulFan);
        c.season(LuckyType.AUGUST, "summer_holidays", "Vacances d'ete", "august/summer_holidays", false, 1400,
                AugustEvents::holidays);
    }

    // -- Moustique ------------------------------------------------------------------------------

    /** Un moustique qui pique ({@code EntityMosquito}) : une abeille en colere, sept points de vie. */
    private static void mosquito(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var bee = SeasonKit.mob(level, EntityTypes.BEE, player.position().add(1.0d, 1.5d, 0.0d), "Moustique");
        if (bee == null) {
            return;
        }
        SeasonKit.stat(bee, Attributes.MAX_HEALTH, 7.0d);
        bee.setRemainingPersistentAngerTime(3600);
        bee.setTarget(player);
    }

    // -- Une petite glace -----------------------------------------------------------------------

    /** Le marchand de glace : une boule (250 $) ou deux (500 $). */
    private static void iceCreamSeller(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.npc(player, level, EntityTypes.WANDERING_TRADER, Vec3.atBottomCenterOf(SeasonKit.around(player, level,
                2, 3)), "Marchand de glace", 120, (p, seller) -> SeasonKit.ask(p, "Marchand de glace",
                "Bonjour, qu'est-ce que je vous sers ? Une boule de glace ou deux ?",
                List.of("Une boule (250 $)", "Deux boules (500 $)", "Au revoir !"), (q, choice) -> {
                    if (choice == 2) {
                        SeasonKit.speak(q, "Marchand de glace", "Au revoir !");
                        return;
                    }
                    long price = choice == 0 ? 250L : 500L;
                    if (!Economy.tryDebit(q, price)) {
                        SeasonKit.fail(q, "Tu n'as pas assez d'argent pour acheter une glace !");
                        return;
                    }
                    SeasonKit.give(q, new ItemStack(choice == 0 ? SeasonItems.ICE_CREAM.get()
                            : SeasonItems.DOUBLE_ICE_CREAM.get()));
                    SeasonKit.speak(q, "Marchand de glace", "Bonne degustation !");
                    SeasonKit.dismiss(seller);
                }));
        SeasonKit.say(player, "Un marchand de glace passe par la ! Fais un clic droit sur lui.");
    }

    // -- Reste hydrate --------------------------------------------------------------------------

    /** Une minute pour boire : une bouteille d'eau, ou une baignade. */
    private static void stayHydrated(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "C'est l'ete, il fait chaud ! Veille a bien t'hydrater !");
        boolean[] drank = {false};
        HYDRATING.put(player.getUUID(), drank);
        SeasonKit.challenge(player, "Tu es deshydrate ! Bois vite de l'eau !", 60, false,
                (p, age) -> drank[0] || p.isInWater() ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING, p -> {
                    HYDRATING.remove(p.getUUID());
                    SeasonKit.win(p, "Ah, ca va mieux !");
                }, p -> {
                    HYDRATING.remove(p.getUUID());
                    SeasonKit.fail(p, "Tu n'as pas bu a temps...");
                    p.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 1));
                });
    }

    private static final java.util.Map<UUID, boolean[]> HYDRATING = new java.util.HashMap<>();

    /** Une bouteille bue pendant « Reste hydrate » ({@code LivingEntityUseItemEvent}). */
    static void drank(ServerPlayer player, ItemStack stack) {
        boolean[] drank = HYDRATING.get(player.getUUID());
        if (drank != null && stack.is(Items.POTION)) {
            drank[0] = true;
        }
    }

    // -- De l'orage dans l'air ------------------------------------------------------------------

    /** Douze secondes d'orage : la foudre frappe les monstres proches, sinon le sol, jamais le joueur. */
    private static void storm(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.timer(player, "De l'orage dans l'air", 12, p -> {
        });
        LuckyTasks.repeat(player, 20, 40, 6, step -> {
            List<Mob> monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(15.0d),
                    mob -> mob instanceof Enemy);
            Vec3 at = monsters.isEmpty() ? Vec3.atBottomCenterOf(SeasonKit.around(player, level, 3, 6))
                    : monsters.get(level.getRandom().nextInt(monsters.size())).position();
            LightningBolt bolt = LuckyWorld.create(level, EntityTypes.LIGHTNING_BOLT, at.x, at.y, at.z);
            if (bolt != null) {
                bolt.setVisualOnly(monsters.isEmpty());
                level.addFreshEntity(bolt);
            }
        });
    }

    // -- Soldes d'ete ---------------------------------------------------------------------------

    /** La roue des soldes : un ventilateur, un tableau, une glace ou deux. */
    private static void summerSale(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<ItemStack> prizes = List.of(new ItemStack(SeasonItems.VENTILATOR.get()), new ItemStack(Items.PAINTING, 3),
                new ItemStack(SeasonItems.ICE_CREAM.get(), 2), new ItemStack(SeasonItems.DOUBLE_ICE_CREAM.get()));
        LuckyTasks.repeat(player, 0, 5, 12, step -> {
            LuckyWorld.bar(player, "Soldes d'ete : " + prizes.get(step % prizes.size()).getHoverName().getString(),
                    ChatFormatting.LIGHT_PURPLE);
            LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(), "block.note_block.hat",
                    SoundSource.PLAYERS, 1.0f, 1.0f + step * 0.05f);
            if (step == 11) {
                ItemStack prize = prizes.get(level.getRandom().nextInt(prizes.size())).copy();
                SeasonKit.give(player, prize);
                SeasonKit.win(player, "Tu viens de recevoir ta recompense : " + prize.getHoverName().getString() + " !");
            }
        });
    }

    // -- Piscine de billets ---------------------------------------------------------------------

    /** Une piscine ou flottent des billets : la plupart sont faux, mais pas tous. */
    private static void moneyPool(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        BlockPos center = SeasonKit.ground(level, (int) Math.floor(player.getX() + look.x * 5.0d),
                (int) Math.floor(player.getZ() + look.z * 5.0d)).below();
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 0, 3))) {
            boolean rim = Math.abs(at.getX() - center.getX()) == 3 || Math.abs(at.getZ() - center.getZ()) == 3
                    || at.getY() == center.getY() - 2;
            BlockState state = rim ? SeasonKit.block("white_concrete") : Blocks.WATER.defaultBlockState();
            if (!level.getBlockState(at).hasBlockEntity() && LuckyWorld.place(player, level, at.immutable(), state)) {
                placed.add(at.immutable());
            }
        }
        if (placed.size() < 40) {
            SeasonKit.fail(player, "Il n'a pas ete possible de creuser la piscine ici.");
            return;
        }
        RandomSource random = level.getRandom();
        for (int index = 0; index < 16; index++) {
            ItemStack bill = new ItemStack(LuckyItems.FAKE_MONEY.get());
            ItemEntity item = new ItemEntity(level, center.getX() + random.nextInt(5) - 1.5d, center.getY() + 1.2d,
                    center.getZ() + random.nextInt(5) - 1.5d, bill);
            level.addFreshEntity(item);
        }
        Economy.credit(player, 2000L);
        SeasonKit.win(player, "Plonge ! Parmi les faux billets, tu as repeche 2000 $ de vrais.");
    }

    // -- Sable mouvant --------------------------------------------------------------------------

    /** Neuf blocs sur neuf de sable mouvant sous le joueur, trente secondes. */
    private static void quicksand(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos feet = player.blockPosition();
        List<BlockPos> placed = new ArrayList<>();
        List<BlockState> before = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, -1, 4))) {
            BlockState state = level.getBlockState(at);
            if (!state.isAir() && !state.hasBlockEntity() && LuckyWorld.canEdit(player, level, at.immutable())) {
                before.add(state);
                placed.add(at.immutable());
                level.setBlock(at, SeasonItems.QUICKSAND.get().defaultBlockState(), 3);
            }
        }
        SeasonKit.fail(player, "Sable mouvant ! Sors-en vite !");
        LuckyTasks.later(player, 600, () -> {
            for (int index = 0; index < placed.size(); index++) {
                if (level.getBlockState(placed.get(index)).is(SeasonItems.QUICKSAND.get())) {
                    level.setBlock(placed.get(index), before.get(index), 3);
                }
            }
        });
    }

    // -- Le dilemme du summer body --------------------------------------------------------------

    /** Le tapis de course : tenir trente secondes dessus, il te pousse en arriere. */
    static void treadmill(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (TREADMILL.contains(player.getUUID()) || SeasonKit.busy(player)) {
            return;
        }
        TREADMILL.add(player.getUUID());
        SeasonKit.say(player, "Cours sans tomber du tapis pendant 30 secondes !");
        float yaw = player.getYRot();
        Vec3 back = Vec3.directionFromRotation(0.0f, yaw).scale(-0.12d);
        SeasonKit.challenge(player, "Le dilemme du summer body", 30, true, (p, age) -> {
            BlockPos under = p.blockPosition().below();
            if (!under.equals(pos) && !p.blockPosition().equals(pos)) {
                return age > 20 ? SeasonKit.Outcome.LOST : SeasonKit.Outcome.GOING;
            }
            p.push(back.x, 0.0d, back.z);
            p.hurtMarked = true;
            return SeasonKit.Outcome.GOING;
        }, p -> {
            TREADMILL.remove(p.getUUID());
            p.addEffect(new MobEffectInstance(SeasonEffects.SUMMER_BODY, 72000, 0));
            p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 72000, 0));
            SeasonKit.win(p, "Summer body : debloque !");
        }, p -> {
            TREADMILL.remove(p.getUUID());
            SeasonKit.fail(p, "Summer body : echec ! Ne t'en fais pas, tu n'as pas besoin de ca pour etre super.");
        });
    }

    // -- Beach volley ---------------------------------------------------------------------------

    /** Le terrain de volley : un rectangle de sable et un filet de barrieres. */
    static boolean volleyCourt(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        BlockPos center = BlockPos.containing(player.position().add(look.scale(6.0d))).below();
        int placed = 0;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-4, 0, -2), center.offset(4, 0, 2))) {
            if (!level.getBlockState(at).hasBlockEntity()
                    && LuckyWorld.place(player, level, at.immutable(), Blocks.SAND.defaultBlockState())) {
                placed++;
            }
        }
        if (placed < 30) {
            return false;
        }
        for (int dz = -2; dz <= 2; dz++) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos net = center.offset(0, dy, dz);
                if (level.getBlockState(net).canBeReplaced()) {
                    LuckyWorld.place(player, level, net, dy == 2 ? SeasonKit.block("white_wool")
                            : Blocks.OAK_FENCE.defaultBlockState());
                }
            }
        }
        return true;
    }

    // -- Team ete ou team hiver -----------------------------------------------------------------

    private static void summerWinter(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.ask(player, "Team ete ou team hiver ?", "Et toi, plutot team ete ou team hiver ?",
                List.of("Team ete", "Team hiver"), (p, choice) -> {
                    if (choice == 0) {
                        SeasonKit.give(p, new ItemStack(LuckyGadgets.BLAZE_AND_STEEL.get()),
                                new ItemStack(Items.SUNFLOWER, 4));
                        SeasonKit.win(p, "Vive le soleil !");
                    } else {
                        for (int dx = -5; dx <= 5; dx++) {
                            for (int dz = -5; dz <= 5; dz++) {
                                BlockPos at = SeasonKit.ground(level, p.getBlockX() + dx, p.getBlockZ() + dz);
                                if (dx * dx + dz * dz <= 25 && level.getBlockState(at).isAir()
                                        && level.getBlockState(at.below()).isFaceSturdy(level, at.below(),
                                        net.minecraft.core.Direction.UP)) {
                                    LuckyWorld.place(p, level, at, Blocks.SNOW.defaultBlockState());
                                }
                            }
                        }
                        SeasonKit.give(p, SeasonKit.named(new ItemStack(Items.SPRUCE_SAPLING, 2), "Sapin de Noel"),
                                new ItemStack(Items.SNOWBALL, 16));
                        SeasonKit.win(p, "Vive la neige !");
                    }
                });
    }

    // -- La vie de chateau (de sable) -----------------------------------------------------------

    /** Un chateau de sable et son majordome, qui rend un service au choix. */
    private static void sandcastle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        BlockPos center = SeasonKit.ground(level, (int) Math.floor(player.getX() + look.x * 6.0d),
                (int) Math.floor(player.getZ() + look.z * 6.0d));
        BlockState wall = Blocks.SANDSTONE.defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 2, 2))) {
            boolean edge = Math.abs(at.getX() - center.getX()) == 2 || Math.abs(at.getZ() - center.getZ()) == 2;
            boolean tower = Math.abs(at.getX() - center.getX()) == 2 && Math.abs(at.getZ() - center.getZ()) == 2;
            if (edge && (at.getY() < center.getY() + 2 || tower) && level.getBlockState(at).canBeReplaced()) {
                LuckyWorld.place(player, level, at.immutable(), wall);
            }
        }
        for (BlockPos corner : new BlockPos[]{center.offset(-2, 3, -2), center.offset(2, 3, -2), center.offset(-2, 3, 2),
                center.offset(2, 3, 2)}) {
            if (level.getBlockState(corner).canBeReplaced()) {
                LuckyWorld.place(player, level, corner, Blocks.CHISELED_SANDSTONE.defaultBlockState());
            }
        }
        SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(center), "Majordome", 180,
                (p, butler) -> SeasonKit.ask(p, "Majordome", "Bonjour. Quel service souhaitez-vous que je rende"
                        + " aujourd'hui ?", List.of("Je souhaiterais obtenir une tete rare",
                        "Je souhaiterais obtenir 8 blocs de paladium", "Je souhaiterais obtenir 2 lucky blocks de juin",
                        "Je souhaiterais obtenir 2 lucky blocks de juillet"), (q, choice) -> {
                    ItemStack reward = switch (choice) {
                        case 0 -> new ItemStack(Items.DRAGON_HEAD);
                        case 1 -> SeasonKit.stack("palamod:paladium_block", 8);
                        case 2 -> LuckyType.JUNE.stack(2);
                        default -> LuckyType.JULY.stack(2);
                    };
                    SeasonKit.give(q, reward);
                    SeasonKit.speak(q, "Majordome", "Ce fut un plaisir.");
                    SeasonKit.dismiss(butler);
                }));
        SeasonKit.say(player, "Un chateau de sable ! Le majordome t'attend dedans.");
    }

    // -- Fais tes valises -----------------------------------------------------------------------

    /** Cinq affaires a ramasser en une minute, avant que les parents ne partent. */
    private static void packYourBags(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack[] things = {new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.BREAD),
                new ItemStack(SeasonItems.SOLAR_CREAM.get()), new ItemStack(Items.FISHING_ROD),
                new ItemStack(SeasonItems.VOLLEY_BALL.get())};
        List<UUID> items = new ArrayList<>();
        for (ItemStack thing : things) {
            BlockPos at = SeasonKit.around(player, level, 4, 10);
            ItemEntity item = new ItemEntity(level, at.getX() + 0.5d, at.getY() + 0.5d, at.getZ() + 0.5d, thing.copy());
            item.setGlowingTag(true);
            level.addFreshEntity(item);
            items.add(item.getUUID());
        }
        SeasonKit.say(player, "Recupere les 5 objets qui brillent avant que tes parents ne partent !");
        SeasonKit.challenge(player, "Fais tes valises", 60, false, (p, age) -> {
            for (UUID id : items) {
                Entity entity = level.getEntity(id);
                if (entity != null && entity.isAlive()) {
                    return SeasonKit.Outcome.GOING;
                }
            }
            return SeasonKit.Outcome.WON;
        }, p -> {
            SeasonKit.win(p, "Valises bouclees ! Direction le camping.");
            SeasonKit.give(p, SeasonKit.named(SeasonKit.stack("palamod:dungeon_backpack_t2", 1), "Valise"));
        }, p -> SeasonKit.fail(p, "Tes parents sont partis sans toi :("));
    }

    // -- Un peu puissant ce ventilateur ---------------------------------------------------------

    /** Un ventilateur geant souffle le joueur dix secondes. */
    private static void powerfulFan(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 push = player.getLookAngle().multiply(-1.0d, 0.0d, -1.0d).normalize();
        SeasonKit.fail(player, "Un peu puissant, ce ventilateur !");
        LuckyTasks.repeat(player, 0, 2, 100, step -> {
            player.push(push.x * 0.35d, 0.02d, push.z * 0.35d);
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.CLOUD, player.getX() - push.x * 2.0d, player.getY() + 1.0d,
                    player.getZ() - push.z * 2.0d, 3, 0.3d, 0.5d, 0.3d, 0.1d);
        });
        SeasonKit.softFall(player, 400);
    }

    // -- Vacances d'ete -------------------------------------------------------------------------

    /** Une petite ile au soleil, cinq minutes, et un coffre de vacances. */
    private static void holidays(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 back = player.position();
        int y = Math.min(150, level.getMaxY() - 12);
        BlockPos center = new BlockPos(player.getBlockX(), y, player.getBlockZ());
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(6, 0, 6))) {
            double distance = at.distSqr(new BlockPos(center.getX(), at.getY(), center.getZ()));
            if (distance > 36.0d || !level.getBlockState(at).isAir()) {
                continue;
            }
            BlockState state = at.getY() == center.getY() ? Blocks.SAND.defaultBlockState()
                    : Blocks.SANDSTONE.defaultBlockState();
            if (LuckyWorld.place(player, level, at.immutable(), state)) {
                placed.add(at.immutable());
            }
        }
        if (placed.size() < 60) {
            placed.forEach(at -> level.removeBlock(at, false));
            SeasonKit.fail(player, "Pas de place pour les vacances ici.");
            return;
        }
        BlockPos palm = center.offset(3, 1, 3);
        for (int dy = 0; dy < 5; dy++) {
            if (LuckyWorld.place(player, level, palm.above(dy), Blocks.JUNGLE_LOG.defaultBlockState())) {
                placed.add(palm.above(dy));
            }
        }
        for (BlockPos leaf : BlockPos.betweenClosed(palm.offset(-2, 5, -2), palm.offset(2, 5, 2))) {
            if (Math.abs(leaf.getX() - palm.getX()) + Math.abs(leaf.getZ() - palm.getZ()) <= 3
                    && LuckyWorld.place(player, level, leaf.immutable(), Blocks.JUNGLE_LEAVES.defaultBlockState())) {
                placed.add(leaf.immutable());
            }
        }
        SeasonKit.chest(player, level, center.offset(-2, 1, -2), List.of(SeasonKit.stack("palamod:paladium_block", 2),
                LuckyType.AUGUST.stack(1), LuckyType.JULY.stack(1), new ItemStack(SeasonItems.DOUBLE_ICE_CREAM.get(), 2)));
        player.teleportTo(center.getX() + 0.5d, center.getY() + 1.0d, center.getZ() + 0.5d);
        SeasonKit.win(player, "Bienvenue en vacances ! Profite bien, le retour est dans 5 minutes.");
        SeasonKit.timer(player, "Vacances d'ete", 300, p -> {
            for (int index = placed.size() - 1; index >= 0; index--) {
                level.removeBlock(placed.get(index), false);
            }
            p.teleportTo(back.x, back.y, back.z);
            SeasonKit.say(p, "Les vacances sont finies !");
        });
    }
}
