package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Les quatre effets que seuls les pains donnent.
 *
 * <ul>
 *   <li><b>Vol de vigueur</b> : un coup de melee rend en vie une part des
 *       degats infliges ;</li>
 *   <li><b>Dernier rempart</b> : le coup qui aurait tue laisse un coeur, et
 *       l'effet s'en va ;</li>
 *   <li><b>Immunite</b> : aucun effet nefaste ne tient ;</li>
 *   <li><b>Flair du mineur</b> : la pierre cassee lache parfois un minerai
 *       ({@link AgriEvents}).</li>
 * </ul>
 *
 * <p>Les effets eux-memes ne font rien : ce sont des marqueurs, lus par les
 * evenements ci-dessous. Un effet qui se retirerait lui-meme pendant son propre
 * tick modifierait la table des effets pendant qu'elle est parcourue.
 */
@EventBusSubscriber
public final class AgriEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, DoHy.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> LIFE_STEAL =
            EFFECTS.register("life_steal", () -> new Marker(0xC2374B));
    public static final DeferredHolder<MobEffect, MobEffect> LAST_STAND =
            EFFECTS.register("last_stand", () -> new Marker(0xE8C34A));
    public static final DeferredHolder<MobEffect, MobEffect> IMMUNITY =
            EFFECTS.register("immunity", () -> new Marker(0x7FD8C8));
    public static final DeferredHolder<MobEffect, MobEffect> PROSPECTING =
            EFFECTS.register("prospecting", () -> new Marker(0x8E7CC3));

    private AgriEffects() {
    }

    static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    /** Un effet benefique sans comportement propre. */
    private static final class Marker extends MobEffect {
        Marker(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }

    /** Vol de vigueur : la melee rend de la vie. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getInflictedDamage() <= 0.0f
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker || attacker == event.getEntity()
                || attacker.level().isClientSide()) {
            return;
        }
        MobEffectInstance steal = attacker.getEffect(LIFE_STEAL);
        if (steal == null) {
            return;
        }
        float healed = (float) (event.getInflictedDamage() * AgriConfig.config().effects().lifeStealPerLevel()
                * (steal.getAmplifier() + 1));
        if (healed > 0.0f) {
            attacker.heal(healed);
        }
    }

    /** Dernier rempart : le coup mortel est annule une fois. En premier, pour que rien d'autre ne compte cette mort. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !victim.hasEffect(LAST_STAND)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
        victim.removeEffect(LAST_STAND);
        victim.setHealth((float) Math.max(1.0d, AgriConfig.config().effects().lastStandHealth()));
        if (victim instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal("Dernier rempart : le coup ne t'a pas tue.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Immunite : les effets nefastes sont leves a mesure qu'ils arrivent. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0
                || !player.hasEffect(IMMUNITY)) {
            return;
        }
        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance active : player.getActiveEffects()) {
            if (active.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(active.getEffect());
            }
        }
        harmful.forEach(player::removeEffect);
    }
}
