package fr.atraxterra.dohy.palamod.hunter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.function.Consumer;

/**
 * Le spawner vide, puis charge d'ames ({@code ItemEmptySpawner}) : par un, il
 * porte sa creature et ses ames ({@link SpawnerSouls}).
 *
 * <p>Nom et infobulle de l'original : « Spawner vide » ou « Spawner (Zombie) »,
 * puis le palier en couleur et le nombre d'ames. L'icone prend la couleur du
 * palier, par la donnee de modele que {@link #withSouls} tient a jour.
 */
public class CustomSpawnerItem extends BlockItem {

    /** {@code §7 §e §a §2 §6} pour les paliers 0 a 4. */
    private static final ChatFormatting[] TIER_COLORS = {
            ChatFormatting.GRAY, ChatFormatting.YELLOW, ChatFormatting.GREEN,
            ChatFormatting.DARK_GREEN, ChatFormatting.GOLD};

    public CustomSpawnerItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Pose les ames et la couleur d'icone qui va avec. */
    public static ItemStack withSouls(ItemStack stack, SpawnerSouls souls) {
        stack.set(PalaHunter.SOULS.get(), souls);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of((float) souls.tier()), List.of(), List.of(), List.of()));
        return stack;
    }

    static Component entityName(Identifier id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        return type == null ? Component.literal(id.toString()) : type.getDescription();
    }

    @Override
    public Component getName(ItemStack stack) {
        SpawnerSouls souls = stack.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        return souls.type().map(id -> (Component) Component.translatable("item.palamod.empty_mob_spawner.filled",
                entityName(id))).orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        SpawnerSouls souls = stack.getOrDefault(PalaHunter.SOULS.get(), SpawnerSouls.EMPTY);
        if (souls.type().isEmpty()) {
            tooltip.accept(Component.translatable("item.palamod.empty_mob_spawner.no_souls")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int tier = souls.tier();
        tooltip.accept(Component.translatable("item.palamod.empty_mob_spawner.tier", tier,
                SpawnerSouls.CREATURES_PER_TIER * tier).withStyle(TIER_COLORS[tier]));
        tooltip.accept(Component.translatable("item.palamod.empty_mob_spawner.souls", souls.souls(),
                SpawnerSouls.MAX_SOULS, entityName(souls.type().get())).withStyle(ChatFormatting.YELLOW));
    }
}
