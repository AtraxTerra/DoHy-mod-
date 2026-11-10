package fr.atraxterra.dohy.client.boss;

import fr.atraxterra.dohy.palamod.boss.BossKind;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Ce que le rendu d'un boss d'event lit de l'entite, a chaque image. */
public class EventBossRenderState extends LivingEntityRenderState {

    public BossKind kind = BossKind.TOBALT;
    /** Etourdi (Tobalt contre un bloc) : les etoiles tournent au-dessus de sa tete. */
    public boolean stunned;
    /** En pleine ruee : Belier, Charge, Morsure. */
    public boolean charging;
}
