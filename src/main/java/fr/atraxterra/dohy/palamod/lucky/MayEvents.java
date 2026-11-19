package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.boost.PalaXpPotions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le lucky block de mai ({@code luckyevents/may} et {@code monthly/modules/may}) : dix-neuf
 * evenements, entre fleurs, defis et familiers.
 *
 * <p>Ecarts : PalaMod n'a pas de familiers ici, les recompenses de « Quand on aime on ne compte
 * pas » deviennent de l'experience ; « Meilleur ami » accepte, en solo, un animal apprivoise ;
 * « Que la force soit avec toi » devient « Le chevalier sombre », sans le casque ni le sabre du
 * film ; les histoires de « Une vieille histoire » sont racontees autrement ; le champ de fleurs
 * est construit ici (le fichier de structure de PalaMod n'est pas dans le jar).
 */
@EventBusSubscriber
public final class MayEvents {

    private static final Map<UUID, Dummy> DUMMIES = new HashMap<>();

    private MayEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.season(LuckyType.MAY, "test_force", "Test de force", "may/test_force", false, 100, MayEvents::strengthTest);
        c.season(LuckyType.MAY, "mlg_pro", "MLG pro", "may/mlg_pro", true, 110, MayEvents::mlg);
        c.season(LuckyType.MAY, "vieille_histoire", "Une vieille histoire", "may/vieille_histoire", false, 110,
                MayEvents::oldStory);
        c.season(LuckyType.MAY, "champ_de_fleur", "Champ de fleur", "may/champ_de_fleur", false, 130,
                MayEvents::flowerField);
        c.season(LuckyType.MAY, "creepy", "Creepy", "may/creepy", true, 130, MayEvents::creepy);
        c.season(LuckyType.MAY, "oh_la_vache", "Oh la vache !", "may/oh_la_vache", false, 130, MayEvents::cows);
        c.season(LuckyType.MAY, "repas_de_roi", "Un repas de roi", "may/repas_de_roi", false, 130, MayEvents::king);
        c.season(LuckyType.MAY, "coffre_mai", "Coffre de mai", "may/coffre_mai", false, 140,
                (player, level, pos) -> SeasonKit.codeRoom(player, level, "Coffre de mai", new int[]{2, 5, 1},
                        List.of(SeasonKit.stack("palamod:green_paladium_block", 2),
                                SeasonKit.stack("palamod:paladium_block", 3),
                                new ItemStack(SeasonItems.STONE_HEARTH.get()), LuckyType.PALADIUM.stack(2))));
        c.season(LuckyType.MAY, "faire_boom", "Ca va faire BOOM", "may/faire_boom", true, 140, MayEvents::megaCreeper);
        c.season(LuckyType.MAY, "on_aime_on_compte_pas", "Quand on aime on ne compte pas (les levels)",
                "may/aime_compte_pas", false, 150, (player, level, pos) -> {
                    SeasonKit.say(player, "Tu aimes tes animaux et ca se voit ! Et si on arretait de compter grace a"
                            + " ces supers recompenses ?");
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                            new ItemStack(Items.EXPERIENCE_BOTTLE, 16));
                    LuckyWorld.drop(level, player.getX(), player.getY(), player.getZ(),
                            new ItemStack(Items.GOLDEN_CARROT, 8));
                });
        c.season(LuckyType.MAY, "affaire_est_dans_le_sac", "L'affaire est dans le sac", "may/affaire_est_dans_le_sac",
                false, 160, MayEvents::bags);
        c.season(LuckyType.MAY, "aucune_avancee", "Aucune avancee", "may/aucune_avancee", true, 170,
                (player, level, pos) -> player.addEffect(new MobEffectInstance(SeasonEffects.NO_FORWARD, 6000, 0)));
        c.season(LuckyType.MAY, "va_te_laver", "Va te laver !", "may/va_te_laver", true, 170, MayEvents::lavaBath);
        c.season(LuckyType.MAY, "force_avec_toi", "Le chevalier sombre", "may/force_avec_toi", false, 180,
                MayEvents::darkKnight);
        c.season(LuckyType.MAY, "vie_en_rose", "La vie en rose", "may/vie_en_rose", true, 180, MayEvents::pinkLife);
        c.season(LuckyType.MAY, "meilleur_ami", "Meilleur ami", "may/meilleur_ami", false, 200, MayEvents::bestFriend);
        c.season(LuckyType.MAY, "homme_invisible", "L'homme invisible", "may/homme_invisible", false, 210,
                MayEvents::invisibleMan);
        c.season(LuckyType.MAY, "cage_doree", "Cage doree", "may/cage_doree", true, 300, MayEvents::goldenCage);
        c.season(LuckyType.MAY, "fete_travail", "Fete du travail", "may/fete_travail", false, 750, MayEvents::laborDay);
    }

    // -- Test de force --------------------------------------------------------------------------

    private static final class Dummy {
        final UUID owner;
        double damage;
        int seconds = 30;

        Dummy(UUID owner) {
            this.owner = owner;
        }
    }

    /** Un mannequin trente secondes ; les degats a l'epee comptent ({@code EntityStrengthStand}). */
    private static void strengthTest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 look = player.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
        Vec3 at = player.position().add(look.scale(2.0d));
        ArmorStand stand = LuckyWorld.create(level, EntityTypes.ARMOR_STAND, at.x, player.getY(), at.z);
        if (stand == null) {
            return;
        }
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Component.literal("30 s - 0 degats").withStyle(ChatFormatting.AQUA));
        level.addFreshEntity(stand);
        Dummy dummy = new Dummy(player.getUUID());
        DUMMIES.put(stand.getUUID(), dummy);
        SeasonKit.win(player, "Un mannequin de force est apparu devant toi. Inflige-lui un maximum de degats pour"
                + " obtenir une recompense (utilise une epee).");
        SeasonKit.timer(player, "Effectue un maximum de degats au mannequin", 30, p -> {
            DUMMIES.remove(stand.getUUID());
            stand.discard();
            ItemStack reward = dummy.damage > 2500.0d ? SeasonKit.stack("palamod:god_stick", 1)
                    : dummy.damage > 1250.0d ? SeasonKit.stack("palamod:paladium_block", 1)
                    : dummy.damage >= 250.0d ? SeasonKit.stack("palamod:paladium_sword", 1) : ItemStack.EMPTY;
            SeasonKit.say(p, "Degats infliges : " + Math.round(dummy.damage));
            if (reward.isEmpty()) {
                SeasonKit.fail(p, "Pas assez fort pour une recompense !");
            } else {
                LuckyWorld.drop(level, stand.getX(), stand.getY() + 0.5d, stand.getZ(), reward);
            }
        });
        LuckyTasks.repeat(player, 20, 20, 29, second -> {
            dummy.seconds = 29 - second;
            stand.setCustomName(Component.literal(dummy.seconds + " s - " + Math.round(dummy.damage) + " degats")
                    .withStyle(ChatFormatting.AQUA));
        });
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Dummy dummy = DUMMIES.get(event.getTarget().getUUID());
        if (dummy == null) {
            return;
        }
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getUUID().equals(dummy.owner)
                || !player.getMainHandItem().is(ItemTags.SWORDS)) {
            return;
        }
        double hit = player.getAttributeValue(Attributes.ATTACK_DAMAGE) * player.getAttackStrengthScale(0.5f);
        if (player.fallDistance > 0.0d && !player.onGround()) {
            hit *= 1.5d;
        }
        dummy.damage += hit * 10.0d;
        player.resetAttackStrengthTicker();
        Entity target = event.getTarget();
        target.setCustomName(Component.literal(dummy.seconds + " s - " + Math.round(dummy.damage) + " degats")
                .withStyle(ChatFormatting.AQUA));
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.2d, target.getZ(), 6, 0.3d, 0.4d,
                    0.3d, 0.1d);
            level.playSound(null, target.blockPosition(), SoundEvents.ARMOR_STAND_HIT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    // -- MLG pro --------------------------------------------------------------------------------

    /** Un seau d'eau en main, et un grand saut : trente secondes ou la chute laisse un demi-coeur. */
    private static void mlg(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Inventory inventory = player.getInventory();
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            int free = inventory.getFreeSlot();
            if (free < 0) {
                SeasonKit.fail(player, "Tu n'as pas de place dans ton inventaire... Dommage !");
                return;
            }
            inventory.setItem(free, held.copy());
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 20, false, false));
        SeasonKit.softFall(player, 600);
        SeasonKit.say(player, "MLG pro ! Pose l'eau avant de toucher le sol.");
    }

    // -- Une vieille histoire -------------------------------------------------------------------

    private static final String[][] STORIES = {
            {"Un porteur d'eau avait deux jarres, dont une fendue qui perdait la moitie de son eau en chemin. Honteuse,"
                    + " elle s'en excusa. Le porteur lui montra alors les fleurs qui poussaient de son seul cote du"
                    + " sentier : il y avait seme des graines, et chaque jour elle les avait arrosees.",
                    "Nos defauts peuvent devenir une force", "Il faut toujours reparer ce qui fuit",
                    "Les fleurs n'aiment pas l'eau"},
            {"Un homme vit un papillon peiner a sortir de son cocon. Pour l'aider, il agrandit le trou. Le papillon"
                    + " sortit sans effort... mais ses ailes, restees faibles, ne purent jamais le porter.",
                    "Il faut laisser les papillons tranquilles", "L'effort fait partie de la croissance",
                    "Un couteau est toujours utile"},
            {"Un jeune ange visita deux salles identiques : une table, une marmite, et des cuilleres trop longues pour"
                    + " manger seul. Dans la premiere, tout le monde avait faim. Dans la seconde, chacun nourrissait"
                    + " son voisin, et tous etaient heureux.",
                    "Les longues cuilleres sont pratiques", "Le ragout est meilleur partage froid",
                    "S'entraider rend la vie meilleure"}};
    private static final int[] MORALS = {0, 1, 2};

    private static void oldStory(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int index = level.getRandom().nextInt(STORIES.length);
        String[] story = STORIES[index];
        SeasonKit.speak(player, "Conteur", "J'ai une histoire pour toi !");
        SeasonKit.speak(player, "Conteur", story[0]);
        SeasonKit.ask(player, "Conteur", "Quelle est la morale de cette histoire ?", List.of(story[1], story[2],
                story[3]), (p, answer) -> {
            if (answer != MORALS[index]) {
                SeasonKit.fail(p, "Perdu, ce n'est pas la bonne reponse !");
                return;
            }
            SeasonKit.win(p, "Felicitations, c'est la bonne reponse ! Voici le livre de cette histoire en recompense.");
            SeasonKit.give(p, SeasonKit.named(new ItemStack(Items.BOOK), "Une vieille histoire", story[1] + "."));
            SeasonKit.give(p, SeasonKit.stack("palamod:paladium_ingot", 4));
        });
    }

    // -- Champ de fleur -------------------------------------------------------------------------

    /** Cinq minutes dans un pre fleuri, tout la-haut, avec des cisailles a toucher de soie. */
    private static void flowerField(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 back = player.position();
        int y = Math.min(250, level.getMaxY() - 4);
        BlockPos center = new BlockPos(player.getBlockX(), y, player.getBlockZ());
        RandomSource random = level.getRandom();
        String[] flowers = {"dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"};
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-7, 0, -7), center.offset(7, 0, 7))) {
            if (at.distSqr(center) > 56.0d || !level.getBlockState(at).isAir()) {
                continue;
            }
            if (LuckyWorld.place(player, level, at.immutable(), Blocks.GRASS_BLOCK.defaultBlockState())) {
                placed.add(at.immutable());
                BlockPos above = at.above();
                if (random.nextInt(3) != 0 && level.getBlockState(above).isAir()) {
                    BlockState flower = SeasonKit.block(flowers[random.nextInt(flowers.length)]);
                    if (LuckyWorld.place(player, level, above, flower)) {
                        placed.add(above);
                    }
                }
            }
        }
        if (placed.size() < 40) {
            SeasonKit.fail(player, "Il n'a pas ete possible de faire pousser le champ ici.");
            return;
        }
        player.teleportTo(center.getX() + 0.5d, center.getY() + 1.0d, center.getZ() + 0.5d);
        ItemStack shears = new ItemStack(Items.SHEARS);
        level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .get(Enchantments.SILK_TOUCH).ifPresent(silk -> shears.enchant(silk, 1));
        SeasonKit.give(player, shears);
        SeasonKit.timer(player, "Champ de fleur", 300, p -> {
            for (int index = placed.size() - 1; index >= 0; index--) {
                BlockPos at = placed.get(index);
                if (!level.getBlockState(at).isAir()) {
                    level.removeBlock(at, false);
                }
            }
            p.teleportTo(back.x, back.y, back.z);
            SeasonKit.win(p, "Tu as ete teleporte a ton point de depart !");
        });
    }

    // -- Creepy ---------------------------------------------------------------------------------

    /** Cinq minutes : des creepers qui font peur apparaissent pres du joueur (12 a 30 secondes). */
    private static void creepy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Fais attention, de mechants creepers vont apparaitre pres de toi !");
        int[] next = {240 + level.getRandom().nextInt(361)};
        SeasonKit.challenge(player, "Des creepers qui font peur vont apparaitre pres de toi !", 300, true, (p, age) -> {
            if (age >= next[0]) {
                next[0] = age + 240 + level.getRandom().nextInt(361);
                Vec3 look = p.getLookAngle().multiply(1.0d, 0.0d, 1.0d).normalize();
                Creeper creeper = SeasonKit.mob(level, EntityTypes.CREEPER, p.position().subtract(look.scale(2.5d)),
                        null);
                if (creeper != null) {
                    creeper.addTag(LuckyWorldEvents.FAKE_CREEPER_TAG);
                    creeper.ignite();
                }
            }
            return SeasonKit.Outcome.GOING;
        }, p -> {
        }, p -> {
        });
    }

    // -- Oh la vache ----------------------------------------------------------------------------

    private static void cows(ServerPlayer player, ServerLevel level, BlockPos pos) {
        String[] names = {"Vache en costume", "Vache de Noel", null};
        for (int index = 0; index < 3; index++) {
            Vec3 at = Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2, 4));
            if (index == 2) {
                SeasonKit.mob(level, EntityTypes.MOOSHROOM, at, null);
            } else {
                SeasonKit.mob(level, EntityTypes.COW, at, names[index]);
            }
        }
        SeasonKit.say(player, "Oh la vache ! Regarde toutes ces vaches !");
    }

    // -- Un repas de roi ------------------------------------------------------------------------

    /** Le Roi de la galette attend huit galettes (huit pains) ; cinq minutes. */
    private static void king(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vec3 at = Vec3.atBottomCenterOf(SeasonKit.around(player, level, 2, 3));
        var king = SeasonKit.npc(player, level, EntityTypes.VILLAGER, at, "Roi de la galette", 300,
                (p, npc) -> SeasonKit.speak(p, "Roi de la galette", "Jette-moi 8 galettes (du pain), j'ai faim !"));
        if (king == null) {
            return;
        }
        king.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        SeasonKit.win(player, "Le Roi de la galette est apparu ! Jette-lui 8 galettes (du pain) pour obtenir une"
                + " recompense.");
        int[] eaten = {0};
        SeasonKit.challenge(player, "Amene 8 galettes au Roi de la galette", 300, false, (p, age) -> {
            if (!king.isAlive()) {
                return SeasonKit.Outcome.LOST;
            }
            if (age % 10 == 0) {
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, king.getBoundingBox().inflate(1.0d),
                        item -> item.getItem().is(Items.BREAD))) {
                    int take = Math.min(item.getItem().getCount(), 8 - eaten[0]);
                    item.getItem().shrink(take);
                    eaten[0] += take;
                    LuckyWorld.sound(level, king.getX(), king.getY(), king.getZ(), "entity.generic.eat",
                            SoundSource.NEUTRAL, 1.0f, 1.0f);
                }
            }
            return eaten[0] >= 8 ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
        }, p -> {
            SeasonKit.win(p, "Tu as donne 8 galettes au Roi de la galette et as recu une recompense.");
            LuckyWorld.drop(level, king.getX(), king.getY() + 1.0d, king.getZ(), SeasonKit.named(
                    new ItemStack(Items.GOLDEN_HELMET), "Couronne du roi"));
            LuckyWorld.drop(level, king.getX(), king.getY() + 1.0d, king.getZ(),
                    SeasonKit.stack("palamod:paladium_block", 1));
            SeasonKit.dismiss(king);
        }, p -> {
            SeasonKit.fail(p, "Le Roi de la galette est reparti le ventre vide.");
            if (king.isAlive()) {
                SeasonKit.dismiss(king);
            }
        });
    }

    // -- Ca va faire BOOM -----------------------------------------------------------------------

    private static void megaCreeper(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Creeper creeper = SeasonKit.mob(level, EntityTypes.CREEPER, player.position().add(2.0d, 0.0d, 0.0d),
                "Mega Creeper");
        if (creeper == null) {
            return;
        }
        creeper.setCustomName(Component.literal("Mega Creeper").withStyle(ChatFormatting.AQUA));
        SeasonKit.stat(creeper, Attributes.SCALE, 4.5d);
        SeasonKit.stat(creeper, Attributes.MAX_HEALTH, 1024.0d);
        creeper.setTarget(player);
    }

    // -- L'affaire est dans le sac --------------------------------------------------------------

    /** Trois sacs fermes : on en choisit un, et l'on decouvre ce qu'il cache ({@code SCDealInBagPacket}). */
    private static void bags(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.ask(player, "L'affaire est dans le sac", "Trois sacs, un seul pour toi. Lequel choisis-tu ?",
                List.of("Le sac de gauche", "Le sac du milieu", "Le sac de droite"), (p, choice) -> {
                    int roll = p.getRandom().nextInt(100);
                    ItemStack reward = roll < 49 ? SeasonKit.stack("palamod:dungeon_leather_backpack", 1)
                            : roll < 64 ? SeasonKit.stack("palamod:dungeon_backpack_t2", 1)
                            : roll < 74 ? new ItemStack(LuckyGadgets.SLEEPING_BAG.get())
                            : roll < 89 ? new ItemStack(Items.BUNDLE)
                            : roll < 95 ? SeasonKit.stack("palamod:dungeon_backpack_t3", 1)
                            : SeasonKit.stack("palamod:dungeon_backpack_t2", 1);
                    LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), reward);
                    SeasonKit.win(p, "Tu viens de recevoir ta recompense !");
                });
    }

    // -- Va te laver ----------------------------------------------------------------------------

    /** Une minute pour toucher de la lave ; sinon, la lave vient a toi. */
    private static void lavaBath(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Va vite te baigner dans la lave !");
        SeasonKit.challenge(player, "Rentre en contact avec de la lave", 60, false,
                (p, age) -> p.isInLava() ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING,
                p -> SeasonKit.win(p, "Tu as reussi a te baigner dans la lave !"), p -> {
                    SeasonKit.fail(p, "Tu n'as pas reussi a te baigner dans la lave !");
                    LuckyWorld.place(p, level, p.blockPosition(), Blocks.LAVA.defaultBlockState());
                });
    }

    // -- Le chevalier sombre --------------------------------------------------------------------

    /** Un chevalier en armure noire, 200 points de vie ({@code EntityDarkKnight}). */
    private static void darkKnight(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var knight = SeasonKit.mob(level, EntityTypes.WITHER_SKELETON, Vec3.atBottomCenterOf(
                SeasonKit.around(player, level, 4, 6)), "Chevalier sombre");
        if (knight == null) {
            return;
        }
        knight.setCustomName(Component.literal("Chevalier sombre").withStyle(ChatFormatting.DARK_RED));
        SeasonKit.stat(knight, Attributes.MAX_HEALTH, 200.0d);
        SeasonKit.stat(knight, Attributes.ATTACK_DAMAGE, 12.0d);
        SeasonKit.stat(knight, Attributes.FOLLOW_RANGE, 64.0d);
        knight.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        knight.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        knight.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        knight.setDropChance(EquipmentSlot.CHEST, 0.0f);
        SeasonKit.loot(knight, SeasonKit.named(new ItemStack(Items.DIAMOND_SWORD), "Lame du chevalier sombre"),
                SeasonKit.stack("palamod:paladium_block", 1));
        knight.setTarget(player);
        SeasonKit.say(player, "Un chevalier sombre te defie en duel !");
    }

    // -- La vie en rose -------------------------------------------------------------------------

    /** Cinq minutes en rose ; qui tient jusqu'au bout recoit un foyer de pierre. */
    private static void pinkLife(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.addEffect(new MobEffectInstance(SeasonEffects.PINK, 6000, 0));
        SeasonKit.win(player, "Vois la vie en rose jusqu'au bout pour obtenir une recompense.");
        SeasonKit.challenge(player, "Vois la vie en rose jusqu'au bout", 300, true,
                (p, age) -> age > 20 && !p.hasEffect(SeasonEffects.PINK) ? SeasonKit.Outcome.LOST : SeasonKit.Outcome.GOING,
                p -> {
                    SeasonKit.win(p, "Tu as recu une recompense !");
                    SeasonKit.give(p, new ItemStack(SeasonItems.STONE_HEARTH.get()));
                }, p -> {
                });
    }

    // -- Meilleur ami ---------------------------------------------------------------------------

    /**
     * Trouver un ami (un joueur, ou en solo un animal apprivoise) dans les cinq blocs, puis rester pres
     * de lui cinq minutes.
     */
    private static void bestFriend(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Trouve vite un ami (un joueur ou ton animal) et reste pres de lui pour obtenir une"
                + " recompense.");
        UUID[] friend = {null};
        int[] since = {-1};
        SeasonKit.challenge(player, "Place-toi pres d'un ami et reste pres de lui", 360, false, (p, age) -> {
            if (age % 20 != 0) {
                return SeasonKit.Outcome.GOING;
            }
            Entity current = friend[0] == null ? null : level.getEntity(friend[0]);
            if (current == null) {
                for (Entity near : level.getEntities(p, p.getBoundingBox().inflate(5.0d), entity -> entity instanceof
                        ServerPlayer || entity instanceof TamableAnimal tame && tame.isOwnedBy(p))) {
                    friend[0] = near.getUUID();
                    since[0] = age;
                    SeasonKit.win(p, "Tu viens de te lier d'amitie avec " + near.getName().getString()
                            + ". Reste a moins de 5 blocs pendant 5 minutes.");
                    break;
                }
                return SeasonKit.Outcome.GOING;
            }
            if (current.distanceTo(p) > 5.0d) {
                return SeasonKit.Outcome.LOST;
            }
            return age - since[0] >= 6000 ? SeasonKit.Outcome.WON : SeasonKit.Outcome.GOING;
        }, p -> {
            SeasonKit.win(p, "Tu as obtenu une recompense pour etre reste proche de ton meilleur ami.");
            LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), new ItemStack(SeasonItems.PLAYER_SADDLE.get()));
        }, p -> SeasonKit.fail(p, "Tu as echoue a te lier d'amitie."));
    }

    // -- L'homme invisible ----------------------------------------------------------------------

    /** Quinze minutes sans qu'aucun joueur ne t'apercoive a moins de quinze blocs. */
    private static void invisibleMan(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "Sois invisible comme un fantome : si tu n'es pas vu pendant 15 minutes, tu obtiendras"
                + " une recompense.");
        SeasonKit.challenge(player, "Reste invisible pour obtenir une recompense", 900, true, (p, age) -> {
            if (age % 20 == 0) {
                for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(15.0d),
                        other -> other != p)) {
                    SeasonKit.fail(p, "Tu as ete repere par " + other.getName().getString() + " !");
                    return SeasonKit.Outcome.LOST;
                }
            }
            return SeasonKit.Outcome.GOING;
        }, p -> {
            SeasonKit.win(p, "Tu as reussi a rester invisible, voici ta recompense !");
            List<ItemStack> rewards = List.of(new ItemStack(SeasonItems.GHOST_CAPE.get()),
                    SeasonKit.stack("palamod:invisible_helmet", 1), SeasonKit.stack("palamod:invisible_chestplate", 1),
                    SeasonKit.stack("palamod:invisible_leggings", 1), SeasonKit.stack("palamod:invisible_boots", 1),
                    new ItemStack(SeasonItems.GHOSTLY_SWORD.get()));
            LuckyWorld.drop(level, p.getX(), p.getY(), p.getZ(), rewards.get(p.getRandom().nextInt(rewards.size())));
        }, p -> {
        });
    }

    // -- Cage doree -----------------------------------------------------------------------------

    /** Une cage de faux or, 7 x 7 ; une petite consolation dedans. */
    private static void goldenCage(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = player.blockPosition();
        SeasonKit.shell(player, level, center.offset(-3, -1, -3), center.offset(3, 5, 3),
                SeasonItems.FAKE_GOLD_BLOCK.get().defaultBlockState());
        SeasonKit.win(player, "Tu es enferme dans une cage doree, mais tu as recu une petite recompense pour te"
                + " consoler !");
        SeasonKit.give(player, new ItemStack(Items.GOLD_NUGGET, 8));
    }

    // -- Fete du travail ------------------------------------------------------------------------

    /** Trente minutes sans gagner d'experience de metier. */
    private static void laborDay(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SeasonKit.say(player, "C'est la fete du travail et tu ne dois donc pas travailler ! N'obtiens pas d'experience"
                + " de metier pendant 30 minutes pour avoir une recompense.");
        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 36000, 0, false, false));
        long start = jobXp(player);
        SeasonKit.challenge(player, "Ne gagne pas d'experience de metier", 1800, true,
                (p, age) -> age % 20 == 0 && jobXp(p) > start ? SeasonKit.Outcome.LOST : SeasonKit.Outcome.GOING,
                p -> {
                    SeasonKit.win(p, "Bravo, tu t'es bien repose ! Voici ta recompense.");
                    SeasonKit.give(p, PalaXpPotions.stack(2, 60));
                }, p -> {
                    p.removeEffect(MobEffects.SATURATION);
                    SeasonKit.fail(p, "Tu as travaille pendant la fete du travail !");
                });
    }

    private static long jobXp(ServerPlayer player) {
        long total = 0L;
        for (Job job : Job.values()) {
            total += JobApi.xp(player, job);
        }
        return total;
    }
}
