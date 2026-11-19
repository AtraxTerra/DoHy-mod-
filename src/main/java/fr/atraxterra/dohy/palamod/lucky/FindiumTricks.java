package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.palamod.defense.PalaDefense;
import fr.atraxterra.dohy.spawn.SpawnApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Les evenements du deuxieme lot pour le lucky block en findium : ceux qui
 * tiennent avec le jeu et le mod, sans objet ni creature propres a PalaMod.
 */
final class FindiumTricks {

    /** {@code new PotionEffect(..., 4800, 0)} : les six effets de « Ne bois pas ca ». */
    private static final int DRINK_TICKS = 4800;
    /** {@code tryPlaceBlock(..., 249/254, ...)} : la cage de verre de « Ca fait haut ». */
    private static final int HIGH_FLOOR = 249;
    /** {@code spawnerCount} : les spawners de « Invocateur ». */
    private static final int SPAWNERS = 9;
    /** La bataille de regard dure trente secondes, la position est remise toutes les deux tics. */
    private static final int STARE_TICKS = 30 * 20;
    /** Les objets que « Duplicata » ne copie pas ({@code bannedItems}, dans le nom de l'objet). */
    private static final List<String> NOT_COPIED = List.of("drawer", "lucky", "safechest");
    /** La couleur de la bouteille d'eau : les deux potions de « Ne bois pas ca » se ressemblent. */
    private static final int WATER = 0x385DC6;

