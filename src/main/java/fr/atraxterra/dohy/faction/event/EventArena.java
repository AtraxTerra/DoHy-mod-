package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * La Warzone : la dimension des events de faction (lot 22f).
 *
 * <p>Sur Paladium, on gagne l'arene de sa ligue par le portail de la Warzone. Ici,
 * {@code palamod:warzone} est une dimension vide (biome {@code the_void} : aucun
 * monstre n'y apparait) et chaque event a son arene, batie en dur la premiere
 * fois qu'il se lance, puis reutilisee.
 *
 * <p><b>L'arene du Boss</b> : un disque de 34 blocs de rayon (Beep Boop mesure
 * onze blocs, Tobalt en fait cinq et demi de large), en briques de pierre noire,
 * cercle de pierre doree, rempart crenele de six blocs, huit piliers de lanternes
 * pour y voir clair a 1 h du matin. Le joueur arrive sur le parvis sud, les
 * factions rivales par les portes est, ouest et nord.
 */
public final class EventArena {

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "warzone"));

    /** Le centre de l'arene du Boss, au niveau du sol. */
    public static final BlockPos BOSS_CENTER = new BlockPos(0, 100, 0);
    public static final int BOSS_RADIUS = 34;

    /** Le bloc temoin, sous le centre : present, l'arene est deja batie. */
    private static final BlockState MARKER = Blocks.LODESTONE.defaultBlockState();

    private EventArena() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    /** Ou le joueur arrive : le parvis sud, face au centre. */
    public static Vec3 bossEntry() {
        return Vec3.atBottomCenterOf(BOSS_CENTER.offset(0, 1, BOSS_RADIUS - 4));
    }

    /** Les trois portes des factions rivales : est, ouest, nord. */
    public static Vec3 rivalGate(int index) {
        int r = BOSS_RADIUS - 4;
        BlockPos pos = switch (index % 3) {
            case 0 -> BOSS_CENTER.offset(r, 1, 0);
            case 1 -> BOSS_CENTER.offset(-r, 1, 0);
            default -> BOSS_CENTER.offset(0, 1, -r);
        };
        return Vec3.atBottomCenterOf(pos);
    }

    /** Garde l'arene chargee pendant l'event : les rivaux s'y battent meme en ton absence. */
    public static void hold(ServerLevel level, boolean on) {
        ChunkPos center = ChunkPos.containing(BOSS_CENTER);
        if (on) {
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, center, 3);
        } else {
            level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, center, 3);
        }
    }

    /**
     * Bati l'arene du Boss si elle n'existe pas. Une seule fois par monde :
     * environ dix mille blocs dans des chunks vides, sans mise a jour des
     * voisins — une fraction de seconde.
     */
    public static void buildBossArena(ServerLevel level) {
        BlockPos c = BOSS_CENTER;
        if (level.getBlockState(c.below(2)).is(MARKER.getBlock())) {
            return;
        }
        int flags = Block.UPDATE_CLIENTS;
        BlockState floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState under = Blocks.BLACKSTONE.defaultBlockState();
        BlockState gold = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
        int r = BOSS_RADIUS;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 2.5d) {
                    continue;
                }
                BlockPos at = c.offset(dx, 0, dz);
                level.setBlock(at.below(), under, flags);
                if (d <= r + 0.5d) {
                    BlockState top = floor;
                    int ring = (int) Math.round(d);
                    if (ring == 8 || ring == 20) {
                        top = gold;
                    } else if (ring == 0 || (ring % 6 == 3 && (dx == 0 || dz == 0))) {
                        top = chiseled;
                    } else if (ring % 7 == 5 && ((dx + dz) & 3) == 0) {
                        top = light;
                    }
                    level.setBlock(at, top, flags);
                    continue;
                }
                // Le rempart : six blocs, creneaux un bloc sur deux.
                level.setBlock(at, floor, flags);
                int height = ((dx + dz) & 1) == 0 ? 7 : 6;
                for (int y = 1; y <= height; y++) {
                    level.setBlock(at.above(y), (dx * 7 + dz * 3 + y) % 5 == 0 ? cracked : wall, flags);
                }
            }
        }
        // Huit piliers de lanternes, a 30 blocs du centre.
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0d * i / 8.0d + Math.PI / 8.0d;
            BlockPos base = c.offset((int) Math.round(Math.cos(angle) * 30.0d), 0,
                    (int) Math.round(Math.sin(angle) * 30.0d));
            for (int y = 1; y <= 5; y++) {
                level.setBlock(base.above(y), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), flags);
            }
            level.setBlock(base.above(6), light, flags);
            level.setBlock(base.above(7), Blocks.LANTERN.defaultBlockState(), flags);
        }
        // Les quatre portes, grillagees : au-dela du rempart, c'est le vide.
        for (int i = 0; i < 4; i++) {
            int sx = i == 0 ? 1 : i == 1 ? -1 : 0;
            int sz = i == 2 ? -1 : i == 3 ? 1 : 0;
            // Des barreaux relies entre eux, le long du rempart.
            BlockState bars = Blocks.IRON_BARS.defaultBlockState()
                    .setValue(BlockStateProperties.NORTH, sx != 0).setValue(BlockStateProperties.SOUTH, sx != 0)
                    .setValue(BlockStateProperties.EAST, sz != 0).setValue(BlockStateProperties.WEST, sz != 0);
            for (int w = -2; w <= 2; w++) {
                for (int d = r + 1; d <= r + 2; d++) {
                    BlockPos at = c.offset(sx * d + (sz != 0 ? w : 0), 0, sz * d + (sx != 0 ? w : 0));
                    for (int y = 1; y <= 4; y++) {
                        level.setBlock(at.above(y), bars, flags);
                    }
                }
            }
        }
        level.setBlock(c.below(2), MARKER, flags);
    }
}
