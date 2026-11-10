package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.base.EnemyBase;
import fr.atraxterra.dohy.faction.reserve.GoldReserve;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Les pieces jointes de la faction : sur le joueur, et sur les chunks revendiques. */
public final class Factions {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    /** La faction du joueur. Conservee a la mort : mourir ne dissout pas une faction. */
    public static final Supplier<AttachmentType<FactionData>> FACTION = ATTACHMENTS.register("faction",
            () -> AttachmentType.builder(FactionData::new)
                    .serialize(FactionData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /** Le proprietaire d'un chunk. Rien n'est ecrit pour un chunk libre. */
    public static final Supplier<AttachmentType<ClaimOwner>> CLAIM = ATTACHMENTS.register("claim",
            () -> AttachmentType.builder(() -> ClaimOwner.NONE)
                    .serialize(ClaimOwner.MAP_CODEC, ClaimOwner::claimed)
                    .build());

    /** La base ennemie du joueur (lot 22g) : une a la fois, gardee a la mort. */
    public static final Supplier<AttachmentType<EnemyBase>> ENEMY_BASE = ATTACHMENTS.register("enemy_base",
            () -> AttachmentType.builder(() -> EnemyBase.EMPTY)
                    .serialize(EnemyBase.MAP_CODEC, EnemyBase::exists)
                    .copyOnDeath()
                    .build());

    /** La reserve d'or du joueur (lot 22e) : le cube et ses placements, gardes a la mort. */
    public static final Supplier<AttachmentType<GoldReserve>> RESERVE = ATTACHMENTS.register("gold_reserve",
            () -> AttachmentType.builder(() -> GoldReserve.EMPTY)
                    .serialize(GoldReserve.MAP_CODEC, GoldReserve::exists)
                    .copyOnDeath()
                    .build());

    /**
     * D'ou le joueur est parti pour l'arene d'un event (lot 22f). Pas garde a la
     * mort : mourir dans l'arene ramene deja au point de reapparition.
     */
    public static final Supplier<AttachmentType<ReturnPoint>> EVENT_RETURN = ATTACHMENTS.register("event_return",
            () -> AttachmentType.builder(() -> ReturnPoint.NONE)
                    .serialize(ReturnPoint.MAP_CODEC, point -> !point.isNone())
                    .build());

    private Factions() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
