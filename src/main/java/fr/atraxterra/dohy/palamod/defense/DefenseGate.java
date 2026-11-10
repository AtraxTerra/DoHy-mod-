package fr.atraxterra.dohy.palamod.defense;

import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ItemLike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Les verrous de l'arbre de faction sur les objets de defense et de pillage.
 *
 * <p>Sur Paladium, l'arbre debloque la <i>fabrication</i> de certains objets
 * (« craft: ») et l'<i>utilisation</i> des autres (« use: »). Ici, comme pour
 * les metiers ({@code JobGateEvents}), tout reste fabricable et c'est la pose
 * ou l'usage qui demande le palier : 26.2 ne permet pas d'annuler une
 * fabrication sans detruire ses ingredients. Un objet fabrique trop tot
 * attend dans un coffre.
 *
 * <p>Sans faction, rien n'est debloque. Le creatif passe outre, sans que la
 * progression reelle change.
 */
public final class DefenseGate {

    /** Objet (par son identifiant palamod) -> la cle de l'arbre qui le debloque. */
    private static final Map<String, String> KEYS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REFUSAL = new HashMap<>();
    private static final long REFUSAL_COOLDOWN = 40L;

    static {
        for (String name : new String[] {"hardened_obsidian", "hardened_obsidian_stairs", "hardened_obsidian_slab"}) {
            KEYS.put(name, "craft:hardened_obsidian");
        }
        for (String effect : new String[] {"poison", "lava", "boom", "sulfuric", "big", "knockback", "mega_boom",
                "anti_aggro", "slime", "frozen"}) {
            KEYS.put(effect + "_obsidian", "use:" + effect + "_obsidian");
        }
        for (String metal : new String[] {"wood", "iron", "gold", "diamond"}) {
            KEYS.put(metal + "_spike", "use:spikes_basic");
            KEYS.put(metal + "_spike_obsidian", "use:spikes_basic");
        }
        for (String metal : new String[] {"amethyst", "titane", "paladium"}) {
            KEYS.put(metal + "_spike", "use:spikes_" + metal);
            KEYS.put(metal + "_spike_obsidian", "use:spikes_" + metal);
            KEYS.put(metal + "_anvil", "craft:" + metal + "_anvil");
        }
        KEYS.put("coordinate_jammer", "craft:coordinate_jammer");
        KEYS.put("lava_sponge", "craft:lava_sponge");
        KEYS.put("aggro_stone", "craft:aggro_stone");
        KEYS.put("fake_water_bucket", "craft:fake_water");
        KEYS.put("pistol_tank", "use:pistol_tank");
        KEYS.put("home_remover", "use:home_remover");
        KEYS.put("voyance_amulet", "craft:voyance_amulet");
        // Le pillage (lot 22d).
        KEYS.put("dynamite", "use:dynamite");
        KEYS.put("ninja_dynamite", "use:ninja_dynamite");
        KEYS.put("big_dynamite", "use:big_dynamite");
        KEYS.put("hood_helmet", "craft:hood_helmet");
        KEYS.put("chest_explorer", "craft:chest_explorer");
        KEYS.put("online_detector", "use:online_detector");
        KEYS.put("paladium_bow", "craft:paladium_bow");
        KEYS.put("switch_arrow", "craft:switch_arrow");
        KEYS.put("extrapolated_bucket", "craft:extrapolated_bucket");
        // La TNT a wither invoque un wither vanilla : le palier qui debloque les withers.
        KEYS.put("wither_tnt", "use:wither");
    }

    private DefenseGate() {
    }

    /** La cle qui debloque cet objet, ou {@code null} s'il est libre. */
    public static String key(ItemLike item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item.asItem());
        return "palamod".equals(id.getNamespace()) ? KEYS.get(id.getPath()) : null;
    }

    public static boolean allows(ServerPlayer player, String key) {
        return key == null || player.isCreative() || FactionTree.unlocked(player, key);
    }

    /** Vrai si le joueur peut ; sinon le lui dit (une fois toutes les deux secondes) et rend faux. */
    public static boolean check(ServerPlayer player, String key) {
        if (allows(player, key)) {
            return true;
        }
        long now = player.level().getGameTime();
        Long last = LAST_REFUSAL.get(player.getUUID());
        if (last == null || now - last >= REFUSAL_COOLDOWN || now < last) {
            if (LAST_REFUSAL.size() > 256) {
                LAST_REFUSAL.clear();
            }
            LAST_REFUSAL.put(player.getUUID(), now);
            player.sendSystemMessage(FactionApi.prefix().append(refusal(player, key)));
        }
        return false;
    }

    private static Component refusal(ServerPlayer player, String key) {
        if (!FactionApi.has(player)) {
            return Component.literal("Il te faut une faction et son arbre de technologie (/f create).")
                    .withStyle(ChatFormatting.RED);
        }
        return FactionTree.unlockOf(key)
                .<Component>map(unlock -> Component.literal("Verrouille : " + unlock.label() + ". Palier "
                        + unlock.tier().number() + " de la branche " + unlock.tier().branch().label()
                        + " de ton arbre de faction.").withStyle(ChatFormatting.RED))
                .orElse(Component.literal("Verrouille par l'arbre de faction.").withStyle(ChatFormatting.RED));
    }
}
