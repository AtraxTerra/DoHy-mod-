package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.equipment.PalaEquipment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Le cinquieme lot, pour les lucky blocks classiques et le findium : ce qui restait apres les
 * quatre premiers, avec les objets et blocs de {@link LuckyGadgets}, les effets de
 * {@link LuckyEffectsExtra} et les creatures de {@link LuckyCreatures}.
 *
 * <p><b>Ecarts de nom et d'image</b> : « Batman nerveux » devient « Chauves-souris nerveuses »,
 * « Vers l'infini et l'au dela » « Direction les etoiles », « Picasso » « L'artiste », « Kobe
 * Bryan » « Panier ! », « Lebron James » « Dunk » et « Squid Game » « Masque de bal » ; leurs
 * images, ou celles qui reprenaient un logo ou un personnage, sont redessinees.
 *
 * <p><b>Pas portes</b> (une ligne dans {@code docs/PALAMOD.md}, section 41) : la statue des
 * admins, le grade Divinity, les disques de Fuze et du Mii, la boutique de Fuze, Adieu faction,
 * Tp All et les skins de golem.
 */
final class ClassicContent {

    /** {@code Random.nextInt(30)} : les lingots de chaque poule de « Tire aux pigeons ». */
    private static final int CHICKEN_DROPS = 30;
    /** {@code EntityAngryWither} : 900 points de vie, vitesse 0,8. */
    private static final double ANGRY_WITHER_HEALTH = 900.0d;

    private ClassicContent() {
    }

