package fr.atraxterra.dohy.palamod.wither;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.job.JobApi;
import fr.atraxterra.dohy.palamod.defense.DefenseGate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le receptacle a wither (wiki « Réceptacle à Wither ») : clic droit avec une
 * gemme de wither, et l'invocation commence.
 *
 * <p>Ce qu'il faut : une faction, la cle de l'arbre qui debloque ce wither, le
 * niveau d'Alchimiste des gemmes majeures (assoiffe 70, predateur 90, supreme
 * 100 — les niveaux 14, 18 et 20 du wiki, sur 100), et un receptacle <b>hors de
 * tout claim</b> : Paladium demandait une zone neutre, et un wither ne nait pas
 * dans sa propre base.
 */
public class WitherReceptacleBlock extends BaseEntityBlock {

    public static final MapCodec<WitherReceptacleBlock> CODEC = simpleCodec(WitherReceptacleBlock::new);

    public WitherReceptacleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WitherReceptacleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof WitherReceptacleBlockEntity receptacle) {
                WitherReceptacleBlockEntity.tick(tickLevel, pos, tickState, receptacle);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof WitherReceptacleBlockEntity receptacle) {
            receptacle.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WitherGemItem gem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(player instanceof ServerPlayer server)
                || !(level.getBlockEntity(pos) instanceof WitherReceptacleBlockEntity receptacle)) {
            return InteractionResult.SUCCESS;
        }
        String refusal = refusal(server, level, pos, receptacle, gem.kind());
        if (refusal != null) {
            FactionApi.tell(server, refusal, ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (!DefenseGate.check(server, gem.kind().unlock())) {
            return InteractionResult.FAIL;
        }
        receptacle.start(gem.kind(), server);
        stack.consume(1, player);
        level.playSound(null, pos, gem.kind() == WitherKind.FUNNY ? PalaWithers.FUNNY_WITHER_INVOKE.get()
                : SoundEvents.WITHER_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.6f);
        FactionApi.tell(server, "Invocation lancee : reste a moins de 6 blocs du receptacle pendant "
                + duration(gem.kind().summonTicks()) + ".", ChatFormatting.GOLD);
        return InteractionResult.SUCCESS;
    }

    private static String refusal(ServerPlayer player, Level level, BlockPos pos,
                                  WitherReceptacleBlockEntity receptacle, WitherKind kind) {
        if (!FactionApi.has(player) && !player.isCreative()) {
            return "Il faut une faction pour invoquer un wither (/f create).";
        }
        if (FactionApi.claimAt(level, pos).claimed()) {
            return "Le receptacle doit etre hors de tout claim : un wither ne s'invoque pas dans une base.";
        }
        if (receptacle.busy()) {
            return "Une invocation est deja en cours sur ce receptacle.";
        }
        if (kind.alchemist() > 0 && !player.isCreative() && !JobApi.meets(player, Job.ALCHEMIST, kind.alchemist())) {
            return "Cette gemme demande le niveau " + kind.alchemist() + " d'Alchimiste.";
        }
        return null;
    }

    static String duration(int ticks) {
        int seconds = ticks / 20;
        return seconds < 60 ? seconds + " s" : (seconds / 60) + " min";
    }
}
