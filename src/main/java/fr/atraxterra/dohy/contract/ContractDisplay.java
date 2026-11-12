package fr.atraxterra.dohy.contract;

import fr.atraxterra.dohy.ui.Gui;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Comment un contrat se montre au joueur.
 *
 * <p>Le mod stocke des cibles techniques — {@code minecraft:deepslate},
 * {@code #c:iron_ores}, parfois rien du tout — parce que c'est ce qu'il faut
 * pour tester une progression. Ce n'est pas ce qu'il faut pour le lire. Un
 * joueur a qui on annonce « miner 2000 #minecraft:base_stone_overworld » lit de
 * la plomberie.
 *
 * <p>Trois niveaux, du meilleur au pire : l'intitule ecrit dans le datapack, le
 * nom de l'item ou de l'entite visee, puis un verbe generique tire du type. Le
 * dernier niveau est deliberement vague plutot que precis-et-illisible.
 */
public final class ContractDisplay {

    private ContractDisplay() {
    }

    /** Intitule complet, quantite comprise : « Miner du soufre — 200 ». */
    public static Component title(ActiveContract contract) {
        return Component.literal(label(contract) + "  —  "
                + MoneyFormat.exact(contract.amount())
                + (contract.kind() == ContractType.SELL_VALUE ? "" : " unites"));
    }

    /** Intitule seul, sans quantite. */
    public static String label(ActiveContract contract) {
        if (contract.label().isPresent()) {
            return contract.label().get();
        }
        String verb = verb(contract.kind());
        String target = targetName(contract);
        return target.isEmpty() ? verb : verb + " : " + target;
    }

    /** Ligne de progression : barre, puis chiffres. */
    public static Component progress(ActiveContract contract) {
        if (contract.kind() == ContractType.SELL_VALUE) {
            return Gui.bar(contract.ratio(), 20).copy()
                    .append(Component.literal("   " + MoneyFormat.exact(contract.progress())
                            + " / " + MoneyFormat.exact(contract.amount())));
        }
        return Gui.bar(contract.progress(), contract.amount(), 20);
    }

    /**
     * Nom de la cible, jamais son identifiant.
     *
     * <p>Une cible peut etre un item, un bloc, une entite ou un tag. Les tags
     * n'ont pas de nom traduit : on embellit le dernier segment du chemin, ce
     * qui donne « Iron ores » pour {@code #c:iron_ores} — approximatif mais
     * lisible, et de toute facon rattrape par un {@code label} du datapack
     * partout ou ca compte.
     */
    public static String targetName(ActiveContract contract) {
        String raw = contract.target().orElse("");
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("#")) {
            String path = raw.substring(1);
            int slash = path.lastIndexOf('/');
            int colon = path.indexOf(':');
            String tail = path.substring(Math.max(slash + 1, colon + 1));
            return Gui.prettify(tail);
        }
        Identifier id = parse(raw);
        if (id == null) {
            return Gui.prettify(raw);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item != null) {
            return new ItemStack(item).getHoverName().getString();
        }
        EntityType<?> entity = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (entity != null) {
            return entity.getDescription().getString();
        }
        return Gui.prettify(id.getPath());
    }

    /**
     * Fabrique un identifiant sans passer par {@code tryParse}, dont la
     * presence n'est pas verifiee sur cette version. Un chemin sans deux-points
     * est pris dans {@code minecraft}, comme partout ailleurs dans le jeu.
     */
    private static Identifier parse(String raw) {
        try {
            int colon = raw.indexOf(':');
            return colon < 0
                    ? Identifier.fromNamespaceAndPath("minecraft", raw)
                    : Identifier.fromNamespaceAndPath(raw.substring(0, colon), raw.substring(colon + 1));
        } catch (Exception malformed) {
            return null;
        }
    }

    /** Icone du contrat : celle du datapack, sinon la cible, sinon le type. */
    public static Item icon(ActiveContract contract) {
        if (contract.icon().isPresent()) {
            Identifier id = parse(contract.icon().get());
            Item explicit = id == null ? null
                    : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (explicit != null) {
                return explicit;
            }
        }

        String raw = contract.target().orElse("");
        if (!raw.isEmpty() && !raw.startsWith("#")) {
            Identifier id = parse(raw);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null) {
                    return item;
                }
            }
        }
        return byType(contract.kind());
    }

    private static Item byType(ContractType type) {
        return switch (type) {
            case MINE_BLOCK -> Items.IRON_PICKAXE;
            case KILL_ENTITY -> Items.IRON_SWORD;
            case HARVEST -> Items.WHEAT;
            case FISH -> Items.FISHING_ROD;
            case SMELT -> Items.FURNACE;
            case SELL_VALUE -> Items.GOLD_INGOT;
            case TRAVEL -> Items.LEATHER_BOOTS;
            case PLACE_BLOCK -> Items.BRICKS;
            case COLLECTION_TIER -> Items.CHEST;
            case JOB_LEVELS -> Items.EXPERIENCE_BOTTLE;
            case PRODUCE -> Items.CRAFTING_TABLE;
            case UNKNOWN -> Items.PAPER;
        };
    }

    private static String verb(ContractType type) {
        return switch (type) {
            case MINE_BLOCK -> "Miner";
            case KILL_ENTITY -> "Abattre";
            case HARVEST -> "Recolter";
            case FISH -> "Pecher";
            case SMELT -> "Cuire au four";
            case SELL_VALUE -> "Vendre au shop";
            case TRAVEL -> "Parcourir des blocs";
            case PLACE_BLOCK -> "Poser des blocs";
            case COLLECTION_TIER -> "Franchir des paliers de collection";
            case JOB_LEVELS -> "Gagner des niveaux de metier";
            case PRODUCE -> "Produire a la machine";
            case UNKNOWN -> "Contrat inconnu";
        };
    }
}
