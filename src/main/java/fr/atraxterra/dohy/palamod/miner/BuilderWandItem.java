package fr.atraxterra.dohy.palamod.miner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Outil de construction : prolonge une surface d'un coup.
 *
 * <p>Releve dans {@code ItemBuilderWand} :
 * <ul>
 *   <li>clic droit sur un bloc : on prend le plan de la face cliquee, centre sur
 *       ce bloc ;</li>
 *   <li>chaque position du plan qui porte le <b>meme bloc</b> que celui clique,
 *       et dont la voisine cote face est libre (air, eau ou lave), recoit une
 *       copie de ce bloc sur cette voisine ;</li>
 *   <li>le cote du plan vaut 6, 9, 11 ou 20 selon le tier, parcouru de
 *       {@code -cote/2} a {@code cote/2} exclu en division entiere : 6, 8, 10
 *       et 20 positions de large, soit les 36, 64, 100 et 400 blocs du wiki ;</li>
 *   <li>un joueur dans une des positions annule tout ;</li>
 *   <li>en survie, chaque bloc pose consomme un exemplaire de l'inventaire, sans
 *       composant particulier ; a court, la pose s'arrete.</li>
 * </ul>
 *
 * <p><b>Ecarts volontaires.</b> L'original consommait l'objet <i>avant</i> de
 * tester la protection : un bloc refuse etait perdu. Ici on consomme apres la
 * pose. Et ses branches est/ouest etaient inversees (le nommage de
 * {@code EnumFacing} en 1.7.10 est notoirement faux) : on pose du cote de la face
 * cliquee, ce qu'il voulait faire.
 *
 * <p>Les doubles blocs (portes, lits, grandes plantes) sont refuses : copier la
 * moitie d'un etat en donnerait des moities orphelines.
 */
public class BuilderWandItem extends Item {

    private static final Component BLOCKED =
            Component.literal("Vous ne pouvez pas poser des blocs sur un joueur.")
                    .withStyle(ChatFormatting.RED);

    private final int side;

    public BuilderWandItem(int side, Properties properties) {
        super(properties);
        this.side = side;
    }

    /** Cote du plan, tel que PalaMod le declare (6, 9, 11, 20). */
    public int side() {
        return side;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(context.getPlayer() instanceof Player player)) {
            return InteractionResult.PASS;
        }
        BlockPos origin = context.getClickedPos();
        BlockState clicked = level.getBlockState(origin);
        if (isFree(clicked) || clicked.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || clicked.hasProperty(BlockStateProperties.BED_PART)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        Direction face = context.getClickedFace();
        Item blockItem = clicked.getBlock().asItem();
        if (blockItem == Items.AIR) {
            return InteractionResult.FAIL;
        }

        List<BlockPos> targets = targets(server, origin, face, clicked.getBlock());
        for (BlockPos target : targets) {
            if (!server.getEntitiesOfClass(Player.class, new AABB(target)).isEmpty()) {
                serverPlayer.sendSystemMessage(BLOCKED);
                return InteractionResult.FAIL;
            }
        }

        boolean creative = serverPlayer.isCreative();
        int placed = 0;
        for (BlockPos target : targets) {
            if (server.isOutsideBuildHeight(target)) {
                continue;
            }
            if (!creative && !hasItem(serverPlayer, blockItem)) {
                break;
            }
            if (!clicked.canSurvive(server, target)) {
                continue;
            }
            BlockSnapshot snapshot = BlockSnapshot.create(server.dimension(), server, target);
            if (!server.setBlock(target, clicked, Block.UPDATE_ALL)) {
                continue;
            }
            if (EventHooks.onBlockPlace(serverPlayer, snapshot, face)) {
                snapshot.restore();
                continue;
            }
            if (!creative) {
                consume(serverPlayer, blockItem);
            }
            placed++;
        }

        if (placed > 0) {
            SoundType sound = clicked.getSoundType();
            server.playSound(null, origin, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Les positions a remplir, dans l'ordre de parcours de PalaMod. */
    private List<BlockPos> targets(Level level, BlockPos origin, Direction face, Block block) {
        List<BlockPos> found = new ArrayList<>();
        Direction.Axis axis = face.getAxis();
        for (int a = -side / 2; a < side / 2; a++) {
            for (int b = -side / 2; b < side / 2; b++) {
                BlockPos layer = switch (axis) {
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(b, a, 0);
                    case X -> origin.offset(0, a, b);
                };
                BlockState here = level.getBlockState(layer);
                BlockPos target = layer.relative(face);
                if (!isFree(here) && here.is(block) && isFree(level.getBlockState(target))) {
                    found.add(target);
                }
            }
        }
        return found;
    }

    /** L'air, l'eau et la lave : ce que {@code isAirBlock} de PalaMod laissait remplacer. */
    private static boolean isFree(BlockState state) {
        return state.isAir() || state.getBlock() instanceof LiquidBlock;
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item) && stack.isComponentsPatchEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void consume(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item) && stack.isComponentsPatchEmpty()) {
                stack.shrink(1);
                return;
            }
        }
    }
}
