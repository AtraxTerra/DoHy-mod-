package fr.atraxterra.dohy.skill;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Application des perks.
 *
 * <h2>Modificateurs transitoires, pas permanents</h2>
 *
 * <p>Les modificateurs d'attribut sont ajoutes en <b>transitoire</b> : ils ne
 * sont pas sauvegardes avec le joueur et sont recalcules a chaque connexion
 * depuis les niveaux de skill. C'est la seule facon sure de proceder — un
 * modificateur permanent survit a la sauvegarde, donc le moindre bug de
 * recalcul se traduirait par des bonus qui s'empilent a l'infini au fil des
 * connexions, sans moyen de revenir en arriere.
 *
 * <p>Pour la meme raison, une montee de niveau recalcule <b>tout</b> plutot que
 * d'appliquer un delta : l'identifiant du modificateur etant stable par perk,
 * la mise a jour remplace la valeur au lieu de l'additionner.
 */
@EventBusSubscriber
public final class SkillPerks {

    private SkillPerks() {
    }

    /** Recalcule les perks a la connexion. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
        }
    }

    /** Recalcule les perks apres une reapparition : les attributs sont reinitialises. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
        }
    }

    /**
     * Applique tous les perks d'attribut correspondant aux niveaux actuels.
     *
     * <p>Les perks d'un meme attribut dans un meme skill partagent un
     * identifiant par niveau, si bien que seul le plus haut palier atteint reste
     * actif pour un attribut donne lorsqu'ils se succedent — et que deux skills
     * touchant le meme attribut se cumulent, ce qui est l'effet voulu.
     */
    public static void apply(ServerPlayer player) {
        for (Skill skill : Skill.values()) {
            int level = SkillApi.levelOf(player, skill);
            for (SkillConfig.Perk perk : SkillConfig.config().def(skill).perks()) {
                if (!"attribute".equals(perk.type()) || perk.attribute().isEmpty()) {
                    continue;
                }
                Identifier modifierId = Identifier.fromNamespaceAndPath(DoHy.MOD_ID,
                        "perk/" + skill.key() + "/" + perk.level());
                applyOne(player, perk, modifierId, level >= perk.level());
            }
        }
    }

    private static void applyOne(ServerPlayer player, SkillConfig.Perk perk,
                                 Identifier modifierId, boolean active) {
        Identifier attributeId = Identifier.bySeparator(perk.attribute().orElseThrow(), ':');
        Holder.Reference<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId).orElse(null);
        if (attribute == null) {
            DoHy.LOGGER.warn("Perk ignore : attribut inconnu {}", attributeId);
            return;
        }

        AttributeInstance instance = player.getAttributes().getInstance(attribute);
        if (instance == null) {
            // Attribut valide mais absent de ce type d'entite.
            return;
        }

        if (!active) {
            instance.removeModifier(modifierId);
            return;
        }

        AttributeModifier.Operation operation = parseOperation(perk.operation().orElse("add_value"));
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(modifierId, perk.amount(), operation));
    }

    private static AttributeModifier.Operation parseOperation(String raw) {
        try {
            return AttributeModifier.Operation.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException error) {
            DoHy.LOGGER.warn("Operation de perk inconnue '{}', add_value utilise", raw);
            return AttributeModifier.Operation.ADD_VALUE;
        }
    }

    /** Somme des bonus d'XP de skill accordes par les perks de ce skill. */
    public static double xpBonus(ServerPlayer player, Skill skill) {
        return sumOf(player, skill, "xp_bonus");
    }

    /** Somme des chances de double drop accordees par les perks de ce skill. */
    public static double doubleDropChance(ServerPlayer player, Skill skill) {
        return sumOf(player, skill, "double_drop_chance");
    }

    /**
     * La meilleure valeur acquise d'un type de perk dans un skill donne.
     *
     * <p>Point d'entree unique pour tous les effets qui ne sont pas des
     * attributs : {@link SkillEffects} les lit par ce biais plutot que de
     * reparcourir la table des perks, de sorte que la regle « seul le plus haut
     * palier compte » n'existe qu'a un seul endroit.
     */
    public static double value(ServerPlayer player, Skill skill, String type) {
        return sumOf(player, skill, type);
    }

    /**
     * La meilleure valeur acquise d'un type de perk, tous skills confondus.
     *
     * <p>Utile pour les effets qui n'appartiennent qu'a un skill — les degats de
     * chute a la construction, le cout d'enclume a la forge — sans que
     * l'appelant ait a savoir lequel. Si un jour deux skills portent le meme
     * type, le meilleur des deux l'emporte, ce qui ne cumule jamais.
     */
    public static double value(ServerPlayer player, String type) {
        double best = 0.0d;
        for (Skill skill : Skill.values()) {
            best = Math.max(best, sumOf(player, skill, type));
        }
        return best;
    }

    /**
     * Les perks d'un meme type ne s'additionnent pas : seul le plus haut palier
     * atteint compte. Sans ca, un perk de 2 % puis 5 % puis 15 % donnerait 22 %
     * au lieu des 15 % annonces au joueur.
     */
    private static double sumOf(ServerPlayer player, Skill skill, String type) {
        int level = SkillApi.levelOf(player, skill);
        double best = 0.0d;
        for (SkillConfig.Perk perk : SkillConfig.config().def(skill).perks()) {
            if (perk.type().equals(type) && level >= perk.level()) {
                best = Math.max(best, perk.amount());
            }
        }
        return best;
    }
}
