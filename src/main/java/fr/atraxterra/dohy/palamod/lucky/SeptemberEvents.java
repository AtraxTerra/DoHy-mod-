package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.economy.Economy;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le lucky block de septembre ({@code monthly/modules/september}) : la rentree, le travail et les
 * metiers. Vingt et un evenements.
 *
 * <p>Ecarts : les structures du serveur Event (la capsule et la Lune, la boucherie, le tribunal,
 * l'agence de voyage) se construisent autour du joueur et repartent ensuite ; les reponses OUI,
 * NON, ARGENT et TEMPS deviennent des choix a cliquer (le test d'anglais se repond toujours dans le
 * chat) ; les repliques du juge et du diable sont reecrites ; la musique du vrai boss final n'est
 * pas reprise.
 */
@EventBusSubscriber
public final class SeptemberEvents {

    /** Les blocs casses pendant un defi ; {@code [1] == 1} : la pierre seulement. */
    private static final Map<UUID, int[]> BREAKS = new HashMap<>();
    /** La question du test d'anglais ou en est le joueur. */
    private static final Map<UUID, Integer> ENGLISH = new HashMap<>();
    private static final Set<UUID> SILENT = new HashSet<>();
    private static final Set<UUID> BROKE_SILENCE = new HashSet<>();
    private static final Set<UUID> BOUNTIES = new HashSet<>();
    /** Les zombies tues a la boucherie. */
    private static final Map<UUID, int[]> BUTCHERS = new HashMap<>();
    /** Les monstres tues depuis « Beaucoup d'experience dans le domaine ». */
    private static final Map<UUID, int[]> HUNTS = new HashMap<>();
    /** L'epreuve du forgeron en cours : ce qui a ete fabrique, et l'inventaire du depart. */
    private static final Map<UUID, Forge> FORGES = new HashMap<>();

    private static final String BUTCHER = "dohy_butcher";
    private static final String FORGE_MARK = "dohy_forge";
    /** Le jeu en anglais : cinq minutes ({@code SCTimedLanguagePacket}). */
    private static final int ENGLISH_TICKS = 6000;

    private SeptemberEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.SEPTEMBER, "dream_job", "Le metier de reve d'une generation", "september/dream_job", false,
                250, SeptemberEvents::dreamJob);
        c.season(LuckyType.SEPTEMBER, "english_test", "Test d'anglais", "september/english_test", true, 250,
                SeptemberEvents::englishTest);
        c.season(LuckyType.SEPTEMBER, "explore_jobs_opening", "Explorons les offres d'emploi",
                "september/explore_job_openings", false, 250,
                (player, level, pos) -> LuckyWorld.drop(level, pos, new ItemStack(SeasonItems.JOB_OFFER.get())));
        c.season(LuckyType.SEPTEMBER, "holiday_pay", "Pecule de vacances", "september/holiday_pay", false, 250,
                SeptemberEvents::holidayPay);
        c.season(LuckyType.SEPTEMBER, "professional_secrecy", "Secret professionnel", "september/professional_secrecy",
                false, 250, SeptemberEvents::secrecy);
        c.season(LuckyType.SEPTEMBER, "work_is_health", "Le travail c'est la sante", "september/work_is_health", true,
                250, SeptemberEvents::workIsHealth);
        c.season(LuckyType.SEPTEMBER, "habit_makes_monk", "L'habit fait le moine", "september/habit_makes_monk", true,
                300, (player, level, pos) -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 4));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200, 0));
                    SeasonKit.fail(player, "Tu t'es transforme en moine : pas de violence pendant une minute !");
                });
        c.season(LuckyType.SEPTEMBER, "time_is_money", "Le temps c'est de l'argent", "september/time_is_money", true,
                300, SeptemberEvents::timeIsMoney);
        c.season(LuckyType.SEPTEMBER, "good_fortune", "La bonne fortune", "september/good_fortune", false, 350,
                SeptemberEvents::goodFortune);
        c.season(LuckyType.SEPTEMBER, "good_pay", "La bonne paye", "september/good_pay", false, 350,
                SeptemberEvents::goodPay);
        c.season(LuckyType.SEPTEMBER, "is_dream_job", "Le metier de reve de la nouvelle generation ?",
                "september/is_dream_job", true, 350, SeptemberEvents::workers);
        c.season(LuckyType.SEPTEMBER, "sandman", "Le marchand de sable", "september/sandman", false, 350,
                SeptemberEvents::sandman);
        c.season(LuckyType.SEPTEMBER, "finally_a_bonus", "Enfin une prime", "september/finally_a_bonus", true, 400,
                SeptemberEvents::bounty);
        c.season(LuckyType.SEPTEMBER, "real_butcher_shop", "C'est une vraie boucherie", "september/real_butcher_shop",
                false, 400, SeptemberEvents::butcher);
        c.season(LuckyType.SEPTEMBER, "september_chest", "Coffre de septembre", "september/september_chest", false,
                450, (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre de septembre",
                        new int[]{3, 0, 9}, List.of(SeasonKit.stack("palamod:titane_block", 8),
                                SeasonKit.stack("palamod:paladium_block", 1), new ItemStack(Items.GOLD_BLOCK, 15),
                                SeasonKit.stack("palamod:green_paladium_block", 1), LuckyType.SEPTEMBER.stack(1))));
        c.season(LuckyType.SEPTEMBER, "devils_advocate", "L'avocat du diable", "september/devils_advocate", false, 500,
                SeptemberEvents::court);
        c.season(LuckyType.SEPTEMBER, "final_boss", "Le boss final", "september/final_boss", false, 500,
                SeptemberEvents::finalBoss);
        c.season(LuckyType.SEPTEMBER, "career", "Faire carriere", "september/career", false, 550,
                (player, level, pos) -> career(player, level, 0, new ArrayList<>()));
        c.season(LuckyType.SEPTEMBER, "lot_experience", "Beaucoup d'experience dans le domaine",
                "september/lot_experience", false, 700, SeptemberEvents::experience);
        c.season(LuckyType.SEPTEMBER, "practice_makes_perfect", "C'est en forgeant qu'on devient forgeron",
                "september/practice_makes_perfect", false, 750, SeptemberEvents::forge);
        c.season(LuckyType.SEPTEMBER, "real_final_boss", "Le vrai boss final", "september/real_final_boss", false,
                1500, SeptemberEvents::realFinalBoss);
    }

    // -- Ecoute des evenements ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            int[] count = BREAKS.get(player.getUUID());
            if (count != null && (count[1] == 0 || event.getState().is(Blocks.STONE))) {
                count[0]++;
            }
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Forge forge = FORGES.get(player.getUUID());
            if (forge != null && !event.getCrafting().isEmpty()) {
                Item item = event.getCrafting().getItem();
                forge.crafted.add(item);
                if (forge.goals.contains(item)) {
                    forge.done.add(item);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        UUID id = event.getPlayer().getUUID();
        if (SILENT.contains(id)) {
            BROKE_SILENCE.add(id);
        }
        Integer question = ENGLISH.get(id);
        if (question != null) {
            event.setCanceled(true);
            answerEnglish(event.getPlayer(), question, normalize(event.getRawText()));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer dead) {
            if (killer instanceof ServerPlayer hunter && hunter != dead && BOUNTIES.remove(dead.getUUID())) {
                Economy.credit(hunter, 3000L);
                LuckyWorld.tell(hunter, "Il y avait une prime sur la tete de " + dead.getName().getString()
                        + " : tu viens de gagner 3000 $ !", ChatFormatting.YELLOW);
            }
            return;
        }
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }
        int[] butchered = BUTCHERS.get(player.getUUID());
        if (butchered != null && event.getEntity().entityTags().contains(BUTCHER)) {
            butchered[0]++;
            LuckyWorld.bar(player, "Zombies tues : " + butchered[0], ChatFormatting.GREEN);
        }
        int[] hunt = HUNTS.get(player.getUUID());
        if (hunt != null) {
            hunt[0]++;
            huntReward(player, hunt[0]);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ENGLISH.remove(id);
        SILENT.remove(id);
        BROKE_SILENCE.remove(id);
        BREAKS.remove(id);
        BUTCHERS.remove(id);
        if (event.getEntity() instanceof ServerPlayer player && FORGES.containsKey(id)) {
            endForge(player);
        }
    }

    // -- Le metier de reve d'une generation -----------------------------------------------------

    /**
     * Astronaute ({@code lb09_cockpit}, {@code lb09_lune}) : une capsule autour du joueur, dix
     * secondes de compte a rebours, le decollage, le noir, puis une minute sur la Lune avant le
     * retour sur Terre.
     */
    private static void dreamJob(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 back = player.position();
        BlockPos base = player.blockPosition();
        List<BlockPos> capsule = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 2, 1))) {
            boolean seat = at.getX() == base.getX() && at.getZ() == base.getZ() && at.getY() < base.getY() + 2;
            if (seat || !level.getBlockState(at).canBeReplaced()) {
                continue;
            }
            BlockState state = at.getY() == base.getY() + 2 ? Blocks.IRON_BLOCK.defaultBlockState()
                    : Blocks.GLASS.defaultBlockState();
            if (LuckyWorld.place(player, level, at.immutable(), state)) {
                capsule.add(at.immutable());
            }
        }
        SeasonKit.win(player, "Ton reve d'enfant se realise : tu es astronaute ! Decollage dans 10 secondes...");
        LuckyWorld.sound(level, back.x, back.y, back.z, "block.beacon.activate", SoundSource.PLAYERS, 1.0f, 0.6f);
        LuckyTasks.repeat(player, 0, 4, 50, step -> {
            level.sendParticles(step < 35 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.FLAME, back.x,
                    back.y + 0.2d, back.z, 6, 0.6d, 0.1d, 0.6d, 0.02d);
            if (step == 35 || step == 40 || step == 45) {
                SeasonKit.say(player, String.valueOf(3 - (step - 35) / 5) + "...");
            }
        });
        LuckyTasks.later(player, 200, () -> {
            SeasonKit.win(player, "Decollage !");
            LuckyWorld.sound(level, back.x, back.y, back.z, "entity.firework_rocket.launch", SoundSource.PLAYERS, 2.0f,
                    0.5f);
            LuckyWorld.sound(level, back.x, back.y, back.z, "entity.generic.explode", SoundSource.PLAYERS, 1.0f, 0.5f);
            player.addEffect(new MobEffectInstance(SeasonEffects.MESS, 60, 0, false, false));
        });
        LuckyTasks.later(player, 260, () -> player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0,
                false, false)));
        LuckyTasks.later(player, 320, () -> {
            removeOurs(level, capsule, Blocks.GLASS.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState());
            moon(player, level, back);
        });
    }

    /** La Lune : un disque de pierre grise criblee de cratere, a 200 blocs ; la gravite y est faible. */
    private static void moon(ServerPlayer player, ServerLevel level, Vec3 back) {
        int y = Math.min(200, level.getMaxY() - 12);
        BlockPos center = new BlockPos(player.getBlockX(), y, player.getBlockZ());
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-7, -1, -7), center.offset(7, 0, 7))) {
            int dx = at.getX() - center.getX();
            int dz = at.getZ() - center.getZ();
            boolean crater = (dx - 3) * (dx - 3) + (dz + 2) * (dz + 2) <= 2 || (dx + 3) * (dx + 3) + (dz - 3) * (dz - 3) <= 1;
            if (dx * dx + dz * dz > 49 || !level.getBlockState(at).isAir() || at.getY() == center.getY() && crater) {
                continue;
            }
            BlockState state = Math.floorMod(dx * 7 + dz * 3, 5) == 0 ? SeasonKit.block("light_gray_concrete")
                    : Blocks.END_STONE.defaultBlockState();
            if (LuckyWorld.place(player, level, at.immutable(), state)) {
                placed.add(at.immutable());
            }
        }
        BlockState dust = SeasonKit.block("light_gray_concrete");
        if (placed.size() < 80) {
            removeOurs(level, placed, dust, Blocks.END_STONE.defaultBlockState());
            player.teleportTo(back.x, back.y, back.z);
            SeasonKit.fail(player, "La fusee n'a pas pu se poser : retour au point de depart.");
            return;
        }
        player.teleportTo(center.getX() + 0.5d, center.getY() + 1.0d, center.getZ() + 0.5d);
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 1260, 2, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1260, 0, false, false));
        SeasonKit.win(player, "Bienvenue sur la Lune ! Retour sur Terre dans une minute.");
        SeasonKit.timer(player, "Sur la Lune", 60, p -> {
            removeOurs(level, placed, dust, Blocks.END_STONE.defaultBlockState());
            p.teleportTo(back.x, back.y, back.z);
            SeasonKit.softFall(p, 200);
            SeasonKit.say(p, "Retour sur Terre !");
        });
    }

    /** Retire les blocs poses par l'evenement, s'ils n'ont pas change entre-temps. */
    private static void removeOurs(ServerLevel level, List<BlockPos> placed, BlockState... states) {
        for (int index = placed.size() - 1; index >= 0; index--) {
            BlockState now = level.getBlockState(placed.get(index));
            for (BlockState state : states) {
                if (now.is(state.getBlock())) {
                    level.removeBlock(placed.get(index), false);
                    break;
                }
            }
        }
        placed.clear();
    }

    // -- Test d'anglais -------------------------------------------------------------------------

    private static final String[] QUESTIONS = {"Conjugue le verbe etre a la troisieme personne : he...",
            "Traduis le mot 'chat' : ...", "Traduis le mot 'trois' : ...",
            "Traduis en anglais la phrase suivante : 'Je vis en France' : ...",
            "Traduis en francais la phrase suivante : 'I'm ten years old' : ..."};
    private static final List<List<String>> ANSWERS = List.of(List.of("is"), List.of("cat"), List.of("three"),
            List.of("i live in france"), List.of("jai dix ans", "jai 10 ans"));

    /** Cinq questions, a repondre dans le chat ; une seule erreur et le jeu passe en anglais. */
    private static void englishTest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ENGLISH.put(player.getUUID(), 0);
        SeasonKit.say(player, "Le test d'anglais va commencer ! Reponds dans le chat.");
        SeasonKit.speak(player, "Professeur", QUESTIONS[0]);
    }

    private static void answerEnglish(ServerPlayer player, int question, String answer) {
        if (!ANSWERS.get(question).contains(answer)) {
            ENGLISH.remove(player.getUUID());
            SeasonKit.fail(player, "Vu que tu ne parles pas bien anglais, tu vas apprendre directement en jeu !");
            player.addEffect(new MobEffectInstance(SeasonEffects.ENGLISH, ENGLISH_TICKS, 0));
            return;
        }
        if (question + 1 == QUESTIONS.length) {
            ENGLISH.remove(player.getUUID());
            SeasonKit.win(player, "Bravo, tu as reussi le test d'anglais. Heureusement, car il etait super facile ;)");
            return;
        }
        ENGLISH.put(player.getUUID(), question + 1);
        SeasonKit.speak(player, "Professeur", QUESTIONS[question + 1]);
    }

    /** Sans accents, sans ponctuation, en minuscules : « J'ai dix ans. » devient « jai dix ans ». */
    private static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    // -- Pecule de vacances ---------------------------------------------------------------------

    /** L'agence de voyage ({@code lb09_agencevoyage}) : son agent offre un sejour au camping. */
    private static void holidayPay(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2, 3)),
                "Agent de voyage", 180, (p, agent) -> SeasonKit.next(p, "Agent de voyage", "Tu as bien travaille cette"
                        + " annee : voici ton pecule de vacances ! Un sejour sur une ile, tous frais payes.",
                        "Partir en vacances", q -> {
                            SeasonKit.dismiss(agent);
                            AugustEvents.holidays(q, level, LuckyType.SEPTEMBER, LuckyType.AUGUST);
                        }));
        SeasonKit.say(player, "Un agent de voyage t'attend. Fais un clic droit sur lui.");
    }

    // -- Secret professionnel -------------------------------------------------------------------

    /** Signer le contrat, puis trente minutes sans un mot dans le chat : le dossier de metier. */
    private static void secrecy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.ask(player, "Secret professionnel", "Acceptes-tu de signer ce contrat et de respecter le secret"
                + " professionnel ?", List.of("OUI", "NON"), (p, answer) -> {
            if (answer != 0) {
                SeasonKit.fail(p, "Tu as choisi de ne pas respecter le secret professionnel.");
                return;
            }
            SILENT.add(p.getUUID());
            BROKE_SILENCE.remove(p.getUUID());
            SeasonKit.win(p, "Tu dois respecter le secret professionnel pendant 30 minutes : pas un mot dans le chat !");
            SeasonKit.challenge(p, "Respecte le secret professionnel", 1800, true,
                    (q, age) -> BROKE_SILENCE.contains(q.getUUID()) ? SeasonKit.Outcome.LOST : SeasonKit.Outcome.GOING,
                    q -> {
                        SILENT.remove(q.getUUID());
                        SeasonKit.win(q, "Tu as respecte le secret professionnel. Voici ta recompense !");
                        SeasonKit.give(q, new ItemStack(SeasonItems.TRADE_FILE.get()));
                    }, q -> {
                        SILENT.remove(q.getUUID());
                        BROKE_SILENCE.remove(q.getUUID());
                        SeasonKit.fail(q, "Tu as parle : le secret professionnel est rompu.");
                    });
        });
    }

    // -- Le travail c'est la sante --------------------------------------------------------------

    /** Trente secondes : un bloc casse par seconde, sinon un coeur en moins. */
    private static void workIsHealth(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.win(player, "Le travail c'est la sante ! Casse des blocs pour ne pas mourir !");
        int[] count = {0, 0};
        BREAKS.put(player.getUUID(), count);
        int[] last = {0};
        SeasonKit.challenge(player, "Tu dois miner un bloc par seconde...", 30, true, (p, age) -> {
            if (age > 0 && age % 20 == 0) {
                if (count[0] <= last[0]) {
                    p.hurtServer(level, level.damageSources().magic(), 2.0f);
                }
                last[0] = count[0];
            }
            return p.isAlive() ? SeasonKit.Outcome.GOING : SeasonKit.Outcome.LOST;
        }, p -> BREAKS.remove(p.getUUID()), p -> BREAKS.remove(p.getUUID()));
    }

    // -- Le temps c'est de l'argent -------------------------------------------------------------

    private static void timeIsMoney(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.ask(player, "Le temps c'est de l'argent", "Tu preferes offrir 60 secondes de ton temps ou 300 $ de ton"
                + " argent ?", List.of("ARGENT (300 $)", "TEMPS (60 secondes)"), (p, choice) -> {
            if (choice == 0 && Economy.tryDebit(p, 300L)) {
                SeasonKit.win(p, "Tu as choisi de payer 300 $ pour ne pas perdre de temps !");
                return;
            }
            if (choice == 0) {
                SeasonKit.fail(p, "Tu n'as pas assez d'argent pour payer !");
            }
            SeasonKit.say(p, "Tu as choisi de perdre 60 secondes de ton temps !");
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200, 9, false, false));
            p.addEffect(new MobEffectInstance(PalaLucky.NO_JUMP, 1200, 0, false, false));
            SeasonKit.timer(p, "Ton temps t'est pris !", 60, q -> SeasonKit.say(q, "Tu as retrouve ton temps."));
        });
    }

    // -- La bonne fortune -----------------------------------------------------------------------

    /** Une pioche en paladium tiree au sort parmi six, de la plus simple a la bonne fortune. */
    private static void goodFortune(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<List<ResourceKey<Enchantment>>> rolls = List.of(List.of(),
                List.of(Enchantments.EFFICIENCY, Enchantments.SILK_TOUCH), List.of(Enchantments.UNBREAKING),
                List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING),
                List.of(Enchantments.EFFICIENCY, Enchantments.SILK_TOUCH, Enchantments.UNBREAKING),
                List.of(Enchantments.FORTUNE, Enchantments.EFFICIENCY, Enchantments.UNBREAKING));
        List<ResourceKey<Enchantment>> roll = rolls.get(level.getRandom().nextInt(rolls.size()));
        ItemStack pickaxe = SeasonKit.stack("palamod:paladium_pickaxe", 1);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> key : roll) {
            int strength = key == Enchantments.EFFICIENCY ? 5 : key == Enchantments.SILK_TOUCH ? 1 : 3;
            enchantments.get(key).ifPresent(holder -> pickaxe.enchant(holder, strength));
        }
        LuckyWorld.drop(level, pos, pickaxe);
        SeasonKit.win(player, "Tu viens de recevoir ta recompense !");
    }

    // -- La bonne paye --------------------------------------------------------------------------

    private static void goodPay(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String[] texts = {"Tu as depasse la limite de vitesse de 20 km/h : une amende de 500 $.",
                "Tu as depense 1250 $ dans de nouveaux vetements.",
                "Tu as mise 1000 $ sur un cheval. Il est tombe a la riviere : tu perds ta mise.",
                "Tu viens de gagner a la loterie la somme de 4000 $ !",
                "Tu as fini le mois de septembre. Ta paye pour ce mois est de 5000 $.",
                "Tu as mise 1000 $ sur un cheval, et il a gagne ! Tu remportes 3000 $."};
        long[] amounts = {-500L, -1250L, -1000L, 4000L, 5000L, 3000L};
        int pick = level.getRandom().nextInt(texts.length);
        SeasonKit.say(player, texts[pick]);
        if (amounts[pick] > 0L) {
            Economy.credit(player, amounts[pick]);
            SeasonKit.win(player, amounts[pick] + " $ ont ete deposes sur ton compte en banque.");
        } else {
            Economy.debit(player, -amounts[pick]);
            SeasonKit.fail(player, -amounts[pick] + " $ ont ete retires de ton compte en banque.");
        }
    }

    // -- Le metier de reve de la nouvelle generation --------------------------------------------

    /** Dix ouvriers ({@code EntityWorker}) envahissent le chantier une minute. */
    private static void workers(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<Entity> crew = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Mob worker = SeasonKit.mob(level, EntityTypes.ZOMBIE_VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player,
                    level, 3, 8)), "Ouvrier");
            if (worker != null) {
                worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                worker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                worker.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
                worker.setDropChance(EquipmentSlot.HEAD, 0.0f);
                worker.setTarget(player);
                crew.add(worker);
            }
        }
        SeasonKit.fail(player, "Le chantier deborde : les ouvriers s'en prennent a toi !");
        LuckyTasks.later(player, 1200, () -> crew.forEach(Entity::discard));
    }

    // -- Le marchand de sable -------------------------------------------------------------------

    private static void sandman(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.npc(player, level, EntityTypes.WANDERING_TRADER, Vec3.atBottomCenterOf(SeasonKit.around(player, level,
                2, 3)), "Marchand de sable", 180, (p, seller) -> SeasonKit.ask(p, "Marchand de sable",
                "Ca te dit de faire affaire avec moi ? Je te propose :", List.of("Un chateau de sable contre 1000 $",
                        "16 sables mouvants contre 1000 $", "2048 sables contre 500 $", "12 sables des ames contre 750 $",
                        "Rien, merci"), (q, choice) -> {
                    if (choice == 4) {
                        SeasonKit.dismiss(seller);
                        return;
                    }
                    long price = choice == 2 ? 500L : choice == 3 ? 750L : 1000L;
                    if (!Economy.tryDebit(q, price)) {
                        SeasonKit.fail(q, "Tu n'as pas assez d'argent pour payer !");
                        return;
                    }
                    SeasonKit.win(q, "Tu viens de payer " + price + " $ !");
                    SeasonKit.dismiss(seller);
                    switch (choice) {
                        case 0 -> AugustEvents.sandcastle(q, level, q.blockPosition());
                        case 1 -> SeasonKit.give(q, new ItemStack(SeasonItems.QUICKSAND.get(), 16));
                        case 2 -> {
                            for (int stack = 0; stack < 32; stack++) {
                                SeasonKit.give(q, new ItemStack(Items.SAND, 64));
                            }
                        }
                        default -> SeasonKit.give(q, new ItemStack(Items.SOUL_SAND, 12));
                    }
                }));
        SeasonKit.say(player, "Le marchand de sable est la. Fais un clic droit sur lui.");
    }

    // -- Enfin une prime ------------------------------------------------------------------------

    /**
     * Une prime de 3000 $ sur la tete du joueur, pour le prochain joueur qui le tue. <b>Ecart</b> :
     * seul sur le serveur, trois chasseurs de primes viennent la reclamer a leur facon.
     */
    private static void bounty(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BOUNTIES.add(player.getUUID());
        SeasonKit.say(player, "Il y a une prime sur ta tete : le prochain joueur qui te tuera obtiendra 3000 $ !");
        if (level.getServer().getPlayerCount() > 1) {
            return;
        }
        for (int index = 0; index < 3; index++) {
            Mob hunter = SeasonKit.mob(level, EntityTypes.VINDICATOR, Vec3.atBottomCenterOf(SeasonKit.around(player,
                    level, 8, 12)), "Chasseur de primes");
            if (hunter != null) {
                hunter.setTarget(player);
            }
        }
    }

    // -- C'est une vraie boucherie --------------------------------------------------------------

    /**
     * La boucherie ({@code lb09_boucherie}) : soixante zombies qui ne brulent pas, une minute pour en
     * tuer un maximum. 40 : de la chair ; 50 : une epee en paladium ; 60 : le couteau de boucher.
     */
    private static void butcher(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.win(player, "Tue un maximum de zombies en 60 secondes !");
        int[] kills = {0};
        BUTCHERS.put(player.getUUID(), kills);
        List<UUID> horde = new ArrayList<>();
        int[] spawned = {0};
        SeasonKit.challenge(player, "Tue un maximum de monstres pour gagner une recompense", 60, true, (p, age) -> {
            if (age % 10 == 0) {
                horde.removeIf(id -> {
                    Entity zombie = level.getEntity(id);
                    return zombie == null || !zombie.isAlive();
                });
                while (horde.size() < 12 && spawned[0] < 60) {
                    Mob zombie = SeasonKit.mob(level, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(SeasonKit.around(p,
                            level, 4, 9)), null);
                    if (zombie == null) {
                        break;
                    }
                    spawned[0]++;
                    zombie.addTag(BUTCHER);
                    zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0));
                    zombie.setTarget(p);
                    horde.add(zombie.getUUID());
                }
            }
            return SeasonKit.Outcome.GOING;
        }, p -> {
            clearHorde(level, horde);
            BUTCHERS.remove(p.getUUID());
            SeasonKit.win(p, "Tu as tue " + kills[0] + " zombies !");
            ItemStack reward = kills[0] >= 60 ? new ItemStack(SeasonItems.BUTCHER_KNIFE.get())
                    : kills[0] >= 50 ? SeasonKit.stack("palamod:paladium_sword", 1)
                    : kills[0] >= 40 ? new ItemStack(Items.ROTTEN_FLESH, 64) : ItemStack.EMPTY;
            if (!reward.isEmpty()) {
                SeasonKit.give(p, reward);
                SeasonKit.win(p, "Tu as gagne une recompense !");
            }
        }, p -> {
            clearHorde(level, horde);
            BUTCHERS.remove(p.getUUID());
        });
    }

    private static void clearHorde(ServerLevel level, List<UUID> horde) {
        for (UUID id : horde) {
            Entity zombie = level.getEntity(id);
            if (zombie != null) {
                zombie.discard();
            }
        }
        horde.clear();
    }

    // -- L'avocat du diable ---------------------------------------------------------------------

    /** Le tribunal ({@code lb09_tribunal}) : le juge accuse, le diable se defend, l'avocat plaide. */
    private static void court(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Defends le diable contre le jugement ! Parle au juge.");
        Mob[] devil = new Mob[1];
        Mob judge = SeasonKit.npc(player, level, EntityTypes.VILLAGER, Vec3.atBottomCenterOf(SeasonKit.around(player,
                level, 2, 3)), "Juge", 300, (p, npc) -> plead(p, npc, devil[0], 0));
        devil[0] = SeasonKit.npc(player, level, EntityTypes.BLAZE, Vec3.atBottomCenterOf(SeasonKit.around(player,
                level, 3, 4)), "Le diable", 300, (p, npc) -> SeasonKit.speak(p, "Le diable",
                "Ne me parle pas, parle au juge ! C'est lui qu'il faut convaincre."));
        if (judge == null && devil[0] != null) {
            SeasonKit.dismiss(devil[0]);
        }
    }

    private static void plead(ServerPlayer player, Entity judge, Entity devil, int round) {
        switch (round) {
            case 0 -> {
                SeasonKit.speak(player, "Juge", "Votre client est accuse d'avoir mis le feu a la foret.");
                SeasonKit.speak(player, "Le diable", "Je n'ai rien fait ! Demande-lui ses preuves.");
                SeasonKit.ask(player, "Juge", "Que repondez-vous ?", List.of("Monsieur le juge, quelles sont vos preuves ?",
                        "Mon client dit qu'il est innocent, croyez-le !", "Je ne suis pas avocat, que fais-je ici ?"),
                        (p, answer) -> verdict(p, judge, devil, answer == 0, round));
            }
            case 1 -> {
                SeasonKit.speak(player, "Juge", "Nous avons trouve d'etranges traces de pas. C'est surement lui !");
                SeasonKit.speak(player, "Le diable", "Moi, je flotte au-dessus du sol : je ne laisse jamais de traces !");
                SeasonKit.ask(player, "Juge", "Que repondez-vous ?", List.of("Mon client etait en train de dormir",
                        "Mon client flotte pour se deplacer, ce ne sont pas ses traces",
                        "Mon client avait des bottes neuves"), (p, answer) -> verdict(p, judge, devil, answer == 1, round));
            }
            default -> {
                SeasonKit.speak(player, "Juge", "Un temoin l'a entendu dire : \"Quand je dis mon nom, tout s'embrase !\"");
                SeasonKit.speak(player, "Le diable", "C'etait une facon de parler : mon nom fait de l'effet, c'est tout !");
                SeasonKit.ask(player, "Juge", "Que repondez-vous ?", List.of("C'est vrai, il met le feu partout",
                        "C'est une simple expression", "Il ne connait pas son propre nom"),
                        (p, answer) -> verdict(p, judge, devil, answer == 1, round));
            }
        }
    }

    private static void verdict(ServerPlayer player, Entity judge, Entity devil, boolean right, int round) {
        if (right && round < 2) {
            plead(player, judge, devil, round + 1);
            return;
        }
        if (right) {
            SeasonKit.speak(player, "Juge", "Votre argumentaire est bon. Votre client est libre... pour le moment.");
            SeasonKit.speak(player, "Le diable", "Bien plaide ! Voici quelques cadeaux diaboliques.");
            SeasonKit.win(player, "Felicitations, tu as bien defendu le diable !");
            SeasonKit.give(player, new ItemStack(LuckyGadgets.BLAZE_AND_STEEL.get()),
                    new ItemStack(SeasonItems.DAEMON_AMULET.get()));
        } else {
            SeasonKit.speak(player, "Juge", "Ce n'est pas la reponse que j'attends. Votre client ira en prison !");
            SeasonKit.fail(player, "Tu n'as pas reussi a defendre le diable !");
            LuckyWorld.sound((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), "ambient.cave",
                    SoundSource.HOSTILE, 1.0f, 0.8f);
        }
        SeasonKit.dismiss(judge);
        if (devil != null) {
            SeasonKit.dismiss(devil);
        }
    }

    // -- Le boss final --------------------------------------------------------------------------

    /**
     * Il faut gagner de l'experience pour voir le boss : au premier point gagne (en jeu ou dans un
     * metier, dans les dix minutes), un des monstres du chasseur apparait, gorge d'experience.
     */
    private static void finalBoss(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Il te faut gagner de l'experience pour voir le boss !");
        long start = experienceOf(player);
        SeasonKit.challenge(player, "Gagne de l'experience pour voir le boss", 600, false,
                (p, age) -> age % 10 == 0 && experienceOf(p) > start ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING,
                p -> spawnBoss(p, (ServerLevel) p.level()), p -> SeasonKit.say(p, "Le boss ne viendra pas cette fois."));
    }

    /** L'experience du jeu et celle des metiers, ensemble. */
    private static long experienceOf(ServerPlayer player) {
        long total = player.totalExperience;
        for (Job job : Job.values()) {
            total += JobApi.xp(player, job);
        }
        return total;
    }

    /** {@code EntityCavernousZombie}, {@code EntityFarmerChicken}, {@code EntityFlowerMonster}, {@code EntityMegaCreeper}. */
    private static void spawnBoss(ServerPlayer player, ServerLevel level) {
        int pick = level.getRandom().nextInt(4);
        Vec3 at = Vec3.atBottomCenterOf(SeasonKit.around(player, level, 3, 5));
        Mob boss = switch (pick) {
            case 0 -> SeasonKit.mob(level, EntityTypes.ZOMBIE, at, "Zombie des cavernes");
            case 1 -> SeasonKit.mob(level, EntityTypes.CHICKEN, at, "Poule fermiere");
            case 2 -> SeasonKit.mob(level, EntityTypes.ZOMBIE, at, "Monstre-fleur");
            default -> SeasonKit.mob(level, EntityTypes.CREEPER, at, "Mega Creeper");
        };
        if (boss == null) {
            return;
        }
        SeasonKit.stat(boss, Attributes.MAX_HEALTH, 50.0d);
        LuckyCreatures.tag(boss, LuckyCreatures.XP);
        switch (pick) {
            case 0 -> {
                boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                boss.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            }
            case 1 -> SeasonKit.stat(boss, Attributes.SCALE, 2.5d);
            case 2 -> {
                boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.ROSE_BUSH));
                boss.setDropChance(EquipmentSlot.HEAD, 0.0f);
                boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 12000, 0));
            }
            default -> SeasonKit.stat(boss, Attributes.SCALE, 1.6d);
        }
        if (pick == 0) {
            boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 12000, 0));
        }
        boss.setTarget(player);
        SeasonKit.fail(player, "Le boss est la : " + boss.getName().getString() + " !");
    }

    // -- Faire carriere -------------------------------------------------------------------------

    private static final int[] QUOTAS = {40, 50, 60, 75};
    private static final String[] CAREER_REWARDS = {"palamod:amethyst_block", "palamod:titane_block",
            "palamod:paladium_block", "palamod:green_paladium_block"};

    /**
     * La carriere ({@code CareerStructure}) : un bloc de pierre de 9 sur 9 devant le joueur. Miner 40,
     * puis 50, 60 et 75 pierres en trente secondes ; un bloc precieux par palier.
     */
    private static void career(ServerPlayer player, ServerLevel level, int stage, List<BlockPos> quarry) {
        String intro = stage == 0 ? "C'est l'heure de faire carriere ! Te sens-tu pret a miner " + QUOTAS[stage]
                + " pierres en 30 secondes ?" : "Palier suivant : " + QUOTAS[stage] + " pierres en 30 secondes ?";
        SeasonKit.ask(player, "Carriere", intro, List.of("C'est parti !", "J'arrete la"), (p, answer) -> {
            if (answer != 0) {
                clearQuarry(level, quarry);
                SeasonKit.say(p, "Sage decision.");
                return;
            }
            if (quarry.isEmpty()) {
                Vec3 look = p.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
                BlockPos center = BlockPos.containing(p.position().add(look.scale(7.0d)));
                for (BlockPos at : BlockPos.betweenClosed(center.offset(-4, 0, -4), center.offset(4, 2, 4))) {
                    quarry.add(at.immutable());
                }
            }
            int placed = 0;
            for (BlockPos at : quarry) {
                if (level.getBlockState(at).isAir() && LuckyWorld.place(p, level, at, Blocks.STONE.defaultBlockState())) {
                    placed++;
                }
            }
            if (stage == 0 && placed < QUOTAS[QUOTAS.length - 1]) {
                clearQuarry(level, quarry);
                SeasonKit.fail(p, "Impossible de generer la carriere ici.");
                return;
            }
            p.addEffect(new MobEffectInstance(MobEffects.HASTE, 600, 1));
            int[] count = {0, 1};
            BREAKS.put(p.getUUID(), count);
            SeasonKit.challenge(p, "Mine " + QUOTAS[stage] + " pierres", 30, false, (q, age) -> {
                if (age % 20 == 0) {
                    LuckyWorld.bar(q, "Pierres : " + count[0] + "/" + QUOTAS[stage], ChatFormatting.YELLOW);
                }
                return count[0] >= QUOTAS[stage] ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
            }, q -> {
                BREAKS.remove(q.getUUID());
                SeasonKit.give(q, SeasonKit.stack(CAREER_REWARDS[stage], 1));
                if (stage + 1 < QUOTAS.length) {
                    career(q, level, stage + 1, quarry);
                } else {
                    clearQuarry(level, quarry);
                    SeasonKit.win(q, "Felicitations, tu as fait une belle carriere ! Tu merites bien ces quelques"
                            + " blocs !");
                }
            }, q -> {
                BREAKS.remove(q.getUUID());
                clearQuarry(level, quarry);
                SeasonKit.fail(q, "Tu n'as pas reussi a faire carriere.");
            });
        });
    }

    private static void clearQuarry(ServerLevel level, List<BlockPos> quarry) {
        for (BlockPos at : quarry) {
            if (level.getBlockState(at).is(Blocks.STONE)) {
                level.removeBlock(at, false);
            }
        }
        quarry.clear();
    }

    // -- Beaucoup d'experience dans le domaine --------------------------------------------------

    private static final int[] HUNT_STEPS = {100, 500, 1000};

    /** Tuer des monstres : un livre « Experience » au centieme, au cinq-centieme, au millieme. */
    private static void experience(ServerPlayer player, ServerLevel level, BlockPos pos) {
        HUNTS.putIfAbsent(player.getUUID(), new int[]{0});
        SeasonKit.say(player, "C'est tout un art de gagner de l'experience. Et sur Minecraft, rien de tel que de tuer"
                + " des monstres : un livre enchante a 100, 500 et 1000 monstres tues !");
    }

    private static void huntReward(ServerPlayer player, int kills) {
        for (int index = 0; index < HUNT_STEPS.length; index++) {
            if (kills != HUNT_STEPS[index]) {
                continue;
            }
            int strength = index + 1;
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("palamod", "experience")))
                    .ifPresent(holder -> EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, strength)));
            SeasonKit.give(player, book);
            SeasonKit.win(player, "Tu as gagne un livre enchante !");
            if (index == HUNT_STEPS.length - 1) {
                HUNTS.remove(player.getUUID());
            }
        }
    }

    // -- C'est en forgeant qu'on devient forgeron -----------------------------------------------

    private static final String[] FORGE_TEXTS = {"la Pickaxe of the gods (trois pioches, deux batons en paladium)",
            "les bottes et les jambieres de voyage", "le plastron de saut et le casque en slime"};
    private static final String[][] FORGE_GOALS = {{"palamod:god_pickaxe"},
            {"palamod:travel_boots", "palamod:travel_leggings"}, {"palamod:travel_jumpchest", "palamod:travel_slimyhelmet"}};
    private static final String[] FORGE_REWARDS = {"palamod:amethyst_anvil", "palamod:titane_anvil",
            "palamod:paladium_anvil"};
    private static final int[] FORGE_SECONDS = {45, 30, 30};

    /**
     * L'epreuve du forgeron ({@code BlackSmithStep}) : trois etapes. Les materiaux sont fournis, il
     * faut fabriquer a temps ; materiaux et fabrications sont repris a la fin de chaque etape, une
     * enclume recompense chaque etape reussie.
     */
    private static void forge(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (FORGES.containsKey(player.getUUID())) {
            SeasonKit.fail(player, "Tu passes deja l'epreuve du forgeron.");
            return;
        }
        BlockPos table = SeasonKit.around(player, level, 1, 2);
        boolean placed = level.getBlockState(table).isAir()
                && LuckyWorld.place(player, level, table, Blocks.CRAFTING_TABLE.defaultBlockState());
        Forge forge = new Forge(placed ? table : null, level);
        FORGES.put(player.getUUID(), forge);
        SeasonKit.win(player, "L'epreuve du forgeron commence ! Un etabli t'attend.");
        forgeStep(player, forge, 0);
    }

    private static List<ItemStack> materials(int step) {
        return switch (step) {
            case 0 -> List.of(SeasonKit.stack("palamod:amethyst_ingot", 3), SeasonKit.stack("palamod:titane_ingot", 3),
                    SeasonKit.stack("palamod:paladium_ingot", 5), new ItemStack(Items.STICK, 6));
            case 1 -> List.of(new ItemStack(Items.FEATHER, 4), SeasonKit.stack("palamod:titane_ingot", 4),
                    new ItemStack(Items.LEATHER, 3));
            default -> List.of(new ItemStack(Items.FEATHER, 2), new ItemStack(Items.IRON_INGOT, 2),
                    new ItemStack(Items.LEATHER, 4), new ItemStack(Items.SLIME_BALL, 3), new ItemStack(Items.DIAMOND, 2));
        };
    }

    private static void forgeStep(ServerPlayer player, Forge forge, int step) {
        forge.step = step;
        forge.crafted.clear();
        forge.done.clear();
        forge.before = inventory(player);
        List<Item> goals = new ArrayList<>();
        for (String id : FORGE_GOALS[step]) {
            goals.add(SeasonKit.stack(id, 1).getItem());
        }
        forge.goals = goals;
        for (ItemStack material : materials(step)) {
            SeasonKit.give(player, mark(material));
        }
        SeasonKit.say(player, "Etape " + (step + 1) + "/3 : commencee. Tu as " + FORGE_SECONDS[step]
                + " secondes pour fabriquer " + FORGE_TEXTS[step] + ".");
        SeasonKit.challenge(player, "Etape " + (step + 1) + "/3 : forge", FORGE_SECONDS[step], false,
                (p, age) -> forge.done.containsAll(goals) ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING, p -> {
                    takeBack(p, forge);
                    SeasonKit.give(p, SeasonKit.stack(FORGE_REWARDS[step], 1));
                    SeasonKit.win(p, "Etape " + (step + 1) + "/3 : validee");
                    if (step == 2) {
                        endForge(p);
                        SeasonKit.win(p, "Tu as reussi l'epreuve du forgeron !");
                        return;
                    }
                    SeasonKit.next(p, "Forgeron", "Bien travaille ! Pret pour l'etape suivante ?", "Continuer l'epreuve",
                            q -> {
                                if (FORGES.get(q.getUUID()) == forge) {
                                    forgeStep(q, forge, step + 1);
                                }
                            });
                }, p -> {
                    endForge(p);
                    SeasonKit.fail(p, "Tu as echoue a l'epreuve du forgeron car tu as ete trop lent !");
                });
    }

    /** Fin de l'epreuve : tout ce qui vient d'elle est repris, l'etabli repart. */
    private static void endForge(ServerPlayer player) {
        Forge forge = FORGES.remove(player.getUUID());
        if (forge == null) {
            return;
        }
        takeBack(player, forge);
        if (forge.table != null && forge.level.getBlockState(forge.table).is(Blocks.CRAFTING_TABLE)) {
            forge.level.removeBlock(forge.table, false);
        }
    }

    /** Les materiaux marques, et ce qui a ete fabrique en plus de ce que le joueur avait au depart. */
    private static void takeBack(ServerPlayer player, Forge forge) {
        player.closeContainer();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (marked(inventory.getItem(slot))) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        Map<Item, Integer> now = inventory(player);
        for (Item item : forge.crafted) {
            int extra = now.getOrDefault(item, 0) - forge.before.getOrDefault(item, 0);
            if (extra > 0) {
                take(inventory, item, extra);
            }
        }
        forge.crafted.clear();
        inventory.setChanged();
    }

    private static Map<Item, Integer> inventory(ServerPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Retire {@code amount} objets, les objets sans enchantement d'abord : ceux qui sortent de l'etabli. */
    private static void take(Inventory inventory, Item item, int amount) {
        int left = amount;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(item) && (pass == 1 || !stack.isEnchanted())) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
    }

    private static ItemStack mark(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putBoolean(FORGE_MARK, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static boolean marked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(FORGE_MARK, false);
    }

    private static final class Forge {
        private final BlockPos table;
        private final ServerLevel level;
        private final Set<Item> crafted = new HashSet<>();
        private final Set<Item> done = new HashSet<>();
        private Map<Item, Integer> before = Map.of();
        private List<Item> goals = List.of();
        private int step;

        Forge(BlockPos table, ServerLevel level) {
            this.table = table;
            this.level = level;
        }
    }

    // -- Le vrai boss final ---------------------------------------------------------------------

    /**
     * Le vrai boss final ({@code RealFinalBossTask}) : un garde du corps en costume, cent points de
     * vie, rapide. Le tuer ou lui survivre deux minutes : la valise du boss et 2500 points
     * d'experience.
     */
    private static void realFinalBoss(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Mob guard = SeasonKit.mob(level, EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(SeasonKit.around(player, level, 6,
                8)), null);
        if (guard == null) {
            return;
        }
        guard.setCustomName(Component.literal("Le vrai boss final").withStyle(ChatFormatting.DARK_RED));
        guard.setCustomNameVisible(true);
        guard.setSilent(true);
        SeasonKit.stat(guard, Attributes.MAX_HEALTH, 100.0d);
        DyedItemColor suit = new DyedItemColor(0x1E1E24);
        for (var piece : List.of(Map.entry(EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE),
                Map.entry(EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS), Map.entry(EquipmentSlot.FEET, Items.LEATHER_BOOTS))) {
            ItemStack cloth = new ItemStack(piece.getValue());
            cloth.set(DataComponents.DYED_COLOR, suit);
            guard.setItemSlot(piece.getKey(), cloth);
            guard.setDropChance(piece.getKey(), 0.0f);
        }
        guard.addEffect(new MobEffectInstance(MobEffects.SPEED, 2400, 0));
        guard.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0));
        guard.setTarget(player);
        LuckyWorld.sound(level, guard.getX(), guard.getY(), guard.getZ(), "event.raid.horn", SoundSource.HOSTILE, 2.0f,
                1.0f);
        SeasonKit.fail(player, "Attention, le vrai boss final arrive ! Tue-le ou survis deux minutes !");
        SeasonKit.challenge(player, "Le vrai boss final", 120, true, (p, age) -> {
            if (!p.isAlive()) {
                return SeasonKit.Outcome.LOST;
            }
            if (age % 20 == 0 && guard.isAlive() && guard.getTarget() == null) {
                guard.setTarget(p);
            }
            return guard.isAlive() ? SeasonKit.Outcome.GOING : SeasonKit.Outcome.WON;
        }, p -> {
            if (guard.isAlive()) {
                guard.discard();
            }
            SeasonKit.give(p, SeasonKit.named(SeasonKit.stack("minecraft:brown_shulker_box", 1), "Valise du boss",
                    "Permet de stocker des objets sur 27 emplacements"));
            p.giveExperiencePoints(2500);
            SeasonKit.win(p, "Tu as vaincu le vrai boss final ! Voici sa valise.");
        }, p -> {
            if (guard.isAlive()) {
                guard.discard();
            }
            SeasonKit.fail(p, "Le vrai boss final a eu raison de toi.");
        });
    }
}
