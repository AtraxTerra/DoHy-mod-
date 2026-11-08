package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce que l'on voit a travers un cave block en endium : les conteneurs.
 *
 * <p>Releve dans {@code BlockEndiumCaveRenderer} : tout conteneur
 * ({@code IInventory}) dont le rayon depuis l'oeil du joueur touche
 * <b>d'abord un cave block en endium</b> est entoure d'un contour bleu
 * ({@code #2537FB}) visible a travers les murs. C'est une fenetre, pas un radar :
 * le joueur doit regarder a travers le bloc.
 *
 * <h2>Pourquoi cote serveur</h2>
 *
 * <p>L'original dessinait les contours dans un rendu de bloc, cote client, avec
 * un scan des chunks sur un fil a part. Ici le contour est un <b>shulker
 * invisible et lumineux</b>, envoye au seul joueur concerne par paquets et
 * jamais ajoute au monde : le client le dessine avec le contour de l'effet
 * « lumineux », a travers les murs, et la couleur vient d'une equipe bleue que
 * seul ce joueur connait. Aucune ligne de rendu a maintenir, rien a desynchroniser
 * avec la refonte du rendu de 26.2, et le scan reste sur le fil du serveur.
 *
 * <p>Le calcul tourne toutes les demi-secondes, sur les chunks a 4 de distance
 * (l'original se limitait aux 64 blocs de rendu de son TESR).
 */
@EventBusSubscriber
public final class EndiumCaveVision {

    private static final int PERIOD = 10;
    private static final int CHUNK_RADIUS = 4;
    private static final double MAX_DISTANCE = 64.0d;
    private static final int MAX_OUTLINES = 128;
    private static final String TEAM = "palamod_cave";

    /** Par joueur : position du conteneur -> identifiant du shulker fantome. */
    private static final Map<UUID, Map<BlockPos, Ghost>> SHOWN = new HashMap<>();

    private record Ghost(int id, String uuid) {
    }

    private EndiumCaveVision() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PERIOD != 0
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        update(player, level, visible(player, level));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHOWN.remove(event.getEntity().getUUID());
    }

    /** Un changement de dimension efface les entites du client : on repart de zero. */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        SHOWN.remove(event.getEntity().getUUID());
    }

    private static List<BlockPos> visible(ServerPlayer player, ServerLevel level) {
        List<BlockPos> caves = new ArrayList<>();
        List<BlockPos> containers = new ArrayList<>();
        int centerX = SectionPos.blockToSectionCoord(player.getBlockX());
        int centerZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof EndiumCaveBlockEntity) {
                        caves.add(entry.getKey());
                    } else if (entry.getValue() instanceof Container) {
                        containers.add(entry.getKey());
                    }
                }
            }
        }
        // Le cave block en endium porte une block entity vide, comme dans
        // PalaMod : c'est ce qui permet de le trouver dans le meme parcours que
        // les conteneurs, sans balayer les blocs un par un.
        if (containers.isEmpty() || caves.isEmpty()) {
            return List.of();
        }

        Vec3 eye = player.getEyePosition();
        List<BlockPos> seen = new ArrayList<>();
        for (BlockPos container : containers) {
            Vec3 center = Vec3.atCenterOf(container);
            if (center.distanceTo(eye) > MAX_DISTANCE) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK
                    && level.getBlockState(hit.getBlockPos()).is(PalaMiner.ENDIUM_CAVE_BLOCK.get())) {
                seen.add(container);
                if (seen.size() >= MAX_OUTLINES) {
                    break;
                }
            }
        }
        return seen;
    }

    private static void update(ServerPlayer player, ServerLevel level, List<BlockPos> visible) {
        Map<BlockPos, Ghost> shown = SHOWN.get(player.getUUID());
        if (shown == null && visible.isEmpty()) {
            return;
        }
        PlayerTeam team = team();
        if (shown == null) {
            shown = new HashMap<>();
            SHOWN.put(player.getUUID(), shown);
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }

        List<Integer> removed = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Ghost>> iterator = shown.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Ghost> entry = iterator.next();
            if (!visible.contains(entry.getKey())) {
                removed.add(entry.getValue().id());
                player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        team, entry.getValue().uuid(), ClientboundSetPlayerTeamPacket.Action.REMOVE));
                iterator.remove();
            }
        }
        if (!removed.isEmpty()) {
            player.connection.send(new ClientboundRemoveEntitiesPacket(
                    removed.stream().mapToInt(Integer::intValue).toArray()));
        }

        for (BlockPos pos : visible) {
            if (shown.containsKey(pos)) {
                continue;
            }
            Ghost ghost = spawn(player, level, pos);
            player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, ghost.uuid(), ClientboundSetPlayerTeamPacket.Action.ADD));
            shown.put(pos, ghost);
        }

        if (shown.isEmpty()) {
            SHOWN.remove(player.getUUID());
            player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        }
    }

    /**
     * Un shulker fantome : construit pour son identifiant et ses donnees, jamais
     * ajoute au monde. Le serveur ne le connait donc pas, ne le fait pas
     * tourner, et un clic dessus est ignore.
     */
    private static Ghost spawn(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Shulker shulker = new Shulker(EntityTypes.SHULKER, level);
        shulker.setPos(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        shulker.setInvisible(true);
        shulker.setGlowingTag(true);
        shulker.setNoGravity(true);
        shulker.setSilent(true);
        shulker.setNoAi(true);
        player.connection.send(new ClientboundAddEntityPacket(shulker.getId(), shulker.getUUID(),
                shulker.getX(), shulker.getY(), shulker.getZ(), 0.0f, 0.0f,
                EntityTypes.SHULKER, 0, Vec3.ZERO, 0.0d));
        var values = shulker.getEntityData().getNonDefaultValues();
        if (values != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(shulker.getId(), values));
        }
        return new Ghost(shulker.getId(), shulker.getUUID().toString());
    }

    /** L'equipe qui colore le contour. Jamais inscrite au tableau des scores du serveur. */
    private static PlayerTeam team() {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), TEAM);
        // #2537FB dans l'original ; BLUE est la couleur d'equipe la plus proche.
        team.setColor(Optional.of(TeamColor.BLUE));
        return team;
    }
}
