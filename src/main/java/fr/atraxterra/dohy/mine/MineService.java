package fr.atraxterra.dohy.mine;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.spawn.SpawnTerminalBlock;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * La Mine : un monde de minage public, remis a neuf toutes les cinq heures
 * (dans l'esprit de la mine de NationsGlory), a part de la dimension du Mineur
 * du PalaMod.
 *
 * <h2>Uniquement sous terre</h2>
 *
 * <p>Demande du joueur : « uniquement sous terre, pas de surface ». La Mine est
 * un monde de roche du fond au plafond — pierre, ardoise des abimes en bas,
 * bedrock au fond et au plafond (y = 123 a 127), aucun ciel. Les galeries sont
 * celles des carvers du jeu : cavernes, boyaux, ravins. On arrive dans une salle
 * creusee a y = 30, eclairee de lanternes, la borne RETOUR contre la paroi nord.
 *
 * <h2>Une mine neuve toutes les cinq heures</h2>
 *
 * <p>Une dimension ne se regenere pas pendant que le serveur tourne. La Mine
 * change donc de <b>terrain</b> plutot que de graine : chaque tranche ouvre une
 * nouvelle zone de 2 000 × 2 000 blocs, loin des precedentes, dans une roche
 * que personne n'a touchee. Demande du joueur (2026-11-15) : toutes les cinq
 * heures, soit a 5 h, 10 h, 15 h, 20 h et 1 h, heure de {@link RealClock} (la
 * derniere tranche, de 1 h a 5 h, ne dure que quatre heures : la grille revient
 * chaque matin a 5 h). L'ancienne zone n'est plus jamais rejointe ; ses
 * fichiers sont effaces au demarrage suivant du serveur ({@link #onAboutToStart}).
 *
 * <p>Dix, cinq et une minute avant la remise a neuf, les joueurs presents sont
 * prevenus ; a l'heure, ils sont ramenes au spawn.
 *
 * <p><b>Un redemarrage ne touche a rien.</b> La salle d'arrivee est posee une
 * fois par tranche : si elle est deja la (sa borne RETOUR en place), un
 * redemarrage la laisse telle quelle. Avant, elle etait refaite au premier
 * passage apres chaque demarrage, et un joueur revenu dans la meme zone croyait
 * a une remise a neuf manquee.
 *
 * <h2>Les regles (choix du joueur)</h2>
 * <ul>
 *   <li>on n'y entre que par la borne MINE de l'atrium du spawn ; on en sort
 *       par la borne RETOUR de l'arrivee ou {@code /spawn} ;</li>
 *   <li>la pierre cassee ne donne rien : ni pierre, ni cobblestone, ni
 *       ardoise des abimes, ni granite, diorite, andesite ou tuf ;</li>
 *   <li>mourir a la Mine ne fait rien perdre : l'inventaire et l'experience
 *       sont rendus a la reapparition ;</li>
 *   <li>aucun monstre ;</li>
 *   <li>on ne sort pas de la zone en cours.</li>
 * </ul>
 *
 * <p>Les minerais : ceux du jeu (emeraude comprise), ceux du PalaMod a leur
 * taux normal, et ceux des mods presents (zinc de Create, minerais et calcaire
 * de NTM NEXT) — voir le biome {@code dohy:mine} et le tag
 * {@code dohy:mine_mod_ores}, ecrits par {@code tools/dohy_assets.py}. Ceux de
 * NTM passent par les copies de {@code tools/mine_ntm_ores.py}, sans
 * l'interrupteur {@code overworld_ores} que coupe le datapack de la Terre.
 */
@EventBusSubscriber
public final class MineService {

    /** La dimension. */
    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "mine"));

    /** La demi-largeur de la zone en cours : 2 000 × 2 000 blocs. */
    static final int HALF = 1000;

    /** L'heure de la premiere remise a neuf de la journee. */
    static final int RESET_HOUR = 5;

    /** Les remises a neuf, en heures apres 5 h : 5 h, 10 h, 15 h, 20 h et 1 h. */
    static final int[] RESET_OFFSETS = {0, 5, 10, 15, 20};

    /** L'ecart entre les zones de deux tranches. */
    static final int SPACING = 20_000;

    /** La hauteur de la salle d'arrivee, en pleine roche (la Mine va de y = -64 a 127). */
    static final int ARRIVAL_Y = 30;
    /** La salle d'arrivee : 7 × 7 a l'interieur, 4 de haut. */
    private static final int ROOM = 3;
    private static final int ROOM_HEIGHT = 4;

    /** Les avertissements avant la remise a neuf, en minutes. */
    private static final int[] WARNINGS = {10, 5, 1};

    /**
     * Les pierres qui ne donnent rien : la pierre et l'ardoise, et les poches
     * de granite, diorite, andesite et tuf qu'on y perce sans cesse — dans une
     * mine toute en roche, elles rempliraient le sac autant que la cobblestone.
     */
    private static final List<net.minecraft.world.item.Item> RUBBLE = List.of(Items.COBBLESTONE,
            Items.COBBLED_DEEPSLATE, Items.STONE, Items.DEEPSLATE, Items.GRANITE, Items.DIORITE, Items.ANDESITE,
            Items.TUFF);

    /** Les tranches avancees par {@code /mine admin remise}, pour essayer sans attendre l'heure. */
    private static int shift;

    private static long lastPeriod = Long.MIN_VALUE;
    private static long preparedPeriod = Long.MIN_VALUE;
    private static long centerPeriod = Long.MIN_VALUE;
    private static BlockPos center;
    private static BlockPos arrival;
    private static int warned = Integer.MAX_VALUE;
    private static int ticks;

    private static final Map<UUID, Kept> KEPT = new HashMap<>();

    /** Ce qu'un joueur mort a la Mine retrouve en reapparaissant. */
    private record Kept(List<ItemStack> items, int level, float progress, int total) {
    }

    private MineService() {
    }

    // -- Le calendrier et la zone en cours ------------------------------------------

    /** La tranche de la Mine : elle change a 5 h, 10 h, 15 h, 20 h et 1 h. */
    static long period() {
        return RealClock.slotKey(RESET_HOUR, RESET_OFFSETS) + shift;
    }

    /** Les minutes avant la prochaine remise a neuf : l'agenda de l'accueil. */
    public static long minutesUntilReset() {
        return RealClock.minutesUntilNextSlot(RESET_HOUR, RESET_OFFSETS);
    }

    /** L'heure de la prochaine remise a neuf, pour les messages et les ecrans (pause, liste des joueurs). */
    public static int nextResetHour() {
        return RealClock.nextSlot(RESET_HOUR, RESET_OFFSETS).getHour();
    }

    /** Le centre, en x, de la zone d'une tranche. */
    static int base(long period) {
        return 200_000 + (int) Math.floorMod(period, 500L) * SPACING;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    public static boolean inMine(Entity entity) {
        return entity.level().dimension() == DIMENSION;
    }

    /** Le centre de la zone en cours, a la hauteur de la salle d'arrivee. */
    static BlockPos center(ServerLevel level) {
        long period = period();
        if (center != null && centerPeriod == period) {
            return center;
        }
        center = new BlockPos(base(period), ARRIVAL_Y, 0);
        centerPeriod = period;
        return center;
    }

    static boolean contains(BlockPos middle, double x, double z) {
        return Math.abs(x - middle.getX()) <= HALF && Math.abs(z - middle.getZ()) <= HALF;
    }

    // -- Entrer, sortir ---------------------------------------------------------------

    /** La borne MINE de l'atrium : vers l'arrivee de la zone en cours. */
    public static void enter(ServerPlayer player) {
        ServerLevel level = level(player.level().getServer());
        if (level == null) {
            tell(player, "La Mine n'existe pas dans ce monde.", ChatFormatting.RED);
            return;
        }
        BlockPos pad = prepare(level);
        player.closeContainer();
        player.fallDistance = 0.0f;
        player.teleport(new TeleportTransition(level, new Vec3(pad.getX() + 0.5d, pad.getY(), pad.getZ() + 0.5d),
                Vec3.ZERO, 180.0f, 0.0f, TeleportTransition.DO_NOTHING));
        tell(player, "La Mine : remise a neuf a " + nextResetHour() + " h.", ChatFormatting.GOLD);
        tell(player, "Sortie : la borne RETOUR ou /spawn.",
                ChatFormatting.GRAY);
    }

    /** La borne RETOUR de l'arrivee, ou {@code /spawn} : vers la plateforme du spawn. */
    public static void leave(ServerPlayer player) {
        player.closeContainer();
        SpawnApi.backToPad(player);
    }

    /**
     * L'arrivee de la tranche : une salle de 7 × 7 creusee en pleine roche au centre
     * de la zone, sol de pierre polie, quatre lanternes, la borne RETOUR contre
     * la paroi nord. Les parois sont bouchees ou il le faut (galerie, lave, eau,
     * gravier qui tomberait) : on arrive toujours dans une salle close et sure,
     * dont on sort a la pioche. Posee une fois par tranche, et pas refaite apres
     * un redemarrage si elle est deja la.
     */
    private static BlockPos prepare(ServerLevel level) {
        BlockPos middle = center(level);
        long period = period();
        if (arrival != null && preparedPeriod == period) {
            return arrival;
        }
        BlockPos terminal = middle.offset(0, 0, -ROOM);
        BlockState there = level.getBlockState(terminal);
        if (there.is(DoHySpawn.TERMINAL.get()) && there.getValue(SpawnTerminalBlock.KIND) == SpawnTerminalBlock.Kind.RETURN) {
            // Deja posee pendant cette tranche, avant un redemarrage : rien a refaire.
            arrival = middle;
            preparedPeriod = period;
            return arrival;
        }
        BlockState wall = Blocks.STONE.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int edge = ROOM + 1;
        for (int dx = -edge; dx <= edge; dx++) {
            for (int dz = -edge; dz <= edge; dz++) {
                for (int dy = -1; dy <= ROOM_HEIGHT; dy++) {
                    BlockPos at = middle.offset(dx, dy, dz);
                    boolean side = Math.abs(dx) == edge || Math.abs(dz) == edge;
                    if (dy == -1 && !side) {
                        level.setBlock(at, floor, Block.UPDATE_ALL);
                    } else if (side || dy == -1 || dy == ROOM_HEIGHT) {
                        BlockState here = level.getBlockState(at);
                        if (here.isAir() || !here.getFluidState().isEmpty()
                                || here.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
                            level.setBlock(at, wall, Block.UPDATE_ALL);
                        }
                    } else {
                        level.setBlock(at, air, Block.UPDATE_ALL);
                    }
                }
            }
        }
        for (int dx = -ROOM; dx <= ROOM; dx += 2 * ROOM) {
            for (int dz = -ROOM; dz <= ROOM; dz += 2 * ROOM) {
                level.setBlock(middle.offset(dx, 0, dz), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.setBlock(middle.offset(0, 0, -ROOM), DoHySpawn.TERMINAL.get().defaultBlockState()
                .setValue(SpawnTerminalBlock.FACING, Direction.SOUTH)
                .setValue(SpawnTerminalBlock.KIND, SpawnTerminalBlock.Kind.RETURN), Block.UPDATE_ALL);
        arrival = middle;
        preparedPeriod = period;
        DoHy.LOGGER.info("Mine : zone de la tranche {} autour de {} {}, salle d'arrivee a y = {}", period, middle.getX(),
                middle.getZ(), middle.getY());
        return arrival;
    }

    // -- Chaque seconde : remise a neuf, avertissements, bords ------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        long period = period();
        if (lastPeriod == Long.MIN_VALUE) {
            lastPeriod = period;
        }
        if (period != lastPeriod) {
            lastPeriod = period;
            warned = Integer.MAX_VALUE;
            for (ServerPlayer player : List.copyOf(level.players())) {
                tell(player, "La Mine s'effondre : une neuve vous attend.",
                        ChatFormatting.RED);
                leave(player);
            }
            DoHy.LOGGER.info("Mine : remise a neuf (tranche {})", period);
            return;
        }
        long minutes = RealClock.minutesUntilNextSlot(RESET_HOUR, RESET_OFFSETS);
        for (int warning : WARNINGS) {
            if (minutes < warning && warning < warned) {
                warned = warning;
                for (ServerPlayer player : level.players()) {
                    tell(player, "La Mine sera remise a neuf dans " + warning + " minute" + (warning > 1 ? "s" : "")
                            + " : ressors avant, ou tu seras ramene au spawn.", ChatFormatting.GOLD);
                }
            }
        }
        BlockPos middle = center(level);
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator() || contains(middle, player.getX(), player.getZ())) {
                continue;
            }
            double x = Math.clamp(player.getX(), middle.getX() - HALF + 1.0d, middle.getX() + HALF - 1.0d);
            double z = Math.clamp(player.getZ(), middle.getZ() - HALF + 1.0d, middle.getZ() + HALF - 1.0d);
            player.teleportTo(x, player.getY(), z);
            tell(player, "C'est le bord de la Mine.", ChatFormatting.RED);
        }
    }

    /** Reconnecte dans une mine qui a change depuis : retour au spawn. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inMine(player)) {
            ServerLevel level = level(player.level().getServer());
            if (level != null && !contains(center(level), player.getX(), player.getZ())) {
                tell(player, "La Mine a ete remise a neuf pendant ton absence.", ChatFormatting.GOLD);
                leave(player);
            }
        }
    }

    // -- Les regles ----------------------------------------------------------------------

    /** La pierre cassee ne donne rien. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.getLevel().dimension() != DIMENSION) {
            return;
        }
        event.getDrops().removeIf(drop -> isRubble(drop));
    }

    private static boolean isRubble(ItemEntity drop) {
        ItemStack stack = drop.getItem();
        for (net.minecraft.world.item.Item item : RUBBLE) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Mort a la Mine : inventaire et experience mis de cote, rien ne tombe. En dernier : apres un totem. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inMine(player) && !event.isCanceled()) {
            Inventory inventory = player.getInventory();
            List<ItemStack> copy = new ArrayList<>(inventory.getContainerSize());
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                copy.add(inventory.getItem(slot).copy());
            }
            KEPT.put(player.getUUID(), new Kept(copy, player.experienceLevel, player.experienceProgress,
                    player.totalExperience));
            inventory.clearContent();
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && KEPT.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Kept kept = KEPT.remove(player.getUUID());
        if (kept == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < kept.items().size() && slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, kept.items().get(slot));
        }
        player.experienceLevel = kept.level();
        player.experienceProgress = kept.progress();
        player.totalExperience = kept.total();
        tell(player, "Mort a la Mine : rien n'est perdu.", ChatFormatting.GREEN);
    }

    /** Aucun monstre a la Mine. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Enemy && !event.loadedFromDisk()
                && event.getLevel().dimension() == DIMENSION) {
            event.setCanceled(true);
        }
    }

    // -- Le menage au demarrage --------------------------------------------------------

    /**
     * Avant l'ouverture des mondes : efface les fichiers des zones des tranches
     * passees (regions, entites, points d'interet). Seule la bande en cours est
     * gardee. Rien d'autre n'est touche, et seulement dans le dossier de la
     * Mine.
     */
    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        lastPeriod = Long.MIN_VALUE;
        preparedPeriod = Long.MIN_VALUE;
        centerPeriod = Long.MIN_VALUE;
        center = null;
        arrival = null;
        shift = 0;
        Path folder = event.getServer().getWorldPath(LevelResource.ROOT).resolve("dimensions")
                .resolve(DoHy.MOD_ID).resolve("mine");
        if (!Files.isDirectory(folder)) {
            return;
        }
        int start = base(period());
        int minRegionX = Math.floorDiv(start - HALF, 512) - 1;
        int maxRegionX = Math.floorDiv(start + HALF, 512) + 1;
        int minRegionZ = Math.floorDiv(-HALF, 512) - 1;
        int maxRegionZ = Math.floorDiv(HALF, 512) + 1;
        int removed = 0;
        Pattern name = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
        for (String sub : new String[] {"region", "entities", "poi"}) {
            Path dir = folder.resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    Matcher match = name.matcher(file.getFileName().toString());
                    if (!match.matches()) {
                        continue;
                    }
                    int x = Integer.parseInt(match.group(1));
                    int z = Integer.parseInt(match.group(2));
                    if (x < minRegionX || x > maxRegionX || z < minRegionZ || z > maxRegionZ) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                }
            } catch (IOException error) {
                DoHy.LOGGER.warn("Mine : menage incomplet dans {}", dir, error);
            }
        }
        if (removed > 0) {
            DoHy.LOGGER.info("Mine : {} fichiers des zones passees effaces", removed);
        }
    }

    // -- La commande d'administration ---------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("mine")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("admin")
                        .then(Commands.literal("remise").executes(context -> {
                            shift++;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Mine remise a neuf a la main (tranche " + period() + ")."), true);
                            return 1;
                        }))
                        .then(Commands.literal("info").executes(context -> {
                            ServerLevel level = level(context.getSource().getServer());
                            String where = level == null ? "absente" : center(level).toShortString();
                            context.getSource().sendSuccess(() -> Component.literal("Mine : tranche " + period()
                                    + ", centre " + where + ", remise a neuf dans "
                                    + RealClock.minutesUntilNextSlot(RESET_HOUR, RESET_OFFSETS) + " min, a "
                                    + nextResetHour() + " h."), false);
                            return 1;
                        })));
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }
}
