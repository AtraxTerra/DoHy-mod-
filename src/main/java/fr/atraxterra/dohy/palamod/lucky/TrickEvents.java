package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.pillage.DynamiteEntity;
import fr.atraxterra.dohy.palamod.pillage.PalaPillage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Les evenements du deuxieme lot pour les lucky blocks en paladium et en
 * endium : ceux qui agissent sur le monde, sans objet ni creature propres a
 * PalaMod.
 */
final class TrickEvents {

    /** {@code Thread.sleep(25000)}, quatre fois, puis {@code getHeightValue + 201}. */
    private static final int SPAM_TP_PERIOD = 25 * 20;
    private static final int SPAM_TP_TIMES = 4;
    private static final int SPAM_TP_HEIGHT = 201;
    /** {@code createExplosion(player, x, y, z, 10.0f, true)} a l'impact de chaque boule de feu. */
    private static final byte GHAST_POWER = 10;
    /** {@code nextInt(30000) - 15000} : la base deco tombe n'importe ou dans ce carre. */
    private static final int BASE_RANGE = 15_000;
    /** {@code getHeightValue + 13}, une coque d'obsidienne de 18 blocs videe sur 15. */
    private static final int BASE_HEIGHT = 13;
    /** Les messages de « Hasta la vista », une seconde d'ecart, avant la fermeture. */
    private static final List<String> CRASH = List.of(
            "Malheureusement nous allons nous quitter d'ici 5 secondes",
            "Content d'avoir passe du temps avec toi",
            "Plus que 3 secondes :(",
            "Plus que 2 secondes :(",
            "T'es encore la ?",
            "Bye bye...");

    private TrickEvents() {
    }

    static void register(LuckyEvents.Catalog c) {
        c.classic("spam_tp", "Spam TP", "spam_tp", true, 50, TrickEvents::spamTp);
        c.classic("ghast", "Ghast dans l'ame", "ghast", true, 60, TrickEvents::ghast);
        c.classic("bol", "On voit pas le fond du bol", "on_voit_pas_le_fond_du_bol", true, 80, TrickEvents::bowl);
        c.classic("nether", "Nether active !", "nether", true, 100, TrickEvents::nether);
        c.classic("crash", "Hasta la vista", "hasta_la_vista", true, 200, TrickEvents::crash);
        c.classic("paladin", "Paladin", "paladin", true, 500, TrickEvents::paladin);
        c.classic("team_killer", "TeamKiller", "teamkiller", true, 800, TrickEvents::teamKiller);
        c.classic("who_are_you", "T ki ?", "t_ki", true, 1000, TrickEvents::whoAreYou);
        c.classic("dirt_is_good", "La dirt, c'est bien", "dirt", true, 1000, (player, level, pos) ->
                LuckyEffectEvents.dirt(player));
        c.classic("reveal", "Silence, ca tourne", "reveal", true, 2000, TrickEvents::reveal);
        c.classic("armaguedon", "Armaguedon", "armaguedon", true, 2000, TrickEvents::armaguedon);
        c.classic("wither_head", "Tete de wither", "wither", false, 100, (player, level, pos) -> {
            ItemStack gem = new ItemStack(fr.atraxterra.dohy.palamod.wither.PalaWithers.FUNNY_WITHER_GEM.get());
            gem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Tete de wither gentil").withStyle(ChatFormatting.YELLOW));
            LuckyWorld.drop(level, pos, gem);
        });
        c.classic("princess", "Princesse", "princesse", false, 200, TrickEvents::princess);
        c.classic("base_deco", "Base deco", "base_deco", false, 1000, TrickEvents::baseDeco);
    }

