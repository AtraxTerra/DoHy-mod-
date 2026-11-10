package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.palamod.fluid.PalaFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * Le pistolet a liquide ({@code PISTOL_TANK}) : un reservoir en paladium monte
 * sur une crosse, qui pose un liquide la ou l'on vise.
 *
 * <p>Clic droit sur une source a portee de main : le pistolet la pompe (eau,
 * lave, eau angelique, Fake Water ; seize seaux, un seul liquide a la fois).
 * Clic droit ailleurs : il pose un seau du liquide la ou le regard touche un
 * bloc, jusqu'a 24 blocs. Pas dans le claim d'un autre.
 */
public class PistolTankItem extends Item {

    public static final int CAPACITY = 16;
    private static final double RANGE = 24.0d;

    public PistolTankItem(Properties properties) {
        super(properties);
    }

    private static boolean pumpable(Fluid fluid) {
        return fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.LAVA)
                || fluid.isSame(PalaFluids.ANGELIC_WATER.get()) || fluid.isSame(PalaDefense.FAKE_WATER.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !DefenseGate.check(serverPlayer, DefenseGate.key(this))) {
            return InteractionResult.FAIL;
        }
        PistolLoad load = stack.getOrDefault(PalaDefense.PISTOL_LOAD.get(), PistolLoad.EMPTY);

        BlockHitResult reach = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (reach.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = reach.getBlockPos();
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isSource() && pumpable(fluid.getType()) && load.amount() < CAPACITY
                    && (load.isEmpty() || fluid.getType().isSame(load.fluidType()))) {
                if (!level.isClientSide() && allowed(player, level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    store(stack, load.plus(fluid.getType()));
                    level.playSound(null, pos, fluid.is(FluidTags.LAVA)
                            ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                return InteractionResult.SUCCESS;
            }
        }

        if (load.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        if (hit.getType() != HitResult.Type.BLOCK || !(load.fluidType().getBucket() instanceof BucketItem bucket)) {
            return InteractionResult.FAIL;
        }
        BlockPos clicked = hit.getBlockPos();
        BlockPos beside = clicked.relative(hit.getDirection());
        boolean placed = (allowed(player, level, clicked) && bucket.emptyContents(player, level, clicked, hit, stack))
                || (allowed(player, level, beside) && bucket.emptyContents(player, level, beside, hit, stack));
        if (!placed) {
            return InteractionResult.FAIL;
        }
        store(stack, load.minus());
        return InteractionResult.SUCCESS;
    }

    /** Pas de pompage ni de tir dans le claim d'un autre joueur. */
    private static boolean allowed(Player player, Level level, BlockPos pos) {
        ClaimOwner owner = FactionApi.claimAt(level, pos);
        return !owner.claimed() || owner.ownedBy(player.getUUID()) || player.isCreative();
    }

    /** Le reservoir, et l'icone qui montre ce qu'il contient (eau, lave, autre). */
    private static void store(ItemStack stack, PistolLoad load) {
        if (load.isEmpty()) {
            stack.remove(PalaDefense.PISTOL_LOAD.get());
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        stack.set(PalaDefense.PISTOL_LOAD.get(), load);
        Fluid fluid = load.fluidType();
        String look = fluid.isSame(Fluids.WATER) ? "water" : fluid.isSame(Fluids.LAVA) ? "lava" : "fluid";
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(look), List.of()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        PistolLoad load = stack.getOrDefault(PalaDefense.PISTOL_LOAD.get(), PistolLoad.EMPTY);
        if (load.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.palamod.pistol_tank.empty", CAPACITY));
        } else {
            tooltip.accept(Component.translatable("tooltip.palamod.pistol_tank.load", load.amount(), CAPACITY,
                    load.fluidType().getFluidType().getDescription()));
        }
    }
}
