package fr.atraxterra.dohy.palamod.equipment;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les effets des armures.
 *
 * <p>Releves classe par classe dans les {@code onArmorTick} de PalaMod :
 * {@code ItemRepairableArmor} et ses tableaux d'effets par emplacement pour le
 * paladium et le paladium vert, {@code ItemMixedEndiumArmor} et son aiguillage
 * sur le type de piece, {@code ItemEndiumArmor} et son test sur les quatre
 * emplacements.
 *
 * <table>
 *   <tr><th>Set</th><th>Casque</th><th>Plastron</th><th>Jambieres</th><th>Bottes</th><th>Panoplie</th></tr>
 *   <tr><td>paladium</td><td>vision nocturne</td><td></td><td></td><td>vitesse I</td><td></td></tr>
 *   <tr><td>paladium vert</td><td>vision nocturne</td><td>celerite I</td><td></td><td>vitesse II</td><td></td></tr>
 *   <tr><td>endium mixe</td><td>vision nocturne</td><td>resistance au feu</td><td>celerite II</td><td>vitesse II, aucune chute</td><td></td></tr>
 *   <tr><td>endium</td><td>vision nocturne</td><td>vision nocturne</td><td>vision nocturne</td><td>vision nocturne</td><td>invisibilite</td></tr>
 * </table>
 *
 * <p>L'amethyste et le titane n'ont aucun effet : ce sont des
 * {@code BaseItemArmor} nues.
 *
 * <p><b>Ce qui n'est pas porte, et pourquoi.</b> Le code d'endium contient,
 * apres le bloc d'invisibilite, une force et une vitesse II qu'aucun saut
 * n'atteint : un {@code goto} les enjambe. Elles ne s'appliquaient jamais. Les
 * porter ajouterait des effets que personne n'a eus.
 *
 * <p><b>La cadence.</b> L'original reappliquait a chaque tick des effets d'une
 * minute. On les reapplique chaque seconde avec une duree de quinze : la vision
 * nocturne clignote sous dix secondes, et quinze laissent la marge d'une seconde
 * manquee. Retirer la piece laisse donc l'effet s'eteindre en quinze secondes au
 * plus — moins longtemps que dans l'original, qui en laissait soixante.
 */
@EventBusSubscriber
public final class ArmorEffects {

    /** Une seconde entre deux passages : les effets durent bien plus. */
    private static final int PERIOD = 20;

    /** Quinze secondes : au-dessus des dix ou la vision nocturne clignote. */
    private static final int DURATION = 300;

    /**
     * L'invisibilite de la panoplie d'endium dure tres peu, comme dans
     * l'original (2 ticks reappliques a chaque tick) : retirer une piece doit
     * rendre visible presque aussitot. Deux passages de marge.
     */
    private static final int INVISIBILITY_DURATION = PERIOD * 2;

    private record Effect(Holder<MobEffect> effect, int amplifier) {
    }

    /** Construite au premier passage : les objets n'existent pas avant l'enregistrement. */
    private static Map<Item, List<Effect>> table;

    private ArmorEffects() {
    }

    private static Map<Item, List<Effect>> table() {
        if (table != null) {
            return table;
        }
        Effect nightVision = new Effect(MobEffects.NIGHT_VISION, 0);
        Map<Item, List<Effect>> map = new HashMap<>();

        map.put(PalaEquipment.PALADIUM_HELMET.get(), List.of(nightVision));
        map.put(PalaEquipment.PALADIUM_BOOTS.get(), List.of(new Effect(MobEffects.SPEED, 0)));

        map.put(PalaEquipment.GREEN_PALADIUM_HELMET.get(), List.of(nightVision));
        map.put(PalaEquipment.GREEN_PALADIUM_CHESTPLATE.get(), List.of(new Effect(MobEffects.HASTE, 0)));
        map.put(PalaEquipment.GREEN_PALADIUM_BOOTS.get(), List.of(new Effect(MobEffects.SPEED, 1)));

        map.put(PalaEquipment.MIXED_ENDIUM_HELMET.get(), List.of(nightVision));
        map.put(PalaEquipment.MIXED_ENDIUM_CHESTPLATE.get(), List.of(new Effect(MobEffects.FIRE_RESISTANCE, 0)));
        map.put(PalaEquipment.MIXED_ENDIUM_LEGGINGS.get(), List.of(new Effect(MobEffects.HASTE, 1)));
        // L'immunite aux chutes des bottes est dans onFall : ce n'est pas un
        // effet de 26.2, mais une potion propre a PalaMod (PotionFeatherFalling).
        map.put(PalaEquipment.MIXED_ENDIUM_BOOTS.get(), List.of(new Effect(MobEffects.SPEED, 1)));

        // Chaque piece d'endium donne la vision nocturne, seule.
        map.put(PalaEquipment.ENDIUM_HELMET.get(), List.of(nightVision));
        map.put(PalaEquipment.ENDIUM_CHESTPLATE.get(), List.of(nightVision));
        map.put(PalaEquipment.ENDIUM_LEGGINGS.get(), List.of(nightVision));
        map.put(PalaEquipment.ENDIUM_BOOTS.get(), List.of(nightVision));

        table = map;
        return map;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % PERIOD != 0) {
            return;
        }

        Map<Item, List<Effect>> effects = table();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            List<Effect> worn = effects.get(player.getItemBySlot(slot).getItem());
            if (worn == null) {
                continue;
            }
            for (Effect effect : worn) {
                // ambient : pas de particules, comme le dernier booleen de
                // PotionEffect(id, duree, niveau, true) dans l'original. On garde
                // l'icone pour que le joueur sache d'ou vient l'effet.
                player.addEffect(new MobEffectInstance(effect.effect(), DURATION,
                        effect.amplifier(), true, false, true));
            }
        }

        if (wearsFullEndium(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    INVISIBILITY_DURATION, 0, true, false, true));
        }
    }

    /**
     * Les quatre emplacements portent de l'endium — et seulement de l'endium.
     *
     * <p>L'endium mixe ne compte pas : l'original teste
     * {@code instanceof ItemEndiumArmor}, et l'endium mixe est une autre classe.
     */
    private static boolean wearsFullEndium(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(PalaEquipment.ENDIUM_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(PalaEquipment.ENDIUM_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(PalaEquipment.ENDIUM_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(PalaEquipment.ENDIUM_BOOTS.get());
    }

    /**
     * Les bottes en endium mixe annulent toute chute.
     *
     * <p>Une immunite, pas une reduction : {@code PotionHandler} ramenait les
     * degats de {@code DamageSource.fall} a zero et annulait l'evenement. Ici on
     * multiplie les degats de la chute par zero, ce qui laisse le reste de la
     * chute intact (le bruit, la poussiere) mais ne blesse plus.
     *
     * <p>Un ecart, assume : l'original laissait la potion une minute apres avoir
     * retire les bottes. Ici l'immunite tient tant qu'on les porte — une chute
     * mortelle n'a pas a dependre d'un reliquat invisible.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getItemBySlot(EquipmentSlot.FEET).is(PalaEquipment.MIXED_ENDIUM_BOOTS.get())) {
            event.setDamageMultiplier(0.0f);
        }
    }
}