    private FindiumTricks() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.findium("villanul", "Villa-nul", "villanul", false, 100, FindiumTricks::villaNul);
        c.findium("dontmove", "Reste a ta place", "dontmove", true, 100, (player, level, pos) ->
                home(player, false));
        c.findium("eyebattle", "Bataille de regard", "eyebattle", true, 100, FindiumTricks::eyeBattle);
        c.findium("minedterrain", "Terrain mine", "terrain_mine", true, 150, FindiumTricks::minedTerrain);
        c.findium("perecastor", "Pere Castor", "pere_castor", false, 150, FindiumTricks::pereCastor);
        c.findium("wetfirecracker", "Petard mouille", "wet_firecracker", false, 150, FindiumTricks::wetFirecracker);
        c.findium("end", "End", "end", true, 200, (player, level, pos) -> mobs(level, EntityTypes.ENDERMAN, pos, 20));
        c.findium("chaos_professor", "Professeur Chaos", "professeur_chaos", true, 200, FindiumTricks::chaos);
        c.findium("dont_drink_that", "Ne bois pas ca", "bois_pas_ca", false, 200, (player, level, pos) ->
                drink(level, pos, List.of(MobEffects.STRENGTH, MobEffects.HASTE, MobEffects.FIRE_RESISTANCE,
                        MobEffects.SPEED, MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION)));
        c.findium("dont_drink_that_bad", "Ne bois pas ca", "bois_pas_ca", true, 200, (player, level, pos) ->
                drink(level, pos, List.of(MobEffects.BLINDNESS, MobEffects.NAUSEA, MobEffects.MINING_FATIGUE,
                        MobEffects.HUNGER, MobEffects.SLOWNESS, MobEffects.POISON)));
        c.findium("havefun", "Have Fun", "have_fun", true, 200, FindiumTricks::haveFun);
        c.findium("wolf_enemy", "L'ennemi des loups", "l_ennemi_des_loups", true, 300, FindiumTricks::wolfEnemy);
        c.findium("fuzecoordinates", "Coordonnees de Fuze", "coordonnes_de_fuze", true, 300,
                FindiumTricks::fuzeCoordinates);
        c.findium("itshigh", "Ca fait haut", "ca_fait_haut", true, 300, FindiumTricks::itsHigh);
        c.findium("mobtower", "Tour a mob", "tour_a_mob", true, 300, FindiumTricks::mobTower);
        c.findium("rantamplan", "Rantamplan", "rantamplan", false, 400, FindiumTricks::rantamplan);
        c.findium("injouable", "Injouable", "injouable", false, 500, (player, level, pos) ->
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 20)));
        c.findium("stayathome", "Reste a la maison", "reste_a_la_maison", true, 700, (player, level, pos) ->
                home(player, true));
        c.findium("its_not_allowed", "Je crois que c'est interdit", "je_crois_que_c_est_interdit", false, 800,
                FindiumTricks::minecart);
        c.findium("headonshoulders", "La tete sur les epaules", "la_tete_sur_les_epaules", false, 1000,
                FindiumTricks::head);
        c.findium("invocator", "Invocateur", "invocateur", true, 1000, FindiumTricks::invocator);
        c.findium("dirty_inventory", "Inventaire plein", "inventaire_plein", true, 5000, (player, level, pos) -> {
            for (int index = 0; index < 20; index++) {
                player.getInventory().add(new ItemStack(Blocks.DIRT, 64));
            }
        });
        c.findium("duplicata", "Duplicata", "duplicata", false, 10000, FindiumTricks::duplicata);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static <T extends Mob> void mobs(ServerLevel level, EntityType<T> type, BlockPos pos, int count) {
        for (int index = 0; index < count; index++) {
            T mob = LuckyWorld.create(level, type, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
            if (mob != null) {
                level.addFreshEntity(mob);
            }
        }
    }

    /** Un villageois qui ne vend rien ({@code EntityGodVillager}, type 3) : un villageois benet, nomme. */
    private static void villaNul(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var villager = LuckyWorld.create(level, EntityTypes.VILLAGER, pos.getX() + 0.5d, pos.getY(),
                pos.getZ() + 0.5d);
        if (villager == null) {
            return;
        }
        CompoundTag data = new CompoundTag();
        CompoundTag profession = new CompoundTag();
        profession.putString("profession", "minecraft:nitwit");
        profession.putString("type", "minecraft:plains");
        profession.putInt("level", 1);
        data.put("VillagerData", profession);
        float yaw = villager.getYRot();
        villager.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
        villager.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, yaw, 0.0f);
        villager.setCustomName(Component.literal("Villa-nul").withStyle(ChatFormatting.GOLD));
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
    }

    /**
     * Un home pris au hasard ({@code HomeManager}). DoHy n'a pas de homes de
     * joueur : ce sont ceux de la faction. Sans home, « Reste a ta place » le dit,
     * « Reste a la maison » ramene au spawn ({@code getSpawnLocation}).
     */
    private static void home(ServerPlayer player, boolean spawnInstead) {
        FactionData data = FactionApi.data(player);
        List<FactionData.Home> homes = data.exists() ? List.copyOf(data.homes()) : List.of();
        if (homes.isEmpty()) {
            if (spawnInstead) {
                SpawnApi.backToPad(player);
            } else {
                player.sendSystemMessage(Component.literal("[Lucky Block] ").withStyle(ChatFormatting.GOLD,
                        ChatFormatting.BOLD).append(Component.literal("Mince, vous n'avez pas de Home")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
            }
            return;
        }
        FactionData.Home home = homes.get(player.getRandom().nextInt(homes.size()));
        Identifier id = Identifier.tryParse(home.dimension());
        ServerLevel level = id == null ? null
                : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            return;
        }
        player.teleport(new TeleportTransition(level, new Vec3(home.x(), home.y(), home.z()), Vec3.ZERO, home.yaw(),
                player.getXRot(), TeleportTransition.DO_NOTHING));
    }

    /**
     * Trente secondes les yeux dans les yeux avec un enderman : le joueur est
     * remis en place toutes les deux tics, la tete levee vers lui ; l'enderman,
     * sans IA et soigne a chaque fois, compte les secondes ({@code EyeBattle}).
     */
    private static void eyeBattle(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var enderman = LuckyWorld.create(level, EntityTypes.ENDERMAN, pos.getX() + 0.5d, pos.getY(),
                pos.getZ() + 1.5d);
        if (enderman == null) {
            return;
        }
        enderman.setNoAi(true);
        enderman.setCustomNameVisible(true);
        level.addFreshEntity(enderman);
        double x = pos.getX() + 0.5d;
        double y = pos.getY();
        double z = pos.getZ() + 0.5d;
        player.teleportTo(x, y, z);
        LuckyWorld.tell(player, "Vous commencez une bataille de regard...", ChatFormatting.YELLOW);
        LuckyTasks.repeat(player, 0, 2, STARE_TICKS / 2 + 1, step -> {
            if (enderman.isRemoved()) {
                return;
            }
            if (step >= STARE_TICKS / 2) {
                enderman.discard();
                return;
            }
            enderman.setHealth(enderman.getMaxHealth());
            enderman.setCustomName(Component.literal("Je t'aurais ! ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(String.valueOf(30 - step / 10)).withStyle(ChatFormatting.YELLOW)));
            player.teleportTo(level, x, y, z, java.util.Set.of(), 5.0f, -45.0f, false);
            enderman.snapTo(player.getX(), player.getY(), player.getZ() + 1.0d, -190.0f, -70.0f);
            enderman.setYHeadRot(-190.0f);
        });
    }

    /** Le bloc laisse place a de la terre, du sable, de la pierre... qui explose si on le casse sans toucher de soie. */
    private static void minedTerrain(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
            return;
        }
        Block[] blocks = {Blocks.SAND, Blocks.DIRT, Blocks.STONE, Blocks.GRAVEL, Blocks.COBBLESTONE,
                Blocks.GRASS_BLOCK};
        Block block = blocks[level.getRandom().nextInt(blocks.length)];
        if (LuckyWorld.place(player, level, pos, block.defaultBlockState())) {
            LuckyEffectEvents.mine(level, pos, block);
        }
    }

    /**
     * Un petit livre du Pere Castor ({@code PereCastor} : Thesee et le Minotaure,
     * 34 pages). L'histoire est racontee ici avec d'autres mots, plus courte.
     */
    private static void pereCastor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<String> pages = List.of(
                "Il etait une fois, en Crete, un roi nomme Minos. Dans les profondeurs de son palais vivait le "
                        + "Minotaure, une bete a corps d'homme et a tete de taureau.",
                "Pour l'enfermer, Minos fit appel a Dedale, le plus habile des architectes. Dedale dessina un "
                        + "labyrinthe si tortueux que nul n'en trouvait la sortie.",
                "Chaque annee, Athenes devait envoyer sept jeunes gens et sept jeunes filles. On les poussait dans "
                        + "le labyrinthe, et personne ne les revoyait jamais.",
                "Un jour, Thesee, fils du roi d'Athenes, demanda a partir avec eux. Il jura de mettre fin a ce "
                        + "tribut et de revenir vivant.",
                "En Crete, la princesse Ariane le vit et s'eprit de lui. Elle lui offrit une pelote de fil : "
                        + "attache-le a l'entree, lui dit-elle, et deroule-le en marchant.",
                "Thesee entra dans la nuit des couloirs. Il marcha longtemps, le fil glissant entre ses doigts, "
                        + "guide par les rugissements de la bete.",
                "Au coeur du labyrinthe, le Minotaure chargea. Le combat fut terrible, mais Thesee l'emporta et "
                        + "la bete ne se releva pas.",
                "Il n'eut plus qu'a suivre le fil d'Ariane pour retrouver la lumiere, et ses compagnons avec lui.",
                "Ainsi finit le tribut d'Athenes. Et depuis, quand on se perd, on cherche toujours son fil "
                        + "d'Ariane.",
                "Fin. Et toi, sauras-tu sortir de ta mine ?");
        List<Filterable<Component>> content = new ArrayList<>();
        for (String page : pages) {
            content.add(Filterable.passThrough(Component.literal(page)));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Une petite histoire..."), "Pere Castor", 0, content, true));
        give(player, book);
    }

    /** Trente detonations pour rien, une par seconde, autour du joueur ({@code WetFirecracker}). */
    private static void wetFirecracker(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyTasks.repeat(player, 0, 20, 30, step -> {
            if (!(player.level() instanceof ServerLevel here)) {
                return;
            }
            RandomSource random = here.getRandom();
            double x = player.getX() + random.nextFloat() * 3.0f - 1.0f;
            double y = player.getY() + random.nextFloat() * 3.0f - 1.0f;
            double z = player.getZ() + random.nextFloat() * 3.0f - 1.0f;
            here.sendParticles(ParticleTypes.POOF, x, y, z, 10, 0.1d, 0.1d, 0.1d, 0.1d);
            here.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        });
    }

    /** Vingt echanges de cases au hasard dans le sac ({@code ChaosProfessor}). */
    private static void chaos(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        var inventory = player.getInventory();
        for (int index = 0; index < 20; index++) {
            int first = random.nextInt(36);
            int second = random.nextInt(36);
            ItemStack a = inventory.getItem(first);
            ItemStack b = inventory.getItem(second);
            inventory.setItem(first, b);
            inventory.setItem(second, a);
        }
    }

    /**
     * Une bouteille sans etiquette, « Ne bois pas ca ! » : six effets de quatre
     * minutes, tous bons ou tous mauvais. Les deux bouteilles se ressemblent et
     * ne disent pas ce qu'elles font.
     */
    private static void drink(ServerLevel level, BlockPos pos, List<Holder<MobEffect>> effects) {
        List<MobEffectInstance> instances = new ArrayList<>();
        for (Holder<MobEffect> effect : effects) {
            instances.add(new MobEffectInstance(effect, DRINK_TICKS, 0));
        }
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(WATER),
                instances, Optional.empty()));
        potion.set(DataComponents.CUSTOM_NAME, Component.literal("Ne bois pas ca !").withStyle(ChatFormatting.RED));
        LinkedHashSet<net.minecraft.core.component.DataComponentType<?>> hidden = new LinkedHashSet<>();
        hidden.add(DataComponents.POTION_CONTENTS);
        potion.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, hidden));
        LuckyWorld.drop(level, pos, potion);
    }

    /**
     * Une boite d'obsidienne durcie autour du joueur ({@code HaveFun}), avec ses
     * bizarreries : sol et plafond de 4 x 4, murs qui descendent trois blocs sous
     * les pieds.
     */
    private static void haveFun(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState obsidian = PalaDefense.HARDENED_OBSIDIAN.get().defaultBlockState();
        BlockPos feet = player.blockPosition();
        int x = feet.getX();
        int y = feet.getY();
        int z = feet.getZ();
        for (int i = x - 2; i < x + 2; i++) {
            for (int k = z - 2; k < z + 2; k++) {
                hard(player, level, new BlockPos(i, y - 1, k), obsidian);
                hard(player, level, new BlockPos(i, y + 2, k), obsidian);
            }
        }
        for (int i = x - 2; i < x + 2; i++) {
            for (int j = y - 3; j < y + 2; j++) {
                hard(player, level, new BlockPos(i, j, z - 2), obsidian);
                hard(player, level, new BlockPos(i, j, z + 2), obsidian);
            }
        }
        for (int k = z - 2; k < z + 2; k++) {
            for (int j = y - 3; j < y + 2; j++) {
                hard(player, level, new BlockPos(x - 2, j, k), obsidian);
                hard(player, level, new BlockPos(x + 2, j, k), obsidian);
            }
        }
    }

    /** {@code tryUpdateBlock} : rien sur la bedrock, rien la ou on n'a pas le droit. */
    private static void hard(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
            LuckyWorld.place(player, level, pos, state);
        }
    }

    /** Vingt loups enrages, lances sur le joueur ({@code setAngry}, {@code setTarget}). */
    private static void wolfEnemy(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 20; index++) {
            Mob wolf = LuckyWorld.create(level, EntityTypes.WOLF, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
            if (wolf == null) {
                continue;
            }
            level.addFreshEntity(wolf);
            wolf.setLastHurtByMob(player);
            if (wolf instanceof NeutralMob angry) {
                angry.setPersistentAngerTarget(EntityReference.of(player));
                angry.startPersistentAngerTimer();
            }
            wolf.setTarget(player);
        }
    }

    /** Un livre « Coordonnees de Fuze »... qui n'en contient pas. */
    private static void fuzeCoordinates(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Coordonnees  de Fuze"), "bigh4ck3r", 0,
                List.of(Filterable.passThrough(Component.literal(
                        "Voila les coordonnees de Fuze : https://www.youtube.com/watch?v=dQw4w9WgXcQ"))), true));
        book.set(DataComponents.CUSTOM_NAME, Component.literal("Coordonnees de Fuze"));
        give(player, book);
    }

    /**
     * Une cage de verre tout en haut du monde (de 249 a 254), et le joueur
     * dedans. Pour en sortir, il faut casser le verre... et tomber.
     */
    private static void itsHigh(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        int floor = Math.min(HIGH_FLOOR, level.getMaxY() - 6);
        int x = pos.getX();
        int z = pos.getZ();
        for (int i = x - 4; i < x + 4; i++) {
            for (int k = z - 4; k < z + 4; k++) {
                hard(player, level, new BlockPos(i, floor, k), glass);
                hard(player, level, new BlockPos(i, floor + 5, k), glass);
            }
        }
        for (int i = x - 4; i < x + 4; i++) {
            for (int j = floor; j < floor + 5; j++) {
                hard(player, level, new BlockPos(i, j, z - 4), glass);
                hard(player, level, new BlockPos(i, j, z + 4), glass);
            }
        }
        for (int k = z - 4; k < z + 4; k++) {
            for (int j = floor; j < floor + 5; j++) {
                hard(player, level, new BlockPos(x - 4, j, k), glass);
                hard(player, level, new BlockPos(x + 4, j, k), glass);
            }
        }
        player.teleportTo(x + 0.5d, floor + 3.0d, z + 0.5d);
    }

    /**
     * De 34 a 46 monstres armes et en fer : zombies a l'epee, squelettes a l'arc,
     * plus rapides (0,43) et plus forts (8) que de coutume.
     */
    private static void mobTower(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        int count = random.nextInt(13) + 34;
        for (int index = 0; index < count; index++) {
            boolean zombie = random.nextDouble() < 0.5d;
            double x = pos.getX() + 0.5d;
            double z = pos.getZ() + 0.5d;
            Mob mob = zombie ? LuckyWorld.create(level, EntityTypes.ZOMBIE, x, pos.getY(), z)
                    : LuckyWorld.create(level, EntityTypes.SKELETON, x, pos.getY(), z);
            if (mob == null) {
                continue;
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(zombie ? Items.IRON_SWORD : Items.BOW));
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.43d);
            }
            var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attack != null) {
                attack.setBaseValue(8.0d);
            }
            level.addFreshEntity(mob);
        }
    }

    /** Cinq loups apprivoises, en pleine sante. */
    private static void rantamplan(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (int index = 0; index < 5; index++) {
            TamableAnimal wolf = LuckyWorld.create(level, EntityTypes.WOLF, pos.getX() + 0.5d, pos.getY(),
                    pos.getZ() + 0.5d);
            if (wolf == null) {
                continue;
            }
            wolf.tame(player);
            wolf.setHealth(wolf.getMaxHealth());
            wolf.setPersistenceRequired();
            level.addFreshEntity(wolf);
        }
    }

    /** Un wagonnet vide, la ou etait le bloc. */
    private static void minecart(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var minecart = LuckyWorld.create(level, EntityTypes.MINECART, pos.getX() + 0.5d, pos.getY(),
                pos.getZ() + 0.5d);
        if (minecart != null) {
            level.addFreshEntity(minecart);
        }
    }

    /** La tete d'un autre joueur en ligne, prevenu ; seul, on recupere la sienne. */
    private static void head(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<ServerPlayer> others = new ArrayList<>(level.getServer().getPlayerList().getPlayers());
        others.remove(player);
        ServerPlayer owner = player;
        if (others.isEmpty()) {
            LuckyWorld.tell(player, "Aucun joueur a l'horizon. Vous avez recupere votre propre tete.",
                    ChatFormatting.RED);
        } else {
            owner = others.get(level.getRandom().nextInt(others.size()));
            LuckyWorld.tell(owner, player.getName().getString() + " a recupere votre tete dans un LuckyBlock :o",
                    ChatFormatting.YELLOW);
        }
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(owner.getGameProfile()));
        give(player, skull);
    }

    /**
     * Neuf spawners dans les premieres cases d'air autour du joueur, chacun d'un
     * monstre au hasard. PalaMod n'en posait qu'aux coordonnees positives (un
     * oubli) ; ici partout.
     */
    private static void invocator(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<EntityType<?>> types = List.of(EntityTypes.SKELETON, EntityTypes.ZOMBIE, EntityTypes.SPIDER,
                EntityTypes.CREEPER, EntityTypes.BLAZE, EntityTypes.ENDERMAN);
        BlockPos feet = player.blockPosition();
        int placed = 0;
        for (int x = feet.getX() - 3; x < feet.getX() + 3 && placed < SPAWNERS; x++) {
            for (int y = feet.getY() - 2; y < feet.getY() + 4 && placed < SPAWNERS; y++) {
                for (int z = feet.getZ() - 3; z < feet.getZ() + 3 && placed < SPAWNERS; z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (!level.getBlockState(at).isAir()
                            || !LuckyWorld.place(player, level, at, Blocks.SPAWNER.defaultBlockState())) {
                        continue;
                    }
                    if (level.getBlockEntity(at) instanceof SpawnerBlockEntity spawner) {
                        spawner.setEntityId(types.get(level.getRandom().nextInt(types.size())), level.getRandom());
                        spawner.setChanged();
                    }
                    placed++;
                }
            }
        }
    }

    /**
     * L'objet en main en un exemplaire de plus ; la main vide (ou un objet
     * interdit), un exemplaire de chaque objet du sac. Les tiroirs, les lucky
     * blocks et les coffres-forts ne se copient pas.
     */
    private static void duplicata(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && copyable(held)) {
            give(player, held.copyWithCount(1));
            return;
        }
        var inventory = player.getInventory();
        List<ItemStack> copies = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && copyable(stack)) {
                copies.add(stack.copyWithCount(1));
            }
        }
        for (ItemStack copy : copies) {
            inventory.add(copy);
        }
    }

    private static boolean copyable(ItemStack stack) {
        Item item = stack.getItem();
        String name = (item.getDescriptionId() + " "
                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).toLowerCase(java.util.Locale.ROOT);
        for (String banned : NOT_COPIED) {
            if (name.contains(banned)) {
                return false;
            }
        }
        return true;
    }
}
