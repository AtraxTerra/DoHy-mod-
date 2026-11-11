package fr.atraxterra.dohy.palamod.achievement;

import fr.atraxterra.dohy.DoHy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** La piece jointe des succes (lot 23b). */
public final class PalaAchievements {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final Supplier<AttachmentType<AchievementData>> DATA = ATTACHMENTS.register("achievements",
            () -> AttachmentType.builder(AchievementData::new)
                    .serialize(AchievementData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    private PalaAchievements() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
