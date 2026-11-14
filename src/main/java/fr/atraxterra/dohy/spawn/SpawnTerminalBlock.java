package fr.atraxterra.dohy.spawn;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.shop.ShopController;
import fr.atraxterra.dohy.ui.menu.BankController;
import fr.atraxterra.dohy.ui.menu.BoutiqueController;
import fr.atraxterra.dohy.ui.menu.ContractsController;
import fr.atraxterra.dohy.ui.menu.MuseumController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Une borne du spawn : un clic ouvre le service qu'elle affiche. Ce sont les
 * memes ecrans que ceux du menu ; la borne leur donne une adresse.
 */
public class SpawnTerminalBlock extends Block {

    /** Les services. Le nom sert d'etat de bloc et de nom de texture ({@code terminal_<nom>}). */
    public enum Kind implements StringRepresentable {
        SHOP("shop", "SHOP", 0x3DFF6A),
        BOUTIQUE("boutique", "BOUTIQUE", 0xFAC400),
        MARKET("market", "MARCHE", 0xFFB52E),
        BANK("bank", "BANQUE", 0x36A3FF),
        CONTRACTS("contracts", "CONTRATS", 0x7C9BFF),
        MUSEUM("museum", "MUSEE", 0xEC4FB5),
        RETURN("return", "RETOUR", 0xFF5A4A),
        DAILY("daily", "RECOMPENSE", 0xFFD83A),
        PORTALS("portals", "DONJONS", 0xC77DFF),
        MINE("mine", "MINE", 0x5FD8E8),
        BLACK_MARKET("black_market", "MARCHE NOIR", 0x9B6BFF);

        private final String name;
        private final String label;
        private final int color;

        Kind(String name, String label, int color) {
            this.name = name;
            this.label = label;
            this.color = color;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Le mot affiche au-dessus de la borne. */
        public String label() {
            return label;
        }

        public int color() {
            return color;
        }
    }

    public static final MapCodec<SpawnTerminalBlock> CODEC = simpleCodec(SpawnTerminalBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    public SpawnTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.SHOP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Une borne posee a la main change de service a chaque clic accroupi ; debout, elle s'ouvre. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isCreative() && player.isShiftKeyDown()) {
            Kind[] kinds = Kind.values();
            level.setBlock(pos, state.setValue(KIND, kinds[(state.getValue(KIND).ordinal() + 1) % kinds.length]),
                    Block.UPDATE_ALL);
            return InteractionResult.SUCCESS;
        }
        switch (state.getValue(KIND)) {
            case SHOP -> ShopController.open(server);
            case BOUTIQUE -> BoutiqueController.INSTANCE.open(server);
            case MARKET -> fr.atraxterra.dohy.auction.AuctionController.INSTANCE.open(server);
            case BANK -> BankController.INSTANCE.open(server);
            case CONTRACTS -> ContractsController.INSTANCE.open(server);
            case MUSEUM -> MuseumController.INSTANCE.open(server);
            case RETURN -> {
                // a l'arrivee de la Mine, la borne ramene au spawn ; au spawn, d'ou l'on venait
                if (fr.atraxterra.dohy.mine.MineService.inMine(server)) {
                    fr.atraxterra.dohy.mine.MineService.leave(server);
                } else {
                    SpawnApi.leave(server);
                }
            }
            case DAILY -> SpawnDaily.INSTANCE.open(server);
            case PORTALS -> SpawnPortals.INSTANCE.open(server);
            case MINE -> fr.atraxterra.dohy.mine.MineService.enter(server);
            case BLACK_MARKET -> fr.atraxterra.dohy.blackmarket.BlackMarketMenu.INSTANCE.open(server);
        }
        return InteractionResult.SUCCESS;
    }
}
