package fr.atraxterra.dohy.palamod.godpickaxe;

import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.List;

/**
 * La fusion automatique de la pioche des dieux ({@code onBlockDestroyed}).
 *
 * <p>Seul le premier objet lache compte : s'il a une recette de four, le bloc
 * ne rend que ce resultat, en un exemplaire ; sinon il rend ses objets
 * habituels. Sur un minerai, la Fortune donne {@code 1 + (F - r) / 2}
 * exemplaires, {@code r} tire entre 0 et F - 1 : 1 a 2 en Fortune III, 1,67
 * en moyenne. Le Toucher de soie coupe la fusion.
 *
 * <p>Tout bloc y passe, comme dans PalaMod : la pierre redonne de la pierre,
 * le sable du verre, une buche du charbon de bois.
 *
 * <p><b>Ecart</b> : dans PalaMod, la fusion cassait le bloc elle-meme, et les
 * orbes d'XP du minerai etaient perdues. Ici elle ne fait que remplacer les
 * objets lâches : l'XP du minerai reste.
 */
@EventBusSubscriber
public final class GodPickaxeEvents {

    /** {@code ToolHandler.minerals} : les minerais ou la Fortune multiplie la fusion. */
    public static final TagKey<Block> MINERALS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "god_pickaxe_minerals"));

    private GodPickaxeEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        ItemStack tool = event.getTool();
        if (!(tool.getItem() instanceof GodPickaxeItem) || !GodPickaxeItem.data(tool).autoSmelt()
                || GodPickaxeItem.isSilkTouch(tool) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<ItemEntity> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }
        ItemEntity first = drops.getFirst();
        SingleRecipeInput input = new SingleRecipeInput(first.getItem().copyWithCount(1));
        ItemStack smelted = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> holder.value().assemble(input))
                .orElse(ItemStack.EMPTY);
        if (smelted.isEmpty()) {
            return;
        }
        int count = 1;
        int fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool)).orElse(0);
        if (fortune > 0 && event.getState().is(MINERALS)) {
            count = Math.max(1, 1 + (fortune - level.getRandom().nextInt(fortune)) / 2);
        }
        first.setItem(smelted.copyWithCount(count));
        drops.subList(1, drops.size()).clear();
    }
}
