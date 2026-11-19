package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le lucky block de Paques ({@code luckyevents/easter} et {@code easter/rework}) : vingt
 * evenements, entre lapins, oeufs et poissons d'avril.
 *
 * <p>Ecarts : le lapin de Paques est un lapin du jeu qui saute de lui-meme ; « Ca fait
 * flipper » retourne la camera ; le poisson d'avril ne change pas le skin (il garde les
 * poissons d'argent) ; la peluche lapin de la pluie d'oeufs devient une patte de lapin.
 */
@EventBusSubscriber
public final class EasterEvents {

    private static final String CLOCHE = "dohy_truc_qui_cloche";
    private static final Map<UUID, Integer> EGGS = new HashMap<>();
    private static final Set<UUID> LAMBS = new HashSet<>();
    private static final Set<UUID> MEAN = new HashSet<>();

    private EasterEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.EASTER, "oeuf_dur", "Oeuf dur", "easter/oeuf_dur", true, 70,
                (player, level, pos) -> easterRabbit(player, level, SeasonItems.FAKE_CHOCOLATE_EGG.get().getDefaultInstance()));
        c.season(LuckyType.EASTER, "pluie_oeufs", "Pluie d'oeufs", "easter/pluie_oeufs", false, 70,
                EasterEvents::eggRain);
        c.season(LuckyType.EASTER, "agneau_pascal", "L'agneau pascal", "easter/agneau_pascal", false, 75,
                EasterEvents::pascal);
        c.season(LuckyType.EASTER, "fait_flipper", "Ca fait flipper", "easter/fait_flipper", true, 80,
                (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.FLIP, 1200, 0));
                    SeasonKit.timer(player, "Ton ecran est retourne", 60, p -> {
                    });
                });
        c.season(LuckyType.EASTER, "poisson_avril", "Poisson d'avril", "easter/poisson_avril", false, 80,
                (player, level, pos) -> ClassicContent.toss(player, new ItemStack(SeasonItems.APRIL_FISH.get())));
        c.season(LuckyType.EASTER, "tintamarre", "Tintamarre", "easter/tintamarre", true, 80, EasterEvents::bells);
        c.season(LuckyType.EASTER, "vrai_lapin", "Le vrai lapin de Paques", "easter/vrai_lapin", false, 80,
                (player, level, pos) -> easterRabbit(player, level, SeasonItems.CHOCOLATE_EGG.get().getDefaultInstance()));
        c.season(LuckyType.EASTER, "soft_like_a_lamb", "Doux comme un agneau", "easter/soft_like_a_lamb", false, 90,
                EasterEvents::softLikeALamb);
        c.season(LuckyType.EASTER, "poissons_avril", "Poissons d'avril", "easter/poisson_avril", true, 120,
                EasterEvents::fishes);
        c.season(LuckyType.EASTER, "april_chest", "Coffre d'avril", "easter/chest", false, 130,
                (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre d'avril", new int[]{7, 1, 9},
                        List.of(new ItemStack(SeasonItems.RAINBOW_LEATHER.get(), 3),
                                new ItemStack(SeasonItems.CHOCOLATE_EGG.get(), 10),
                                new ItemStack(SeasonItems.GOLDEN_EGG.get(), 3),
                                SeasonKit.stack("palamod:paladium_block", 3), new ItemStack(Items.GOLD_BLOCK, 5),
                                SeasonKit.stack("palamod:green_paladium_block", 3), LuckyType.PALADIUM.stack(3))));
        c.season(LuckyType.EASTER, "cadeau_pas_piege", "Cadeau pas piege", "easter/cadeau_pas_piege", false, 150,
                (player, level, pos) -> ClassicContent.toss(player, new ItemStack(SeasonItems.EASTER_GIFT.get())));
        c.season(LuckyType.EASTER, "cadeau_piege", "Cadeau piege", "easter/cadeau_piege", true, 150,
                (player, level, pos) -> ClassicContent.toss(player, new ItemStack(SeasonItems.FAKE_EASTER_GIFT.get())));
        c.season(LuckyType.EASTER, "chasse_oeuf", "Chasse aux oeufs", "easter/chasse_oeufs", false, 150,
                EasterEvents::eggHunt);
        c.season(LuckyType.EASTER, "lapin_garou", "Le lapin de Paques, enfin je crois...", "easter/lapin_garou", true,
                190, EasterEvents::killerBunny);
        c.season(LuckyType.EASTER, "paquotille", "Paquotille", "easter/paquotille", true, 200, EasterEvents::trinkets);
        c.season(LuckyType.EASTER, "poule_oeuf_or", "La poule aux oeufs d'or", "easter/poule_oeufs_or", false, 220,
                EasterEvents::goldenChicken);
        c.season(LuckyType.EASTER, "truc_qui_cloche", "Un truc qui cloche", "easter/truc_qui_cloche", true, 240,
                EasterEvents::somethingRings);
        c.season(LuckyType.EASTER, "hackerman", "Hackerman", "easter/hackerman", true, 350,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(SeasonEffects.HACKED, 1200, 0)));
        c.season(LuckyType.EASTER, "paquetole", "Paquetole", "easter/paquetole", false, 600, EasterEvents::yellowEggs);
        c.season(LuckyType.EASTER, "not_an_april_fool", "Ce n'est pas un poisson d'avril", "easter/not_an_april_fool",
                false, 700, (player, level, pos) -> {
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                            SeasonKit.stack("palamod:endium_fishing_rod", 1));
                    SeasonKit.say(player, "Tu as recu une canne a peche en endium !");
                });
    }

    // -- Lapins ---------------------------------------------------------------------------------

    /** Le lapin de Paques ({@code EntityEasterRabbit}) : quinze bonds, un oeuf a chacun. */
    private static void easterRabbit(ServerPlayer player, ServerLevel level, ItemStack egg) {
        Rabbit rabbit = SeasonKit.mob(level, EntityTypes.RABBIT, player.position().add(1.0d, 0.0d, 0.0d),
                "Lapin de Paques");
        if (rabbit == null) {
            return;
        }
        rabbit.setCustomName(Component.literal("Lapin de Paques").withStyle(ChatFormatting.AQUA));
        rabbit.setInvulnerable(true);
        RandomSource random = level.getRandom();
        LuckyTasks.repeat(player, 20, 20, 15, hop -> {
            if (!rabbit.isAlive()) {
                return;
            }
            double angle = random.nextInt(4) * Math.PI / 2.0d;
            rabbit.setDeltaMovement(Math.cos(angle) * 0.4d, 0.45d, Math.sin(angle) * 0.4d);
            rabbit.hurtMarked = true;
            LuckyWorld.drop(level, rabbit.getX(), rabbit.getY() + 0.3d, rabbit.getZ(), egg.copy());
            for (int color : new int[]{0xFF4040, 0xFFB040, 0xF2F24A, 0x40D060, 0x4A8CFF, 0xB060E0}) {
                level.sendParticles(new DustParticleOptions(color, 1.0f), rabbit.getX(), rabbit.getY() + 0.4d,
                        rabbit.getZ(), 2, 0.25d, 0.2d, 0.25d, 0.0d);
            }
            if (hop == 14) {
                level.sendParticles(ParticleTypes.POOF, rabbit.getX(), rabbit.getY() + 0.3d, rabbit.getZ(), 15, 0.3d,
                        0.3d, 0.3d, 0.02d);
                rabbit.discard();
            }
        });
    }

    /** Le lapin tueur : 80 points de vie, et il mord fort. */
    private static void killerBunny(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Rabbit rabbit = SeasonKit.mob(level, EntityTypes.RABBIT, player.position().add(1.5d, 0.0d, 0.0d), null);
        if (rabbit == null) {
            return;
        }
        rabbit.setComponent(DataComponents.RABBIT_VARIANT, Rabbit.Variant.EVIL);
        SeasonKit.stat(rabbit, Attributes.MAX_HEALTH, 80.0d);
        SeasonKit.stat(rabbit, Attributes.ATTACK_DAMAGE, 10.0d);
        rabbit.setTarget(player);
    }

    /** L'agneau Pascal : un agneau, rien de plus. */
    private static void pascal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var sheep = SeasonKit.mob(level, EntityTypes.SHEEP, player.position().add(1.0d, 0.0d, 0.0d), null);
        if (sheep != null) {
            sheep.setAge(-1200);
            sheep.setCustomName(Component.literal("Pascal").withStyle(ChatFormatting.AQUA));
            sheep.setCustomNameVisible(true);
        }
    }

    // -- Oeufs ----------------------------------------------------------------------------------

    /** Cinquante oeufs tombent du ciel en dix blocs autour, un par seconde ({@code EntityEasterEgg}). */
    private static void eggRain(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        LuckyTasks.repeat(player, 0, 20, 50, step -> {
            BlockPos at = SeasonKit.around(player, level, 0, 10);
            var egg = new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg(level,
                    at.getX() + 0.5d, at.getY() + 12.0d, at.getZ() + 0.5d, new ItemStack(Items.EGG));
            level.addFreshEntity(egg);
            if (random.nextInt(50) == 0) {
                LuckyWorld.drop(level, at, new ItemStack(Items.RABBIT_FOOT));
            }
        });
    }

    /** La chasse aux oeufs : dix oeufs bleus a trouver en une minute, trente a quarante blocs autour. */
    private static void eggHunt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<BlockPos> eggs = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            for (int tries = 0; tries < 20; tries++) {
                BlockPos at = SeasonKit.around(player, level, 30, 40);
                if (level.getBlockState(at).isAir() && LuckyWorld.place(player, level, at,
                        SeasonItems.BLUE_EASTER_EGG.get().defaultBlockState())) {
                    eggs.add(at);
                    break;
                }
            }
        }
        EGGS.put(player.getUUID(), 0);
        SeasonKit.say(player, "Clique sur les oeufs bleus pour obtenir des recompenses ! Il y en a " + eggs.size()
                + " cache(s) entre 30 et 40 blocs.");
        SeasonKit.challenge(player, "Chasse aux oeufs", 60, false,
                (p, age) -> EGGS.getOrDefault(p.getUUID(), 0) >= eggs.size() ? SeasonKit.Outcome.WON
                        : SeasonKit.Outcome.GOING, p -> {
                    EGGS.remove(p.getUUID());
                    SeasonKit.win(p, "Tu as trouve tous les oeufs !");
                    SeasonKit.give(p, SeasonKit.stack("palamod:paladium_block", 1));
                }, p -> {
                    int found = EGGS.getOrDefault(p.getUUID(), 0);
                    EGGS.remove(p.getUUID());
                    for (BlockPos egg : eggs) {
                        if (level.getBlockState(egg).is(SeasonItems.BLUE_EASTER_EGG.get())) {
                            level.removeBlock(egg, false);
                        }
                    }
                    SeasonKit.fail(p, "Temps ecoule : " + found + " oeuf(s) sur " + eggs.size() + ".");
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 0));
                });
    }

    /** Un oeuf bleu trouve : un lingot de paladium, et il compte pour la chasse. */
    static void blueEggFound(ServerPlayer player) {
        EGGS.computeIfPresent(player.getUUID(), (uuid, count) -> count + 1);
        SeasonKit.give(player, SeasonKit.stack("palamod:paladium_ingot", 1));
        Integer count = EGGS.get(player.getUUID());
        if (count != null) {
            LuckyWorld.bar(player, "Oeufs trouves : " + count, ChatFormatting.AQUA);
        }
    }

    /** Paquetole : dix oeufs jaunes caches dans les trente blocs ; chacun cache un tresor. */
    private static void yellowEggs(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int placed = 0;
        for (int index = 0; index < 10; index++) {
            for (int tries = 0; tries < 20; tries++) {
                BlockPos at = SeasonKit.around(player, level, 10, 30);
                if (level.getBlockState(at).isAir() && LuckyWorld.place(player, level, at,
                        SeasonItems.YELLOW_EASTER_EGG.get().defaultBlockState())) {
                    placed++;
                    break;
                }
            }
        }
        SeasonKit.say(player, placed + " oeufs jaunes sont caches autour de toi. Casse-les pour trouver leur tresor !");
    }

    /** Ce que cache un oeuf jaune ({@code getRandomReward}, sur 111). */
    static ItemStack yellowReward(RandomSource random) {
        int roll = random.nextInt(111);
        if (roll < 25) {
            return new ItemStack(Items.EGG, 4);
        } else if (roll < 50) {
            return new ItemStack(SeasonItems.CHOCOLATE_EGG.get());
        } else if (roll < 65) {
            return new ItemStack(Items.RABBIT_SPAWN_EGG);
        } else if (roll < 80) {
            return SeasonKit.stack("palamod:paladium_ingot", 1);
        } else if (roll < 90) {
            return SeasonKit.stack("palamod:paladium_block", 1);
        } else if (roll < 100) {
            return SeasonKit.stack("palamod:green_paladium_block", 1);
        }
        return new ItemStack(SeasonItems.EASTER_BUNNY_HELMET.get());
    }

    /** La poule aux oeufs d'or : trois oeufs d'or, un toutes les deux secondes, puis elle s'en va. */
    private static void goldenChicken(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var chicken = SeasonKit.mob(level, EntityTypes.CHICKEN, player.position().add(1.0d, 0.0d, 0.0d),
                "Poule aux oeufs d'or");
        if (chicken == null) {
            return;
        }
        chicken.setCustomName(Component.literal("Poule aux oeufs d'or").withStyle(ChatFormatting.GOLD));
        chicken.setInvulnerable(true);
        LuckyTasks.repeat(player, 40, 40, 3, step -> {
            if (!chicken.isAlive()) {
                return;
            }
            LuckyWorld.drop(level, chicken.getX(), chicken.getY() + 0.3d, chicken.getZ(),
                    new ItemStack(SeasonItems.GOLDEN_EGG.get()));
            level.sendParticles(new DustParticleOptions(0xD4AF37, 1.5f), chicken.getX(), chicken.getY() + 0.5d,
                    chicken.getZ(), 15, 0.3d, 0.3d, 0.3d, 0.0d);
            level.playSound(null, chicken.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1.0f, 1.0f);
            if (step == 2) {
                level.sendParticles(ParticleTypes.POOF, chicken.getX(), chicken.getY() + 0.3d, chicken.getZ(), 15,
                        0.3d, 0.3d, 0.3d, 0.02d);
                chicken.discard();
            }
        });
    }

    // -- Poissons -------------------------------------------------------------------------------

    /** Vingt creatures de la mer, sur la terre ferme, dans les dix blocs. */
    private static void fishes(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<EntityType<? extends Mob>> animals = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            animals.add(EntityTypes.SQUID);
            animals.add(EntityTypes.GLOW_SQUID);
            animals.add(EntityTypes.DOLPHIN);
            animals.add(EntityTypes.PUFFERFISH);
            animals.add(EntityTypes.COD);
        }
        Collections.shuffle(animals, new java.util.Random(level.getRandom().nextLong()));
        for (EntityType<? extends Mob> type : animals) {
            BlockPos at = SeasonKit.around(player, level, 1, 10);
            SeasonKit.mob(level, type, Vec3.atBottomCenterOf(at), null);
        }
    }

    // -- Cloches et ecrans ----------------------------------------------------------------------

    /** Deux minutes de cloches, toutes les cinq secondes. */
    private static void bells(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.timer(player, "Les cloches vont bientot se taire", 120, p -> {
        });
        LuckyTasks.repeat(player, 0, 100, 24, step -> level.playSound(null, player.blockPosition(),
                SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, 1.0f));
    }

    /** Un objet de l'inventaire, au hasard, sonne quand on le tient. */
    private static void somethingRings(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Inventory inventory = player.getInventory();
        List<Integer> filled = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                filled.add(slot);
            }
        }
        if (filled.isEmpty()) {
            return;
        }
        ItemStack stack = inventory.getItem(filled.get(level.getRandom().nextInt(filled.size())));
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(CLOCHE, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Un truc qui cloche"));
        SeasonKit.say(player, "Tu as de la chance... diiing !");
    }

    /** L'objet tenu sonne-t-il ? ({@link SeasonEffects}) */
    static boolean ringing(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(CLOCHE, false);
    }

    // -- Doux comme un agneau -------------------------------------------------------------------

    /** Trente minutes sans blesser personne : un colorant arc-en-ciel. */
    private static void softLikeALamb(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Sois doux comme un agneau et n'inflige pas de degats pendant 30 minutes.");
        LAMBS.add(player.getUUID());
        MEAN.remove(player.getUUID());
        SeasonKit.challenge(player, "Sois doux comme un agneau", 1800, true,
                (p, age) -> MEAN.contains(p.getUUID()) ? SeasonKit.Outcome.LOST : SeasonKit.Outcome.GOING, p -> {
                    LAMBS.remove(p.getUUID());
                    SeasonKit.win(p, "Tu as ete doux comme un agneau ! Voici un colorant special, essaie-le sur un"
                            + " mouton.");
                    LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), new ItemStack(SeasonItems.RAINBOW_COLORING.get()));
                }, p -> {
                    LAMBS.remove(p.getUUID());
                    MEAN.remove(p.getUUID());
                    SeasonKit.fail(p, "Tu as ete mechant, tu as inflige des degats a un joueur ou une creature.");
                });
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer player && LAMBS.contains(player.getUUID()) && event.getEntity() != player) {
            MEAN.add(player.getUUID());
        }
    }

    // -- Paquotille -----------------------------------------------------------------------------

    /** Quatre outils en paladium... au bout du rouleau. */
    private static void trinkets(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (String id : new String[]{"palamod:paladium_axe", "palamod:paladium_pickaxe", "palamod:paladium_shovel",
                "palamod:paladium_hammer"}) {
            ItemStack tool = SeasonKit.stack(id, 1);
            if (tool.isDamageableItem()) {
                tool.setDamageValue(tool.getMaxDamage() - 1);
            }
            ClassicContent.toss(player, tool);
        }
    }

    // -- Trou noir du faux cadeau ---------------------------------------------------------------

    /** Le trou noir du faux cadeau ({@code EntityBlackHole}) : celui de mars, a la place du cadeau. */
    static void blackHole(ServerPlayer player, ServerLevel level, Vec3 at) {
        ArmorStand hole = LuckyCreatures.marker(level, at.x, at.y, at.z);
        if (hole == null) {
            return;
        }
        hole.setCustomNameVisible(false);
        level.addFreshEntity(hole);
        Vec3 center = at.add(0.0d, 1.0d, 0.0d);
        LuckyTasks.repeat(player, 0, 1, 100, tick -> {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 12, 0.5d, 0.5d, 0.5d, 0.05d);
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 4, 0.3d, 0.3d, 0.3d, 0.0d);
            Vec3 pull = center.subtract(player.position());
            if (pull.length() < 20.0d && pull.length() > 0.5d) {
                player.setDeltaMovement(player.getDeltaMovement().add(pull.normalize().scale(0.15d)));
                player.hurtMarked = true;
            }
            if (tick >= 10 && tick % 25 == 0) {
                Inventory inventory = player.getInventory();
                int a = level.getRandom().nextInt(36);
                int b = level.getRandom().nextInt(36);
                ItemStack first = inventory.getItem(a);
                inventory.setItem(a, inventory.getItem(b));
                inventory.setItem(b, first);
            }
            if (tick == 99) {
                hole.discard();
            }
        });
    }
}
