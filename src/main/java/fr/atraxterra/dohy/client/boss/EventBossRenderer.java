package fr.atraxterra.dohy.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.boss.BossKind;
import fr.atraxterra.dohy.palamod.boss.EventBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Le rendu d'un boss d'event : son modele, sa texture d'origine, et l'echelle
 * {@code sizeMultiplier} que le PalaMod appliquait par {@code glScalef}.
 */
public class EventBossRenderer extends MobRenderer<EventBoss, EventBossRenderState, EventBossModel> {

    private final BossKind kind;
    private final Identifier texture;

    public EventBossRenderer(EntityRendererProvider.Context context, BossKind kind) {
        super(context, new EventBossModel(context.bakeLayer(EventBossModel.layer(kind)), kind), kind.width() * 0.4f);
        this.kind = kind;
        this.texture = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "textures/entity/boss/" + kind.id() + ".png");
    }

    @Override
    public EventBossRenderState createRenderState() {
        return new EventBossRenderState();
    }

    @Override
    public void extractRenderState(EventBoss boss, EventBossRenderState state, float partialTick) {
        super.extractRenderState(boss, state, partialTick);
        state.kind = kind;
        state.stunned = boss.isStunned();
        state.charging = boss.isCharging();
    }

    @Override
    protected void scale(EventBossRenderState state, PoseStack pose) {
        float scale = kind.scale();
        pose.scale(scale, scale, scale);
    }

    @Override
    public Identifier getTextureLocation(EventBossRenderState state) {
        return texture;
    }
}
