package fr.atraxterra.dohy.farmland;

import com.mojang.serialization.Codec;
import fr.atraxterra.dohy.DoHy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * La farmland (lot 24e) : la dimension du Chasseur, {@code dohy:farmland}. Une carte batie et
 * indestructible ou n'apparaissent que les creatures du metier. Voir {@link Farmland}.
 */
public final class DoHyFarmland {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** La version de la carte construite, gardee sur sa dimension ; 0 tant qu'elle n'existe pas. */
    public static final Supplier<AttachmentType<Integer>> BUILT = ATTACHMENTS.register(
            "farmland_built", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    private DoHyFarmland() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
