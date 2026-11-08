package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.fluid.PalaFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La vie du chaudron forme ({@code TileEntityCauldronCore.func_145845_h}).
 *
 * <ul>
 *   <li><b>remplissage</b> : un seau d'eau angelique <b>jete</b> dans le bassin
 *       vide le remplit d'un coup (neuf sources) ; le seau est consomme ;</li>
 *   <li><b>bassin entame</b> : s'il reste de l'eau angelique sans que les neuf
 *       sources y soient — une source puisee, un seau verse a la main — le
 *       chaudron explose et son bassin devient de la lave ;</li>
 *   <li><b>travail</b> : bassin plein et un joueur a 4,5 blocs (le plus proche,
 *       qui doit avoir le niveau du coeur), tout objet jete dans le bassin est
 *       traite au tick ; le resultat ressort au-dessus du centre et retombe
 *       dans le bassin ;</li>
 *   <li><b>integrite</b> : toutes les 11 ticks de travail, un bloc manquant au
 *       plancher ou a l'anneau fait exploser le chaudron.</li>
 * </ul>
 */
public class CauldronCoreBlockEntity extends BlockEntity {

    private static final double PLAYER_RANGE = 4.5d;
    private static final int INTEGRITY_PERIOD = 11;
    /** Au plus 32 recettes par tick, comme la boucle de l'original. */
    private static final int MAX_CRAFTS_PER_TICK = 32;

    /**
     * XP de PalaJobs par fleur jetee ({@code DropCauldronObjective}, niveau 15) :
     * pissenlit et coquelicot 0,75, orchidee 1, les sept autres metas 2.
     */
    private static final Map<Item, Double> FLOWER_XP = Map.ofEntries(
            Map.entry(Items.DANDELION, 0.75d), Map.entry(Items.POPPY, 0.75d),
            Map.entry(Items.BLUE_ORCHID, 1.0d), Map.entry(Items.ALLIUM, 2.0d),
            Map.entry(Items.AZURE_BLUET, 2.0d), Map.entry(Items.RED_TULIP, 2.0d),
            Map.entry(Items.ORANGE_TULIP, 2.0d), Map.entry(Items.WHITE_TULIP, 2.0d),
            Map.entry(Items.PINK_TULIP, 2.0d), Map.entry(Items.OXEYE_DAISY, 2.0d));
    private static final int FLOWER_XP_LEVEL = 15;
    private static final double PALADIUM_FLOWER_XP = 100.0d;
    private static final int PALADIUM_FLOWER_XP_LEVEL = 80;

    private int timer;
    private boolean exploding;