    static void register(LuckyEvents.Catalog c) {
        // Paladium et endium.
        c.classic("fish_bowl", "Fish and Chips", "fish_and_chips", true, 20, ClassicContent::fishBowl);
        c.classic("colorful_lamp", "Colorful Lamp", "colorful_lamp", false, 20,
                (player, level, pos) -> toss(player, new ItemStack(LuckyGadgets.COLORED_LAMP.get(), 16)));
        c.classic("hunter_plant", "Hunter Plant", "hunter_plant", false, 20,
                (player, level, pos) -> toss(player, new ItemStack(LuckyGadgets.HUNTER_FLOWER.get())));
        c.classic("commando", "Mission commando", "mission_commando", true, 20,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(PalaLucky.COMMANDO, 9600, 0)));
        c.classic("picasso", "L'artiste", "picasso", false, 20, ClassicContent::painting);
        c.classic("space_food", "Direction les etoiles", "space_food", false, 30,
                (player, level, pos) -> toss(player, new ItemStack(LuckyGadgets.SPACE_FOOD.get())));
        c.classic("chicken_shoot", "Tire aux pigeons", "tir_aux_pigeons", false, 30, ClassicContent::chickens);
        c.classic("nervous_batman", "Chauves-souris nerveuses", "chauves_souris", true, 50, ClassicContent::bats);
        c.classic("kawaii_tnt", "TNT Mignonne", "kawaii_tnt", false, 50,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.KAWAII_TNT.get())));
        c.classic("cow_player", "Cow Player", "cow_player", true, 100, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(PalaLucky.COW_PLAYER, MobEffectInstance.INFINITE_DURATION, 0));
            LuckyWorld.bar(player, "Meuuuuh", ChatFormatting.WHITE);
        });
        c.classic("prickly_sheep", "Titanesque", "titanesque", true, 100, ClassicContent::pricklySheep);
        c.classic("angry_wither", "Whiter enerve", "angry_wither", true, 100, ClassicContent::angryWither);
        c.classic("fake_tnt", "Fake TNT", "fake_tnt", false, 150,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.FAKE_TNT.get(), 16)));
        c.classic("analyste", "Analyste", "analyste", false, 150,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.CHUNK_ANALYSER.get())));
        c.classic("pyrobarbare", "Pyrobarbare", "pyrobarbare", false, 150,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.BLAZE_AND_STEEL.get())));
        c.classic("grappin", "Hook hook jab", "hook_hook_jab", false, 150, (player, level, pos) -> {
            LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.GRAPPIN.get()));
            LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.GRAPPIN_HOOK.get(), 64));
        });
        c.classic("magic_potion", "Potion magique", "potion_magique", false, 150,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.MAGIC_POTION.get())));
        c.classic("lasso", "Lasso", "lasso", false, 150,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.LASSO.get())));
        c.classic("jetpack", "Envole-moi", "envole-moi", false, 150, (player, level, pos) -> {
            toss(player, new ItemStack(LuckyGadgets.JETPACK.get()));
            toss(player, new ItemStack(Items.FIREWORK_ROCKET, 16));
        });
        c.classic("wifi", "Wi-Fi", "wi-fi", false, 150,
                (player, level, pos) -> toss(player, new ItemStack(LuckyGadgets.WIRELESS_LEVER_ITEM.get())));
        c.classic("coloriage", "Coloriage", "coloriage", false, 200,
                (player, level, pos) -> toss(player, new ItemStack(LuckyGadgets.DYEING_MACHINE.get())));
        c.classic("palachat", "Palachat", "palachat", false, 300, ClassicContent::palachat);
        c.classic("biome_painter", "Biome Painter", "biome_painter", false, 300,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.BIOME_PAINTER.get())));
        c.classic("less_kawaii_tnt", "TNT Moins mignonne", "tnt_moins_mignonne", false, 1000,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.LESS_KAWAII_TNT.get())));
        c.classic("camouflage", "Camouflage", "camouflage", false, 1200, (player, level, pos) -> {
            toss(player, new ItemStack(LuckyGadgets.LUCKY_FLOWER.get()));
            LuckyWorld.bar(player, "Cette fleur est a couper le souffle", ChatFormatting.AQUA);
        });

        // Findium.
        c.findium("timemachine", "Time Machine", "flashy_block", false, 50, (player, level, pos) -> {
            if (level.getBlockState(pos).isAir()) {
                LuckyWorld.place(player, level, pos, LuckyGadgets.FLASHY_BLOCK.get().defaultBlockState());
            }
        });
        c.findium("peacenlove", "Peace 'N Love", "peacenlove", false, 100, ClassicContent::dove);
        c.findium("magnetic", "Aimante", "aimente", true, 100,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(PalaLucky.MAGNETIC, 12000, 0)));
        c.findium("banana", "Fruit Exotique", "banana", false, 100, ClassicContent::banana);
        c.findium("palafakeore", "Pas la", "pasla", true, 100, ClassicContent::fakeOres);
        c.findium("happynewyear", "Nouvel an", "happynewyear", false, 100,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.NEW_YEAR_FIREWORK.get())));
        c.findium("mariomushrrom", "Mauvais Jeu", "champignon_geant", false, 100,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.GIANT_MUSHROOM.get())));
        c.findium("statstone", "Pierre de statistiques", "statstone", false, 100,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.STATS_STONE.get())));
        c.findium("texturenotfound", "404 Not found", "404_", true, 100,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(PalaLucky.NO_TEXTURE, 1200, 0)));
        c.findium("lag", "56K", "56k", true, 100, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(PalaLucky.LAG, 1200, 0));
            LuckyWorld.bar(player, "Attention aux lags", ChatFormatting.YELLOW);
        });
        c.findium("boatfurnace", "Turboat", "turboat", false, 150,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.BOAT_FURNACE.get())));
        c.findium("lebron_james", "Dunk", "lebron_james", false, 200,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.BASKET_HOOP.get())));
        c.findium("kobe_bryan", "Panier !", "kobe_bryan", false, 200,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.BASKETBALL.get())));
        c.findium("meter", "Oui metre", "oui_metre", false, 300,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.METER.get())));
        c.findium("nether_enabled", "Nether Active", "nether_active", true, 300, ClassicContent::fakePortal);
        c.findium("breakears", "Casse Oreilles", "casse_oreilles", true, 300, (player, level, pos) -> {
            LuckyWorld.place(player, level, pos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            LuckyWorld.place(player, level, pos.above(), LuckyGadgets.ALARM.get().defaultBlockState());
        });
        c.findium("petrock", "Caillou de compagnie", "caillou_de_compagnie", false, 300,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.PET_ROCK.get())));
        c.findium("noobtrap", "Piege a Noob", "piege_a_noob", false, 300,
                (player, level, pos) -> LuckyWorld.place(player, level, pos, LuckyGadgets.JAWS_TRAP.get().defaultBlockState()));
        c.findium("sleepingbag", "Belle etoile", "sleepingbag", false, 300,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.SLEEPING_BAG.get())));
        c.findium("squidgame", "Masque de bal", "masque_de_bal", false, 300,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.PARTY_MASK.get())));
        c.findium("itshot", "Attention c'est chaud", "attention_c_est_chaud", true, 400, (player, level, pos) ->
                LuckyWorld.place(player, level, player.blockPosition().below(),
                        LuckyGadgets.FAKE_LAVA.get().defaultBlockState()));
        c.findium("soda", "Soda", "soda_pop", false, 400,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.SODA.get())));
        c.findium("poulpogun", "PoulpoGun", "poulpotron", false, 500,
                (player, level, pos) -> give(player, new ItemStack(LuckyGadgets.POULPOGUN.get())));
        c.findium("freexp", "XP gratuite", "xp_gratuite", false, 500, (player, level, pos) -> xpMonster(player, level, false));
        c.findium("smokedsalmon", "Saumon Fume", "saumon_fume", false, 500, ClassicContent::smokeBombs);
        c.findium("coffrefort", "Coffre fort", "coffre_fort", false, 500,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.SAFE_CHEST_ITEM.get())));
        c.findium("catdetector", "Cat Detector", "cat_detector", false, 500,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.CAT_DETECTOR.get())));
        c.findium("zoo", "Zoo", "zoo", true, 600, ClassicContent::zoo);
        c.findium("orangeorblue", "Orange ou bleu ?", "orange_ou_bleu", false, 700, (player, level, pos) -> {
            ArmorStand marker = LuckyCreatures.marker(level, player.getX(), player.getY(), player.getZ());
            if (marker != null) {
                LuckyCreatures.tag(marker, LuckyCreatures.COLLECTOR);
                level.addFreshEntity(marker);
            }
        });
        c.findium("removedfromgame", "Retire du jeu", "retire_du_jeu_dessin", true, 700, ClassicContent::stalker);
        c.findium("tombe", "Ton heure a sonne", "tombe", false, 700, (player, level, pos) -> LuckyEffectsExtra.tomb(player));
        c.findium("bad_face", "T'as une mauvaise mine", "bad_face", false, 700,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(PalaLucky.BAD_FACE, 99999, 0)));
        c.findium("trapstring", "Piege a ficelle", "trapficelle", false, 800,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.STRING_TRAP.get())));
        c.findium("chatdetector", "Chat Detector", "chat_detector", false, 800,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.CHAT_DETECTOR.get())));
        c.findium("whatdidyoueat", "T'as mange quoi ?", "t_a_mange_quoi", true, 900, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(PalaLucky.FART, 600, 1));
            LuckyWorld.bar(player, "T'arretes de peter, oui?", ChatFormatting.LIGHT_PURPLE);
        });
        c.findium("freemorexp", "XP encore plus gratuite", "xp_encore_plus_gratuite", false, 1000,
                (player, level, pos) -> xpMonster(player, level, true));
        c.findium("howdidyougetthat", "Comment t'as eu ca ?", "comment_t_as_eu_ca", false, 1000,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.FAKE_COMMAND_BLOCK.get())));
        c.findium("johntheripper", "John The Reaper", "john_the_reaper", false, 1000,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.BRUTEFORCER.get())));
        c.findium("bouseul", "Bouseul", "bouseul", false, 1000,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.FOLLOW_COMPASS.get())));
        c.findium("chewedwork", "Travail mache", "travail_mache", false, 3000, ClassicContent::chewedWork);
        c.findium("generaldrudgery", "Corvee generale", "corve_generale", false, 4000, ClassicContent::drudgery);
        c.findium("megacoffrefort", "Mega Coffre fort", "mega_coffre_fort", false, 5000,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.MEGA_SAFE_CHEST_ITEM.get())));
        c.findium("ultracoffrefort", "Ultra Coffre fort", "ultra_coffre_fort", false, 10000,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(LuckyGadgets.ULTRA_SAFE_CHEST_ITEM.get())));
    }

    // -- Outils ---------------------------------------------------------------------------

    /** {@code addItemStackToInventory} : dans l'inventaire, ou au sol s'il est plein. */
    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** {@code entityDropItem} : lache aux pieds du joueur. */
    static void toss(ServerPlayer player, ItemStack stack) {
        ItemEntity item = player.drop(stack, false);
        if (item != null) {
            item.setNoPickUpDelay();
        }
    }

    private static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, Vec3 at) {
        return LuckyCreatures.spawn(level, type, at);
    }

    /** Une case de surface a {@code min} - {@code max} blocs du joueur ({@code 21 + nextInt(42)}). */
    private static BlockPos far(ServerPlayer player, ServerLevel level, int min, int max) {
        var random = level.getRandom();
        int dx = (min + random.nextInt(max - min)) * (random.nextBoolean() ? 1 : -1);
        int dz = (min + random.nextInt(max - min)) * (random.nextBoolean() ? 1 : -1);
        int x = player.getBlockX() + dx;
        int z = player.getBlockZ() + dz;
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
    }

    // -- Evenements -------------------------------------------------------------------------

    /** Fish and Chips : le casque tombe, et un bocal a poisson le remplace. « Blop Blop ». */
    private static void fishBowl(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.isEmpty()) {
            LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(), helmet.copy());
        }
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(LuckyGadgets.FISH_BOWL_ITEM.get()));
        LuckyWorld.bar(player, "Blop Blop", ChatFormatting.AQUA);
    }

    /**
     * L'artiste ({@code Picasso}) : un tableau de PalaMod ({@code EntityLuckyPainting}). Ici une
     * peinture du jeu, avec l'un des tableaux de PalaMod.
     */
    private static void painting(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack painting = new ItemStack(Items.PAINTING);
        var registry = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
        List<Holder.Reference<PaintingVariant>> lucky = registry.listElements()
                .filter(variant -> variant.key().identifier().getNamespace().equals("palamod"))
                .toList();
        if (!lucky.isEmpty()) {
            painting.set(DataComponents.PAINTING_VARIANT, lucky.get(level.getRandom().nextInt(lucky.size())));
        }
        toss(player, painting);
    }

    /** Tire aux pigeons : trois poules montent dans le ciel, chacune avec ses lingots. */
    private static void chickens(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 3; index++) {
            Mob chicken = (Mob) spawn(level, EntityTypes.CHICKEN, player.position().add(index - 1, 1.0d, 2.0d));
            if (chicken != null) {
                chicken.getPersistentData().putInt(LuckyCreatures.DROPS, level.getRandom().nextInt(CHICKEN_DROPS));
                chicken.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 600, 0));
            }
        }
    }

    /** Batman nerveux : vingt chauves-souris qui piquent. */
    private static void bats(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 20; index++) {
            Entity bat = spawn(level, EntityTypes.BAT, LuckyCreatures.near(player, 3.0d).add(0.0d, 2.0d, 0.0d));
            if (bat != null) {
                LuckyCreatures.tag(bat, LuckyCreatures.NERVOUS);
            }
        }
    }

    /** Titanesque ({@code EntityPricklySheep}) : un mouton immense, qui pique au contact. */
    private static void pricklySheep(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob sheep = (Mob) spawn(level, EntityTypes.SHEEP, LuckyCreatures.near(player, 3.0d));
        if (sheep != null) {
            LuckyCreatures.tag(sheep, LuckyCreatures.PRICKLY);
            sheep.getAttribute(Attributes.SCALE).setBaseValue(2.5d);
            sheep.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0d);
            sheep.setHealth(40.0f);
            sheep.setCustomName(Component.literal("Titanesque"));
        }
    }

    /** Whiter enerve ({@code EntityAngryWither}) : un wither de 900 points de vie. */
    private static void angryWither(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob wither = (Mob) spawn(level, EntityTypes.WITHER, player.position().add(0.0d, 2.0d, 4.0d));
        if (wither != null) {
            wither.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024.0d, ANGRY_WITHER_HEALTH));
            wither.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.8d);
            wither.setHealth(wither.getMaxHealth());
        }
    }

    /** Palachat : un chat a soi, qui offre de temps en temps de la terre ou un lingot de paladium. */
    private static void palachat(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Entity cat = spawn(level, EntityTypes.CAT, LuckyCreatures.near(player, 2.0d));
        if (cat instanceof TamableAnimal tame) {
            tame.tame(player);
            tame.setCustomName(Component.literal("Palachat").withStyle(ChatFormatting.GOLD));
            tame.setPersistenceRequired();
            LuckyCreatures.tag(tame, LuckyCreatures.PALACHAT);
        }
    }

    /**
     * Peace 'N Love ({@code EntityDove}) : une colombe porte une epee en paladium enchantee
     * (Tranchant, Aura de feu, Solidite), qu'elle lache en mourant. Ici un perroquet gris.
     */
    private static void dove(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Entity parrot = spawn(level, EntityTypes.PARROT, player.position().add(0.0d, 2.0d, 0.0d));
        if (!(parrot instanceof Mob dove)) {
            return;
        }
        ItemStack sword = new ItemStack(PalaEquipment.PALADIUM_SWORD.get());
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.FIRE_ASPECT, Enchantments.UNBREAKING)) {
            enchantments.get(key).ifPresent(holder -> sword.enchant(holder, holder.value().getMaxLevel()));
        }
        dove.setItemSlot(EquipmentSlot.MAINHAND, sword);
        dove.setDropChance(EquipmentSlot.MAINHAND, 2.0f);
        dove.setCustomName(Component.literal("Colombe"));
        if (dove instanceof net.minecraft.world.entity.animal.parrot.Parrot bird) {
            bird.setVariant(net.minecraft.world.entity.animal.parrot.Parrot.Variant.GRAY);
        }
    }

    /** Fruit exotique : un bananier pousse sur le lucky block. */
    private static void banana(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState log = LuckyGadgets.BANANA_LOG.get().defaultBlockState();
        BlockState leaves = LuckyGadgets.BANANA_LEAVES.get().defaultBlockState();
        for (int dy = 0; dy < 4; dy++) {
            LuckyWorld.place(player, level, pos.above(dy), log);
        }
        BlockPos crown = pos.above(4);
        LuckyWorld.place(player, level, crown, leaves);
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            LuckyWorld.place(player, level, crown.relative(direction), leaves);
            LuckyWorld.place(player, level, crown.relative(direction).relative(direction).below(), leaves);
        }
    }

    /**
     * Pas la ({@code spawnAnimatedStructure}, {@code witchMagic}) : des faux minerais de paladium
     * apparaissent un a un dans la roche autour du joueur. Ils ne rendent rien.
     */
    private static void fakeOres(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos feet = player.blockPosition();
        LuckyTasks.repeat(player, 0, 5, 30, step -> {
            var random = level.getRandom();
            BlockPos at = feet.offset(random.nextInt(9) - 4, -1 - random.nextInt(4), random.nextInt(9) - 4);
            BlockState there = level.getBlockState(at);
            if (there.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || there.is(Blocks.DIRT)
                    || there.is(Blocks.GRASS_BLOCK)) {
                if (LuckyWorld.place(player, level, at, LuckyGadgets.FAKE_PALADIUM_ORE.get().defaultBlockState())) {
                    level.sendParticles(ParticleTypes.WITCH, at.getX() + 0.5d, at.getY() + 1.0d, at.getZ() + 0.5d,
                            8, 0.3d, 0.3d, 0.3d, 0.0d);
                }
            }
        });
    }

    /** Nether Active : un cadre d'obsidienne et son faux portail, a cote du joueur. Y entrer tue. */
    private static void fakePortal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos base = player.blockPosition().relative(player.getDirection(), 3);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                BlockPos at = base.offset(player.getDirection().getClockWise().getStepX() * dx, dy,
                        player.getDirection().getClockWise().getStepZ() * dx);
                boolean inside = dx == 0 && (dy == 1 || dy == 2);
                LuckyWorld.place(player, level, at, inside ? LuckyGadgets.FAKE_PORTAL.get().defaultBlockState() : obsidian);
            }
        }
    }

    /** Saumon fume : trois bombes fumigenes, d'une couleur au hasard chacune. */
    private static void smokeBombs(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 3; index++) {
            ItemStack bomb = new ItemStack(LuckyGadgets.SMOKE_BOMB.get());
            DyeColor color = DyeColor.values()[level.getRandom().nextInt(DyeColor.values().length)];
            bomb.set(DataComponents.DYED_COLOR, new DyedItemColor(color.getTextureDiffuseColor()));
            LuckyWorld.drop(level, pos, bomb);
        }
    }

    /**
     * XP gratuite : un des monstres du chasseur ({@code EntityFarmerChicken}, {@code EntityCavernousZombie},
     * {@code EntityFlowerMonster}, {@code EntityMegaCreeper}), qui lache beaucoup d'experience.
     * XP encore plus gratuite : la poule du fermier, toujours.
     */
    private static void xpMonster(ServerPlayer player, ServerLevel level, boolean chicken) {
        List<EntityType<? extends Mob>> types = List.of(EntityTypes.CHICKEN, EntityTypes.ZOMBIE, EntityTypes.CREEPER,
                EntityTypes.CREEPER);
        int index = chicken ? 0 : level.getRandom().nextInt(types.size());
        Mob mob = (Mob) spawn(level, types.get(index), LuckyCreatures.near(player, 3.0d));
        if (mob == null) {
            return;
        }
        LuckyCreatures.tag(mob, LuckyCreatures.XP);
        if (index == 3) {
            mob.getAttribute(Attributes.SCALE).setBaseValue(1.6d);
        }
        if (chicken) {
            mob.getPersistentData().putBoolean(LuckyCreatures.XP_MORE, true);
        }
    }

    /** Zoo : quatorze creatures du chasseur, sur le modele du jeu, autour du joueur. */
    private static void zoo(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<EntityType<?>> animals = List.of(EntityTypes.CHICKEN, EntityTypes.ZOMBIE, EntityTypes.CREEPER,
                EntityTypes.CREEPER, EntityTypes.ARMADILLO, EntityTypes.SILVERFISH, EntityTypes.GLOW_SQUID,
                EntityTypes.SILVERFISH, EntityTypes.TURTLE, EntityTypes.PARROT, EntityTypes.PANDA, EntityTypes.GOAT,
                EntityTypes.DOLPHIN, EntityTypes.COW);
        for (EntityType<?> type : animals) {
            spawn(level, type, LuckyCreatures.near(player, 4.0d));
        }
        var elephant = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(
                Identifier.fromNamespaceAndPath("palamod", "elephant"));
        elephant.ifPresent(type -> spawn(level, type, LuckyCreatures.near(player, 5.0d)));
    }

    /**
     * Retire du jeu ({@code EntityHerobrine}) : une silhouette hostile apparait entre 21 et 63 blocs,
     * 40 points de vie, dans des particules de portail. Ici un enderman, deja en colere.
     */
    private static void stalker(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos at = far(player, level, 21, 63);
        Mob enderman = (Mob) spawn(level, EntityTypes.ENDERMAN, Vec3.atBottomCenterOf(at));
        if (enderman != null) {
            enderman.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0d);
            enderman.setHealth(40.0f);
            enderman.setTarget(player);
        }
    }

    /** Travail mache : les trophees du chasseur, une table d'enchantement, et un mot. */
    private static void chewedWork(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (var item : List.of(LuckyGadgets.SNAKE_VENOM, LuckyGadgets.PARROT_FEATHER, LuckyGadgets.TURTLE_SCALES,
                LuckyGadgets.GOAT_SHOE, LuckyGadgets.MEDUSE_HOOK, LuckyGadgets.PANDA_DROOL, LuckyGadgets.SNAIL_SHELL)) {
            LuckyWorld.drop(level, pos, new ItemStack(item.get()));
        }
        LuckyWorld.drop(level, pos, new ItemStack(fr.atraxterra.dohy.palamod.defense.PalaDefense.WITHER_SKULL_FRAGMENT.get()));
        LuckyWorld.drop(level, pos, new ItemStack(Items.ENCHANTING_TABLE));
        LuckyWorld.tell(player, "Et hop! Tout le travail du chasseur, deja mache.", ChatFormatting.GREEN);
    }

    /**
     * Corvee generale : un boss au hasard, entre 21 et 63 blocs. <b>Ecart</b> : les boss de DoHy
     * ne vivent que dans leur combat de faction ; ici un ravageur de 300 points de vie, qui lache
     * 16 lingots de paladium.
     */
    private static void drudgery(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos at = far(player, level, 21, 63);
        Mob boss = (Mob) spawn(level, EntityTypes.RAVAGER, Vec3.atBottomCenterOf(at));
        if (boss == null) {
            return;
        }
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0d);
        boss.setHealth(300.0f);
        boss.setCustomName(Component.literal("Boss de corvee").withStyle(ChatFormatting.DARK_RED));
        boss.setPersistenceRequired();
        boss.getPersistentData().putInt(LuckyCreatures.DROPS, 16);
        player.sendSystemMessage(Component.literal("(Prive) Un boss a spawne en " + at.getX() + " | " + at.getY()
                + " | " + at.getZ() + ".").withStyle(ChatFormatting.GREEN));
    }
}
