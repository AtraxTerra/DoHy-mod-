package fr.atraxterra.dohy.palamod.lucky;

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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le lucky block de juin ({@code luckyevents/june}) : la musique. Dix-huit evenements.
 *
 * <p>Ecarts : PalaMod jouait des morceaux enregistres (un tube coreen, le theme d'un film de
 * requin, une musique d'ascenseur, la radio des admins) ; ici ce sont des airs joues au bloc de
 * notes. Les disques de « Un vrai Jukebox » (des chansons de joueurs et de videastes) deviennent
 * des disques du jeu. La « Battle de danse » se joue contre un danseur, sans les admins.
 */
final class JuneEvents {

    private static final String POUET = "dohy_pouet";
    private static final Map<UUID, Rhythm> RHYTHMS = new HashMap<>();

    private JuneEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.JUNE, "maladie_pouet_pouet", "Maladie du pouet pouet", "june/maladie_pouet_pouet", false, 80,
                JuneEvents::pouet);
        c.season(LuckyType.JUNE, "plus_batterie", "Plus de batterie", "june/plus_batterie", true, 80,
                JuneEvents::lowBattery);
        c.season(LuckyType.JUNE, "super_danseur", "Super danseur !", "june/super_danseur", true, 80,
                (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.DANCE, 290, 0));
                    tune(player, level, 14);
                });
        c.season(LuckyType.JUNE, "ascenseur_emotionnel", "Ascenseur emotionnel", "june/ascenseur_emotionnel", true,
                100, JuneEvents::elevator);
        c.season(LuckyType.JUNE, "cinema_muet", "Cinema muet", "june/cinema_muet", true, 100,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(SeasonEffects.SILENT_MOVIE, 2400, 0)));
        c.season(LuckyType.JUNE, "enflamme_dancefloor", "Enflamme le dance-floor", "june/enflamme_dancefloor", true,
                100, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(SeasonEffects.FIRE_WALK, 300, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 0));
                });
        c.season(LuckyType.JUNE, "musique_qui_fait_peur", "La musique qui fait peur !", "june/musique_qui_fait_peur",
                false, 100, JuneEvents::shark);
        c.season(LuckyType.JUNE, "test_rythme", "Test de rythme", "june/test_rythme", false, 100,
                JuneEvents::rhythmTest);
        c.season(LuckyType.JUNE, "virus_dance", "Le virus de la dance", "june/virus_dance", false, 100,
                (player, level, pos) -> LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                        new ItemStack(SeasonItems.RADIO.get())));
        c.season(LuckyType.JUNE, "enfant_trompette", "L'enfant a la trompette", "june/enfant_trompette", true, 120,
                JuneEvents::trumpetKid);
        c.season(LuckyType.JUNE, "quack", "Quack", "june/quack", true, 130,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(SeasonEffects.QUACK, 36000, 0)));
        c.season(LuckyType.JUNE, "disque_or", "Disque d'or", "june/disque_or", false, 140,
                (player, level, pos) -> LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                        new ItemStack(SeasonItems.GOLD_RECORD.get())));
        c.season(LuckyType.JUNE, "heavy_metal", "Heavy Metal", "june/heavy_metal", false, 140,
                (player, level, pos) -> LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                        new ItemStack(SeasonItems.ELECTRIC_GUITAR.get())));
        c.season(LuckyType.JUNE, "coffre_juin", "Coffre de juin", "june/coffre_juin", false, 180,
                (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre de juin", new int[]{6, 2, 4},
                        List.of(SeasonKit.stack("palamod:green_paladium_block", 2),
                                SeasonKit.stack("palamod:paladium_block", 3),
                                new ItemStack(SeasonItems.BRIGHT_SOUND.get(), 4),
                                new ItemStack(SeasonItems.TRUMPET.get()), LuckyType.PALADIUM.stack(2))));
        c.season(LuckyType.JUNE, "suis_bip", "Suis le bip", "june/suis_bip", true, 180, JuneEvents::followBeep);
        c.season(LuckyType.JUNE, "reveille_toi", "Reveille-toi", "june/reveille_toi", false, 240,
                (player, level, pos) -> LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                        new ItemStack(LuckyGadgets.ALARM.get())));
        c.season(LuckyType.JUNE, "vrai_jukebox_vraie_musique", "Un vrai Jukebox pour de la vraie musique",
                "june/vrai_jukebox_vraie_musique", false, 240, JuneEvents::jukebox);
        c.season(LuckyType.JUNE, "battle_dance", "Battle de danse", "june/battle_danse", false, 700,
                JuneEvents::danceBattle);
    }

    // -- Musique --------------------------------------------------------------------------------

    private static final int[] MELODY = {0, 4, 7, 12, 7, 4, 2, 5, 9, 14, 9, 5, 4, 7, 11, 16};

    /** Un petit air entrainant au bloc de notes, {@code seconds} secondes. */
    static void tune(ServerPlayer player, ServerLevel level, int seconds) {
        LuckyTasks.repeat(player, 0, 5, seconds * 4, step -> {
            float pitch = (float) Math.pow(2.0d, (MELODY[step % MELODY.length] - 12) / 12.0d);
            LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(),
                    step % 4 == 0 ? "block.note_block.basedrum" : "block.note_block.pling", SoundSource.RECORDS, 1.0f,
                    Math.max(0.5f, Math.min(2.0f, pitch)));
        });
    }

    // -- Maladie du pouet pouet -----------------------------------------------------------------

    /** Un objet de l'inventaire, au hasard, fait « pouet » quand on le tient. */
    private static void pouet(ServerPlayer player, ServerLevel level, BlockPos pos) {
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
        tag.putBoolean(POUET, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Pouet pouet"));
        SeasonKit.say(player, "Tu as de la chance... pouuet !");
    }

    /** L'objet tenu fait-il « pouet » ? ({@link SeasonEffects}) */
    static boolean squeaking(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(POUET, false);
    }

    // -- Plus de batterie -----------------------------------------------------------------------

    /** Deux ralentissements, puis la batterie lache : quinze secondes sans bouger. */
    private static void lowBattery(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        LuckyTasks.later(player, 40, () -> player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2)));
        LuckyTasks.later(player, 80, () -> {
            LuckyWorld.bar(player, "Batterie faible...", ChatFormatting.RED);
            LuckyWorld.sound(level, player.getX(), player.getY(), player.getZ(), "block.note_block.bass",
                    SoundSource.PLAYERS, 1.0f, 0.5f);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 9, false, false));
            player.addEffect(new MobEffectInstance(PalaLucky.NO_JUMP, 300, 0, false, false));
        });
    }

    // -- Ascenseur emotionnel -------------------------------------------------------------------

    /** Un palier a la couche 200, dix secondes de musique d'attente, et le palier disparait. */
    private static void elevator(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int y = Math.min(200, level.getMaxY() - 4);
        BlockPos floor = new BlockPos(player.getBlockX(), y, player.getBlockZ());
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 0, 1))) {
            if (level.getBlockState(at).isAir() && LuckyWorld.place(player, level, at.immutable(),
                    Blocks.STONE.defaultBlockState())) {
                placed.add(at.immutable());
            }
        }
        if (!placed.contains(floor)) {
            placed.forEach(at -> level.removeBlock(at, false));
            return;
        }
        player.teleportTo(floor.getX() + 0.5d, floor.getY() + 1.0d, floor.getZ() + 0.5d);
        SeasonKit.say(player, "Ding ! Etage 200, ascenseur emotionnel...");
        tune(player, level, 10);
        SeasonKit.softFall(player, 1200);
        LuckyTasks.later(player, 213, () -> {
            placed.forEach(at -> {
                if (level.getBlockState(at).is(Blocks.STONE)) {
                    level.removeBlock(at, false);
                }
            });
            SeasonKit.say(player, "Descente express !");
        });
    }

    // -- La musique qui fait peur ---------------------------------------------------------------

    /** Un aileron fonce vers le joueur au rythme de la contrebasse, puis s'evanouit : une peluche. */
    private static void shark(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        Vec3 start = player.position().add(look.scale(30.0d));
        ArmorStand fin = LuckyCreatures.marker(level, start.x, player.getY() - 0.6d, start.z);
        if (fin == null) {
            return;
        }
        fin.setCustomNameVisible(false);
        fin.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, SeasonKit.stack("minecraft:gray_concrete_powder", 1));
        level.addFreshEntity(fin);
        LuckyTasks.repeat(player, 0, 4, 58, step -> {
            double t = (step + 1) / 58.0d;
            Vec3 at = start.add(player.position().subtract(start).scale(t));
            fin.teleportTo(at.x, player.getY() - 0.6d, at.z);
            level.sendParticles(ParticleTypes.SPLASH, at.x, player.getY() + 0.2d, at.z, 6, 0.3d, 0.0d, 0.3d, 0.0d);
            if (step % Math.max(1, 6 - step / 12) == 0) {
                LuckyWorld.sound(level, at.x, at.y, at.z, "block.note_block.bass", SoundSource.HOSTILE, 1.5f,
                        step % 2 == 0 ? 0.5f : 0.53f);
            }
            if (step == 57) {
                level.sendParticles(ParticleTypes.POOF, at.x, player.getY() + 0.5d, at.z, 20, 0.4d, 0.4d, 0.4d, 0.02d);
                fin.discard();
                ClassicContent.toss(player, new ItemStack(SeasonItems.SHARK_PLUSH.get()));
            }
        });
    }

    // -- Test de rythme -------------------------------------------------------------------------

    private static final class Rhythm {
        final List<BlockPos> pads;
        final List<Integer> sequence = new ArrayList<>();
        int typed;
        int wave;
        boolean listening;

        Rhythm(List<BlockPos> pads) {
            this.pads = pads;
        }
    }

    /** Quatre blocs sonores : ecouter, puis rejouer trois vagues de notes ({@code SoundStructure}). */
    private static void rhythmTest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        BlockPos center = BlockPos.containing(player.position().add(look.scale(3.0d)));
        List<BlockPos> pads = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            BlockPos at = center.offset(index - 2, 0, 0);
            if (!level.getBlockState(at).canBeReplaced()) {
                at = at.above();
            }
            if (LuckyWorld.place(player, level, at, SeasonItems.BRIGHT_SOUND.get().defaultBlockState())) {
                pads.add(at);
            }
        }
        if (pads.size() < 4) {
            pads.forEach(at -> level.removeBlock(at, false));
            SeasonKit.fail(player, "Il n'y a pas la place pour le test de rythme ici.");
            return;
        }
        Rhythm rhythm = new Rhythm(pads);
        RHYTHMS.put(player.getUUID(), rhythm);
        SeasonKit.say(player, "Ecoute les blocs sonores, puis rejoue la meme suite en cliquant dessus.");
        nextWave(player, level, rhythm);
        SeasonKit.challenge(player, "Test de rythme", 120, false, (p, age) -> rhythm.wave >= 3
                ? SeasonKit.Outcome.WON : RHYTHMS.containsKey(p.getUUID()) ? SeasonKit.Outcome.GOING
                : SeasonKit.Outcome.LOST, p -> {
            RHYTHMS.remove(p.getUUID());
            SeasonKit.win(p, "Tu as reussi l'epreuve ! Les blocs sonores sont a toi.");
            for (BlockPos pad : pads) {
                if (level.getBlockState(pad).getBlock() instanceof SeasonItems.BrightSoundBlock) {
                    level.removeBlock(pad, false);
                }
            }
            LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), new ItemStack(SeasonItems.BRIGHT_SOUND.get(), 3));
            LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), SeasonKit.stack("palamod:paladium_ingot", 4));
        }, p -> {
            RHYTHMS.remove(p.getUUID());
            for (BlockPos pad : pads) {
                if (level.getBlockState(pad).getBlock() instanceof SeasonItems.BrightSoundBlock) {
                    level.removeBlock(pad, false);
                }
            }
        });
    }

    private static void nextWave(ServerPlayer player, ServerLevel level, Rhythm rhythm) {
        rhythm.listening = false;
        rhythm.typed = 0;
        RandomSource random = level.getRandom();
        rhythm.sequence.add(random.nextInt(4));
        if (rhythm.sequence.size() < 3) {
            rhythm.sequence.add(random.nextInt(4));
        }
        List<Integer> notes = List.copyOf(rhythm.sequence);
        LuckyTasks.repeat(player, 20, 15, notes.size(), step -> {
            SeasonItems.BrightSoundBlock.ring(level, rhythm.pads.get(notes.get(step)), pitch(notes.get(step)));
            if (step == notes.size() - 1) {
                rhythm.listening = true;
                LuckyWorld.bar(player, "A toi ! " + notes.size() + " notes", ChatFormatting.AQUA);
            }
        });
    }

    private static float pitch(int pad) {
        return new float[]{0.6f, 0.8f, 1.0f, 1.3f}[pad];
    }

    /** Un bloc sonore clique ({@link SeasonItems.BrightSoundBlock}). */
    static void padPressed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Rhythm rhythm = RHYTHMS.get(player.getUUID());
        if (rhythm == null || !rhythm.listening) {
            return;
        }
        int pad = rhythm.pads.indexOf(pos);
        if (pad < 0) {
            return;
        }
        if (rhythm.sequence.get(rhythm.typed) != pad) {
            SeasonKit.fail(player, "Tu as echoue a jouer la note, le jeu recommence.");
            rhythm.sequence.clear();
            rhythm.wave = 0;
            nextWave(player, level, rhythm);
            return;
        }
        rhythm.typed++;
        if (rhythm.typed == rhythm.sequence.size()) {
            rhythm.wave++;
            if (rhythm.wave < 3) {
                SeasonKit.win(player, "Tu as reussi a jouer la vague (" + rhythm.wave + "/3). La prochaine va commencer.");
                nextWave(player, level, rhythm);
            }
        }
    }

    // -- L'enfant a la trompette ----------------------------------------------------------------

    /** Dix minutes : un enfant a la trompette surgit toutes les une a deux minutes. */
    private static void trumpetKid(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int[] next = {20 * (60 + level.getRandom().nextInt(61))};
        SeasonKit.challenge(player, "L'enfant a la trompette", 600, true, (p, age) -> {
            if (age >= next[0]) {
                next[0] = age + 20 * (60 + level.getRandom().nextInt(61));
                var kid = SeasonKit.mob(level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(p, level, 2,
                        4)), "Enfant a la trompette");
                if (kid != null) {
                    kid.setAge(-24000);
                    kid.setInvulnerable(true);
                    LuckyTasks.repeat(p, 0, 20, 10, step -> {
                        LuckyWorld.sound(level, kid.getX(), kid.getY(), kid.getZ(), "item.goat_horn.sound.7",
                                SoundSource.NEUTRAL, 2.0f, 1.4f);
                        if (step == 9) {
                            level.sendParticles(ParticleTypes.POOF, kid.getX(), kid.getY() + 0.5d, kid.getZ(), 15, 0.3d,
                                    0.3d, 0.3d, 0.02d);
                            kid.discard();
                        }
                    });
                }
            }
            return SeasonKit.Outcome.GOING;
        }, p -> {
        }, p -> {
        });
    }

    // -- Suis le bip ----------------------------------------------------------------------------

    /** Une passerelle de vingt blocs : rejoindre le bloc rouge au bout avant le bip. */
    private static void followBeep(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.fromYRot(player.getYRot());
        BlockPos start = player.blockPosition().below();
        List<BlockPos> placed = new ArrayList<>();
        for (int step = 0; step <= 20; step++) {
            BlockPos at = start.relative(facing, step);
            BlockState state = step == 20 ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
            if (level.getBlockState(at).canBeReplaced() && LuckyWorld.place(player, level, at, state)) {
                placed.add(at);
            }
        }
        BlockPos goal = start.relative(facing, 20);
        SeasonKit.say(player, "Rejoins l'autre cote de la plateforme (le bloc rouge) avant le bip pour ne pas subir de"
                + " malus.");
        SeasonKit.challenge(player, "Suis le bip", 10, false, (p, age) -> {
            if (age % 20 == 0) {
                LuckyWorld.sound(level, p.getX(), p.getY(), p.getZ(), "block.note_block.bit", SoundSource.PLAYERS,
                        1.0f, 1.0f + age / 200.0f);
            }
            return p.blockPosition().below().equals(goal) || p.blockPosition().equals(goal.above())
                    ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
        }, p -> {
            SeasonKit.win(p, "Juste a temps !");
            placed.forEach(at -> level.removeBlock(at, false));
        }, p -> {
            LuckyWorld.sound(level, p.getX(), p.getY(), p.getZ(), "block.note_block.bit", SoundSource.PLAYERS, 2.0f,
                    2.0f);
            SeasonKit.fail(p, "Bip ! Trop tard.");
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200, 1));
            placed.forEach(at -> level.removeBlock(at, false));
        });
    }

    // -- Un vrai Jukebox -------------------------------------------------------------------------

    private static void jukebox(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.JUKEBOX));
        List<ItemStack> discs = new ArrayList<>();
        for (String disc : new String[]{"cat", "blocks", "chirp", "mall", "stal", "strad"}) {
            discs.add(SeasonKit.stack("minecraft:music_disc_" + disc, 1));
        }
        for (int index = 0; index < 3; index++) {
            LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                    discs.remove(level.getRandom().nextInt(discs.size())));
        }
    }

    // -- Battle de danse ------------------------------------------------------------------------

    private static final String[] MOVES = {"Saute !", "Accroupis-toi !", "Ne bouge plus !"};

    /** Un danseur montre cinq pas ; il faut les reproduire, chacun en trois secondes. */
    private static void danceBattle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var dancer = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player,
                level, 2, 3)), "Danseur", 60, (p, npc) -> {
        });
        if (dancer == null) {
            return;
        }
        SeasonKit.say(player, "Reproduis les mouvements du danseur pour gagner une recompense.");
        tune(player, level, 20);
        int[] move = {level.getRandom().nextInt(MOVES.length)};
        int[] round = {0};
        int[] since = {0};
        int[] jumps = {player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.JUMP))};
        Vec3[] spot = {player.position()};
        LuckyWorld.bar(player, "Pas 1/5 : " + MOVES[move[0]], ChatFormatting.LIGHT_PURPLE);
        SeasonKit.challenge(player, "Battle de danse", 30, false, (p, age) -> {
            int nowJumps = p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.JUMP));
            boolean done = switch (move[0]) {
                case 0 -> nowJumps > jumps[0];
                case 1 -> p.isShiftKeyDown();
                default -> age - since[0] >= 50 && p.position().distanceToSqr(spot[0]) < 0.04d;
            };
            boolean late = age - since[0] > 60;
            if (move[0] == 2 && p.position().distanceToSqr(spot[0]) >= 0.04d) {
                late = true;
            }
            if (late) {
                return SeasonKit.Outcome.LOST;
            }
            if (!done) {
                return SeasonKit.Outcome.GOING;
            }
            round[0]++;
            if (round[0] >= 5) {
                return SeasonKit.Outcome.WON;
            }
            move[0] = p.getRandom().nextInt(MOVES.length);
            since[0] = age;
            jumps[0] = nowJumps;
            spot[0] = p.position();
            LuckyWorld.bar(p, "Pas " + (round[0] + 1) + "/5 : " + MOVES[move[0]], ChatFormatting.LIGHT_PURPLE);
            return SeasonKit.Outcome.GOING;
        }, p -> {
            SeasonKit.win(p, "Quel danseur ! Voici ta recompense.");
            LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), new ItemStack(SeasonItems.RADIO.get()));
            LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), SeasonKit.stack("palamod:paladium_block", 2));
            SeasonKit.dismiss(dancer);
        }, p -> {
            SeasonKit.fail(p, "Rate ! Le danseur s'en va.");
            if (dancer.isAlive()) {
                SeasonKit.dismiss(dancer);
            }
        });
    }
}
