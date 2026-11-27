package fr.atraxterra.dohy.client.miner;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.dimminer.MinerBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * {@code RenderMinerBoss} : ombre de 1, une texture par cristal brise
 * ({@code miner_boss_<etat>.png}, les cristaux y disparaissent un a un).
 *
 * <p>Les drapeaux d'animation se deduisent de l'attaque et de son tick, comme les posaient les
 * attaques de l'original. La toupie ajoutait 60 degres de lacet par tick au serveur ; un client ne
 * recoit l'orientation que tous les trois ticks, alors elle tourne ici, au rendu.
 */
public class MinerBossRenderer extends MobRenderer<MinerBoss, MinerBossRenderState, MinerBossModel> {

    private static final Identifier[] TEXTURES = new Identifier[MinerBoss.CRYSTALS + 1];

    static {
        for (int state = 0; state <= MinerBoss.CRYSTALS; state++) {
            TEXTURES[state] = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
                    "textures/entity/miner_boss/miner_boss_" + state + ".png");
        }
    }

    public MinerBossRenderer(EntityRendererProvider.Context context) {
        super(context, new MinerBossModel(context.bakeLayer(MinerBossModel.LAYER)), 1.0f);
    }

    @Override
    public MinerBossRenderState createRenderState() {
        return new MinerBossRenderState();
    }

    @Override
    public void extractRenderState(MinerBoss boss, MinerBossRenderState state, float partialTick) {
        super.extractRenderState(boss, state, partialTick);
        state.state = Math.clamp(boss.state(), 0, MinerBoss.CRYSTALS);
        state.stunned = boss.isStunned();
        MinerBoss.Attack attack = boss.attack();
        float tick = boss.attackTick() + partialTick;
        state.raised = attack == MinerBoss.Attack.TURN
                || attack == MinerBoss.Attack.STRIKE && tick < MinerBoss.HIT_TICK;
        state.rightArmRaised = attack == MinerBoss.Attack.PUNCH && tick < 40.0f;
        state.leftArmRaised = attack == MinerBoss.Attack.SHOCKWAVE;
        state.punching = attack == MinerBoss.Attack.PUNCH && tick >= 40.0f;
        state.punchProgress = Math.clamp(tick - 39.0f, 0.0f, 10.0f);
        state.strikingGround = (attack == MinerBoss.Attack.SHOCKWAVE || attack == MinerBoss.Attack.STRIKE)
                && tick >= MinerBoss.HIT_TICK;
        state.strikeProgress = Math.clamp(tick - (MinerBoss.HIT_TICK - 1), 0.0f, 5.0f);
        if (attack == MinerBoss.Attack.TURN && tick >= 40.0f && !state.stunned) {
            state.bodyRot = Mth.wrapDegrees(state.bodyRot + (tick - 40.0f) * 60.0f);
        }
    }

    @Override
    public Identifier getTextureLocation(MinerBossRenderState state) {
        return TEXTURES[state.state];
    }
}
