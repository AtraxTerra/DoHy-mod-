package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;

/**
 * La guerre des nations : le soldat, et les chunks qu'un siege tient charges.
 *
 * <p>Un siege ne survit pas a un arret du serveur (voir {@link Siege#suspend}) :
 * au demarrage, les chunks qu'il tenait sont rendus.
 */
@EventBusSubscriber
public final class DoHyWar {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DoHy.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Soldier>> SOLDIER = ENTITIES.register(
            "soldier", key -> EntityType.Builder.<Soldier>of(Soldier::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, key)));

    static final TicketController TICKETS = new TicketController(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "siege"), DoHyWar::validate);

    private DoHyWar() {
    }

    private static void validate(ServerLevel level, TicketHelper helper) {
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
        for (net.minecraft.core.BlockPos pos : List.copyOf(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(pos);
        }
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SOLDIER.get(), Soldier.createAttributes().build());
    }

    @SubscribeEvent
    public static void onTickets(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
