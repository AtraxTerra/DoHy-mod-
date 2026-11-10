package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * La porte en obsidienne ({@code OBSIDIAN_ITEM_DOOR}) : elle s'ouvre a la main,
 * et aucun monstre ne l'enfonce. En vanilla, une porte qui s'ouvre a la main
 * compte pour une porte en bois, que les zombies cassent en difficile.
 */
public class ObsidianDoorBlock extends DoorBlock {

    public ObsidianDoorBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return entity instanceof Player;
    }
}
