package fr.atraxterra.dohy.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;

/**
 * Les compteurs de l'arbre que le jeu ne tient pas lui-meme : structures
 * visitees, stick of god, outil de construction, reserve d'or. Les objets des
 * lots suivants y ajoutent les leurs (dynamites lancees, withers invoques...).
 */
@EventBusSubscriber
public final class FactionStats {

    /** Prefixe d'une structure visitee : « visit:minecraft:village_plains@12,-4 ». */
    public static final String VISIT = "visit:";
    public static final String GOD_STICK = "use:god_stick";
    public static final String BUILDER_WAND = "use:builder_wand";
    public static final String RESERVE_INVESTS = "reserve:invests";
    public static final String RESERVE_TIER = "reserve:tier";

    private FactionStats() {
    }

    /** Toutes les cinq secondes : la structure ou se tient le joueur, et les ameliorations echues. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 0) {
            return;
        }
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return;
        }
        FactionTree.complete(player, false);
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> here = level.structureManager().getAllStructuresAt(pos);
        if (here.isEmpty()) {
            return;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Structure structure : here.keySet()) {
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (start.isValid()) {
                Identifier id = registry.getKey(structure);
                String key = VISIT + (id == null ? "?" : id) + "@" + start.getChunkPos().x() + ","
                        + start.getChunkPos().z();
                if (!data.stats.containsKey(key)) {
                    data.stats.put(key, 1L);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FactionTree.complete(player, false);
        }
    }
}
