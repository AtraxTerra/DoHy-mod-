package fr.atraxterra.dohy.palamod.lucky;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.redstone.Orientation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les blocs des lucky blocks du cinquieme lot ({@code luckyblock/blocks}), chacun dans sa
 * classe interne : la plupart n'ont que quelques lignes. Les ecarts avec PalaMod sont dits
 * au-dessus de chaque bloc, et repris dans {@code docs/PALAMOD.md} (section 41).
 */
final class GadgetBlocks {

    private GadgetBlocks() {
    }

    /** Un bloc de decoration a la forme reduite ({@code renderPetRock}, le ballon, le panier...). */
    static class ShapedBlock extends Block {
        private final VoxelShape shape;

        ShapedBlock(VoxelShape shape, Properties properties) {
            super(properties);
            this.shape = shape;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }
    }

    // -- Colorful Lamp --------------------------------------------------------------

    /**
     * La lampe coloree ({@code BlockColoredLamp}) : allumee par la redstone (lumiere 14), de
     * seize couleurs ({@code getRenderColor} selon la metadonnee). Un clic change la couleur.
     */
    static class LampBlock extends Block {
        static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 15);
        static final BooleanProperty LIT = BlockStateProperties.LIT;
        /** Les seize teintes de {@code getRenderColor}, dans l'ordre des metadonnees. */
        static final int[] TINTS = {0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF, 0xFF7D00,
                0xFF7D7D, 0x7DFF7D, 0x7D00FF, 0x7DFF00, 0x7D7DFF, 0x00FF7D, 0xFF007D, 0xBEBEBE};

        LampBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(COLOR, 0).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(COLOR, LIT);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.cycle(COLOR), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server) {
                server.scheduleTick(pos, this, 1);
            }
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                       Orientation orientation, boolean moved) {
            if (level instanceof ServerLevel server) {
                server.scheduleTick(pos, this, 2);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, powered), Block.UPDATE_ALL);
            }
        }
    }

    // -- Hunter Plant ---------------------------------------------------------------------

    /**
     * La plante du chasseur ({@code BlockHunterPlant}) : toutes les 3 000 tics, un monstre au
     * hasard a cote d'elle (zombie, squelette, creeper, araignee). De quoi chasser.
     */
    static class HunterFlowerBlock extends ShapedBlock {
        static final int PERIOD = 3000;

        HunterFlowerBlock(Properties properties) {
            super(Block.box(4, 0, 4, 12, 13, 12), properties);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, PERIOD);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            var types = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER, EntityTypes.SPIDER,
                    EntityTypes.ZOMBIE);
            Mob mob = (Mob) LuckyWorld.create(level, types.get(random.nextInt(types.size())), pos.getX() + 0.5d,
                    pos.getY(), pos.getZ() + 0.5d);
            if (mob != null) {
                level.addFreshEntity(mob);
            }
            level.scheduleTick(pos, this, PERIOD);
        }
    }

    // -- TNT mignonnes et Fake TNT ---------------------------------------------------------

    /**
     * Les TNT de PalaMod ({@code BlockKawaiiTNT}, {@code BlockFakeTNT}) : allumees au briquet,
     * a la charge de feu ou par la redstone, elles brulent quatre secondes, puis
     * {@code createExplosion} a leur puissance : 0 pour la TNT mignonne (un simple pop) et la
     * fausse TNT, 5 pour la moins mignonne.
     */
    static class LuckyTntBlock extends Block {
        private final float power;

        LuckyTntBlock(float power, Properties properties) {
            super(properties);
            this.power = power;
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
            if (!(stack.getItem() instanceof FlintAndSteelItem) && !(stack.getItem() instanceof FireChargeItem)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (level instanceof ServerLevel server) {
                prime(server, pos, state, player);
                if (stack.getItem() instanceof FlintAndSteelItem) {
                    stack.hurtAndBreak(1, player, hand);
                } else {
                    stack.consume(1, player);
                }
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                       Orientation orientation, boolean moved) {
            if (level instanceof ServerLevel server && server.hasNeighborSignal(pos)) {
                prime(server, pos, state, null);
            }
        }

        private void prime(ServerLevel level, BlockPos pos, BlockState state, LivingEntity igniter) {
            level.removeBlock(pos, false);
            PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, igniter);
            tnt.setFuse(90);
            tnt.setBlockState(state);
            level.addFreshEntity(tnt);
            level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0f, 1.0f);
            LuckyTasks.later(null, 80, () -> {
                if (tnt.isRemoved()) {
                    return;
                }
                tnt.discard();
                if (power > 0.0f) {
                    level.explode(null, tnt.getX(), tnt.getY(), tnt.getZ(), power, Level.ExplosionInteraction.TNT);
                } else {
                    level.sendParticles(ParticleTypes.HEART, tnt.getX(), tnt.getY() + 0.5d, tnt.getZ(), 12, 0.6d,
                            0.4d, 0.6d, 0.0d);
                    level.playSound(null, tnt.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 1.0f,
                            1.6f);
                }
            });
        }
    }

    // -- Wi-Fi ------------------------------------------------------------------------------

    /**
     * Le levier sans fil ({@code BlockWirelessLever}) : relie a un bloc (clic accroupi sur ce
     * bloc, levier en main), il l'actionne a distance. <b>Ecart</b> : PalaMod alimentait la
     * position reliee ; ici le levier ouvre ou ferme ce qui s'ouvre (porte, trappe, portillon)
     * et allume ou eteint ce qui s'allume (lampe).
     */
    static class WirelessLeverBlock extends Block implements EntityBlock {
        WirelessLeverBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LinkBlockEntity(pos, state);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (level.getBlockEntity(pos) instanceof LinkBlockEntity link) {
                GadgetItems.WirelessLeverItem.target(stack).ifPresent(link::link);
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof LinkBlockEntity link)) {
                return InteractionResult.SUCCESS;
            }
            if (link.target == null) {
                player.sendSystemMessage(Component.literal("Ce levier n'est relie a rien.").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            BlockState target = server.getBlockState(link.target);
            BlockState next = null;
            for (var property : List.of(BlockStateProperties.OPEN, BlockStateProperties.LIT, BlockStateProperties.POWERED)) {
                if (target.hasProperty(property)) {
                    next = target.cycle(property);
                    break;
                }
            }
            if (next == null) {
                player.sendSystemMessage(Component.literal("Le bloc relie ne s'actionne pas.").withStyle(ChatFormatting.RED));
            } else {
                server.setBlock(link.target, next, Block.UPDATE_ALL);
                server.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 0.8f);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** La position reliee d'un levier sans fil, ou le proprietaire d'un piege a ficelle. */
    static class LinkBlockEntity extends BlockEntity {
        private BlockPos target;
        private UUID owner;

        LinkBlockEntity(BlockPos pos, BlockState state) {
            super(LuckyGadgets.LINK_ENTITY.get(), pos, state);
        }

        void link(BlockPos pos) {
            target = pos;
            setChanged();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (target != null) {
                output.store("target", BlockPos.CODEC, target);
            }
            if (owner != null) {
                output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
            }
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            target = input.read("target", BlockPos.CODEC).orElse(null);
            owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        }
    }

    // -- Coloriage ------------------------------------------------------------------------------

    /**
     * La machine a teindre ({@code BlockDyeingMachine}) : un objet teignable en main (armure de
     * cuir...), chaque clic lui donne la couleur suivante. <b>Ecart</b> : PalaMod ouvrait un
     * ecran de melange des teintures.
     */
    static class DyeingMachineBlock extends Block {
        DyeingMachineBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
            if (!stack.has(DataComponents.DYED_COLOR) && !stack.is(net.minecraft.tags.ItemTags.DYEABLE)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (level instanceof ServerLevel server) {
                DyedItemColor current = stack.get(DataComponents.DYED_COLOR);
                int index = 0;
                DyeColor[] colors = DyeColor.values();
                if (current != null) {
                    for (int i = 0; i < colors.length; i++) {
                        if (colors[i].getTextureDiffuseColor() == current.rgb()) {
                            index = (i + 1) % colors.length;
                        }
                    }
                }
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colors[index].getTextureDiffuseColor()));
                server.sendParticles(ParticleTypes.EFFECT, pos.getX() + 0.5d, pos.getY() + 1.1d, pos.getZ() + 0.5d, 8,
                        0.3d, 0.1d, 0.3d, 0.0d);
                server.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // -- Casse Oreilles -------------------------------------------------------------------------

    /** L'alarme ({@code BlockAlarm}) : alimentee, elle sonne fort, une fois par seconde. */
    static class AlarmBlock extends ShapedBlock {
        static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        AlarmBlock(Properties properties) {
            super(Block.box(3, 0, 3, 13, 12, 13), properties);
            registerDefaultState(stateDefinition.any().setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWERED);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server) {
                server.scheduleTick(pos, this, 2);
            }
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                       Orientation orientation, boolean moved) {
            if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, 2);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            }
            if (powered) {
                level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.8f);
                level.scheduleTick(pos, this, 20);
            }
        }
    }

    // -- Nether Active ---------------------------------------------------------------------------

    /**
     * Le faux portail du Nether ({@code BlockFakePortal}) : le traverser tue, puis le portail
     * et son cadre d'obsidienne disparaissent.
     */
    static class FakePortalBlock extends Block {
        FakePortalBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Block.box(6, 0, 0, 10, 16, 16);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                    InsideBlockEffectApplier effects, boolean intersects) {
            if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)
                    || player.isCreative() || player.isSpectator()) {
                return;
            }
            player.hurtServer(server, server.damageSources().genericKill(), Float.MAX_VALUE);
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -3, -2), pos.offset(2, 3, 2))) {
                BlockState there = server.getBlockState(near);
                if (there.is(this) || there.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
                    server.removeBlock(near, false);
                }
            }
        }
    }

    // -- Piege a Noob ---------------------------------------------------------------------------

    /**
     * Le piege a machoires ({@code BlockJawsTrap}) : ce qui marche dedans est retenu et blesse
     * (un demi-coeur tous les cinq tics dans PalaMod ; ici un coeur toutes les demi-secondes).
     */
    static class JawsTrapBlock extends ShapedBlock {
        JawsTrapBlock(Properties properties) {
            super(Block.box(1, 0, 1, 15, 3, 15), properties);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                    InsideBlockEffectApplier effects, boolean intersects) {
            if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)
                    || entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return;
            }
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 6, false, false));
            if (living.tickCount % 10 == 0) {
                living.hurtServer(server, server.damageSources().generic(), 2.0f);
                server.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
            }
        }
    }

    // -- Caillou de compagnie -------------------------------------------------------------------

    /** Le caillou de compagnie ({@code BlockPetRock}) : un clic, et il est content. */
    static class PetRockBlock extends ShapedBlock {
        PetRockBlock(Properties properties) {
            super(Block.box(4, 0, 4, 12, 6, 12), properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5d, pos.getY() + 0.7d, pos.getZ() + 0.5d, 4,
                        0.25d, 0.2d, 0.25d, 0.0d);
                server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // -- Belle etoile ---------------------------------------------------------------------------

    /**
     * Le sac de couchage ({@code BlockSleepingBag}) : on y dort la nuit sans changer son point
     * de reapparition. <b>Ecart</b> : la nuit passe d'un coup, comme quand tout le monde dort.
     */
    static class SleepingBagBlock extends ShapedBlock {
        SleepingBagBlock(Properties properties) {
            super(Block.box(0, 0, 0, 16, 3, 16), properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            if (!server.isDarkOutside() || server.dimension() != Level.OVERWORLD) {
                player.sendSystemMessage(Component.literal("Tu ne peux dormir a la belle etoile que la nuit.")
                        .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.SUCCESS;
            }
            var minecraft = server.getServer();
            minecraft.getCommands().performPrefixedCommand(minecraft.createCommandSourceStack().withSuppressedOutput(),
                    "time set day");
            server.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
            player.sendSystemMessage(Component.literal("Une nuit a la belle etoile...").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }
    }

    // -- Attention c'est chaud ------------------------------------------------------------------

    /** La fausse lave ({@code BlockFakeLava}) : sans boite de collision, elle ne brule pas. */
    static class FakeLavaBlock extends Block {
        FakeLavaBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                    InsideBlockEffectApplier effects, boolean intersects) {
            entity.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(0.6d, 0.4d, 0.6d));
        }
    }

    // -- Detecteurs --------------------------------------------------------------------------------

    /** Un bloc qui emet un signal de redstone tant qu'il est {@code POWERED}. */
    abstract static class DetectorBlock extends Block {
        static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        DetectorBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(POWERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWERED);
        }

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return state.getValue(POWERED) ? 15 : 0;
        }

        void power(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
            if (state.getValue(POWERED) != on) {
                level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, this);
            }
        }
    }

    /** Le detecteur de chats ({@code TileEntityCatDetector}) : un chat a huit blocs, et le signal. */
    static class CatDetectorBlock extends DetectorBlock {
        CatDetectorBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, 20);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            boolean cat = !level.getEntities((Entity) null, new AABB(pos).inflate(8.0d),
                    entity -> entity.getType() == EntityTypes.CAT || entity.getType() == EntityTypes.OCELOT).isEmpty();
            power(level, pos, state, cat);
            level.scheduleTick(pos, this, 20);
        }
    }

    /**
     * Le detecteur de chat ({@code TileEntityChatDetector}) : un message dans le chat, et une
     * impulsion d'une seconde. <b>Ecart</b> : PalaMod laissait choisir un mot ; ici tout
     * message compte.
     */
    static class ChatDetectorBlock extends DetectorBlock {
        static final Set<Placed> PLACED = new HashSet<>();

        record Placed(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos) {
        }

        ChatDetectorBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server) {
                PLACED.add(new Placed(server.dimension(), pos.immutable()));
                if (!server.getBlockTicks().hasScheduledTick(pos, this)) {
                    server.scheduleTick(pos, this, 100);
                }
            }
        }

        /** Toutes les cinq secondes, il se reinscrit (au redemarrage, la liste repart vide) et s'eteint. */
        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            PLACED.add(new Placed(level.dimension(), pos.immutable()));
            power(level, pos, state, false);
            level.scheduleTick(pos, this, 100);
        }

        static void onChat(net.minecraft.server.MinecraftServer server) {
            PLACED.removeIf(placed -> {
                ServerLevel level = server.getLevel(placed.dimension());
                if (level == null || !level.isLoaded(placed.pos())) {
                    return false;
                }
                BlockState state = level.getBlockState(placed.pos());
                if (!(state.getBlock() instanceof ChatDetectorBlock detector)) {
                    return true;
                }
                detector.power(level, placed.pos(), state, true);
                level.scheduleTick(placed.pos(), detector, 20);
                return false;
            });
        }
    }

    // -- Piege a ficelle ------------------------------------------------------------------------------

    /**
     * Le piege a ficelle ({@code BlockStringTrapOff}) : une ficelle tendue au sol, qui empetre ce
     * qui la traverse, sauf celui qui l'a posee. <b>Ecart</b> : PalaMod le reglait par un ecran
     * (cible, effet) ; ici il ralentit et fait briller l'intrus.
     */
    static class StringTrapBlock extends ShapedBlock implements EntityBlock {
        StringTrapBlock(Properties properties) {
            super(Block.box(0, 0, 0, 16, 2, 16), properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LinkBlockEntity(pos, state);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (placer instanceof Player player && level.getBlockEntity(pos) instanceof LinkBlockEntity owner) {
                owner.owner = player.getUUID();
                owner.setChanged();
            }
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                    InsideBlockEffectApplier effects, boolean intersects) {
            if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)
                    || level.getBlockEntity(pos) instanceof LinkBlockEntity link && entity.getUUID().equals(link.owner)) {
                return;
            }
            if (!living.hasEffect(MobEffects.SLOWNESS)) {
                server.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2));
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        }
    }

    // -- Comment t'as eu ca ? ----------------------------------------------------------------------

    /**
     * Le faux bloc de commande ({@code BlockFakeCommandBlock}) : une impulsion de redstone, et il
     * « execute » {@code give @p granite}.
     */
    static class FakeCommandBlock extends Block {
        static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;

        FakeCommandBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(TRIGGERED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(TRIGGERED);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                       Orientation orientation, boolean moved) {
            if (!(level instanceof ServerLevel server)) {
                return;
            }
            boolean powered = server.hasNeighborSignal(pos);
            if (powered && !state.getValue(TRIGGERED)) {
                Player nearest = server.getNearestPlayer(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                        16.0d, false);
                if (nearest != null) {
                    ItemStack granite = new ItemStack(Items.GRANITE);
                    if (!nearest.getInventory().add(granite)) {
                        nearest.drop(granite, false);
                    }
                }
            }
            if (powered != state.getValue(TRIGGERED)) {
                server.setBlock(pos, state.setValue(TRIGGERED, powered), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("Commande : give @p granite").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.SUCCESS;
        }
    }

    // -- Camouflage ------------------------------------------------------------------------------

    /**
     * La fleur des lucky blocks ({@code LuckyBlockFlower}) : elle fane trois jours apres avoir ete
     * posee ({@code 259 200 000} millisecondes). <b>Ecart</b> : trois jours de jeu, pas de vraie vie.
     */
    static class LuckyFlowerBlock extends ShapedBlock {
        static final int LIFETIME = 3 * 24000 * 72;

        LuckyFlowerBlock(Properties properties) {
            super(Block.box(5, 0, 5, 11, 12, 11), properties);
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (level instanceof ServerLevel server && !server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, LIFETIME);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            level.removeBlock(pos, false);
        }
    }

    // -- Coffres-forts ------------------------------------------------------------------------------

    /**
     * Les coffres-forts ({@code BlockSafeChest}, {@code BlockMegaSafeChest}, {@code BlockUltraSafeChest}) :
     * 54 cases, a la seule personne qui l'a pose. <b>Ecarts</b> : PalaMod fermait le coffre par un
     * code ; ici c'est le joueur qui l'a pose. Le mega et l'ultra gardent leur contenu quand on les
     * casse (le grand coffre-fort de PalaMod l'emportait dans l'objet) ; le simple le lache.
     */
    static class SafeChestBlock extends Block implements EntityBlock {
        static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING =
                BlockStateProperties.HORIZONTAL_FACING;
        private final boolean keeps;

        SafeChestBlock(boolean keeps, Properties properties) {
            super(properties);
            this.keeps = keeps;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        boolean keeps() {
            return keeps;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new SafeChestBlockEntity(pos, state);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (placer instanceof Player player && level.getBlockEntity(pos) instanceof SafeChestBlockEntity safe) {
                safe.owner = player.getUUID();
                safe.ownerName = player.getName().getString();
                var contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) {
                    contents.copyInto(safe.items);
                }
                safe.setChanged();
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SafeChestBlockEntity safe) {
                if (safe.allows(player)) {
                    player.openMenu(safe);
                } else {
                    player.sendSystemMessage(Component.literal("Ce coffre-fort appartient a " + safe.ownerName + ".")
                            .withStyle(ChatFormatting.RED));
                }
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SafeChestBlockEntity safe) {
                if (keeps) {
                    ItemStack drop = new ItemStack(this);
                    drop.set(DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.fromItems(safe.items));
                    if (!player.isCreative() || !safe.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, drop);
                    }
                    safe.clearContent();
                } else {
                    Containers.dropContents(level, pos, safe);
                }
            }
            return super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
            if (level.getBlockEntity(pos) instanceof SafeChestBlockEntity safe && !safe.allows(player)) {
                return 0.0f;
            }
            return super.getDestroyProgress(state, player, level, pos);
        }
    }

    /** Le contenu d'un coffre-fort et son proprietaire. */
    static class SafeChestBlockEntity extends BlockEntity implements Container, MenuProvider {
        static final int SIZE = 54;
        private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        private UUID owner;
        private String ownerName = "?";

        SafeChestBlockEntity(BlockPos pos, BlockState state) {
            super(LuckyGadgets.SAFE_ENTITY.get(), pos, state);
        }

        boolean allows(Player player) {
            return owner == null || owner.equals(player.getUUID()) || player.isCreative();
        }

        /** Le Bruteforcer a trouve le code : le coffre s'ouvre a tous. */
        void unlock() {
            owner = null;
            ownerName = "personne";
            setChanged();
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack removed = ContainerHelper.removeItem(items, slot, count);
            if (!removed.isEmpty()) {
                setChanged();
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            stack.limitSize(getMaxStackSize(stack));
            setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(this, player) && allows(player);
        }

        @Override
        public void clearContent() {
            items.clear();
        }

        @Override
        public Component getDisplayName() {
            return getBlockState().getBlock().getName();
        }

        @Override
        public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
            return ChestMenu.sixRows(id, inventory, this);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            ContainerHelper.saveAllItems(output, items);
            if (owner != null) {
                output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
            }
            output.putString("owner_name", ownerName);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            items.clear();
            ContainerHelper.loadAllItems(input, items);
            owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
            ownerName = input.getStringOr("owner_name", "?");
        }
    }

    // -- John The Reaper ---------------------------------------------------------------------------

    /**
     * Le Bruteforcer ({@code BlockBruteforcer}) : pose a cote d'un coffre-fort, un clic lance le
     * cassage du code ; cinq minutes plus tard, le coffre s'ouvre a tous.
     */
    static class BruteforcerBlock extends Block {
        static final int DURATION = 6000;
        static final BooleanProperty POWERED = BlockStateProperties.POWERED;

        BruteforcerBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(POWERED, false)
                    .setValue(SafeChestBlock.FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(POWERED, SafeChestBlock.FACING);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return defaultBlockState().setValue(SafeChestBlock.FACING, context.getHorizontalDirection().getOpposite());
        }

        private static SafeChestBlockEntity safe(Level level, BlockPos pos) {
            for (Direction direction : Direction.values()) {
                if (level.getBlockEntity(pos.relative(direction)) instanceof SafeChestBlockEntity safe) {
                    return safe;
                }
            }
            return null;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            if (safe(level, pos) == null) {
                player.sendSystemMessage(Component.literal("Aucun coffre-fort trouve a proximite du Bruteforcer.")
                        .withStyle(ChatFormatting.RED));
            } else if (!state.getValue(POWERED)) {
                server.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
                server.scheduleTick(pos, this, DURATION);
                player.sendSystemMessage(Component.literal("Recherche du code... (5 minutes)").withStyle(ChatFormatting.GREEN));
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            SafeChestBlockEntity safe = safe(level, pos);
            if (safe != null) {
                safe.unlock();
                level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.2f);
            }
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        }
    }

    // -- Ton heure a sonne ---------------------------------------------------------------------------

    /**
     * La tombe ({@code BlockTombe}, {@code TileEntityTombe}) : elle garde ce que le joueur portait a
     * sa mort. Un clic de son proprietaire lui rend tout, et la tombe s'en va.
     */
    static class TombBlock extends ShapedBlock implements EntityBlock {
        TombBlock(Properties properties) {
            super(Block.box(1, 0, 4, 15, 15, 12), properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TombBlockEntity(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                   BlockHitResult hit) {
            if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof TombBlockEntity tomb)) {
                return InteractionResult.SUCCESS;
            }
            if (tomb.owner != null && !tomb.owner.equals(player.getUUID()) && !player.isCreative()) {
                player.sendSystemMessage(Component.literal("Ici repose " + tomb.ownerName + ".")
                        .withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            for (ItemStack stack : tomb.items) {
                if (!player.getInventory().add(stack.copy())) {
                    player.drop(stack.copy(), false);
                }
            }
            tomb.items.clear();
            server.removeBlock(pos, false);
            LuckyWorld.sound(server, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, "particle.soul_escape",
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
            return 0.0f;
        }
    }

    /** Ce que garde une tombe. */
    static class TombBlockEntity extends BlockEntity {
        private final List<ItemStack> items = new ArrayList<>();
        private UUID owner;
        private String ownerName = "?";

        TombBlockEntity(BlockPos pos, BlockState state) {
            super(LuckyGadgets.TOMB_ENTITY.get(), pos, state);
        }

        void fill(UUID owner, String name, List<ItemStack> stacks) {
            this.owner = owner;
            this.ownerName = name;
            items.clear();
            items.addAll(stacks);
            setChanged();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), items);
            if (owner != null) {
                output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
            }
            output.putString("owner_name", ownerName);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            items.clear();
            input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(items::addAll);
            owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
            ownerName = input.getStringOr("owner_name", "?");
        }
    }
}
