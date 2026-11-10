package fr.atraxterra.dohy.palamod.boss;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Les boss de l'event Boss et les combattants des factions rivales (lot 22f).
 *
 * <p>Un type d'entite par boss, pour que chacun ait sa boite de collision : de
 * 2 x 3,7 blocs pour Morsula a 6 x 11 pour Beep Boop. Ils ne craignent pas le
 * feu — Tobalt porte un volcan.
 */
@EventBusSubscriber
public final class PalaBosses {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PalaMod.NAMESPACE);

    private static final Map<BossKind, DeferredHolder<EntityType<?>, EntityType<EventBoss>>> BOSSES =
            new EnumMap<>(BossKind.class);

    static {
        for (BossKind kind : BossKind.values()) {
            BOSSES.put(kind, ENTITIES.register("boss_" + kind.id(), key -> EntityType.Builder
                    .<EventBoss>of((type, level) -> new EventBoss(type, level, kind), MobCategory.MONSTER)
                    .sized(kind.width(), kind.height())
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key))));
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<RivalFighter>> RIVAL_FIGHTER = ENTITIES.register(
            "rival_fighter", key -> EntityType.Builder.<RivalFighter>of(RivalFighter::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    private PalaBosses() {
    }

    public static EntityType<EventBoss> type(BossKind kind) {
        return BOSSES.get(kind).get();
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (BossKind kind : BossKind.values()) {
            event.put(type(kind), EventBoss.createAttributes(kind).build());
        }
        event.put(RIVAL_FIGHTER.get(), Vindicator.createAttributes().build());
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
