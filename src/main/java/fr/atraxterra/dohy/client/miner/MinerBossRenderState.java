package fr.atraxterra.dohy.client.miner;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Ce que le rendu du gardien des cristaux lit de l'entite : les drapeaux et les compteurs
 * d'animation qu'{@code EntityMinerBoss} tenait dans son DataWatcher, deduits ici de l'attaque en
 * cours et de son tick.
 */
public class MinerBossRenderState extends LivingEntityRenderState {

    /** Les cristaux brises, de 0 a 6. */
    public int state;
    public boolean stunned;
    public boolean raised;
    public boolean rightArmRaised;
    public boolean leftArmRaised;
    public boolean punching;
    public float punchProgress;
    public boolean strikingGround;
    public float strikeProgress;
}
