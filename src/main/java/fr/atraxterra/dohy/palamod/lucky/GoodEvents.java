package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.legendary.EndiumGauntletItem;
import fr.atraxterra.dohy.palamod.legendary.GauntletData;
import fr.atraxterra.dohy.palamod.legendary.LegendaryEffect;
import fr.atraxterra.dohy.palamod.legendary.PalaLegendary;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import fr.atraxterra.dohy.palamod.storage.ChestTier;
import fr.atraxterra.dohy.palamod.storage.PalaStorage;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Les bons evenements des lucky blocks classiques (paladium et endium).
 *
 * <p>Chacun reprend le {@code perform} de sa classe de PalaMod
 * ({@code modules.luckyblock.luckyevents}) : memes quantites, memes durees,
 * memes formes. Les ecarts sont notes sur place.
 */
final class GoodEvents {

    static final String WOW_BG = "wow_bg";

    private GoodEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.classic("body_guard", "Body Guard", "body_guard", false, 10, GoodEvents::bodyGuard);
        c.classic("pala_tower", "Pala-Pillone", "pala-pillone", false, 10, GoodEvents::palaTower);
        c.classic("pala_relou", "Ouaiche t'es relou", "palarelou", false, 10, GoodEvents::palaRelou);
        c.classic("rainbow_armor", "Arc en armure", "arc_en_armure", false, 20, GoodEvents::rainbowArmor);
        c.classic("mineral_rush", "Ruee vers les minerais", "mineral_rush", false, 30, GoodEvents::mineralRush);
        c.classic("hole", "Puits", "puits", false, 30, GoodEvents::hole);
        c.classic("mineral_shower", "Mineral Shower", "mineral_shower", false, 50, GoodEvents::mineralShower);
        c.classic("walk_in_music", "Walk in Music", "walk_in_music", false, 50, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(PalaLucky.WALK_IN_MUSIC, MobEffectInstance.INFINITE_DURATION));
            LuckyWorld.bar(player, "Dancing Floor", ChatFormatting.LIGHT_PURPLE);
        });
        c.classic("super_man", "SuperMan", "superman", false, 50, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(PalaLucky.SUPER_MAN, 2400)));
        c.classic(WOW_BG, "Quelle dinguerie !", "endiumluckyblock", false, 50, GoodEvents::wowBg);
        c.classic("spawner", "Spawner", "spawner", false, 100, GoodEvents::spawner);
        c.classic("consolation", "Consolation", "consolation", false, 100, (player, level, pos) -> {
            LuckyWorld.drop(level, pos, new ItemStack(PalaItems.PALADIUM_INGOT.get(), 30 + level.getRandom().nextInt(94)));
            LuckyWorld.bar(player, "Voila de quoi te consoler", ChatFormatting.YELLOW);
        });
        c.classic("caballo", "Caballo de la muerte", "caballo_de_la_muerte", false, 200, GoodEvents::caballo);
        c.classic("diamond_beacon", "Diamond Beacon", "diamond_beacon", false, 200, beacon(() -> Blocks.DIAMOND_BLOCK));
        c.classic("amethyst_beacon", "Amethyst Beacon", "amethyst_beacon", false, 400,
                beacon(PalaBlocks.AMETHYST_BLOCK));
        // L'Endium Beacon de PalaMod est en blocs de lapis (Blocks.lapis_block), pas en endium.
        c.classic("endium_beacon", "Endium Beacon", "endium_beacon", false, 400, beacon(() -> Blocks.LAPIS_BLOCK));
        c.classic("fast_learner", "Fast Learner", "fast_learner", false, 500, GoodEvents::fastLearner);
        c.classic("titane_beacon", "Titane Beacon", "titane_beacon", false, 800, beacon(PalaBlocks.TITANE_BLOCK));
        c.classic("lucky", "Trop chanceux pour ce monde", "endium", false, 900, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaItems.ENDIUM_NUGGET.get())));
        c.classic("explosif", "Explosif", "explosif", false, 900, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaPillage.BIG_DYNAMITE.get(), 32)));
        c.classic("legendary_stone", "Legendary Stone", "legendary_stone", false, 1000, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaLegendary.RANDOM.get())));
        c.classic("paladium_beacon", "Paladium Beacon", "paladium_beacon", false, 1200,
                beacon(PalaBlocks.PALADIUM_BLOCK));
        c.classic("mega_fast_learner", "Mega Fast Learner", "mega_fast_learner", false, 2400,
                GoodEvents::megaFastLearner);
        c.classic("add_money", "Et paf + de sous", "add_money", false, 5000, GoodEvents::addMoney);
        c.classic("tethanos", "Ineluctable", "ineluctable", false, 5000, (player, level, pos) ->
                LuckyWorld.drop(level, pos, new ItemStack(PalaLegendary.ENDIUM_GAUNTLET.get())));
        c.classic("rodshild", "Rodshild", "rodshild", false, 5000, GoodEvents::rodshild);
        c.classic("meaga_tethanos", "Mega-Ineluctable", "mineluctable", false, 12500, GoodEvents::megaTethanos);
    }

    /** Deux golems de fer au nom du joueur, qui le defendent et frappent ce qu'il frappe. */
    private static void bodyGuard(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 2; index++) {
            IronGolem golem = LuckyWorld.create(level, EntityTypes.IRON_GOLEM,
                    pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
            if (golem == null) {
                continue;
            }
            golem.setPlayerCreated(true);
            golem.setCustomName(Component.literal(player.getName().getString() + "'s BodyGuard"));
            golem.addTag(LuckyWorldEvents.GUARD_TAG);
            golem.addTag(LuckyWorldEvents.OWNER_TAG + player.getUUID());
            golem.setPersistenceRequired();
            level.addFreshEntity(golem);
        }
        LuckyWorld.bar(player, "Pour vous servir !", ChatFormatting.AQUA);
    }

    /** Une colonne de huit blocs de paladium qui monte du sol, un bloc tous les trois tics. */
    private static void palaTower(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState block = PalaBlocks.PALADIUM_BLOCK.get().defaultBlockState();
        LuckyTasks.repeat(player, 0, 3, 8, step -> {
            BlockPos at = pos.above(step);
            if (LuckyWorld.place(player, level, at, block)) {
                LuckyWorld.particles(level, ParticleTypes.WITCH, at.getX() + 0.5d, at.getY() + 0.5d,
                        at.getZ() + 0.5d, 12, 0.4d, 0.02d);
                LuckyWorld.sound(level, at.getX() + 0.5d, at.getY() + 0.5d, at.getZ() + 0.5d,
                        SoundEvents.METAL_PLACE, 0.8f, 0.9f + step * 0.05f);
            }
        });
    }

    /** Le faux bloc de paladium, qui saute quinze fois avant de ceder un vrai. */
    private static void palaRelou(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyWorld.place(player, level, pos, PalaLucky.PALADIUM_FAKE_BLOCK.get().defaultBlockState());
    }

    /** L'armure de cuir « Rainbow Armor », dont la couleur fait le tour du cercle en trois secondes. */
    private static void rainbowArmor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Item[] pieces = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
        for (Item piece : pieces) {
            ItemStack stack = new ItemStack(piece);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Rainbow Armor").withStyle(ChatFormatting.AQUA));
            stack.set(PalaLucky.RAINBOW.get(), true);
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(LuckyWorldEvents.rainbow(level.getGameTime())));
            LuckyWorld.drop(level, player.getX(), player.getY() + 0.5d, player.getZ(), stack);
        }
    }

    /**
     * Un lapin rapide qui porte un minerai ({@code EntityMineralRabbit}) : il en
     * lache un a chaque saut, dix sauts, puis disparait ; tue, il en lache dix.
     */
    private static void mineralRush(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Item[] minerals = {Items.DIAMOND, PalaItems.PALADIUM_INGOT.get(), PalaItems.TITANE_INGOT.get(),
                PalaItems.AMETHYST_INGOT.get()};
        Item mineral = minerals[level.getRandom().nextInt(minerals.length)];
        Rabbit rabbit = LuckyWorld.create(level, EntityTypes.RABBIT, pos.getX() + 0.5d, pos.getY() + 2.0d,
                pos.getZ() + 0.5d);
        if (rabbit == null) {
            return;
        }
        rabbit.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 2));
        rabbit.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, MobEffectInstance.INFINITE_DURATION, 2));
        rabbit.addTag(LuckyWorldEvents.MINERAL_TAG);
        rabbit.addTag(LuckyWorldEvents.MINERAL_ITEM_TAG + BuiltInRegistries.ITEM.getKey(mineral));
        rabbit.setPersistenceRequired();
        level.addFreshEntity(rabbit);
    }

    /**
     * Un eclair, de l'obsidienne, puis le puits crache : deux lingots de titane
     * et un de paladium chaque seconde pendant cinq secondes, puis quinze
     * lingots d'amethyste, trois par seconde. L'eclair ne fait ici que la lumiere.
     */
    private static void hole(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyWorld.lightning(level, player, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, true);
        LuckyWorld.place(player, level, pos, Blocks.OBSIDIAN.defaultBlockState());
        double x = pos.getX() + 0.5d;
        double y = pos.getY() + 1.2d;
        double z = pos.getZ() + 0.5d;
        LuckyTasks.repeat(player, 20, 20, 5, step -> {
            LuckyWorld.drop(level, x, y, z, new ItemStack(PalaItems.TITANE_INGOT.get(), 2));
            LuckyWorld.drop(level, x, y, z, new ItemStack(PalaItems.PALADIUM_INGOT.get()));
            LuckyWorld.particles(level, ParticleTypes.ENCHANTED_HIT, x, pos.getY() + 1.5d, z, 50, 0.3d, 0.01d);
        });
        LuckyTasks.repeat(player, 107, 7, 15, step -> {
            LuckyWorld.drop(level, x, y, z, new ItemStack(PalaItems.AMETHYST_INGOT.get()));
            LuckyWorld.particles(level, ParticleTypes.ENCHANTED_HIT, x, pos.getY() + 1.5d, z, 50, 0.3d, 0.01d);
        });
    }

    /** Quinze secondes de pluie, trois diamants ou lingots par seconde, au-dessus de la tete. */
    private static void mineralShower(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyTasks.repeat(player, 20, 20, 15, step -> {
            int roll = level.getRandom().nextInt(3);
            Item item = roll == 0 ? Items.DIAMOND
                    : roll == 1 ? PalaItems.PALADIUM_INGOT.get() : PalaItems.TITANE_INGOT.get();
            LuckyWorld.drop(level, player.getX(), player.getY() + 3.0d, player.getZ(), new ItemStack(item, 3));
        });
    }

    /** Neuf lucky blocks en endium autour, un dixieme dessus. Ils ne peuvent pas refaire le coup. */
    private static void wowBg(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = pos.offset(dx, 0, dz);
                if (chained(player, level, at)) {
                    LuckyWorld.particles(level, ParticleTypes.CLOUD, at.getX() + 0.5d, at.getY() + 0.5d,
                            at.getZ() + 0.5d, 30, 0.4d, 0.02d);
                }
            }
        }
        BlockPos top = pos.above();
        if (chained(player, level, top)) {
            LuckyWorld.particles(level, ParticleTypes.HEART, top.getX() + 0.5d, top.getY() + 1.0d,
                    top.getZ() + 0.5d, 20, 0.5d, 0.05d);
        }
        LuckyWorld.sound(level, pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d,
                SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    private static boolean chained(ServerPlayer player, ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at).canBeReplaced()
                || !LuckyWorld.place(player, level, at, PalaLucky.ENDIUM_LUCKY_BLOCK.get().defaultBlockState())) {
            return false;
        }
        if (level.getBlockEntity(at) instanceof LuckyBlockEntity lucky) {
            lucky.setChained(true);
        }
        return true;
    }

    /** Un generateur de zombies, de squelettes ou d'araignees, a la place du bloc. */
    private static void spawner(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<EntityType<?>> mobs = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER);
        BlockState state = Blocks.SPAWNER.defaultBlockState();
        if (LuckyWorld.place(player, level, pos, state) && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.setEntityId(mobs.get(level.getRandom().nextInt(mobs.size())), level.getRandom());
            spawner.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    /** Un cheval squelette nomme « Caballo ». Ici il est apprivoise par le joueur : il sert. */
    private static void caballo(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SkeletonHorse horse = LuckyWorld.create(level, EntityTypes.SKELETON_HORSE,
                pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (horse == null) {
            return;
        }
        horse.setCustomName(Component.literal("Caballo").withStyle(ChatFormatting.AQUA));
        horse.setCustomNameVisible(true);
        horse.tameWithName(player);
        horse.setPersistenceRequired();
        level.addFreshEntity(horse);
    }

    /**
     * Une pyramide de beacon ({@code Beacon}) : 7 x 7, 5 x 5 et 3 x 3 blocs du
     * metal, posee un bloc par tic autour du joueur sans l'emmurer, puis le
     * beacon au sommet et un eclair.
     */
    private static LuckyEvent.Action beacon(Supplier<? extends Block> block) {
        return (player, level, pos) -> {
            BlockState state = block.get().defaultBlockState();
            List<BlockPos> cells = new ArrayList<>();
            int[] sizes = {7, 5, 3};
            for (int layer = 0; layer < sizes.length; layer++) {
                int half = sizes[layer] / 2;
                for (int dx = -half; dx <= half; dx++) {
                    for (int dz = -half; dz <= half; dz++) {
                        cells.add(pos.offset(dx, layer, dz));
                    }
                }
            }
            LuckyTasks.repeat(player, 0, 1, cells.size(), step -> {
                BlockPos at = cells.get(step);
                if (!player.getBoundingBox().intersects(new AABB(at)) && LuckyWorld.place(player, level, at, state)) {
                    LuckyWorld.particles(level, ParticleTypes.HEART, at.getX() + 0.5d, at.getY() + 1.0d,
                            at.getZ() + 0.5d, 2, 0.3d, 0.0d);
                }
            });
            LuckyTasks.later(player, cells.size() + 2, () -> {
                BlockPos top = pos.above(3);
                if (LuckyWorld.place(player, level, top, Blocks.BEACON.defaultBlockState())) {
                    LuckyWorld.lightning(level, player, top.getX() + 0.5d, top.getY(), top.getZ() + 0.5d, true);
                }
            });
        };
    }

    /** Un niveau de plus dans un metier au hasard. PalaMod annoncait « 5 niveaux » et en donnait un. */
    private static void fastLearner(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Job job = Job.values()[level.getRandom().nextInt(Job.values().length)];
        if (JobApi.grantLevels(player, job, 1)) {
            LuckyWorld.bar(player, "Waw ! Tu as gagne un niveau en " + job.display(), ChatFormatting.GREEN);
        } else {
            LuckyWorld.bar(player, job.display() + " est deja au niveau maximum", ChatFormatting.GRAY);
        }
    }

    /** Un niveau de plus dans chaque metier. */
    private static void megaFastLearner(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (Job job : Job.values()) {
            JobApi.grantLevels(player, job, 1);
        }
        LuckyWorld.bar(player, "Waw ! Tu as gagne un niveau dans tous les metiers", ChatFormatting.GREEN);
    }

    /** Un dixieme du solde en plus ({@code getBalance / 10}). */
    private static void addMoney(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long gain = Economy.balance(player) / 10L;
        if (gain > 0L) {
            Economy.credit(player, gain);
        }
        LuckyWorld.tell(player, "Tu viens de gagner " + MoneyFormat.exact(gain), ChatFormatting.GREEN);
    }

    /** Un coffre en endium, un lingot de paladium dans chacune de ses 108 cases. */
    private static void rodshild(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState chest = PalaStorage.CHESTS.get(ChestTier.ENDIUM).get().defaultBlockState();
        if (LuckyWorld.place(player, level, pos, chest) && level.getBlockEntity(pos) instanceof Container container) {
            int slots = Math.min(ChestTier.ENDIUM.size(), container.getContainerSize());
            for (int slot = 0; slot < slots; slot++) {
                container.setItem(slot, new ItemStack(PalaItems.PALADIUM_INGOT.get()));
            }
            container.setChanged();
        }
    }

    /** Le gant de l'infini : le gant en endium avec ses six pierres. */
    private static void megaTethanos(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack gauntlet = new ItemStack(PalaLegendary.ENDIUM_GAUNTLET.get());
        EndiumGauntletItem.store(gauntlet, new GauntletData(List.of(LegendaryEffect.TELEPORTATION,
                LegendaryEffect.INVISIBILITY, LegendaryEffect.FORTUNE, LegendaryEffect.POWER, LegendaryEffect.JOBS,
                LegendaryEffect.CHAOS), 0, Map.of()));
        LuckyWorld.drop(level, pos, gauntlet);
    }
}
