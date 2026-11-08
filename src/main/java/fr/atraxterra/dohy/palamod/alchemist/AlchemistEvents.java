package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce que l'Alchimiste fait hors de ses blocs.
 *
 * <ul>
 *   <li><b>potion de foudre</b> ({@code EventHandlerAlchemist.playerTick}) :
 *       sous la pluie, coiffe d'un casque en fer ou en or, une fiole d'eau vide
 *       en main — une chance sur 800 par tick, une quarantaine de secondes en
 *       moyenne — la foudre tombe vingt blocs au-dessus et la fiole devient une
 *       potion de foudre ; 30 XP de PalaJobs. <b>Ecart</b> : l'original se
 *       contentait de la pluie quelque part dans le monde ; il faut ici etre
 *       sous la pluie ;</li>
 *   <li><b>buches</b> : 10 XP de PalaJobs par buche de jacaranda, de judée ou
 *       d'érable coupee (rien pour l'ostrya, comme l'original) ;</li>
 *   <li><b>table de l'Alchimiste</b> : une table d'enchantement entouree de plus
 *       de 60 de puissance ouvre {@link MysticEnchantMenu} ;</li>
 *   <li><b>grass breaker</b> : voir {@link MysticEnchant}.</li>
 * </ul>
 */
@EventBusSubscriber
public final class AlchemistEvents {

    /** {@code nextInt(800) == 300}. */
    private static final int LIGHTNING_CHANCE = 800;
    private static final double LIGHTNING_XP = 30.0d;
    private static final double LOG_XP = 10.0d;
    /** {@code > 60.0F} : seize bibliotheques mystiques. */
    private static final float MYSTIC_POWER = 60.0f;

    private AlchemistEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(Items.IRON_HELMET) && !helmet.is(Items.GOLDEN_HELMET)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(Items.GLASS_BOTTLE) || !level.isRainingAt(player.blockPosition().above())) {
            return;
        }
        if (player.getRandom().nextInt(LIGHTNING_CHANCE) != 0) {
            return;
        }
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(player.getX(), player.getY() + 20.0d, player.getZ(), 0.0f, 0.0f);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        held.shrink(1);
        ItemStack potion = new ItemStack(PalaAlchemist.LIGHTNING_POTION.get());
        if (!player.getInventory().add(potion)) {
            player.drop(potion, false);
        }
        AlchemistXp.grant(player, LIGHTNING_XP, 0);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getState();
        if (state.is(PalaWoods.JACARANDA_LOG.get()) || state.is(PalaWoods.JUDEECERCIS_LOG.get())
                || state.is(PalaWoods.ERABLE_LOG.get())) {
            if (!PlacedBlocks.wasPlayerPlaced(event)) {
                AlchemistXp.grant(player, LOG_XP, 0);
            }
            return;
        }
        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
            int level = MysticEnchant.GRASS_BREAKER.levelOn(player, event.getTool());
            if (level > 0) {
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem();
                    stack.grow(player.getRandom().nextInt(level + 1));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE) || player.isSecondaryUseActive()) {
            return;
        }
        if (power(level, pos) <= MYSTIC_POWER) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        MysticEnchantMenu.open(player);
    }

    /** La puissance des bibliotheques autour de la table, sans le plafond de 15 du vanilla. */
    static float power(Level level, BlockPos table) {
        float power = 0.0f;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
                BlockPos shelf = table.offset(offset);
                power += level.getBlockState(shelf).getEnchantPowerBonus(level, shelf);
            }
        }
        return power;
    }
}
