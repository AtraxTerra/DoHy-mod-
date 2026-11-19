package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.boost.PalaXpPotions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Le lucky block de mars ({@code monthly/modules/march}) : l'espace. Quinze evenements.
 *
 * <p>Ecarts : les ecrans de PalaMod (le voeu de l'etoile filante, le test du systeme
 * solaire, la calibration du satellite, les messages a decoder) deviennent des dialogues
 * dans le chat ; les sons enregistres de « Bruit Spatial » deviennent des mots brouilles ;
 * la lune de « Tu es dans la lune » est construite ici (le fichier de structure de PalaMod
 * n'est pas dans le jar).
 */
final class MarchEvents {

    private static final LuckyType MARCH = LuckyType.MARCH;

    private MarchEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.MARCH, "asteroid_rain", "Pluie d'asteroides", "march/asteroid_rain", true, 90,
                MarchEvents::asteroidRain);
        c.season(LuckyType.MARCH, "head_in_the_stars", "La tete dans les etoiles", "march/head_in_stars", false, 90,
                MarchEvents::headInStars);
        c.season(LuckyType.MARCH, "little_green_men", "Les petits hommes verts", "march/little_green_men", false, 90,
                MarchEvents::littleGreenMen);
        c.season(LuckyType.MARCH, "black_hole", "Trou noir", "march/black_hole", true, 110, MarchEvents::blackHole);
        c.season(LuckyType.MARCH, "no_oxygen", "Plus d'oxygene", "march/no_oxygen", true, 110, MarchEvents::noOxygen);
        c.season(LuckyType.MARCH, "shooting_star", "Etoile filante", "march/shooting_star", false, 120,
                MarchEvents::shootingStar);
        c.season(LuckyType.MARCH, "vital_space", "L'espace vital", "march/vital_space", false, 120,
                MarchEvents::vitalSpace);
        c.season(LuckyType.MARCH, "astronaut_training", "Entrainement d'astronaute", "march/astronaut_training",
                false, 130, (player, level, pos) -> training(player, 0));
        c.season(LuckyType.MARCH, "encounter_third_kind", "Rencontre du troisieme type",
                "march/encounter_third_kind", false, 130, MarchEvents::alien);
        c.season(LuckyType.MARCH, "in_the_moon", "Tu es dans la lune", "march/in_the_moon", false, 130,
                MarchEvents::inTheMoon);
        c.season(LuckyType.MARCH, "march_chest", "Coffre de Mars", "march/chest", false, 140,
                (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre de Mars", new int[]{3, 7, 8},
                        List.of(new ItemStack(SeasonItems.MOON_PIECE.get()),
                                new ItemStack(SeasonItems.GALACTIC_PILE.get(), 2),
                                SeasonKit.stack("palamod:paladium_block", 4), SeasonKit.stack("palamod:titane_block", 20),
                                SeasonKit.stack("palamod:green_paladium_block", 1),
                                new ItemStack(SeasonItems.SPLASH_POTION_JUPITER_GRAVITY.get()),
                                LuckyType.PALADIUM.stack(3))));
        c.season(LuckyType.MARCH, "beyond_the_stars", "Par dela les etoiles", "march/beyond_the_stars", false, 220,
                MarchEvents::beyondTheStars);
        c.season(LuckyType.MARCH, "spatial_noise", "Bruit Spatial", "march/spatial_noise", false, 300,
                (player, level, pos) -> spatialNoise(player, 0));
        c.season(LuckyType.MARCH, "well_calibrated", "Bien calibre", "march/well_calibrated", false, 300,
                MarchEvents::wellCalibrated);
        c.season(LuckyType.MARCH, "like_a_rocket", "Comme une fusee", "march/like_a_rocket", false, 700,
                MarchEvents::likeARocket);
    }

    // -- Pluie d'asteroides ---------------------------------------------------------------------

    /** Au plus haut point de la colonne, puis quinze boules de feu, une toutes les deux secondes. */
    private static void asteroidRain(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos top = SeasonKit.ground(level, player.getBlockX(), player.getBlockZ());
        player.teleportTo(top.getX() + 0.5d, top.getY(), top.getZ() + 0.5d);
        SeasonKit.say(player, "Vous avez ete teleporte au point le plus haut");
        LuckyTasks.repeat(player, 40, 40, 15, step -> {
            RandomSource random = level.getRandom();
            double x = player.getX() + (random.nextDouble() * 6.0d - 3.0d);
            double z = player.getZ() + (random.nextDouble() * 6.0d - 3.0d);
            var fireball = LuckyWorld.create(level, EntityTypes.FIREBALL, x, player.getY() + 10.0d, z);
            if (fireball != null) {
                fireball.setDeltaMovement(0.0d, -1.0d, 0.0d);
                level.addFreshEntity(fireball);
            }
        });
    }

    // -- La tete dans les etoiles ---------------------------------------------------------------

    private static void headInStars(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.addEffect(new MobEffectInstance(SeasonEffects.STAR, 2400, 0));
        SeasonKit.say(player, "Vous avez la tete dans les etoiles !");
        SeasonKit.say(player, "Ne retirez pas cette potion, vous pourriez le regretter !");
        LuckyTasks.later(player, 2395, () -> {
            if (player.hasEffect(SeasonEffects.STAR)) {
                player.removeEffect(SeasonEffects.STAR);
                SeasonKit.say(player, "Vous etes revenu sur Terre !");
                SeasonKit.give(player, new ItemStack(SeasonItems.STAR_HELMET.get()));
            }
        });
    }

    // -- Les petits hommes verts ----------------------------------------------------------------

    /** Vingt mini-zombies astronautes ; chacun lache peut-etre un tresor ({@code RandomObjectPicker}). */
    private static void littleGreenMen(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        int spawned = 0;
        for (int tries = 0; tries < 50 && spawned < 20; tries++) {
            BlockPos at = BlockPos.containing(player.getX() + random.nextInt(7) - 3, player.getY() + random.nextInt(3),
                    player.getZ() + random.nextInt(7) - 3);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            Zombie zombie = SeasonKit.mob(level, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(at), "Zombie Astronaute");
            if (zombie == null) {
                continue;
            }
            zombie.setBaby(true);
            zombie.setCustomName(net.minecraft.network.chat.Component.literal("Zombie Astronaute")
                    .withStyle(ChatFormatting.AQUA));
            ItemStack drop = astronautDrop(random);
            if (!drop.isEmpty()) {
                SeasonKit.loot(zombie, drop);
            }
            spawned++;
        }
        LuckyWorld.tell(player, spawned + " mini-zombies ont ete invoques autour de vous !", ChatFormatting.YELLOW);
    }

    private static ItemStack astronautDrop(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 10) {
            return new ItemStack(SeasonItems.GALACTIC_PILE.get());
        } else if (roll < 30) {
            return SeasonKit.stack("palamod:green_paladium_ingot", 1);
        } else if (roll < 33) {
            return SeasonKit.stack("palamod:green_paladium_block", 1);
        } else if (roll < 65) {
            return new ItemStack(Items.SLIME_BALL);
        } else if (roll < 80) {
            return SeasonKit.stack("palamod:travel_slimyhelmet", 1);
        }
        return ItemStack.EMPTY;
    }

    // -- Trou noir ------------------------------------------------------------------------------

    /** Cinq secondes : il aspire le joueur et lui melange l'inventaire. */
    private static void blackHole(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        Vec3 at = player.position().add(look.scale(5.0d));
        if (!level.getBlockState(BlockPos.containing(at)).isAir()
                || !level.getBlockState(BlockPos.containing(at).above()).isAir()) {
            at = player.position();
        }
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
                RandomSource random = level.getRandom();
                int a = random.nextInt(36);
                int b = random.nextInt(36);
                ItemStack first = inventory.getItem(a);
                inventory.setItem(a, inventory.getItem(b));
                inventory.setItem(b, first);
            }
            if (tick == 99) {
                hole.discard();
            }
        });
        level.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    // -- Plus d'oxygene -------------------------------------------------------------------------

    private static void noOxygen(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.addEffect(new MobEffectInstance(SeasonEffects.NO_OXYGEN, 600, 0));
        SeasonKit.give(player, new ItemStack(Blocks.GLOWSTONE), new ItemStack(Items.IRON_INGOT, 4));
        SeasonKit.say(player, "Cree un casque des etoiles et porte-le pour ne plus avoir besoin d'oxygene !");
        SeasonKit.timer(player, "Plus d'oxygene", 30, p -> {
        });
    }

    // -- Etoile filante -------------------------------------------------------------------------

    private static void shootingStar(ServerPlayer player, ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 12.0d, player.getZ(), 40, 6.0d, 0.5d,
                6.0d, 0.2d);
        SeasonKit.ask(player, "Etoile filante", "Tu as vu une etoile filante ! Quel voeu souhaites-tu faire ?",
                List.of("Je souhaite obtenir plus d'experience", "Je souhaite obtenir plus de puissance",
                        "Je souhaite obtenir plus de richesse", "Je souhaite prendre plus de temps libre"),
                (p, wish) -> {
                    switch (wish) {
                        case 0 -> {
                            Job job = Job.values()[p.getRandom().nextInt(Job.values().length)];
                            JobApi.grant(p, job, 5000L);
                            SeasonKit.say(p, "Tu viens de gagner 5000 XP en " + job.display() + ".");
                        }
                        case 1 -> {
                            p.giveExperienceLevels(10);
                            SeasonKit.say(p, "Tu viens d'obtenir 10 niveaux d'experience.");
                        }
                        case 2 -> {
                            Economy.credit(p, 7500L);
                            SeasonKit.say(p, "Tu viens de recevoir 7500 $.");
                        }
                        default -> {
                            SeasonKit.give(p, PalaXpPotions.stack(2, 30));
                            SeasonKit.say(p, "Tu viens de recevoir une potion d'XP de metier.");
                        }
                    }
                });
    }

    // -- L'espace vital -------------------------------------------------------------------------

    /** Cinq minutes sans laisser un autre joueur approcher a moins de cinq blocs. */
    private static void vitalSpace(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Un espace vital a ete cree autour de vous.");
        SeasonKit.say(player, "Si vous ne contaminez pas cet espace, vous serez recompense.");
        SeasonKit.say(player, "L'espace vital est actif pendant 5 minutes.");
        int[] contaminated = {0};
        SeasonKit.challenge(player, "Evite les autres joueurs a tout prix !", 300, true, (p, age) -> {
            if (age % 20 == 0) {
                for (ServerPlayer other : p.level().getEntitiesOfClass(ServerPlayer.class,
                        p.getBoundingBox().inflate(5.0d), other -> other != p)) {
                    if (!other.hasEffect(MobEffects.SLOWNESS)) {
                        other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200, 1));
                        LuckyWorld.tell(other, "Tu as ete ralenti par le joueur " + p.getName().getString() + ".",
                                ChatFormatting.RED);
                        contaminated[0]++;
                    }
                }
            }
            return SeasonKit.Outcome.GOING;
        }, p -> {
            if (contaminated[0] == 0) {
                SeasonKit.win(p, "Felicitations, tu as reussi a ne pas contaminer l'espace vital.");
                SeasonKit.give(p, new ItemStack(SeasonItems.SURVIVING_STONE.get()),
                        SeasonKit.stack("palamod:heal_stick", 1));
            } else {
                SeasonKit.fail(p, "Tu as contamine l'espace vital (" + contaminated[0] + " joueur(s) ralenti(s)).");
            }
        }, p -> {
        });
    }

    // -- Entrainement d'astronaute --------------------------------------------------------------

    private static final String[] PLANETS = {"martienne", "lunaire", "de Jupiter"};

    /**
     * Trois etapes de trente secondes : dix sauts sous la gravite de Mars, dix sous celle de la
     * lune, puis tenir sous celle de Jupiter, ou l'on ne saute plus. Les potions de gravite
     * recompensent l'effort, meme rate.
     */
    private static void training(ServerPlayer player, int stage) {
        if (stage == 0) {
            SeasonKit.say(player, "Ton entrainement va commencer...");
        }
        SeasonKit.say(player, "Entraine-toi a resister a la gravite " + PLANETS[stage]
                + (stage < 2 ? " en sautant 10 fois pour obtenir une recompense !" : "..."));
        switch (stage) {
            case 0 -> player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 600, 0));
            case 1 -> player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 600, 2));
            default -> player.addEffect(new MobEffectInstance(SeasonEffects.JUPITER_GRAVITY, 600, 0));
        }
        int start = player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
        int[] last = {0};
        SeasonKit.challenge(player, "Entrainement d'astronaute", 30, stage == 2, (p, age) -> {
            if (stage == 2) {
                return SeasonKit.Outcome.GOING;
            }
            int jumps = p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) - start;
            if (jumps != last[0]) {
                last[0] = jumps;
                LuckyWorld.bar(p, "Sauts confirmes : " + Math.min(jumps, 10) + "/10", ChatFormatting.YELLOW);
            }
            return jumps >= 10 ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
        }, p -> {
            p.removeEffect(MobEffects.JUMP_BOOST);
            p.removeEffect(SeasonEffects.JUPITER_GRAVITY);
            if (stage < 2) {
                SeasonKit.win(p, "Tu as reussi la " + (stage == 0 ? "premiere" : "seconde") + " etape de l'entrainement.");
                training(p, stage + 1);
            } else {
                SeasonKit.win(p, "Tu as reussi la derniere etape de l'entrainement.");
                gravityPotions(p, 3);
            }
        }, p -> {
            p.removeEffect(MobEffects.JUMP_BOOST);
            p.removeEffect(SeasonEffects.JUPITER_GRAVITY);
            SeasonKit.fail(p, "Tu n'as pas reussi l'entrainement.");
            gravityPotions(p, Math.max(1, stage));
        });
    }

    private static void gravityPotions(ServerPlayer player, int count) {
        List<ItemStack> potions = new ArrayList<>(List.of(new ItemStack(SeasonItems.SPLASH_POTION_MARS_GRAVITY.get()),
                new ItemStack(SeasonItems.SPLASH_POTION_MOON_GRAVITY.get()),
                new ItemStack(SeasonItems.SPLASH_POTION_JUPITER_GRAVITY.get())));
        for (int index = 0; index < count; index++) {
            SeasonKit.give(player, potions.get(player.getRandom().nextInt(potions.size())));
        }
    }

    // -- Rencontre du troisieme type ------------------------------------------------------------

    private static final String SEEN_ALIEN = "dohy_seen_alien";

    /** Un alien parle trois langues etranges ; il faut le comprendre sans l'effrayer. */
    private static void alien(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2, 3));
        SeasonKit.npc(player, level, EntityTypes.ENDERMAN, at, "Alien", 180, (p, alien) -> {
            if (p.getTags().contains(SEEN_ALIEN)) {
                SeasonKit.next(p, "Alien", "Content de te retrouver ! Mon espece est fan de la tienne ! Voici un cadeau"
                        + " qu'un de mes amis m'a transmis pour les humains.", "Recevoir le cadeau", q -> {
                    SeasonKit.give(q, new ItemStack(LuckyGadgets.COLORED_LAMP.get()));
                    SeasonKit.dismiss(alien);
                });
                return;
            }
            SeasonKit.ask(p, "Alien", ". e 3 8 H7 SH S UF G", List.of("Bonjour !", "Aaaah ! Un monstre !"),
                    (q, first) -> {
                        if (first != 0) {
                            scared(q, alien);
                            return;
                        }
                        q.addTag(SEEN_ALIEN);
                        SeasonKit.ask(q, "Alien", "01000001 01101101 01101001", List.of("Ennemi ?", "Ami ?"),
                                (r, second) -> {
                                    if (second != 1) {
                                        scared(r, alien);
                                        return;
                                    }
                                    SeasonKit.ask(r, "Alien", "-.. .. - . ...   -- --- ..   ..- -. .   .--. .... .-. .- ..."
                                            + " .   .-.. --- -. --. ..- .", List.of("Bien sur",
                                            "Bonjour, comment allez-vous aujourd'hui ?"), (s, third) -> {
                                        if (third != 1) {
                                            scared(s, alien);
                                            return;
                                        }
                                        SeasonKit.next(s, "Alien", "Merci ! Grace a ton aide j'ai reussi a decoder ton"
                                                + " langage. Voici une recompense intergalactique.",
                                                "Recevoir la recompense", t -> {
                                                    SeasonKit.give(t, new ItemStack(LuckyGadgets.WIRELESS_LEVER_ITEM.get()),
                                                            SeasonKit.stack("palamod:paladium_block", 1));
                                                    SeasonKit.dismiss(alien);
                                                });
                                    });
                                });
                    });
        });
        SeasonKit.say(player, "Un etre etrange est apparu... Fais un clic droit sur lui.");
    }

    private static void scared(ServerPlayer player, net.minecraft.world.entity.Entity alien) {
        SeasonKit.fail(player, "Tu as fait peur a l'alien et il a fui !");
        SeasonKit.dismiss(alien);
    }

    // -- Tu es dans la lune ---------------------------------------------------------------------

    /** Une lune au-dessus du joueur, a la couche 250, un coffre au milieu ; trois secondes de noir avant. */
    private static void inTheMoon(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int y = Math.min(250, level.getMaxY() - 8);
        BlockPos center = new BlockPos(player.getBlockX(), y, player.getBlockZ());
        RandomSource random = level.getRandom();
        int placed = 0;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-7, -4, -7), center.offset(7, 0, 7))) {
            double dx = at.getX() - center.getX();
            double dz = at.getZ() - center.getZ();
            double dy = (at.getY() - center.getY()) * 1.8d;
            if (dx * dx + dy * dy + dz * dz > 49.0d) {
                continue;
            }
            BlockState state = random.nextInt(6) == 0 ? Blocks.GRAY_CONCRETE.defaultBlockState()
                    : random.nextInt(3) == 0 ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()
                    : Blocks.WHITE_CONCRETE.defaultBlockState();
            if (level.getBlockState(at).isAir() && LuckyWorld.place(player, level, at.immutable(), state)) {
                placed++;
            }
        }
        // Les cratere : quelques creux en surface.
        for (int crater = 0; crater < 4; crater++) {
            BlockPos hole = center.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            if (!hole.equals(center) && level.getBlockState(hole).is(Blocks.WHITE_CONCRETE)) {
                level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (placed < 40) {
            SeasonKit.fail(player, "Il n'a pas ete possible de poser la lune ici.");
            return;
        }
        SeasonKit.chest(player, level, center.above(), List.of(new ItemStack(SeasonItems.MOON_PIECE.get(), 3),
                new ItemStack(Items.WATER_BUCKET)));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false));
        LuckyTasks.later(player, 60, () -> {
            player.teleportTo(center.getX() + 1.5d, center.getY() + 1.0d, center.getZ() + 0.5d);
            SeasonKit.say(player, "Tu as ete teleporte dans la lune !");
        });
    }

    // -- Par dela les etoiles -------------------------------------------------------------------

    private static void beyondTheStars(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos at = level.getBlockState(pos).canBeReplaced() ? pos : player.blockPosition();
        if (LuckyWorld.place(player, level, at, SeasonItems.TELESCOPE.get().defaultBlockState())) {
            SeasonKit.win(player, "Un telescope est apparu a tes pieds ! Regarde dedans (clic droit).");
        } else {
            SeasonKit.give(player, new ItemStack(SeasonItems.TELESCOPE.get()));
        }
    }

    /** Le test du telescope ({@code SolarTestUI}) : trois questions, et le telescope s'en va. */
    static void solarTest(ServerPlayer player, ServerLevel level, BlockPos telescope) {
        List<String> orders = new ArrayList<>(List.of("Mercure, Venus, Terre, Mars, Jupiter, Saturne",
                "Venus, Mercure, Terre, Mars, Saturne, Jupiter", "Mercure, Terre, Venus, Jupiter, Mars, Saturne"));
        Collections.shuffle(orders, new java.util.Random(player.getRandom().nextLong()));
        int right = orders.indexOf("Mercure, Venus, Terre, Mars, Jupiter, Saturne");
        SeasonKit.ask(player, "Telescope", "Dans quel ordre les planetes tournent-elles autour du Soleil, de la plus"
                + " proche a la plus lointaine ?", orders, (p, first) -> {
            if (first != right) {
                solarFail(p, level, telescope);
                return;
            }
            SeasonKit.ask(p, "Telescope", "Quelle est la plus grosse planete du systeme solaire ?",
                    List.of("Saturne", "Jupiter", "La Terre"), (q, second) -> {
                        if (second != 1) {
                            solarFail(q, level, telescope);
                            return;
                        }
                        SeasonKit.ask(q, "Telescope", "Qu'y a-t-il entre Mars et Jupiter ?",
                                List.of("La ceinture d'asteroides", "La station spatiale", "La lune"), (r, third) -> {
                                    if (third != 0) {
                                        solarFail(r, level, telescope);
                                        return;
                                    }
                                    removeTelescope(level, telescope);
                                    SeasonKit.win(r, "Tu as reussi le test et tu obtiens 1 amulette de feu.");
                                    SeasonKit.give(r, SeasonKit.stack("palamod:magma_amulet", 1));
                                });
                    });
        });
    }

    private static void solarFail(ServerPlayer player, ServerLevel level, BlockPos telescope) {
        removeTelescope(level, telescope);
        SeasonKit.fail(player, "Tu n'as pas reussi le test.");
    }

    private static void removeTelescope(ServerLevel level, BlockPos telescope) {
        if (level.getBlockState(telescope).is(SeasonItems.TELESCOPE.get())) {
            level.removeBlock(telescope, false);
            level.sendParticles(ParticleTypes.POOF, telescope.getX() + 0.5d, telescope.getY() + 0.5d,
                    telescope.getZ() + 0.5d, 15, 0.3d, 0.3d, 0.3d, 0.02d);
        }
    }

    // -- Bruit Spatial --------------------------------------------------------------------------

    private static final String[] NOISES = {"Pe... p..s", "...r l'h..me, m..s un gr..d", "p.s p..r l'h..an..e"};
    private static final List<List<String>> HEARD = List.of(List.of("Petit pois", "Petit pas", "E.T. pas"),
            List.of("Pomme rend", "Homme cran", "Homme grand"),
            List.of("Poids homme allite", "Chat humanite", "Pas humanite"));
    private static final int[] HEARD_RIGHT = {1, 2, 2};

    /** Trois messages brouilles venus de l'espace : il faut deviner ce qu'on a entendu. */
    private static void spatialNoise(ServerPlayer player, int step) {
        if (step == 0) {
            SeasonKit.ask(player, "Bruit Spatial", "Un message radio arrive, tres brouille...",
                    List.of("Commencer le decryptage"), (p, start) -> spatialNoise(p, 1));
            return;
        }
        LuckyWorld.sound((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), "block.note_block.bit",
                SoundSource.PLAYERS, 1.0f, 0.6f + step * 0.2f);
        SeasonKit.ask(player, "Bruit Spatial", "Quel son as-tu entendu ? \"" + NOISES[step - 1] + "\"",
                HEARD.get(step - 1), (p, answer) -> {
                    if (answer != HEARD_RIGHT[step - 1]) {
                        SeasonKit.fail(p, "Tu n'as pas reussi a decoder le message, mission echouee.");
                    } else if (step == 3) {
                        SeasonKit.win(p, "Felicitations, tu as decrypte tous les messages !");
                    } else {
                        spatialNoise(p, step + 1);
                    }
                });
    }

    // -- Bien calibre ---------------------------------------------------------------------------

    /** La PSA demande de regler l'antenne de son satellite : trois reglages, vingt secondes. */
    private static void wellCalibrated(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.ask(player, "PSA", "La PSA (Paladium Space Agency) te demande de l'aide pour calibrer son satellite.",
                List.of("Calibrer le satellite"), (p, start) -> calibrate(p, 0));
    }

    private static void calibrate(ServerPlayer player, int round) {
        if (round == 3) {
            SeasonKit.win(player, "La PSA te remercie pour tes services. Tu obtiens un unclaim finder orange.");
            SeasonKit.give(player, SeasonKit.stack("palamod:unclaim_finder_orange", 1));
            return;
        }
        int[] angles = {45, 90, 135, 180, 225, 270};
        int target = angles[player.getRandom().nextInt(angles.length)];
        List<String> options = new ArrayList<>();
        for (int angle : angles) {
            options.add(angle + " degres");
        }
        SeasonKit.ask(player, "Satellite", "Reglage " + (round + 1) + "/3 : le signal est le plus fort a " + target
                + " degres. Oriente l'antenne.", options, (p, answer) -> {
            if (angles[answer] != target) {
                SeasonKit.fail(p, "Tu n'as pas reussi a calibrer le satellite.");
            } else {
                calibrate(p, round + 1);
            }
        });
    }

    // -- Comme une fusee ------------------------------------------------------------------------

    private static void likeARocket(ServerPlayer player, ServerLevel level, BlockPos pos) {
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f,
                0.5f);
        SeasonKit.say(player, "3... 2... 1... Decollage !");
        LuckyTasks.later(player, 100, () -> {
            ItemStack rocket = new ItemStack(SeasonItems.SUPERSONIC_ROCKET.get());
            LuckyWorld.drop(level, player.getX(), player.getY() + 0.5d, player.getZ(), rocket);
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.0d, player.getZ(), 30, 0.5d,
                    0.5d, 0.5d, 0.1d);
        });
    }
}
