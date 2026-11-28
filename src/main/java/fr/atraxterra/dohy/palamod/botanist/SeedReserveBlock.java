package fr.atraxterra.dohy.palamod.botanist;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La reserve de graines ({@code BlockBotanistReserve}) : une armoire a bocaux du laboratoire de
 * JobsReborn, posee dans le monde sur deux blocs de large et deux de haut ({@link WideMachineBlock}).
 * Elle ouvre la reserve du joueur qui clique ({@link SeedReserve}) : la meme partout.
 */
public class SeedReserveBlock extends WideMachineBlock {

    public static final MapCodec<SeedReserveBlock> CODEC = simpleCodec(SeedReserveBlock::new);

    public SeedReserveBlock(Properties properties) {
        super(properties, Block.box(0.0d, 0.0d, 1.0d, 16.0d, 16.0d, 15.0d), Block.box(0.0d, 0.0d, 1.0d, 16.0d, 15.0d,
                15.0d));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntity machine(BlockPos pos, BlockState state) {
        return new SeedReserveBlockEntity(pos, state);
    }

    @Override
    protected void open(ServerPlayer player, BlockEntity machine) {
        BlockPos pos = machine.getBlockPos();
        player.openMenu(new SimpleMenuProvider((containerId, inventory, viewer) -> {
            SeedReserve.Storage storage = new SeedReserve.Storage(player, pos);
            // La valeur libre 0 du menu : les XP de premiere graine deja recues, une plante par bit.
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    return index == MachineMenu.DATA_EXTRA ? storage.rewardMask() : 0;
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return MachineMenu.DATA_COUNT;
                }
            };
            return new MachineMenu(PalaBotanist.SEED_RESERVE_MENU.get(), containerId, inventory, storage, data,
                    MachineLayout.SEED_RESERVE);
        }, Component.translatable("container.palamod.seed_reserve")));
    }
}
