package fr.atraxterra.dohy.spawn;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aller au spawn, en revenir, et le construire quand il le faut.
 *
 * <p>La construction se fait par tranches, quelques milliers de blocs par
 * tick, pour ne pas figer le jeu : celui qui demande le spawn pendant ce temps
 * attend ou il est et y est envoye a la fin.
 */
public final class SpawnApi {

    /** Blocs poses par tick pendant la construction. */
    private static final int PER_TICK = 5000;
    /** Tranches de x videes par tick avant une reconstruction : environ 50 000 blocs lus chacune. */
    private static final int CLEAR_SLICES = 6;

    /** Largeur de la carte du monde dessinee au spawn, en blocs (la hauteur suit la carte). */
    private static final int MOSAIC_COLUMNS = 104;

    private static Job job;
    /** La carte du monde du spawn, et la carte de la Terre dont elle a ete tiree. */
    private static EarthData mapSource;
    private static int[][] mapMosaic;
    /** Le pays de chaque pixel de la carte du monde (0 : la mer), pour y dessiner les pays pris. */
    private static int[][] mapCountries;
    private static SpawnBuilder.MapSite mapSite;
    /** Ceux qu'il faut envoyer au spawn au prochain tick : une connexion ou une reapparition n'est pas le moment. */
    private static final Set<UUID> ARRIVALS = new LinkedHashSet<>();

    private SpawnApi() {
    }

    /** Le dessin de la carte du monde, refait quand la carte de la Terre change ; {@code null} sans carte. */
    private static int[][] mosaic() {
        EarthData earth = EarthData.get();
        if (earth != mapSource) {
            mapSource = earth;
            mapMosaic = earth == null ? null : earth.mosaic(MOSAIC_COLUMNS);
            mapSite = SpawnBuilder.site(mapMosaic);
            mapCountries = null;
            if (mapMosaic != null) {
                mapCountries = new int[mapMosaic.length][];
                for (int row = 0; row < mapMosaic.length; row++) {
                    mapCountries[row] = new int[mapMosaic[row].length];
                    for (int column = 0; column < mapMosaic[row].length; column++) {
                        EarthData.Country country = mapMosaic[row][column] == 0 ? null
                                : earth.countryOfPixel(MOSAIC_COLUMNS, column, row);
                        mapCountries[row][column] = country == null ? 0 : country.id();
                    }
                }
            }
        }
        return mapMosaic;
    }

    /** Ou la carte du monde est posee dans le spawn ; {@code null} si ce monde n'a pas de carte de la Terre. */
    static SpawnBuilder.MapSite mapSite() {
        mosaic();
        return mapSite;
    }

    /** Le pays dessine sous ce point de la dalle, ou {@code null} (la mer, ou hors de la carte). */
    static EarthData.Country countryOnMap(double x, double z) {
        SpawnBuilder.MapSite site = mapSite();
        EarthData earth = EarthData.get();
        if (site == null || earth == null) {
            return null;
        }
        int column = site.column((int) Math.floor(x));
        int row = site.row((int) Math.floor(z));
        if (column < 0 || row < 0 || mapMosaic[row][column] == 0) {
            return null;
        }
        return earth.countryOfPixel(MOSAIC_COLUMNS, column, row);
    }

