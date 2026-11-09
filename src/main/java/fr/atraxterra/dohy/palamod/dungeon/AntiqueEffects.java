package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Les effets du set antique complet, hors des donjons : le vol et
 * l'invisibilite.
 *
 * <p>Le wiki ne decrit pas l'armure antique ; ses deux effets viennent des
 * patchnotes de Paladium V11 (« correction de la description de l'effet
 * d'invisibilite sur l'armure antique », 11.0.0.1 ; « correction de pouvoir
 * voler en retirant l'armure antique », 11.0.2.1). Choix du joueur : les
 * deux, au set complet, hors des donjons — dedans, le vol casserait les
 * salles, et c'est le bonus de donjon de {@link DungeonGearItem} qui joue.
 *
 * <p>Le vol n'est retire qu'a qui l'a recu d'ici (une etiquette le note), et
 * des qu'une piece manque : c'est le bogue que la 11.0.2.1 corrigeait. Qui
 * volait recoit une chute lente de 5 secondes. L'invisibilite est rafraichie
 * en continu, discrete (sans particules) ; une potion d'invisibilite bue par
 * ailleurs n'est pas touchee.
 */
@EventBusSubscriber
public final class AntiqueEffects {

    static final String FLIGHT_TAG = "palamod.antique_flight";
    private static final int INVISIBILITY_TICKS = 60;

    private AntiqueEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        boolean active = DungeonGearItem.antiqueSet(player) && !DungeonManager.inDimension(player);
        Abilities abilities = player.getAbilities();
        if (active) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
                player.addTag(FLIGHT_TAG);
            }
            MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
            if (current == null || (current.isAmbient() && current.getDuration() < INVISIBILITY_TICKS - 20)) {
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVISIBILITY_TICKS, 0, true, false,
                        true));
            }
            return;
        }
        if (!player.entityTags().contains(FLIGHT_TAG)) {
            return;
        }
        player.removeTag(FLIGHT_TAG);
        if (!player.isCreative() && !player.isSpectator()) {
            boolean flying = abilities.flying;
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
            if (flying) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false, true));
            }
        }
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        if (current != null && current.isAmbient() && current.getDuration() <= INVISIBILITY_TICKS) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }
}
