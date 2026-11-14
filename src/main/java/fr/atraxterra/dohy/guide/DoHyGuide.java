package fr.atraxterra.dohy.guide;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.guide.plan.PlanData;
import fr.atraxterra.dohy.guide.plan.PlanItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * L'aide au joueur : l'encyclopedie et les plans de construction.
 *
 * <p>L'encyclopedie n'enregistre rien ici (un menu, des fiches de datapack,
 * une piece jointe) ; le plan est un objet, qui porte sa structure et sa pose
 * dans un composant.
 */
public final class DoHyGuide {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DoHy.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PlanData>> PLAN_DATA =
            COMPONENTS.registerComponentType("plan", builder -> builder
                    .persistent(PlanData.CODEC)
                    .networkSynchronized(PlanData.STREAM_CODEC));

    public static final DeferredItem<PlanItem> PLAN = ITEMS.registerItem("plan", PlanItem::new,
            properties -> properties.stacksTo(1).rarity(Rarity.UNCOMMON));

    private DoHyGuide() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
    }
}