    /**
     * Les pays a prendre sur la carte du monde : ceux que tiennent des joueurs
     * en or, les capitales en rouge, le reste dans sa teinte. Ne touche qu'aux
     * pixels qui changent ; rien tant que le spawn n'est pas construit.
     */
    public static void paintNations(MinecraftServer server) {
        int[][] mosaic = mosaic();
        SpawnBuilder.MapSite site = mapSite;
        EarthData earth = EarthData.get();
        if (mosaic == null || site == null || earth == null || job != null) {
            return;
        }
        ServerLevel level = server.getLevel(DoHySpawn.DIMENSION);
        if (level == null || level.getData(DoHySpawn.BUILT.get()) != target()) {
            return;
        }
        Set<Integer> held = new java.util.HashSet<>();
        for (fr.atraxterra.dohy.earth.EarthNations.Held entry : fr.atraxterra.dohy.earth.EarthNations.ledger(server)
                .held()) {
            held.add(entry.country());
        }
        EarthData.Grid grid = earth.config().grid();
        int step = Math.max(1, grid.width() / MOSAIC_COLUMNS);
        Set<Integer> capitals = new java.util.HashSet<>();
        for (int[] capital : fr.atraxterra.dohy.earth.EarthNations.capitals(server)) {
            int column = Math.floorDiv(capital[0] - grid.minX(), grid.cell()) / step;
            int row = Math.floorDiv(capital[1] - grid.minZ(), grid.cell()) / step;
            if (row >= 0 && row < mosaic.length && column >= 0 && column < mosaic[row].length) {
                capitals.add(row << 12 | column);
            }
        }
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState red = Blocks.REDSTONE_BLOCK.defaultBlockState();
        int changed = 0;
        for (int row = 0; row < mosaic.length; row++) {
            for (int column = 0; column < mosaic[row].length; column++) {
                int tint = mosaic[row][column];
                if (tint == 0) {
                    continue;
                }
                BlockState wanted = capitals.contains(row << 12 | column) ? red
                        : held.contains(mapCountries[row][column]) ? gold : SpawnBuilder.mapTint(tint);
                BlockPos pos = new BlockPos(site.west() - column, SpawnBuilder.FLOOR, site.north() - row);
                if (!level.getBlockState(pos).equals(wanted)) {
                    level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
                    changed++;
                }
            }
        }
        if (changed > 0) {
            DoHy.LOGGER.info("Spawn : {} pixel(s) de la carte du monde redessines ({} pays pris, {} capitale(s))",
                    changed, held.size(), capitals.size());
        }
    }

    /**
     * La version du spawn que ce monde doit avoir : celle du dessin, plus mille
     * quand une carte de la Terre s'y ajoute — poser ou retirer le datapack
     * d'une carte fait donc redessiner le lieu.
     */
    static int target() {
        return SpawnBuilder.VERSION + (mosaic() == null ? 0 : 1000);
    }

    public static boolean inSpawn(net.minecraft.world.entity.Entity entity) {
        return entity.level().dimension() == DoHySpawn.DIMENSION;
    }

    /** La commande et la carte du menu : au spawn si l'on n'y est pas, retour sinon. */
    public static void toggle(ServerPlayer player) {
        if (inSpawn(player)) {
            leave(player);
        } else {
            enter(player);
        }
    }

    /** Le spawn ne se rejoint que depuis les trois dimensions du jeu : ni d'un donjon, ni d'une arene. */
    private static boolean reachable(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD || player.level().dimension() == Level.NETHER
                || player.level().dimension() == Level.END;
    }

