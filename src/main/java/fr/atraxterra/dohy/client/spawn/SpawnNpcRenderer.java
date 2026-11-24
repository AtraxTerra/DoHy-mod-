package fr.atraxterra.dohy.client.spawn;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.spawn.SpawnNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Un PNJ du spawn : le corps humain du modele vanilla, habille selon son guichet (des peaux
 * originales, {@code tools/spawn_npc_assets.py}) — le tablier du marchand, le costume du
 * banquier, le casque du contremaitre, la capuche du receleur.
 */
public class SpawnNpcRenderer extends HumanoidMobRenderer<SpawnNpc, SpawnNpcRenderState,
        HumanoidModel<SpawnNpcRenderState>> {

    private static final Identifier[] TEXTURES = new Identifier[SpawnNpc.Role.values().length];

    static {
        for (SpawnNpc.Role role : SpawnNpc.Role.values()) {
            TEXTURES[role.ordinal()] = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                    "textures/entity/spawn_npc/" + role.id() + ".png");
        }
    }

    public SpawnNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public SpawnNpcRenderState createRenderState() {
        return new SpawnNpcRenderState();
    }

    @Override
    public void extractRenderState(SpawnNpc npc, SpawnNpcRenderState state, float partialTick) {
        super.extractRenderState(npc, state, partialTick);
        state.role = npc.role().ordinal();
    }

    @Override
    public Identifier getTextureLocation(SpawnNpcRenderState state) {
        return TEXTURES[Math.floorMod(state.role, TEXTURES.length)];
    }
}
