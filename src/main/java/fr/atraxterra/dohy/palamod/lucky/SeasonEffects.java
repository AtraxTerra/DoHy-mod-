package fr.atraxterra.dohy.palamod.lucky;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Les effets des lucky blocks saisonniers ({@code PotionsRegister} des modules du mois).
 * Comme ceux de {@link PalaLucky}, ils ne font rien par eux-memes : ce qui suit les regarde,
 * a chaque seconde du joueur.
 */
@EventBusSubscriber
public final class SeasonEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, PalaMod.NAMESPACE);

    // -- Mars -----------------------------------------------------------------------------------

    /** {@code PotionStar} : des etoiles plein l'ecran ; a la fin, le casque des etoiles. */
    public static final DeferredHolder<MobEffect, LuckyEffect> STAR =
            EFFECTS.register("star", () -> new LuckyEffect(MobEffectCategory.BENEFICIAL, 0xF5E06B));
    /** {@code PotionNoOxygen} : un coeur toutes les deux secondes, sauf casque des etoiles sur la tete. */
    public static final DeferredHolder<MobEffect, LuckyEffect> NO_OXYGEN =
            EFFECTS.register("no_oxygen", () -> new LuckyEffect(MobEffectCategory.HARMFUL, 0x3A62D8));
    /** {@code PotionJupiterGravity} : la gravite de Jupiter, on ne peut plus sauter. */
    public static final DeferredHolder<MobEffect, LuckyEffect> JUPITER_GRAVITY = EFFECTS.register("jupiter_gravity",
            () -> modified(MobEffectCategory.HARMFUL, 0xC9874A, "jupiter_gravity", -1.0d, Attributes.JUMP_STRENGTH));

    private SeasonEffects() {
    }

    private static LuckyEffect modified(MobEffectCategory category, int color, String name, double amount,
                                        Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        LuckyEffect effect = new LuckyEffect(category, color);
        effect.addAttributeModifier(attribute, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "season_" + name),
                amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        return effect;
    }

    static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.hasEffect(STAR) && player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.3d, player.getZ(), 2, 0.4d, 0.1d,
                    0.4d, 0.0d);
        }
        if (player.hasEffect(NO_OXYGEN) && player.tickCount % 40 == 0
                && !player.getItemBySlot(EquipmentSlot.HEAD).is(SeasonItems.STAR_HELMET.get())) {
            player.hurtServer(level, level.damageSources().drown(), 2.0f);
        }
    }
}
