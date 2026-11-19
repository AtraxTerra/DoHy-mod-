package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le lucky block de juillet ({@code monthly/modules/july}) : les pirates. Vingt evenements.
 *
 * <p>Ecarts : PalaMod envoyait le joueur sur le serveur Event (l'aquarium, le rafiot, l'ile du
 * roi des pirates) ; ici tout se construit autour du joueur, et la carte au tresor mene a un coffre
 * enterre dans son monde. « Connaitre les oceans » se gagne a la premiere gorgee d'eau de trop,
 * sans mourir. Les repliques de pirates sont reecrites.
 */
final class JulyEvents {

    private static final String TREASURE = "dohy_treasure";

    private JulyEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.JULY, "powder_talk", "Faire parler la poudre", "july/powder_talk", false, 200,
                JulyEvents::powderTalk);
        c.season(LuckyType.JULY, "bottle_sea", "Une bouteille a la mer", "july/bottle_sea", false, 250,
                (player, level, pos) -> {
                    ClassicContent.toss(player, new ItemStack(SeasonItems.SEA_BOTTLE.get()));
                    SeasonKit.say(player, "Tu viens de trouver une bouteille a la mer ! Regarde donc ce qu'elle"
                            + " contient !");
                });
        c.season(LuckyType.JULY, "fresh_water_sailor", "Tu n'es qu'un marin d'eau douce", "july/fresh_water_sailor",
                true, 250, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.NO_BOAT, 2400, 0));
                    if (player.getVehicle() != null) {
                        player.stopRiding();
                    }
                    SeasonKit.timer(player, "Tu ne peux plus utiliser de bateau", 120, p -> {
                    });
                });
        c.season(LuckyType.JULY, "land_in_sight", "Terre en vue ?", "july/land_in_sight", false, 250,
                JulyEvents::raft);
        c.season(LuckyType.JULY, "parrot", "Perroquet", "july/parrot", false, 250, (player, level, pos) -> {
            flint(player, level, 620);
            LuckyTasks.later(player, 620, () -> SeasonKit.give(player, new ItemStack(SeasonItems.PARROT_SEEDS.get())));
        });
        c.season(LuckyType.JULY, "eye_patch", "Cache-oeil", "july/eye_patch", true, 300, (player, level, pos) -> {
            player.addEffect(new MobEffectInstance(SeasonEffects.EYE_PATCH, 2400, 0));
            SeasonKit.say(player, "Tu as un cache-oeil pendant 2 minutes !");
            LuckyTasks.later(player, 2400, () -> SeasonKit.give(player, new ItemStack(SeasonItems.EYE_PATCH.get())));
        });
        c.season(LuckyType.JULY, "good_sailor", "Connaitre les oceans en profondeur", "july/good_sailor", false, 300,
                JulyEvents::goodSailor);
        c.season(LuckyType.JULY, "hacker", "Pirate informatique", "july/hacker", false, 300, (player, level, pos) -> {
            ClassicContent.toss(player, new ItemStack(SeasonItems.ENCRYPTED_COMPUTER.get()));
            SeasonKit.say(player, "Tu as recu un ordinateur crypte !");
        });
        c.season(LuckyType.JULY, "pipe_and_wooden_leg", "Pipe et jambe de bois", "july/pipe_and_wooden_leg", false, 300,
                JulyEvents::shanty);
        c.season(LuckyType.JULY, "scorbut", "Scorbut", "july/scorbut", true, 300, JulyEvents::scurvy);
        c.season(LuckyType.JULY, "talk_like_pirate", "Parler comme un pirate, ce n'est pas fait pour tout le monde !",
                "july/talk_like_pirate", true, 300, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.PIRATE_TALK, 12000, 0));
                    SeasonKit.say(player, "Tu as ete maudit par un pirate, essaie donc d'ecrire un message dans le chat !");
                    LuckyTasks.later(player, 12000, () -> SeasonKit.win(player, "Tu as ete gueri de ta malediction !"));
                });
        c.season(LuckyType.JULY, "feed_the_fish", "Tu vas nourrir les poissons !", "july/feed_the_fish", true, 350,
                JulyEvents::aquarium);
        c.season(LuckyType.JULY, "music_party", "La fete de la musique, c'est tous les jours avec les pirates !",
                "july/music_party", false, 350, JulyEvents::musicParty);
        c.season(LuckyType.JULY, "vigilance_duty", "Devoir de vigie", "july/vigilance_duty", false, 350,
                JulyEvents::lookout);
        c.season(LuckyType.JULY, "no_powder_talk", "Faire parler la poudre ?", "july/powder_talk_no", false, 400,
                (player, level, pos) -> {
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.BARREL, 64));
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.BARREL, 36));
                });
        c.season(LuckyType.JULY, "treasure_chest_july", "Coffre (au tresor) de juillet", "july/treasure_chest_july",
                false, 450, (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre (au tresor) de juillet",
                        CODE, List.of(SeasonKit.stack("palamod:titane_block", 8),
                                SeasonKit.stack("palamod:paladium_block", 1), new ItemStack(Items.GOLD_BLOCK, 15),
                                SeasonKit.stack("palamod:green_paladium_block", 1))));
        c.season(LuckyType.JULY, "felon", "Felon !", "july/felon", true, 500, (player, level, pos) -> {
            if (Economy.tryDebit(player, 2500L)) {
                SeasonKit.fail(player, "J'en avais besoin pour piller le bastion de Salazar ! (2500 $)");
            } else {
                SeasonKit.win(player, "Tu as eu de la chance, ton argent n'a pas ete vole !");
            }
        });
        c.season(LuckyType.JULY, "pirate_king_treasure", "Le tresor du roi des pirates", "july/pirate_king_treasure",
                false, 500, JulyEvents::pirateKing);
        c.season(LuckyType.JULY, "phantom_boat", "Bateau fantome", "july/phamtom_boat", false, 650,
                JulyEvents::phantomBoat);
        c.season(LuckyType.JULY, "red_cross", "La croix rouge", "july/red_cross", false, 1550, JulyEvents::redCross);
    }

    /** Le code du coffre de juillet, que l'ordinateur decrypte revele. */
    private static final int[] CODE = {4, 1, 7};

    // -- La poudre ------------------------------------------------------------------------------

    /** Quatre barils de poudre autour du joueur sautent ; il en garde un, et cent barils vides. */
    private static void powderTalk(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<BlockPos> barrels = new ArrayList<>();
        BlockState barrel = SeasonItems.POWDER_BARREL.get().defaultBlockState()
                .setValue(SeasonItems.PowderBarrelBlock.POWDER, 16);
        for (BlockPos at : new BlockPos[]{player.blockPosition().offset(3, 0, 0), player.blockPosition().offset(-3, 0, 0),
                player.blockPosition().offset(0, 0, 3), player.blockPosition().offset(0, 0, -3)}) {
            if (level.getBlockState(at).canBeReplaced() && LuckyWorld.place(player, level, at, barrel)) {
                barrels.add(at);
            }
        }
        LuckyWorld.tell(player, "[!] Tu as fait parler la poudre ! Attention a l'explosion !", ChatFormatting.YELLOW);
        LuckyTasks.later(player, 60, () -> {
            for (BlockPos at : barrels) {
                if (level.getBlockState(at).is(SeasonItems.POWDER_BARREL.get())) {
                    level.removeBlock(at, false);
                    level.explode(null, at.getX() + 0.5d, at.getY() + 0.5d, at.getZ() + 0.5d, 2.0f,
                            Level.ExplosionInteraction.NONE);
                }
            }
            LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.BARREL, 64));
            LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.BARREL, 36));
            LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                    new ItemStack(SeasonItems.POWDER_BARREL.get()));
        });
    }

    // -- Messages -------------------------------------------------------------------------------

    /** Le message seche, rendu lisible au four ({@code ItemPirateKingMessage}). */
    static void readPirateMessage(ServerPlayer player) {
        SeasonKit.speak(player, "Message", "Tu pensais vraiment que ce coffre sur la plage etait mon tresor ?");
        SeasonKit.speak(player, "Message", "Mon vrai tresor dort sous la terre ferme. Va trouver le roi des pirates :"
                + " lui seul en a la carte.");
    }

    // -- Terre en vue ---------------------------------------------------------------------------

    /** Un rafiot : un radeau, un mat, une voile et un coffre de marin. */
    private static void raft(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        BlockPos center = SeasonKit.ground(level, (int) Math.floor(player.getX() + look.x * 5.0d),
                (int) Math.floor(player.getZ() + look.z * 5.0d));
        int placed = 0;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-2, 0, -1), center.offset(2, 0, 1))) {
            if (level.getBlockState(at).canBeReplaced()
                    && LuckyWorld.place(player, level, at.immutable(), Blocks.OAK_PLANKS.defaultBlockState())) {
                placed++;
            }
        }
        if (placed < 10) {
            SeasonKit.fail(player, "Impossible de te teleporter au rafiot !");
            return;
        }
        for (int dy = 1; dy <= 4; dy++) {
            LuckyWorld.place(player, level, center.above(dy), Blocks.OAK_FENCE.defaultBlockState());
        }
        for (int dy = 2; dy <= 4; dy++) {
            for (int dz = -1; dz <= 1; dz += 2) {
                LuckyWorld.place(player, level, center.above(dy).offset(0, 0, dz), SeasonKit.block("white_wool"));
            }
        }
        SeasonKit.chest(player, level, center.offset(2, 1, 0), List.of(new ItemStack(Items.COOKED_COD, 6),
                new ItemStack(Items.NAUTILUS_SHELL), new ItemStack(Items.SPYGLASS), new ItemStack(Items.GOLD_INGOT, 4)));
        player.teleportTo(center.getX() - 1.5d, center.getY() + 1.0d, center.getZ() + 0.5d);
        SeasonKit.win(player, "Tu as ete teleporte au rafiot !");
    }

    // -- Capitaine Flint ------------------------------------------------------------------------

    private static final String[] FLINT = {
            "Je connais une bonne planque de contrebandiers, tu devrais y jeter un oeil !",
            "Mon ancien maitre avait une jambe de bois et un rire terrible. Toi, tu as l'air plus gentil !",
            "Cela fait longtemps que je cherche une ile secrete. J'espere que tu m'y emmeneras un jour !",
            "Si un jour tu trouves un message dans une bouteille, essaie de le secher dans un four !"};

    /** Le perroquet Capitaine Flint, sur l'epaule, qui parle ; il repart au bout de {@code ticks}. */
    static void flint(ServerPlayer player, ServerLevel level, int ticks) {
        var parrot = SeasonKit.mob(level, EntityTypes.PARROT, player.position().add(0.0d, 1.0d, 0.0d),
                "Capitaine Flint");
        if (parrot == null) {
            return;
        }
        parrot.tame(player);
        parrot.setInvulnerable(true);
        UUID id = parrot.getUUID();
        LuckyTasks.repeat(player, 20, Math.max(20, ticks / FLINT.length), FLINT.length, line ->
                SeasonKit.speak(player, "Capitaine Flint", FLINT[line]));
        LuckyTasks.later(player, ticks, () -> {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.3d, entity.getZ(), 10, 0.2d,
                        0.2d, 0.2d, 0.02d);
                entity.discard();
            }
        });
    }

    // -- Connaitre les oceans -------------------------------------------------------------------

    /** Deux minutes pour ne faire qu'un avec l'ocean : manquer d'air sous l'eau. */
    private static void goodSailor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Tu as 2 minutes pour boire la tasse sous l'eau afin d'obtenir une recompense !");
        SeasonKit.challenge(player, "Ne fais qu'un avec l'ocean", 120, false,
                (p, age) -> p.isUnderWater() && p.getAirSupply() <= 0 ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING,
                p -> {
                    p.setAirSupply(p.getMaxAirSupply());
                    SeasonKit.win(p, "Tu as reussi a te noyer a temps ! Voici le Hollandais volant.");
                    SeasonKit.give(p, SeasonKit.named(new ItemStack(LuckyGadgets.BOAT_FURNACE.get()),
                            "Hollandais volant", "Un bateau plus rapide que les autres."));
                }, p -> SeasonKit.fail(p, "Tu n'as pas reussi a te noyer a temps !"));
    }

    // -- Pipe et jambe de bois ------------------------------------------------------------------

    /** Le marin Xavier chante ; il faut trouver la fin de son couplet. */
    private static void shanty(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var sailor = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player,
                level, 2, 3)), "Marin Xavier", 120, (p, npc) -> sing(p, npc));
        if (sailor != null) {
            SeasonKit.say(player, "Un vieux marin veut te chanter quelque chose. Fais un clic droit sur lui.");
        }
    }

    private static void sing(ServerPlayer player, Entity sailor) {
        SeasonKit.speak(player, "Marin Xavier", "Avec ma pipe et ma jambe de bois,");
        SeasonKit.speak(player, "Marin Xavier", "J'ai fait le tour des mers trois fois,");
        SeasonKit.speak(player, "Marin Xavier", "Et quand le vent souffle sur le pont...");
        SeasonKit.ask(player, "Marin Xavier", "Comment finit la chanson ?", List.of("Je mange une pomme",
                "Je chante plus fort que le grand ouragan !", "Je rentre a la maison"), (p, answer) -> {
            if (answer != 1) {
                SeasonKit.fail(p, "Ce n'est pas comme ca que ca finit !");
            } else {
                SeasonKit.win(p, "Bien joue, tu as reussi a completer la fin de la chanson !");
                SeasonKit.give(p, new ItemStack(SeasonItems.PIPE.get()));
            }
            SeasonKit.dismiss(sailor);
        });
    }

    // -- Scorbut --------------------------------------------------------------------------------

    /** Trois minutes de scorbut ; seule une orange soigne, sinon une dent tombe. */
    private static void scurvy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.addEffect(new MobEffectInstance(SeasonEffects.SCURVY, 3600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
        SeasonKit.say(player, "Tu as le scorbut, tu peux te soigner en mangeant une orange (pomme et colorant orange a"
                + " l'etabli).");
        LuckyTasks.later(player, 3590, () -> {
            if (player.hasEffect(SeasonEffects.SCURVY)) {
                SeasonKit.fail(player, "Le scorbut t'a fait perdre une dent !");
                SeasonKit.give(player, new ItemStack(SeasonItems.SAILOR_TOOTH.get()));
            }
        });
    }

    // -- Parler comme un pirate -----------------------------------------------------------------

    private static final String[] PIRATE_LINES = {
            "Je confonds encore babord et tribord, mais je suis un grand pirate !",
            "Mon perroquet parle mieux que moi, et c'est lui le capitaine.",
            "J'ai le mal de mer rien qu'en regardant une flaque.",
            "Je lave le pont tous les jours, c'est ma plus grande aventure.",
            "Des que je vois un ennemi, je hisse le drapeau blanc !",
            "Mon tresor ? Trois boutons et une cuillere en bois."};

    /** Une replique de pirate, a la place du message ({@link SeasonEffects}). */
    static String pirateLine(RandomSource random) {
        return PIRATE_LINES[random.nextInt(PIRATE_LINES.length)];
    }

    // -- Tu vas nourrir les poissons ------------------------------------------------------------

    /** Un aquarium de verre plein d'eau autour du joueur, une minute ; le dessus est ouvert. */
    private static void aquarium(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = player.blockPosition();
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 7, 3))) {
            boolean wall = Math.abs(at.getX() - center.getX()) == 3 || Math.abs(at.getZ() - center.getZ()) == 3
                    || at.getY() == center.getY() - 1;
            BlockState state = wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState();
            if ((wall || level.getBlockState(at).canBeReplaced()) && !level.getBlockState(at).hasBlockEntity()
                    && LuckyWorld.place(player, level, at.immutable(), state)) {
                placed.add(at.immutable());
            }
        }
        for (int fish = 0; fish < 4; fish++) {
            SeasonKit.mob(level, EntityTypes.TROPICAL_FISH, Vec3.atCenterOf(center.offset(level.getRandom().nextInt(5)
                    - 2, 2 + level.getRandom().nextInt(4), level.getRandom().nextInt(5) - 2)), null);
        }
        SeasonKit.timer(player, "Tu vas nourrir les poissons !", 60, p -> {
            for (BlockPos at : placed) {
                BlockState state = level.getBlockState(at);
                if (state.is(Blocks.GLASS) || state.is(Blocks.WATER)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        });
    }

    // -- La fete de la musique ------------------------------------------------------------------

    /** Trois musiciens jouent trente secondes ; chacun laisse son instrument. */
    private static void musicParty(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String[] names = {"Batteur", "Accordeoniste", "Guitariste"};
        ItemStack[] gifts = {new ItemStack(SeasonItems.DRUM.get()), new ItemStack(SeasonItems.ACCORDION.get()),
                new ItemStack(SeasonItems.ELECTRIC_GUITAR.get())};
        List<Entity> band = new ArrayList<>();
        for (String name : names) {
            var musician = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(
                    player, level, 2, 4)), name, 35, (p, npc) -> {
            });
            if (musician != null) {
                band.add(musician);
            }
        }
        SeasonKit.say(player, "Les musiciens viennent de faire leur apparition ! Ils te donneront un objet a la fin de"
                + " la fete !");
        JuneEvents.tune(player, level, 30);
        LuckyTasks.later(player, 600, () -> {
            for (int index = 0; index < band.size(); index++) {
                Entity musician = band.get(index);
                LuckyWorld.drop(level, musician.getX(), musician.getY() + 0.5d, musician.getZ(), gifts[index]);
                SeasonKit.dismiss(musician);
            }
        });
    }

    // -- Devoir de vigie ------------------------------------------------------------------------

    /** Une vigie a vingt blocs de haut : une minute sans bouger, et une longue-vue. */
    private static void lookout(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 back = player.position();
        BlockPos top = SeasonKit.ground(level, player.getBlockX(), player.getBlockZ()).above(20);
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(top.offset(-1, -1, -1), top.offset(1, -1, 1))) {
            if (level.getBlockState(at).isAir() && LuckyWorld.place(player, level, at.immutable(),
                    Blocks.BEDROCK.defaultBlockState())) {
                placed.add(at.immutable());
            }
        }
        if (placed.size() < 9) {
            placed.forEach(at -> level.removeBlock(at, false));
            SeasonKit.fail(player, "Pas de place pour la vigie ici.");
            return;
        }
        player.teleportTo(top.getX() + 0.5d, top.getY(), top.getZ() + 0.5d);
        SeasonKit.say(player, "Tu as ete teleporte sur ton devoir de vigie, reste immobile pour obtenir ta recompense !");
        Vec3 spot = player.position();
        SeasonKit.challenge(player, "Devoir de vigie : quelqu'un viendra bientot te relayer", 60, true,
                (p, age) -> p.position().distanceToSqr(spot) > 4.0d ? SeasonKit.Outcome.LOST : SeasonKit.Outcome.GOING,
                p -> {
                    placed.forEach(at -> level.removeBlock(at, false));
                    p.teleportTo(back.x, back.y, back.z);
                    SeasonKit.win(p, "Tu as reussi ton devoir de vigie !");
                    SeasonKit.give(p, SeasonKit.named(new ItemStack(Items.SPYGLASS), "Longue-vue de pirate"));
                }, p -> {
                    placed.forEach(at -> level.removeBlock(at, false));
                    p.teleportTo(back.x, back.y, back.z);
                    SeasonKit.fail(p, "Tu n'as pas reussi ton devoir de vigie !");
                });
    }

    // -- Pirate informatique --------------------------------------------------------------------

    /** Le decryptage ({@code ComputerUI}) : trois questions ; l'ordinateur devient decrypte. */
    static void decrypt(ServerPlayer player, ServerLevel level, BlockPos computer) {
        SeasonKit.ask(player, "Ordinateur", "ACCES REFUSE. Combien font 1010 + 0101 en binaire ?",
                List.of("1111", "1001", "0110"), (p, first) -> {
                    if (first != 0) {
                        SeasonKit.fail(p, "Mot de passe incorrect.");
                        return;
                    }
                    SeasonKit.ask(p, "Ordinateur", "Quelle lettre suit A, C, E, G ?", List.of("H", "I", "J"),
                            (q, second) -> {
                                if (second != 1) {
                                    SeasonKit.fail(q, "Mot de passe incorrect.");
                                    return;
                                }
                                SeasonKit.ask(q, "Ordinateur", "Dernier verrou : FF en decimal ?",
                                        List.of("155", "255", "256"), (r, third) -> {
                                            if (third != 1) {
                                                SeasonKit.fail(r, "Mot de passe incorrect.");
                                                return;
                                            }
                                            if (level.getBlockState(computer).is(SeasonItems.ENCRYPTED_COMPUTER.get())) {
                                                level.setBlock(computer, SeasonItems.DECRYPTED_COMPUTER.get()
                                                        .defaultBlockState(), 3);
                                            }
                                            SeasonKit.win(r, "Ordinateur decrypte ! Clique dessus pour lire ses"
                                                    + " donnees.");
                                        });
                            });
                });
    }

    /** Les donnees top secretes : le code du coffre de juillet. */
    static void secretFiles(ServerPlayer player) {
        SeasonKit.speak(player, "Paladium secret defense", "Document reserve au personnel autorise.");
        LuckyWorld.tell(player, "Code secret du mois de juillet : " + CODE[0] + CODE[1] + CODE[2],
                ChatFormatting.RED);
    }

    // -- Le tresor du roi des pirates -----------------------------------------------------------

    /** Le roi des pirates laisse sa carte ; le tresor est enterre a cinquante blocs ou plus. */
    private static void pirateKing(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var king = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player,
                level, 2, 3)), "Roi des pirates", 120, (p, npc) -> SeasonKit.next(p, "Roi des pirates",
                "Mon tresor ? Je vous le laisse si vous voulez. Trouvez-le ! Je l'ai cache quelque part dans ce monde !",
                "Recevoir le tresor", q -> {
                    SeasonKit.give(q, treasureMap(q, level));
                    SeasonKit.dismiss(npc);
                }));
        if (king != null) {
            king.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            SeasonKit.say(player, "Le roi des pirates est la. Fais un clic droit sur lui.");
        }
    }

    /** Enterre le coffre et rend la carte qui y mene. */
    private static ItemStack treasureMap(ServerPlayer player, ServerLevel level) {
        BlockPos surface = SeasonKit.around(player, level, 50, 120);
        BlockPos chest = surface.below(3);
        SeasonKit.chest(player, level, chest, List.of(SeasonKit.stack("palamod:paladium_block", 2),
                new ItemStack(Items.GOLD_INGOT, 12), new ItemStack(Items.DIAMOND, 3),
                new ItemStack(SeasonItems.PIRATE_MESSAGE.get())));
        ItemStack map = new ItemStack(SeasonItems.TREASURE_MAP.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", chest.getX());
        tag.putInt("y", chest.getY());
        tag.putInt("z", chest.getZ());
        CompoundTag data = new CompoundTag();
        data.put(TREASURE, tag);
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return map;
    }

    /** La carte : direction et distance du tresor. */
    static void readMap(ServerPlayer player, ItemStack map) {
        CustomData data = map.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty(TREASURE);
        if (!tag.contains("x")) {
            LuckyWorld.tell(player, "Cette carte est effacee.", ChatFormatting.GRAY);
            return;
        }
        int x = tag.getIntOr("x", 0);
        int z = tag.getIntOr("z", 0);
        double dx = x + 0.5d - player.getX();
        double dz = z + 0.5d - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance <= 2) {
            LuckyWorld.bar(player, "X marque l'endroit ! Creuse trois blocs sous tes pieds.", ChatFormatting.GOLD);
            return;
        }
        String[] directions = {"au sud", "au sud-ouest", "a l'ouest", "au nord-ouest", "au nord", "au nord-est",
                "a l'est", "au sud-est"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int index = (int) Math.floorMod(Math.round(angle / 45.0d), 8);
        LuckyWorld.bar(player, "Le tresor est a " + distance + " blocs, " + directions[index] + ".", ChatFormatting.GOLD);
    }

    // -- Bateau fantome -------------------------------------------------------------------------

    private static final String CREW = "dohy_phantom_crew";

    /** Un equipage de squelettes et leur roi : tous les vaincre en cinq minutes. */
    private static void phantomBoat(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<UUID> crew = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            Mob skeleton = SeasonKit.mob(level, EntityTypes.SKELETON, Vec3.atBottomCenterOf(SeasonKit.around(player,
                    level, 5, 9)), null);
            if (skeleton != null) {
                skeleton.addTag(CREW);
                skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0));
                crew.add(skeleton.getUUID());
            }
        }
        Mob king = SeasonKit.mob(level, EntityTypes.WITHER_SKELETON, Vec3.atBottomCenterOf(SeasonKit.around(player,
                level, 6, 9)), "Roi wither squelette");
        if (king != null) {
            king.setCustomName(Component.literal("Roi wither squelette").withStyle(ChatFormatting.RED));
            SeasonKit.stat(king, Attributes.MAX_HEALTH, 100.0d);
            king.addTag(CREW);
            crew.add(king.getUUID());
        }
        SeasonKit.say(player, "Un equipage fantome debarque ! Tue tous les squelettes pour obtenir une recompense.");
        int total = crew.size();
        int[] lastLeft = {total};
        SeasonKit.challenge(player, "Tue tous les squelettes", 300, false, (p, age) -> {
            if (age % 10 != 0) {
                return SeasonKit.Outcome.GOING;
            }
            int left = 0;
            for (UUID id : crew) {
                Entity entity = level.getEntity(id);
                if (entity != null && entity.isAlive()) {
                    left++;
                }
            }
            if (left < lastLeft[0]) {
                lastLeft[0] = left;
                LuckyWorld.bar(p, "Squelettes tues : " + (total - left) + "/" + total, ChatFormatting.YELLOW);
            }
            return left == 0 ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
        }, p -> {
            SeasonKit.win(p, "L'equipage fantome est vaincu !");
            List<ItemStack> rewards = List.of(SeasonKit.stack("palamod:paladium_sword", 1),
                    new ItemStack(SeasonItems.GHOSTLY_SWORD.get()), new ItemStack(Items.BONE, 16),
                    new ItemStack(SeasonItems.TOOTH_SKELETON_SKULL.get()));
            for (ItemStack reward : rewards) {
                LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), reward);
            }
        }, p -> {
            SeasonKit.fail(p, "L'equipage fantome s'est evanoui...");
            for (UUID id : crew) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        });
    }

    // -- La croix rouge -------------------------------------------------------------------------

    /** Une croix rouge au sol et la potion d'invulnerabilite. */
    private static void redCross(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = player.blockPosition().below();
        BlockState red = SeasonKit.block("red_wool");
        for (int step = -2; step <= 2; step++) {
            for (BlockPos at : new BlockPos[]{center.offset(step, 0, 0), center.offset(0, 0, step)}) {
                if (!level.getBlockState(at).isAir() && !level.getBlockState(at).hasBlockEntity()) {
                    LuckyWorld.place(player, level, at, red);
                }
            }
        }
        SeasonKit.give(player, new ItemStack(SeasonItems.INVULNERABILITY_POTION.get()));
        SeasonKit.win(player, "La croix rouge te soigne : te voila insensible aux effets negatifs pendant une minute.");
        player.addEffect(new MobEffectInstance(SeasonEffects.IMMUNE, 1200, 0));
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.NAUSEA);
    }
}
