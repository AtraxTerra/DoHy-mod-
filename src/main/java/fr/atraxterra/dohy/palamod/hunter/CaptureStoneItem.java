package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.function.Consumer;

/**
 * Pierre de capture ({@code ItemCaptureStone}).
 *
 * <p>Vide, elle attend une creature : la tuer a l'epee de capture avec une pierre
 * vide dans l'inventaire l'y enferme ({@link HunterEvents}). Pleine, un clic
 * droit sur un bloc la relache et consomme la pierre. Elle ne s'empile pas, vide
 * ou pleine.
 *
 * <p>La creature revient avec toutes ses donnees (nom, age, couleur, taille,
 * effets, armure) et sa vie pleine. <b>Ecarts</b> : elle apparait au-dessus de la
 * face cliquee, centree, comme un oeuf d'apparition — l'original la posait au
 * coin du bloc, deux blocs plus haut ; son identifiant d'origine est oublie, faute
 * de quoi deux copies d'une meme pierre se marcheraient dessus ; et la protection
 * des zones est celle du jeu ({@code mayUseItemAt}).
 */
public class CaptureStoneItem extends Item {

    public CaptureStoneItem(Properties properties) {
        super(properties);
    }

    public static boolean isFull(ItemStack stack) {
        return stack.has(PalaHunter.CAPTURED.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        CapturedEntity captured = stack.get(PalaHunter.CAPTURED.get());
        return captured == null ? super.getName(stack)
                : Component.translatable("item.palamod.capture_stone.full", captured.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CapturedEntity captured = stack.get(PalaHunter.CAPTURED.get());
        tooltip.accept(captured == null
                ? Component.translatable("item.palamod.capture_stone.empty")
                : Component.translatable("item.palamod.capture_stone.entity", captured.name()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CapturedEntity captured = stack.get(PalaHunter.CAPTURED.get());
        Player player = context.getPlayer();
        if (captured == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        if (player != null && !player.mayUseItemAt(target, context.getClickedFace(), stack)) {
            return InteractionResult.FAIL;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(captured.type());
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (entity == null) {
            return InteractionResult.FAIL;
        }
        CompoundTag data = captured.data().copy();
        data.remove("UUID");
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
        entity.snapTo(target.getX() + 0.5d, target.getY(), target.getZ() + 0.5d,
                entity.getYRot(), entity.getXRot());
        if (entity instanceof LivingEntity living) {
            living.setHealth(living.getMaxHealth());
            living.deathTime = 0;
        }
        entity.setDeltaMovement(0.0d, 0.0d, 0.0d);
        level.addFreshEntity(entity);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