    public static void enter(ServerPlayer player) {
        if (!reachable(player)) {
            tell(player, "Le spawn ne se rejoint pas d'ici.", ChatFormatting.RED);
            return;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(DoHySpawn.DIMENSION);
        if (level == null) {
            tell(player, "Le spawn n'existe pas dans ce monde.", ChatFormatting.RED);
            return;
        }
        if (level.getData(DoHySpawn.BUILT.get()) != target()) {
            if (job == null) {
                job = new Job(level, level.getData(DoHySpawn.BUILT.get()) > 0);
                DoHy.LOGGER.info("Spawn : construction de la version {} ({} blocs)", target(),
                        job.plan.blocks().size());
            }
            job.waiting.add(player.getUUID());
            tell(player, "Le spawn se construit, tu y seras envoye dans quelques secondes.", ChatFormatting.GOLD);
            return;
        }
        send(player, level);
    }

    private static void send(ServerPlayer player, ServerLevel level) {
        player.setData(DoHySpawn.RETURN.get(), ReturnPoint.of(player));
        player.closeContainer();
        toPad(player, level);
        tell(player, "Bienvenue au spawn. /spawn pour repartir.", ChatFormatting.GREEN);
    }

    /** Pose le joueur au centre de la plateforme d'arrivee, tourne vers le pont. */
    static void toPad(ServerPlayer player, ServerLevel level) {
        player.fallDistance = 0.0f;
        player.teleport(new TeleportTransition(level,
                new Vec3(0.5d, SpawnBuilder.FLOOR + 1.0d, SpawnBuilder.PAD_Z + 0.5d), Vec3.ZERO, 180.0f, 0.0f,
                TeleportTransition.DO_NOTHING));
    }

    /** Ramene le joueur d'ou il venait. */
    public static void leave(ServerPlayer player) {
        if (!inSpawn(player)) {
            return;
        }
        ReturnPoint point = player.getData(DoHySpawn.RETURN.get());
        player.setData(DoHySpawn.RETURN.get(), ReturnPoint.NONE);
        player.closeContainer();
        player.fallDistance = 0.0f;
        point.send(player);
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    /** Envoie le joueur au spawn au prochain tick. */
    static void arriveSoon(ServerPlayer player) {
        ARRIVALS.add(player.getUUID());
    }

    /** Un tick : les arrivees en attente, les tableaux, puis la construction s'il y en a une en cours. */
    static void tick(MinecraftServer server) {
        if (!ARRIVALS.isEmpty()) {
            List<UUID> due = new ArrayList<>(ARRIVALS);
            ARRIVALS.clear();
            for (UUID id : due) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && player.isAlive() && reachable(player)) {
                    enter(player);
                }
            }
        }
        SpawnBoards.tick(server);
        if (job == null) {
            return;
        }
        if (job.step()) {
            Job done = job;
            job = null;
            done.level.setData(DoHySpawn.BUILT.get(), done.version);
            DoHy.LOGGER.info("Spawn : construit");
            paintNations(server);
            for (UUID id : done.waiting) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && !inSpawn(player)) {
                    send(player, done.level);
                }
            }
        }
    }

    static void stop() {
        ARRIVALS.clear();
        job = null;
        mapSource = null;
        mapMosaic = null;
        mapCountries = null;
        mapSite = null;
    }

    /** Vrai tant qu'une construction est en cours. */
    public static boolean building() {
        return job != null;
    }

    /** Une construction en cours : vider l'ancien, poser le nouveau, raccorder, etiqueter. */
    private static final class Job {
        private final ServerLevel level;
        private final SpawnBuilder.Plan plan;
        /** La version en cours de construction : celle du moment ou le plan a ete tire. */
        private final int version;
        private final Set<UUID> waiting = new LinkedHashSet<>();
        private final ObjectIterator<Long2ObjectMap.Entry<BlockState>> placing;
        private final List<BlockPos> shaped = new ArrayList<>();
        private boolean clearing;
        private int clearX = -SpawnBuilder.CLEAR_X;
        private int shapeIndex;
        private boolean labelled;

        Job(ServerLevel level, boolean rebuild) {
            this.level = level;
            this.version = target();
            this.plan = SpawnBuilder.plan(mosaic());
            this.placing = plan.blocks().long2ObjectEntrySet().iterator();
            this.clearing = rebuild;
        }

        /** Avance d'un tick ; vrai quand tout est fini. */
        boolean step() {
            if (clearing) {
                clear();
                return false;
            }
            if (placing.hasNext()) {
                for (int count = 0; count < PER_TICK && placing.hasNext(); count++) {
                    Long2ObjectMap.Entry<BlockState> entry = placing.next();
                    BlockPos pos = BlockPos.of(entry.getLongKey());
                    BlockState state = entry.getValue();
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    if (state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof WallBlock) {
                        shaped.add(pos);
                    }
                }
                return false;
            }
            if (shapeIndex < shaped.size()) {
                // Barreaux et murets : leur forme depend de leurs voisins, connus maintenant.
                for (int count = 0; count < PER_TICK && shapeIndex < shaped.size(); count++) {
                    BlockPos pos = shaped.get(shapeIndex++);
                    BlockState state = level.getBlockState(pos);
                    BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
                    if (joined != state) {
                        level.setBlock(pos, joined, Block.UPDATE_CLIENTS);
                    }
                }
                return false;
            }
            if (!labelled) {
                labelled = true;
                for (SpawnBuilder.Label label : plan.labels()) {
                    level.setBlock(label.pos(), DoHySpawn.HOLOGRAM.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(label.pos()) instanceof HologramBlockEntity hologram) {
                        hologram.set(label.text(), label.color(), label.scale());
                    }
                }
                return false;
            }
            return true;
        }

        /**
         * Vide tout le volume ou une version du spawn a pu batir, quelques
         * tranches de x par tick : ce qui n'est pas dans le nouveau plan
         * redevient de l'air, pitons de l'ancien dessin compris.
         */
        private void clear() {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int slice = 0; slice < CLEAR_SLICES && clearX <= SpawnBuilder.CLEAR_X; slice++, clearX++) {
                for (int z = SpawnBuilder.CLEAR_Z_MIN; z <= SpawnBuilder.CLEAR_Z_MAX; z++) {
                    for (int y = SpawnBuilder.CLEAR_Y_MIN; y <= SpawnBuilder.CLEAR_Y_MAX; y++) {
                        pos.set(clearX, y, z);
                        if (!level.getBlockState(pos).isAir() && !plan.blocks().containsKey(pos.asLong())) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
            if (clearX > SpawnBuilder.CLEAR_X) {
                clearing = false;
            }
        }
    }
}
