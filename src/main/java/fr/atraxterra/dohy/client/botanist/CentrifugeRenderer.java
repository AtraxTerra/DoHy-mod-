package fr.atraxterra.dohy.client.botanist;

import fr.atraxterra.dohy.client.rpg.BedrockModel;
import fr.atraxterra.dohy.palamod.botanist.CentrifugeBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * La centrifugeuse : la roue, les cristaux, les fioles et l'aiguille du modele de JobsReborn. Le
 * modele fait 65 pixels de haut et 60 de large : a 0,4, il tient sur ses deux blocs (un peu plus large
 * qu'un bloc, comme le scanner). L'attente boucle ; la centrifugation et sa fin se jouent une fois,
 * depuis leur debut, et restent sur leur derniere image.
 */
public class CentrifugeRenderer extends LabMachineRenderer<CentrifugeBlockEntity> {

    public CentrifugeRenderer(BlockEntityRendererProvider.Context context) {
        super(0.4f, 0.0f, 0.0f, false);
    }

    @Override
    protected BedrockModel model() {
        return BotanistModels.centrifuge();
    }

    @Override
    protected Identifier texture() {
        return BotanistModels.CENTRIFUGE_TEXTURE;
    }

    @Override
    protected String clip(CentrifugeBlockEntity machine) {
        return machine.anim().clip();
    }

    @Override
    protected float seconds(CentrifugeBlockEntity machine, float age) {
        return machine.anim().looping() ? age / 20.0f : Math.max(0.0f, (age - machine.animSince()) / 20.0f);
    }
}
