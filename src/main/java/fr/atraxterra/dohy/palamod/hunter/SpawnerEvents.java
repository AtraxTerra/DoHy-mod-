package fr.atraxterra.dohy.palamod.hunter;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.miner.MinerXpEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.Optional;
import java.util.Set;

/**
 * Le marteau des cavernes, le spawner casse, et la vie du spawner customise.
 *
 * <p>Releve dans {@code SpawnerEventHandler}, {@code ItemCavernHammer},
 * {@code SpawnerManager} :
 * <ul>
 *   <li><b>ame</b> : casser au marteau un spawner de donjon de zombie, squelette,
 *       araignee, araignee venimeuse, slime ou sorciere donne une ame au premier
 *       spawner customise de la meme creature qui a de la place dans
 *       l'inventaire, sinon au dernier spawner vide. Sans spawner disponible, la
 *       casse est refusee — l'ame serait perdue ;</li>
 *   <li><b>spawner casse</b> : chaque spawner de donjon pris dans l'explosion
 *       d'un creeper a une chance sur deux de lacher un spawner casse ;</li>
 *   <li><b>XP</b> : casser un spawner de donjon donne 1 250 XP de Chasseur
 *       (converties) a partir du niveau 40 ;</li>
 *   <li><b>fusion</b> : deux spawners de la meme creature se fusionnent a
 *       l'enclume, ames additionnees jusqu'a 250, pour total / 10 niveaux
 *       (le wiki dit 1 XP).</li>
 * </ul>
 * <b>Ecarts</b> : seuls les spawners de donjon donnent une ame, jamais un
 * spawner customise pose — sinon on taperait sans fin le sien ; et sans le
 * marteau, un spawner de donjon se casse normalement (l'original bloquait toute
 * casse sur le serveur de minage).
 */
@EventBusSubscriber
public final class SpawnerEvents {

    /** {@code SpawnerManager} : CaveSpider, Skeleton, Slime, Spider, Witch, Zombie. */
    private static final Set<EntityType<?>> SOUL_TYPES = Set.of(
            EntityTypes.CAVE_SPIDER, EntityTypes.SKELETON, EntityTypes.SLIME,
            EntityTypes.SPIDER, EntityTypes.WITCH, EntityTypes.ZOMBIE);

    /** 1 250 XP de PalaJobs, a partir du niveau 8 de Chasseur. */
    private static final double SPAWNER_XP = 1250.0d;
    private static final int SPAWNER_XP_LEVEL = 40;

    /** {@code nextInt(2) == 0}. */
    private static final float BROKEN_SPAWNER_CHANCE = 0.5f;

    private SpawnerEvents() {
    }

    /** Le spawner customise porte la block entity du spawner vanilla. */
    @SubscribeEvent
    public static void onBlockEntityTypes(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityTypes.MOB_SPAWNER, PalaHunter.CUSTOM_SPAWNER.get());
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !event.getState().is(Blocks.SPAWNER)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (player.getMainHandItem().is(PalaHunter.CAVERN_HAMMER.get()) && !player.isCreative()) {
            if (!collectSoul(player, level, event.getPos())) {
                event.setCanceled(true);
                return;
            }
        }
        if (JobApi.level(player, Job.HUNTER) >= SPAWNER_XP_LEVEL) {
            JobApi.grant(player, Job.HUNTER, MinerXpEvents.convert(player, SPAWNER_XP));
        }
    }

    /** Vrai si l'ame a trouve un spawner ; faux, avec un message, sinon. */
    private static boolean collectSoul(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return refuse(player, "cannot_collect");
        }
        Optional<EntityType<?>> type = typeOf(level, spawner);
        if (type.isEmpty() || !SOUL_TYPES.contains(type.get())) {
            return refuse(player, "wrong_type");
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type.get());
        ItemStack target = candidate(player.getInventory(), id);
        if (target == null) {
            return refuse(player, "no_spawner");
        }
        SpawnerSouls souls = target.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        CustomSpawnerItem.withSouls(target, souls.withSoul(id));
        return true;
    }

    private static boolean refuse(ServerPlayer player, String key) {
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.palamod.cavern_hammer." + key)));
        return false;
    }

    /** La creature du spawner, lue dans ses donnees ({@code SpawnData.entity.id}). */
    private static Optional<EntityType<?>> typeOf(ServerLevel level, SpawnerBlockEntity spawner) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        spawner.getSpawner().save(output);
        CompoundTag data = output.buildResult();
        String id = data.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id", "");
        Identifier key = Identifier.tryParse(id);
        return key == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(key);
    }

    /**
     * Le premier spawner de la meme creature qui a de la place, sinon le dernier
     * spawner vide, dans les 36 cases de l'inventaire.
     */
    private static ItemStack candidate(Inventory inventory, Identifier id) {
        ItemStack blank = null;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(PalaHunter.CUSTOM_SPAWNER_ITEM.get())) {
                continue;
            }
            SpawnerSouls souls = stack.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
            if (souls.isBlank()) {
                blank = stack;
            } else if (souls.type().get().equals(id) && souls.souls() < SpawnerSouls.MAX_SOULS) {
                return stack;
            }
        }
        return blank;
    }

    /** Un creeper pres d'un spawner de donjon : une chance sur deux d'un spawner casse. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getExplosion().getDirectSourceEntity() instanceof Creeper)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockState(pos).is(Blocks.SPAWNER) && level.getRandom().nextFloat() < BROKEN_SPAWNER_CHANCE) {
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                        new ItemStack(PalaHunter.BROKEN_SPAWNER.get())));
            }
        }
    }

    /** Casse, le spawner customise rend son objet et ses ames. */
    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof CustomSpawnerBlock)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SpawnerSouls souls = event.getBlockEntity() == null ? SpawnerSouls.EMPTY
                : event.getBlockEntity().getData(PalaHunter.SPAWNER_SOULS.get());
        BlockPos pos = event.getPos();
        ItemStack stack = CustomSpawnerItem.withSouls(new ItemStack(PalaHunter.CUSTOM_SPAWNER_ITEM.get()), souls);
        event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, stack));
    }

    /** Deux spawners de la meme creature : ames additionnees, total / 10 niveaux. */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!left.is(PalaHunter.CUSTOM_SPAWNER_ITEM.get()) || !right.is(PalaHunter.CUSTOM_SPAWNER_ITEM.get())) {
            return;
        }
        SpawnerSouls a = left.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        SpawnerSouls b = right.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        if (a.isBlank() || b.isBlank() || !a.type().equals(b.type())) {
            return;
        }
        int total = Math.min(SpawnerSouls.MAX_SOULS, a.souls() + b.souls());
        event.setOutput(CustomSpawnerItem.withSouls(left.copy(), new SpawnerSouls(a.type(), total)));
        event.setXpCost(Math.max(1, total / 10));
        event.setMaterialCost(1);
    }
}