    /** Le dessus d'une colonne ({@code getHeightValue}) : le premier bloc d'air au-dessus du sol. */
    private static int top(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    /**
     * Quatre fois, toutes les 25 secondes : un nuage de fumee, et le joueur
     * repart 201 blocs au-dessus du sol. La chute fait le reste.
     */
    private static void spamTp(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyTasks.repeat(player, SPAM_TP_PERIOD, SPAM_TP_PERIOD, SPAM_TP_TIMES, step -> {
            if (!(player.level() instanceof ServerLevel here)) {
                return;
            }
            here.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0d, player.getZ(), 200,
                    0.4d, 0.8d, 0.4d, 0.0d);
            int x = net.minecraft.util.Mth.floor(player.getX());
            int z = net.minecraft.util.Mth.floor(player.getZ());
            double y = Math.min(top(here, x, z) + SPAM_TP_HEIGHT, here.getMaxY() - 2);
            player.teleportTo(player.getX(), y, player.getZ());
        });
    }

    /**
     * Quatre boules de feu partent du bloc vers les quatre points cardinaux
     * ({@code EntityFireball}, acceleration 1) ; chacune explose en puissance 10
     * la ou elle touche. Sans lanceur : rien ne casse dans un claim.
     */
    private static void ghast(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        CompoundTag power = new CompoundTag();
        power.putByte("ExplosionPower", GHAST_POWER);
        for (int[] direction : directions) {
            var fireball = EntityTypes.FIREBALL.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (fireball == null) {
                continue;
            }
            fireball.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), power.copy()));
            fireball.snapTo(pos.getX() + 0.5d + direction[0], pos.getY() + 0.5d, pos.getZ() + 0.5d + direction[1],
                    0.0f, 0.0f);
            fireball.setDeltaMovement(new Vec3(direction[0], 0.0d, direction[1]).scale(0.1d));
            level.addFreshEntity(fireball);
        }
    }

    /** Un bol de soupe ; le premier repas qui suit coiffe le joueur d'une citrouille. */
    private static void bowl(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ItemStack stew = new ItemStack(Items.MUSHROOM_STEW);
        stew.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("On ne voit pas le fond du ").withStyle(ChatFormatting.RED)
                        .append(Component.literal("bol").withStyle(ChatFormatting.YELLOW)));
        if (!player.getInventory().add(stew)) {
            player.drop(stew, false);
        }
        LuckyEffectEvents.bowl(player);
        LuckyWorld.bar(player, "Si tu as faim, tu es servi", ChatFormatting.AQUA);
    }

    /**
     * Un carre de 10 x 11 colonnes passe au nether : le dessus devient de la
     * netherrack, trois fois sur dix il prend feu, trois fois sur cent un
     * zombie-cochon y apparait.
     */
    private static void nether(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        for (int dx = -5; dx < 5; dx++) {
            for (int dz = -5; dz < 6; dz++) {
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                BlockPos ground = new BlockPos(x, top(level, x, z) - 1, z);
                LuckyWorld.place(player, level, ground, Blocks.NETHERRACK.defaultBlockState());
                if (random.nextInt(10) > 6) {
                    BlockPos above = ground.above();
                    LuckyWorld.place(player, level, above, BaseFireBlock.getState(level, above));
                }
                if (random.nextInt(100) > 96) {
                    var piglin = LuckyWorld.create(level, EntityTypes.ZOMBIFIED_PIGLIN, x + 0.5d,
                            ground.getY() + 1.0d, z + 0.5d);
                    if (piglin != null) {
                        level.addFreshEntity(piglin);
                    }
                }
            }
        }
    }

    /**
     * Six messages, une seconde d'ecart, puis le jeu « plante » : PalaMod
     * fermait le client ({@code PacketCrash}). Ici le joueur est renvoye du
     * monde avec ces mots, sans rien risquer de sa sauvegarde.
     */
    private static void crash(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyTasks.repeat(player, 0, 20, CRASH.size() + 1, step -> {
            if (step < CRASH.size()) {
                LuckyWorld.tell(player, CRASH.get(step), ChatFormatting.RED);
            } else {
                player.connection.disconnect(Component.literal("Hasta la vista"));
            }
        });
    }

    /** Un faux grade, et la verite au survol ({@code /tellraw}). */
    private static void paladin(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Component prank = Component.literal("Et non c'etait un prank ! Poce bleu :)")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
        player.sendSystemMessage(Component.literal("Bravo ! Vous avez gagne un grade paladin")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withUnderlined(true)
                        .withHoverEvent(new HoverEvent.ShowText(prank))));
    }

    /** Dix lapins tueurs ({@code setRabbitType(99)}), lances sur le joueur. */
    private static void teamKiller(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag killer = new CompoundTag();
        killer.putInt("RabbitType", 99);
        for (int index = 0; index < 10; index++) {
            Rabbit rabbit = LuckyWorld.create(level, EntityTypes.RABBIT, pos.getX() + 0.5d, pos.getY(),
                    pos.getZ() + 0.5d);
            if (rabbit == null) {
                continue;
            }
            float yaw = rabbit.getYRot();
            rabbit.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), killer.copy()));
            rabbit.snapTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, yaw, 0.0f);
            rabbit.setTarget(player);
            level.addFreshEntity(rabbit);
        }
    }

    /**
     * Trente secondes chez un autre joueur pris au hasard, puis retour a sa place
     * ({@code UsersManager.getInvincibles} : invincible pendant ce temps). Seul en
     * ligne, il ne se passe rien.
     */
    private static void whoAreYou(ServerPlayer player, ServerLevel level, BlockPos pos) {
        List<ServerPlayer> others = new ArrayList<>(level.getServer().getPlayerList().getPlayers());
        others.remove(player);
        if (others.isEmpty()) {
            LuckyWorld.tell(player, "Aucun autre joueur en ligne... dommage !", ChatFormatting.RED);
            return;
        }
        ServerPlayer target = others.get(level.getRandom().nextInt(others.size()));
        player.sendSystemMessage(Component.literal("Vous allez etre teleporte a ").withStyle(ChatFormatting.RED)
                .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" pendant 30 secondes puis revenir a votre position. Have Fun")
                        .withStyle(ChatFormatting.RED)));
        ServerLevel home = level;
        Vec3 back = player.position();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        // {@code runTaskAsyncLater(..., 5000)} puis trente secondes, compte a rebours compris.
        LuckyTasks.repeat(player, 100, 20, 31, step -> {
            if (step == 0) {
                if (!target.isRemoved() && target.level() instanceof ServerLevel there) {
                    player.setInvulnerable(true);
                    player.teleportTo(there, target.getX(), target.getY(), target.getZ(), java.util.Set.of(),
                            target.getYRot(), target.getXRot(), false);
                }
            }
            if (step < 30) {
                LuckyWorld.tell(player, (30 - step) + " secondes restantes...", ChatFormatting.RED);
                return;
            }
            player.teleportTo(home, back.x, back.y, back.z, java.util.Set.of(), yaw, pitch, false);
            player.setInvulnerable(false);
        });
    }

    /** Cinq secondes de compte a rebours, puis le joueur donne sa position a tout le serveur. */
    private static void reveal(ServerPlayer player, ServerLevel level, BlockPos pos) {
        LuckyTasks.repeat(player, 0, 20, 6, step -> {
            if (step < 5) {
                player.sendSystemMessage(Component.literal("Votre position sera revelee dans ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(String.valueOf(5 - step)).withStyle(ChatFormatting.DARK_RED))
                        .append(Component.literal(" secondes").withStyle(ChatFormatting.RED)));
                return;
            }
            String where = "Hi ! Je me trouve en " + player.getBlockX() + " " + player.getBlockY() + " "
                    + player.getBlockZ();
            player.level().getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + player.getName().getString() + "> " + where), false);
        });
    }

    /**
     * Dix vagues, une par seconde, de dix grosses dynamites lachees dix blocs
     * au-dessus du sol dans un carre de 20 blocs ({@code DynamiteEntity.BIG},
     * meche de 30).
     */
    private static void armaguedon(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        LuckyTasks.repeat(player, 20, 20, 10, step -> {
            for (int index = 0; index < 10; index++) {
                int x = pos.getX() + random.nextInt(20) - 10;
                int z = pos.getZ() + random.nextInt(20) - 10;
                DynamiteEntity dynamite = new DynamiteEntity(level, x + 0.5d, top(level, x, z) + 10.0d, z + 0.5d,
                        new ItemStack(PalaPillage.BIG_DYNAMITE.get()));
                level.addFreshEntity(dynamite.fuse(30));
            }
        });
    }

    /**
     * Un cube d'argile de 9 blocs pousse autour du bloc, une case apres l'autre,
     * dans des coeurs ({@code spawnProtectedAnimatedStructure}) ; cinq secondes
     * plus tard il est vide sur 7. Un bloc qui tomberait sur le joueur le fait
     * monter au-dessus de la colonne.
     */
    private static void princess(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockPos center = pos.above(4);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = center.getX() - 4; x < center.getX() + 5; x++) {
            for (int y = center.getY() - 4; y < center.getY() + 5; y++) {
                for (int z = center.getZ() - 4; z < center.getZ() + 5; z++) {
                    cells.add(new BlockPos(x, y, z));
                }
            }
        }
        BlockState clay = Blocks.CLAY.defaultBlockState();
        // {@code Thread.sleep(5)} par bloc : une dizaine par tic, 73 tics pour les 729.
        int perTick = 10;
        LuckyTasks.repeat(player, 0, 1, (cells.size() + perTick - 1) / perTick, step -> {
            for (int index = step * perTick; index < Math.min(cells.size(), (step + 1) * perTick); index++) {
                BlockPos cell = cells.get(index);
                if (player.blockPosition().equals(cell)) {
                    player.teleportTo(player.getX(), top(level, cell.getX(), cell.getZ()) + 2.0d, player.getZ());
                }
                if (LuckyWorld.place(player, level, cell, clay)) {
                    level.sendParticles(ParticleTypes.HEART, cell.getX() + 0.5d, cell.getY() + 0.5d,
                            cell.getZ() + 0.5d, 2, 0.4d, 0.4d, 0.4d, 0.1d);
                }
            }
        });
        LuckyTasks.later(player, 100, () -> LuckyWorld.box(player, level, center, 7, 7, 7,
                Blocks.AIR.defaultBlockState(), null));
    }

    /**
     * Le joueur part dans un coin du monde ({@code nextInt(30000) - 15000} sur x
     * et z, 13 blocs au-dessus du sol) et s'y retrouve dans une coque
     * d'obsidienne de 18 blocs videe sur 15. Ici le tirage reste dans la bordure
     * du monde.
     */
    private static void baseDeco(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        WorldBorder border = level.getWorldBorder();
        int x = (int) Math.round(border.getCenterX());
        int z = (int) Math.round(border.getCenterZ());
        for (int attempt = 0; attempt < 20; attempt++) {
            int tryX = random.nextInt(BASE_RANGE * 2) - BASE_RANGE;
            int tryZ = random.nextInt(BASE_RANGE * 2) - BASE_RANGE;
            if (border.isWithinBounds(tryX + 0.5d, tryZ + 0.5d, 20.0d)) {
                x = tryX;
                z = tryZ;
                break;
            }
        }
        // Le chunk d'arrivee est genere d'abord : sans lui, la hauteur serait celle du fond du monde.
        int y = Math.min(ContentEvents.surface(level, x, z) + BASE_HEIGHT, level.getMaxY() - 10);
        player.teleportTo(level, x + 0.5d, y, z + 0.5d, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        BlockPos center = new BlockPos(x, y, z);
        LuckyWorld.box(player, level, center, 18, 18, 18, Blocks.OBSIDIAN.defaultBlockState(), null);
        LuckyWorld.box(player, level, center, 15, 15, 15, Blocks.AIR.defaultBlockState(), null);
    }
}
