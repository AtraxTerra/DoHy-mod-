package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Socle des blocs de machine.
 *
 * <p>Il fait deux choses, et pas une de plus : brancher le ticker du serveur,
 * et rendre le contenu de la machine au joueur quand elle est cassee.
 *
 * <p><b>Le contenu est rendu dans {@code playerWillDestroy}</b> et non dans un
 * crochet de retrait de bloc. La raison est pratique : cette methode est
 * publique et sa signature est stable, la ou les crochets de retrait ont change
 * de nom plusieurs fois entre 1.21 et 26.2. La contrepartie est connue — une
 * machine detruite par une explosion ou poussee par un piston perd son contenu.
 * Les machines de ce chapitre ne contiennent que de la pierre et des
 * particules ; le jour ou l'une d'elles portera quelque chose de couteux, il
 * faudra une table de butin qui copie l'inventaire.
 *
 * <p>L'ouverture de l'interface ne passe pas par ce bloc mais par
 * {@link PalaMachineEvents} : un evenement d'interaction deja utilise par les
 * verrous de metier, dont la signature est verifiee, plutot qu'une methode
 * protegee de plus a deviner.
 */
public abstract class MachineBlock extends BaseEntityBlock {

    protected MachineBlock(Properties properties) {
        super(properties);
    }

    /**
     * Le ticker, cote serveur uniquement.
     *
     * <p>Un ticker cote client ferait tourner la machine deux fois : une fois
     * pour de vrai, une fois dans une copie que personne ne sauvegarde.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof MachineBlockEntity machine) {
                machine.serverTick();
            }
        };
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            Containers.dropContents(level, pos, machine);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Le codec du parent.
     *
     * <p>Comme pour les cultures : la serialisation pilotee par donnees
     * n'intervient pas pour un bloc enregistre, l'instance venant du registre.
     */
    @Override
    protected abstract MapCodec<? extends BaseEntityBlock> codec();
}
