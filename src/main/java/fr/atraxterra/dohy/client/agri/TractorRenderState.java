package fr.atraxterra.dohy.client.agri;

import fr.atraxterra.dohy.agri.TractorTool;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Ce que le rendu du tracteur lit de l'entite, image par image. */
public class TractorRenderState extends LivingEntityRenderState {

    public TractorTool tool = TractorTool.NONE;
    /** L'outil tourne : le rabatteur de la moissonneuse aussi. */
    public boolean working;
}
