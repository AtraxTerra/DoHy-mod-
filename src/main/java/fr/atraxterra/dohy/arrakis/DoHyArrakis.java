package fr.atraxterra.dohy.arrakis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Arrakis : la planete Duna de NTM NEXT Space, reprise par Berskar.
 *
 * <p>NTM Space garde ses fusees, son orbite, sa lune (Ike, devenue la Premiere
 * Lune) et ses minerais ; Berskar change le reste. Le relief est le sien
 * ({@link ArrakisTerrain}, par-dessus les reglages de bruit de Duna), l'air se
 * respire, on y pese son poids, le ciel est couleur de sable, et une navette
 * part du spawn vers l'astroport d'Arrakeen ({@link ArrakisPort}).
 *
 * <p>Sans NTM Space, rien de tout cela n'existe : la borne de la navette le dit.
 */
public final class DoHyArrakis {

    public static final String SPACE_MOD = "hbmspace";
    /** La dimension de Duna dans NTM Space. */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(SPACE_MOD, "duna"));

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, DoHy.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    static {
        DENSITY.register("arrakis", () -> ArrakisTerrain.MAP_CODEC);
    }

    /** La version de l'astroport construit, gardee sur la dimension ; 0 tant qu'il n'existe pas. */
    public static final Supplier<AttachmentType<Integer>> PORT_BUILT = ATTACHMENTS.register(
            "arrakis_port", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("version"), value -> value > 0)
                    .build());

    private DoHyArrakis() {
    }

    public static void register(IEventBus modBus) {
        DENSITY.register(modBus);
        ATTACHMENTS.register(modBus);
        ArrakisContent.register(modBus);
        modBus.addListener(DoHyArrakis::setup);
    }

    /** Vrai si NTM Space est la : sans lui, pas de planete. */
    public static boolean available() {
        return ModList.get().isLoaded(SPACE_MOD);
    }

    public static boolean onArrakis(Entity entity) {
        return entity.level().dimension() == DIMENSION;
    }

    private static void setup(FMLCommonSetupEvent event) {
        if (!available()) {
            return;
        }
        event.enqueueWork(() -> {
            ArrakisPlanet.install();
            ArrakisSky.register();
        });
    }
}
