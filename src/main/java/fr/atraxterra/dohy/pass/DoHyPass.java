package fr.atraxterra.dohy.pass;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Le pass mensuel : ses douze reliques et le composant qui date une relique.
 * Le reste (quetes, niveaux, recompenses) vit sur le joueur ({@link PassData}).
 */
public final class DoHyPass {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DoHy.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DoHy.MOD_ID);

    /** La saison (annee x 12 + mois) ou une relique a ete gagnee. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RELIC_SEASON =
            COMPONENTS.registerComponentType("relic_season", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private static final Map<Relic, DeferredItem<RelicItem>> RELICS = new EnumMap<>(Relic.class);

    static {
        for (Relic relic : Relic.values()) {
            RELICS.put(relic, ITEMS.registerItem(relic.path(), properties -> new RelicItem(relic, properties),
                    properties -> properties.stacksTo(1).rarity(Rarity.EPIC)));
        }
    }

    private DoHyPass() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
    }

    public static RelicItem relicItem(Relic relic) {
        return RELICS.get(relic).get();
    }

    /** Une relique, datee de la saison ou elle est gagnee. */
    public static ItemStack relic(Relic relic, int season) {
        ItemStack stack = new ItemStack(relicItem(relic));
        stack.set(RELIC_SEASON.get(), season);
        return stack;
    }
}