    public CauldronCoreBlockEntity(BlockPos pos, BlockState state) {
        super(PalaAlchemist.CAULDRON_CORE_ENTITY.get(), pos, state);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server) || !CauldronCoreBlock.isFormed(getBlockState())) {
            return;
        }
        int[] basin = CauldronStructure.basin(server, worldPosition);
        boolean full = basin[0] == CauldronStructure.BASIN.size();
        if (basin[1] > 0 && !full) {
            explode(server);
            return;
        }
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class,
                new AABB(worldPosition).inflate(1.5d, 0.0d, 1.5d).expandTowards(0.0d, 2.5d, 0.0d),
                ItemEntity::isAlive);
        if (!full) {
            fillFromThrownBucket(server, items);
            return;
        }
        ServerPlayer player = nearestPlayer(server);
        if (player == null || !AlchemistXp.allows(player, PalaAlchemist.CAULDRON_CORE.get())) {
            return;
        }
        timer++;
        process(server, player, items);
        if (timer % INTEGRITY_PERIOD == 0 && !CauldronStructure.isComplete(server, worldPosition)) {
            explode(server);
        }
    }

    private void fillFromThrownBucket(ServerLevel server, List<ItemEntity> items) {
        for (BlockPos offset : CauldronStructure.BASIN) {
            if (!server.getBlockState(worldPosition.offset(offset)).isAir()) {
                return;
            }
        }
        for (ItemEntity item : items) {
            if (item.getItem().is(PalaFluids.ANGELIC_WATER_BUCKET.get())) {
                item.getItem().shrink(1);
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
                BlockState water = PalaFluids.ANGELIC_WATER_BLOCK.get().defaultBlockState();
                for (BlockPos offset : CauldronStructure.BASIN) {
                    server.setBlock(worldPosition.offset(offset), water, Block.UPDATE_ALL);
                }
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, worldPosition.getX() + 0.5d,
                        worldPosition.getY() + 2.0d, worldPosition.getZ() + 0.5d, 1, 0.0d, 0.0d, 0.0d, 0.0d);
                server.sendParticles(ParticleTypes.FLAME, worldPosition.getX() + 0.5d, worldPosition.getY() + 2.0d,
                        worldPosition.getZ() + 0.5d, 9, 1.0d, 0.1d, 1.0d, 0.0d);
                server.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                        4.0f, 1.0f);
                return;
            }
        }
    }

    private ServerPlayer nearestPlayer(ServerLevel server) {
        AABB area = new AABB(worldPosition).inflate(PLAYER_RANGE, 2.0d, PLAYER_RANGE);
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, area, ServerPlayer::isAlive)) {
            double distance = player.distanceToSqr(Vec3.atCenterOf(worldPosition));
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private void process(ServerLevel server, ServerPlayer player, List<ItemEntity> items) {
        RandomSource random = server.getRandom();
        List<ItemEntity> candidates = new ArrayList<>();
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem();
            int count = stack.getCount();
            Double flowerXp = FLOWER_XP.get(stack.getItem());
            if (flowerXp != null) {
                // Coquelicot et ses metas, pissenlit : une chance par fleur.
                for (int i = 0; i < count; i++) {
                    ItemStack rare = rareFlower(random);
                    if (!rare.isEmpty()) {
                        output(server, rare);
                    }
                }
                AlchemistXp.grant(player, flowerXp * count, FLOWER_XP_LEVEL);
                entity.discard();
            } else if (stack.is(PalaAlchemist.ENDIUM_FLOWER_ITEM.get())) {
                if (!AlchemistXp.allows(player, PalaAlchemist.ENDIUM_POLLEN.get())) {
                    // Ecart : l'original consommait la fleur pour rien.
                    AlchemistXp.refuse(player, PalaAlchemist.ENDIUM_POLLEN.get());
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    output(server, new ItemStack(PalaAlchemist.ENDIUM_POLLEN.get()));
                    if (random.nextInt(1000) <= 1) {
                        output(server, new ItemStack(PalaItems.ENDIUM_NUGGET.get()));
                    }
                    int roll = random.nextInt(100);
                    if (roll >= 90) {
                        output(server, new ItemStack(PalaAlchemist.AMETHYST_POLLEN.get()));
                    } else if (roll >= 75) {
                        output(server, new ItemStack(PalaAlchemist.TITANE_POLLEN.get()));
                    } else if (roll >= 50) {
                        output(server, new ItemStack(PalaAlchemist.PALADIUM_POLLEN.get()));
                    }
                }
                entity.discard();
            } else if (stack.is(PalaAlchemist.PALADIUM_FLOWER_ITEM.get())) {
                output(server, new ItemStack(PalaItems.PALADIUM_INGOT.get(), count));
                AlchemistXp.grant(player, PALADIUM_FLOWER_XP * count, PALADIUM_FLOWER_XP_LEVEL);
                entity.discard();
            } else if (stack.is(PalaAlchemist.TITANE_POLLEN.get())) {
                output(server, new ItemStack(PalaBlocks.TITANE_BLOCK.get(), count));
                entity.discard();
            } else if (stack.is(PalaAlchemist.AMETHYST_POLLEN.get())) {
                output(server, new ItemStack(PalaBlocks.AMETHYST_BLOCK.get(), 2 * count));
                entity.discard();
            } else if (stack.is(PalaAlchemist.PALADIUM_POLLEN.get())) {
                output(server, new ItemStack(PalaItems.PALADIUM_INGOT.get(), 5 * count));
                entity.discard();
            } else {
                candidates.add(entity);
            }
        }
        craft(server, candidates);
    }

    /**
     * La cascade de l'original, par fleur : 1/500 fleur minerale, sinon 1/300
     * fleur de paladium, sinon 1/5 000 harpagophytum, sinon 1/15 000 fleur
     * d'endium, sinon 1/1 754 clathrus.
     */
    private static ItemStack rareFlower(RandomSource random) {
        if (random.nextInt(500) == 0) {
            return new ItemStack(PalaAlchemist.MINERAL_FLOWER_ITEM.get());
        }
        if (random.nextInt(300) == 0) {
            return new ItemStack(PalaAlchemist.PALADIUM_FLOWER_ITEM.get());
        }
        if (random.nextInt(5000) == 0) {
            return new ItemStack(PalaAlchemist.HARPAGOPHYTUM_FLOWER_ITEM.get());
        }
        if (random.nextInt(15000) == 0) {
            return new ItemStack(PalaAlchemist.ENDIUM_FLOWER_ITEM.get());
        }
        if (random.nextInt(1754) == 0) {
            return new ItemStack(PalaAlchemist.CLATHRUSARCHERI_FLOWER_ITEM.get());
        }
        return ItemStack.EMPTY;
    }

    /** Les recettes a objets, une pour chaque bois brillant et la poudre lumineuse. */
    private void craft(ServerLevel server, List<ItemEntity> candidates) {
        int crafts = 0;
        while (crafts < MAX_CRAFTS_PER_TICK) {
            CauldronRecipe recipe = CauldronRecipe.match(candidates);
            if (recipe == null) {
                break;
            }
            recipe.consume(candidates);
            output(server, recipe.result());
            candidates.removeIf(entity -> !entity.isAlive() || entity.getItem().isEmpty());
            crafts++;
        }
        if (crafts > 0) {
            server.sendParticles(ParticleTypes.FLAME, worldPosition.getX() + 0.5d, worldPosition.getY() + 2.0d,
                    worldPosition.getZ() + 0.5d, 12, 1.0d, 0.2d, 1.0d, 0.0d);
            server.playSound(null, worldPosition, SoundEvents.WITHER_DEATH, SoundSource.BLOCKS, 0.6f, 0.6f);
        }
    }

    private void output(ServerLevel server, ItemStack stack) {
        ItemEntity item = new ItemEntity(server, worldPosition.getX() + 0.5d, worldPosition.getY() + 2.0d,
                worldPosition.getZ() + 0.5d, stack, 0.0d, 0.0d, 0.0d);
        server.addFreshEntity(item);
    }

    /** {@code destroy()} : explosion, le coeur tombe, le bassin devient de la lave. */
    private void explode(ServerLevel server) {
        exploding = true;
        BlockPos pos = worldPosition;
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5d, pos.getY() + 1.5d,
                pos.getZ() + 0.5d, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        server.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0f, 1.0f);
        Block.popResource(server, pos.above(2), new ItemStack(PalaAlchemist.CAULDRON_CORE.get()));
        server.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos offset : CauldronStructure.BASIN) {
            BlockPos cell = pos.offset(offset);
            if (!server.getBlockState(cell).is(Blocks.BEDROCK)) {
                server.setBlock(cell, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** {@code unmount()} : un coeur forme casse vide son bassin d'eau angelique. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (exploding || level == null || !CauldronCoreBlock.isFormed(state)) {
            return;
        }
        for (BlockPos offset : CauldronStructure.BASIN) {
            BlockPos cell = pos.offset(offset);
            if (level.getFluidState(cell).getType().isSame(PalaFluids.ANGELIC_WATER.get())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        timer = input.getIntOr("Timer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Timer", timer);
    }
}
