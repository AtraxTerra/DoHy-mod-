package fr.atraxterra.dohy.palamod;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Equippable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Au demarrage du serveur : les armures, outils et armes de PalaMod qu'on ne
 * pourrait pas enchanter.
 *
 * <p>En 26.2, enchanter demande deux choses que rien ne verifie a la
 * compilation : le composant {@code enchantable} (la table) et les tags
 * {@code minecraft:enchantable/*} que lit chaque enchantement (la table et
 * l'enclume). Un objet qui manque l'un ou l'autre se fabrique, se porte, et
 * refuse Solidite sans un mot. Ce controle releve, pour chaque objet du
 * namespace qui s'use, mine, frappe ou se porte en armure, les enchantements
 * de base de son type qu'il refuserait, et l'ecrit au journal.
 *
 * <p>Les objets a usages comptes (sticks, anneaux, outil magique...) s'usent
 * sans etre des outils : ils sont ecartes par {@link #COUNTED_USES}.
 */
@EventBusSubscriber
public final class EnchantabilityCheck {

    /** Des objets qui s'usent mais ne s'enchantent pas, sur Paladium non plus. */
    private static final List<String> COUNTED_USES = List.of(
            "_stick", "_ring", "magical_tool", "aggro_stone", "guardian_hammer", "capture_stone");

    private EnchantabilityCheck() {
    }

    private static boolean counted(String path) {
        for (String suffix : COUNTED_USES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /** Les refus, par objet : vide si tout va bien. Public pour la commande de diagnostic et les tests. */
    public static Map<String, List<String>> problems(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        Map<String, List<String>> problems = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!PalaMod.NAMESPACE.equals(id.getNamespace()) || counted(id.getPath())) {
                continue;
            }
            ItemStack stack = item.getDefaultInstance();
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            boolean armor = equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
            boolean tool = stack.has(DataComponents.TOOL);
            boolean weapon = stack.has(DataComponents.WEAPON);
            boolean damageable = stack.isDamageableItem();
            if (!armor && !tool && !weapon && !damageable) {
                continue;
            }
            List<String> missing = new ArrayList<>();
            if (!stack.has(DataComponents.ENCHANTABLE)) {
                missing.add("table");
            }
            refuse(enchantments, stack, damageable, Enchantments.UNBREAKING, missing);
            refuse(enchantments, stack, tool, Enchantments.EFFICIENCY, missing);
            refuse(enchantments, stack, weapon, Enchantments.SHARPNESS, missing);
            refuse(enchantments, stack, armor, Enchantments.PROTECTION, missing);
            if (!missing.isEmpty()) {
                problems.put(id.getPath(), missing);
            }
        }
        return problems;
    }

    private static void refuse(HolderLookup.RegistryLookup<Enchantment> enchantments, ItemStack stack, boolean applies,
                               ResourceKey<Enchantment> key, List<String> missing) {
        if (!applies) {
            return;
        }
        Holder<Enchantment> enchantment = enchantments.getOrThrow(key);
        if (!enchantment.value().canEnchant(stack)) {
            missing.add(key.identifier().getPath());
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        Map<String, List<String>> problems = problems(event.getServer().registryAccess());
        if (problems.isEmpty()) {
            DoHy.LOGGER.info("Enchantements : armures, outils et armes de PalaMod tous enchantables");
        } else {
            DoHy.LOGGER.warn("Enchantements refuses ({} objets) : {}", problems.size(), problems);
        }
    }
}
