package fr.atraxterra.dohy.palamod.godpickaxe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code ItemGodPickaxe} : la pioche des dieux, qui monte de niveau en minant.
 *
 * <ul>
 *   <li><b>Incassable</b> : la barre d'usure montre l'avancement vers le niveau
 *       suivant, et disparait au niveau 100 ;</li>
 *   <li><b>Paxel</b> : la vitesse du palier vaut pour tous les blocs, et la
 *       recolte vaut pour la pioche, la hache et la pelle ;</li>
 *   <li><b>XP</b> : 1 par bloc plein (ou feuillage) de durete non nulle ;</li>
 *   <li><b>Efficacite et Fortune</b> : poses par la pioche elle-meme selon le
 *       palier. Elle n'est pas enchantable, ni a la table, ni a l'enclume ;</li>
 *   <li><b>Toucher de soie</b> : au niveau 100, accroupi + clic droit
 *       l'active ou le retire ;</li>
 *   <li><b>Fusion automatique</b> : une amelioration posee au grinder, au
 *       niveau 100 seulement (voir {@link GodPickaxeEvents}).</li>
 * </ul>
 *
 * <p><b>Ecart</b> : PalaMod reecrivait les enchantements a chaque bloc mine, ce
 * qui effacait le Toucher de soie. Ici ils ne changent qu'au changement de
 * palier, et le Toucher de soie reste.
 */
public class GodPickaxeItem extends Item {

    private static final int BAR_STEPS = 13;

    public GodPickaxeItem(Properties properties) {
        super(properties);
    }

    public static GodPickaxeData data(ItemStack stack) {
        return stack.getOrDefault(PalaGodPickaxe.DATA.get(), GodPickaxeData.EMPTY);
    }

    public static int level(ItemStack stack) {
        return data(stack).level();
    }

    public static boolean isSilkTouch(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
                .anyMatch(holder -> holder.is(Enchantments.SILK_TOUCH));
    }

    // -- Minage ------------------------------------------------------------------

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return GodPickaxeLevels.tier(level(stack)).material().speed();
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        GodPickaxeLevels.Tier tier = GodPickaxeLevels.tier(level(stack));
        return !state.is(tier.material().incorrectBlocksForDrops())
                && (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && miner instanceof Player
                && (state.isSolidRender() || state.is(BlockTags.LEAVES))
                && state.getDestroySpeed(level, pos) > 0.0f) {
            addXp(stack, 1.0d, level.registryAccess());
        }
        return true;
    }

    /** {@code addXp} : l'XP ne compte plus au niveau 100. */
    public static void addXp(ItemStack stack, double amount, HolderLookup.Provider registries) {
        GodPickaxeData data = data(stack);
        int before = data.level();
        if (before >= GodPickaxeLevels.MAX) {
            return;
        }
        GodPickaxeData next = data.withXp(data.xp() + amount);
        stack.set(PalaGodPickaxe.DATA.get(), next);
        if (next.level() != before) {
            applyLevel(stack, registries);
        }
    }

    /** Pose l'Efficacite, la Fortune et la texture du palier ; le Toucher de soie reste. */
    public static void applyLevel(ItemStack stack, HolderLookup.Provider registries) {
        int level = level(stack);
        GodPickaxeLevels.Tier tier = GodPickaxeLevels.tier(level);
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            set(mutable, enchantments, Enchantments.EFFICIENCY, tier.efficiency());
            set(mutable, enchantments, Enchantments.FORTUNE, tier.fortune());
            if (level < GodPickaxeLevels.MAX) {
                set(mutable, enchantments, Enchantments.SILK_TOUCH, 0);
            }
        });
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of((float) GodPickaxeLevels.texture(level)), List.of(), List.of(), List.of()));
    }

    private static void set(ItemEnchantments.Mutable mutable,
                            HolderLookup.RegistryLookup<Enchantment> enchantments,
                            ResourceKey<Enchantment> key, int value) {
        mutable.removeIf(holder -> holder.is(key));
        if (value > 0) {
            enchantments.get(key).ifPresent(holder -> mutable.set(holder, value));
        }
    }

    // -- Toucher de soie -----------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || level(stack) < GodPickaxeLevels.MAX) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            boolean enable = !isSilkTouch(stack);
            HolderLookup.RegistryLookup<Enchantment> enchantments =
                    level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            EnchantmentHelper.updateEnchantments(stack,
                    mutable -> set(mutable, enchantments, Enchantments.SILK_TOUCH, enable ? 1 : 0));
            server.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(enable
                    ? "message.palamod.god_pickaxe.silk_on" : "message.palamod.god_pickaxe.silk_off")
                    .withStyle(ChatFormatting.AQUA)));
        }
        return InteractionResult.SUCCESS;
    }

    // -- Affichage -------------------------------------------------------------

    /** {@code Pickaxe of the gods (Niveau N)}, et {@code [Silk]} quand il est actif. */
    @Override
    public Component getName(ItemStack stack) {
        Component name = Component.empty().append(super.getName(stack))
                .append(Component.translatable("item.palamod.god_pickaxe.level", level(stack) + 1)
                        .withStyle(ChatFormatting.GRAY));
        if (isSilkTouch(stack)) {
            name = name.copy().append(Component.translatable("item.palamod.god_pickaxe.silk")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return name;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GodPickaxeData data = data(stack);
        int level = data.level();
        tooltip.accept(Component.translatable("item.palamod.god_pickaxe.tooltip.level", level + 1)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.empty());
        if (level >= GodPickaxeLevels.MAX) {
            tooltip.accept(Component.translatable("item.palamod.god_pickaxe.tooltip.max")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltip.accept(Component.translatable("item.palamod.god_pickaxe.tooltip.next",
                            Component.literal(String.format(Locale.ROOT, "%.1f", GodPickaxeLevels.missing(data.xp())))
                                    .withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (data.autoSmelt()) {
            tooltip.accept(Component.translatable("item.palamod.god_pickaxe.tooltip.auto_smelt")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return level(stack) < GodPickaxeLevels.MAX;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(BAR_STEPS * GodPickaxeLevels.progress(data(stack).xp()));
    }

    /** La couleur de l'usure vanilla : rouge en debut de niveau, vert pres du suivant. */
    @Override
    public int getBarColor(ItemStack stack) {
        float hue = GodPickaxeLevels.progress(data(stack).xp()) / 3.0f;
        float rise = Math.min(1.0f, hue * 6.0f);
        float fall = Math.max(0.0f, Math.min(1.0f, 2.0f - hue * 6.0f));
        return ((int) (255 * fall) << 16) | ((int) (255 * rise) << 8);
    }
}
