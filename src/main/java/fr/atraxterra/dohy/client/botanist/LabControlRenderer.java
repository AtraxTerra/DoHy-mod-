package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.LabControlBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * Le centre de controle : le pupitre, ses trois aiguilles, ses voyants et sa cloche (modele de
 * JobsReborn, 50 pixels de large, 55 de haut), a 0,55 sur ses deux blocs de large et de haut. Ses
 * trois animations bouclent : normale, alerte (la cloche sonne), construction.
 */
public class LabControlRenderer extends LabMachineRenderer<LabControlBlockEntity> {

    public LabControlRenderer(BlockEntityRendererProvider.Context context) {
        super(0.55f, 0.0f, 8.0f, true);
    }

    @Override
    protected BedrockModel model() {
        return BotanistModels.labControl();
    }

    @Override
    protected Identifier texture() {
        return BotanistModels.LAB_CONTROL_TEXTURE;
    }

    @Override
    protected String clip(LabControlBlockEntity machine) {
        return machine.anim().clip();
    }

    @Override
    protected float seconds(LabControlBlockEntity machine, float age) {
        return age / 20.0f;
    }
}
