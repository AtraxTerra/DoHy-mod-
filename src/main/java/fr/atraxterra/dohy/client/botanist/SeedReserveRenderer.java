package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.SeedReserveBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * La reserve de graines : l'armoire aux quinze bocaux du modele de JobsReborn (62 pixels de large et
 * de haut), a 0,5 sur ses deux blocs de large et de haut, avec son animation d'attente.
 */
public class SeedReserveRenderer extends LabMachineRenderer<SeedReserveBlockEntity> {

    public SeedReserveRenderer(BlockEntityRendererProvider.Context context) {
        super(0.5f, 0.0f, -0.5f, true);
    }

    @Override
    protected BedrockModel model() {
        return BotanistModels.seedReserve();
    }

    @Override
    protected Identifier texture() {
        return BotanistModels.SEED_RESERVE_TEXTURE;
    }

    @Override
    protected String clip(SeedReserveBlockEntity machine) {
        return "idle";
    }

    @Override
    protected float seconds(SeedReserveBlockEntity machine, float age) {
        return age / 20.0f;
    }
}
