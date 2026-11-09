package fr.atraxterra.dohy.palamod.legendary;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * Le gant en endium ({@code ItemEndiumGauntlet}) : un porte-pierres.
 *
 * <ul>
 *   <li>Il nait a l'etabli autour d'une pierre (voir {@link EndiumGauntletRecipe})
 *       et en recoit d'autres au grinder, une de chaque type au plus.</li>
 *   <li>Accroupi, clic droit : la pierre suivante, dans l'ordre de l'enumeration.</li>
 *   <li>Clic droit : l'effet de la pierre selectionnee, avec la recharge propre a
 *       cette pierre dans ce gant — 24 heures, 12 quand il en porte six.</li>
 * </ul>
 *
 * <p><b>Ecart</b> : l'original prenait l'icone dorée de l'Infinity Gauntlet
 * quand on renommait le gant ainsi a l'enclume. Ici, c'est le gant complet —
 * les six pierres — qui prend ce nom et cette icone. Il n'avait aucune
 * durabilite reelle (128, jamais entamee) : il n'en a pas.
 */
public class EndiumGauntletItem extends Item {

    /** La chaine de {@code custom_model_data} que lit la definition d'objet. */
    private static final String INFINITY = "infinity";

    public EndiumGauntletItem(Properties properties) {
        super(properties);
    }

    public static GauntletData data(ItemStack stack) {
        return stack.getOrDefault(PalaLegendary.GAUNTLET.get(), GauntletData.EMPTY);
    }

    /** Ecrit le contenu, et l'icone qui va avec. */
    public static void store(ItemStack stack, GauntletData data) {
        stack.set(PalaLegendary.GAUNTLET.get(), data);
        syncIcon(stack, data);
    }

    private static void syncIcon(ItemStack stack, GauntletData data) {
        boolean infinity = data.full();
        CustomModelData current = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        boolean shown = current != null && current.strings().contains(INFINITY);
        if (infinity && !shown) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of(INFINITY), List.of()));
        } else if (!infinity && shown) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    /** Un gant venu d'ailleurs (caisse, commande) prend son icone au premier tick. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        syncIcon(stack, data(stack));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        GauntletData data = data(stack);
        List<LegendaryEffect> stones = data.sorted();
        if (stones.isEmpty()) {
            LegendaryStones.tell(serverPlayer, Component.translatable("message.palamod.gauntlet.empty"));
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            // L'original repartait a zero sur la longueur du tableau brut, qui
            // comptait aussi les ameliorations « plus d'emplacements » : il
            // pouvait deborder. On tourne sur les pierres.
            GauntletData next = data.withSelected(Math.floorMod(data.selected() + 1, stones.size()));
            store(stack, next);
            LegendaryStones.tell(serverPlayer, Component.translatable("message.palamod.gauntlet.selected",
                    Component.translatable(next.current().nameKey())));
            return InteractionResult.SUCCESS;
        }
        LegendaryEffect effect = data.current();
        long cooldown = data.full() ? LegendaryStones.FULL_GAUNTLET_COOLDOWN : LegendaryStones.STONE_COOLDOWN;
        boolean used = LegendaryStones.activate(serverPlayer, server, effect, data.lastUse(effect), cooldown,
                now -> store(stack, data(stack).withLastUse(effect, now)));
        return used ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** « Endium Gauntlet (Chaos) », ou « Infinity Gauntlet (Chaos) » complet. */
    @Override
    public Component getName(ItemStack stack) {
        GauntletData data = data(stack);
        Component base = data.full() ? Component.translatable("item.palamod.endium_gauntlet.infinity")
                : super.getName(stack);
        LegendaryEffect current = data.current();
        return current == null ? base
                : Component.translatable("item.palamod.endium_gauntlet.named", base,
                        Component.translatable(current.nameKey()));
    }

    /** Les pierres, la selectionnee marquee, et la recharge de chacune. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GauntletData data = data(stack);
        List<LegendaryEffect> stones = data.sorted();
        if (stones.isEmpty()) {
            return;
        }
        long cooldown = data.full() ? LegendaryStones.FULL_GAUNTLET_COOLDOWN : LegendaryStones.STONE_COOLDOWN;
        tooltip.accept(Component.translatable("tooltip.palamod.gauntlet.stones")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE));
        LegendaryEffect current = data.current();
        for (LegendaryEffect stone : stones) {
            MutableComponent line = Component.literal(stone == current ? "> " : "  ")
                    .append(Component.translatable(stone.nameKey()))
                    .append(Component.literal(" : "))
                    .append(LegendaryStoneItem.status(data.lastUse(stone), cooldown));
            tooltip.accept(line.withStyle(stone == current ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.palamod.gauntlet.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
