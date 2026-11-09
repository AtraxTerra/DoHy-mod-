package fr.atraxterra.dohy.palamod.dungeon;

import fr.atraxterra.dohy.job.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.CHARGE;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.FIRE_RING;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.PULL;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.ROOT;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.SLAM;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.SUMMON;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.TELEPORT;
import static fr.atraxterra.dohy.palamod.dungeon.BossAbility.VOLLEY;

/**
 * Les quatre donjons (« Java | Donjon v12 ») : chacun lie a un metier, dont il
 * donne l'XP a chaque palier reussi.
 *
 * <p>Les salles sont construites par le mod, dans les blocs du theme du
 * donjon : la ferme et la foret pour Talikus, la mine pour Nimbria, la jungle
 * de chasse pour Vitalys, le temple marin pour Manelios. Les monstres sont des
 * monstres vanilla du theme, renforces au palier ; les boss, les noms du wiki
 * poses sur des monstres vanilla agrandis.
 *
 * <p>Mini-boss et boss : le wiki en nomme cinq de chaque par donjon (l'original
 * de la V11, puis quatre de la V12) sans dire ou ils apparaissent. La table des
 * recompenses de boss va de 5 en 5 ; on place donc un mini-boss aux paliers
 * en 5 (5, 15, 25...) et un boss aux paliers en 0, chacun a tour de role.
 */
public enum Dungeon {

    TALIKUS("talikus", "Talikus", Job.FARMER, ChatFormatting.GREEN),
    NIMBRIA("nimbria", "Nimbria", Job.MINER, ChatFormatting.GRAY),
    VITALYS("vitalys", "Vitalys", Job.HUNTER, ChatFormatting.RED),
    MANELIOS("manelios", "Manelios", Job.ALCHEMIST, ChatFormatting.AQUA);

    /** Les blocs d'une salle. */
    public record Palette(Block floor, Block floorAlt, Block wall, Block pillar, Block ceiling, Block light,
                          Block decor, Block button) {
    }

    private final String key;
    private final String display;
    private final Job job;
    private final ChatFormatting color;

    Dungeon(String key, String display, Job job, ChatFormatting color) {
        this.key = key;
        this.display = display;
        this.job = job;
        this.color = color;
    }

    public String key() {
        return key;
    }

    public String display() {
        return display;
    }

    public Job job() {
        return job;
    }

    public ChatFormatting color() {
        return color;
    }

    /**
     * L'element de ses essences, d'apres les textures de Palamod Recreated :
     * foret, caverne, flamme, eau.
     */
    public String element() {
        return switch (this) {
            case TALIKUS -> "forest";
            case NIMBRIA -> "cavern";
            case VITALYS -> "flame";
            case MANELIOS -> "aquatic";
        };
    }

    public static Dungeon byKey(String key) {
        for (Dungeon dungeon : values()) {
            if (dungeon.key.equals(key)) {
                return dungeon;
            }
        }
        return null;
    }

    public Palette palette() {
        return switch (this) {
            case TALIKUS -> new Palette(Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.SHROOMLIGHT, Blocks.HAY_BLOCK,
                    Blocks.OAK_BUTTON);
            case NIMBRIA -> new Palette(Blocks.DEEPSLATE_TILES, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
                    Blocks.OAK_LOG, Blocks.TUFF_BRICKS, Blocks.OCHRE_FROGLIGHT, Blocks.RAW_IRON_BLOCK,
                    Blocks.POLISHED_BLACKSTONE_BUTTON);
            case VITALYS -> new Palette(Blocks.MOSSY_COBBLESTONE, Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS,
                    Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.VERDANT_FROGLIGHT, Blocks.BONE_BLOCK,
                    Blocks.JUNGLE_BUTTON);
            case MANELIOS -> new Palette(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE,
                    Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.AMETHYST_BLOCK,
                    Blocks.STONE_BUTTON);
        };
    }

    /** Le monstre vanilla de chaque type, dans le theme du donjon. */
    public EntityType<? extends Mob> mob(MobKind kind) {
        return switch (kind) {
            case MELEE, TANK -> switch (this) {
                case TALIKUS -> EntityTypes.ZOMBIE_VILLAGER;
                case NIMBRIA -> EntityTypes.ZOMBIE;
                case VITALYS -> EntityTypes.HUSK;
                case MANELIOS -> EntityTypes.DROWNED;
            };
            case MAGE -> this == NIMBRIA || this == MANELIOS ? EntityTypes.EVOKER : EntityTypes.WITCH;
            case RANGED -> switch (this) {
                case TALIKUS -> EntityTypes.SKELETON;
                case NIMBRIA -> EntityTypes.STRAY;
                case VITALYS -> EntityTypes.BOGGED;
                case MANELIOS -> EntityTypes.DROWNED;
            };
            case FLYING -> EntityTypes.BLAZE;
            case ASSASSIN -> this == NIMBRIA ? EntityTypes.CAVE_SPIDER : EntityTypes.SPIDER;
        };
    }

    /** Le nom affiche d'un type de monstre. */
    public String mobName(MobKind kind) {
        return switch (kind) {
            case MELEE -> "Guerrier";
            case MAGE -> "Mage";
            case RANGED -> "Tireur";
            case FLYING -> "Volant";
            case ASSASSIN -> "Assassin";
            case TANK -> "Colosse";
        } + " de " + display;
    }

    /** L'original de la V11, puis les quatre de la V12, dans l'ordre du wiki. */
    public List<BossSpec> miniBosses() {
        return switch (this) {
            case TALIKUS -> List.of(
                    BossSpec.of("Gardien de Talikus", EntityTypes.ZOMBIE_VILLAGER, 1.6f, SLAM, SUMMON),
                    BossSpec.of("Arbre ancien", EntityTypes.CREAKING, 1.5f, ROOT, SUMMON),
                    BossSpec.of("Exo-armure", EntityTypes.ZOMBIE, 1.5f, CHARGE, SLAM),
                    BossSpec.of("Grand-mere", EntityTypes.WITCH, 1.3f, ROOT, FIRE_RING),
                    BossSpec.of("Sanglier de guerre", EntityTypes.ZOGLIN, 1.4f, CHARGE, SLAM));
            case NIMBRIA -> List.of(
                    BossSpec.duo("Freres mineurs", EntityTypes.ZOMBIE, 1.4f, SLAM, CHARGE),
                    BossSpec.of("Fileuse", EntityTypes.CAVE_SPIDER, 2.5f, ROOT, SUMMON),
                    BossSpec.of("Main de pierre", EntityTypes.VINDICATOR, 1.5f, CHARGE, PULL),
                    BossSpec.of("Lanceur", EntityTypes.SKELETON, 1.8f, VOLLEY, TELEPORT),
                    BossSpec.of("Ancien", EntityTypes.WITHER_SKELETON, 1.8f, SLAM, SUMMON));
            case VITALYS -> List.of(
                    BossSpec.of("Traqueuse", EntityTypes.SPIDER, 2.0f, CHARGE, SUMMON),
                    BossSpec.of("Chimere", EntityTypes.ZOGLIN, 1.7f, CHARGE, FIRE_RING),
                    BossSpec.of("Portail", EntityTypes.ILLUSIONER, 1.5f, TELEPORT, SUMMON),
                    BossSpec.of("Marteau", EntityTypes.VINDICATOR, 1.7f, SLAM, CHARGE),
                    BossSpec.of("Marmite", EntityTypes.WITCH, 1.6f, ROOT, FIRE_RING));
            case MANELIOS -> List.of(
                    BossSpec.duo("Jumeaux noyes", EntityTypes.DROWNED, 1.5f, PULL, CHARGE),
                    BossSpec.of("Davy Jones", EntityTypes.DROWNED, 2.0f, PULL, SUMMON),
                    BossSpec.of("Poisson-globe", EntityTypes.GUARDIAN, 2.0f, ROOT, PULL),
                    BossSpec.of("Hydre", EntityTypes.WITHER, 1.0f, VOLLEY, ROOT),
                    BossSpec.of("Crabe", EntityTypes.SPIDER, 2.2f, CHARGE, SLAM));
        };
    }

    public List<BossSpec> bosses() {
        return switch (this) {
            case TALIKUS -> List.of(
                    BossSpec.of("Seigneur de Talikus", EntityTypes.ZOMBIE_VILLAGER, 2.4f, SLAM, SUMMON, CHARGE),
                    BossSpec.of("Grappin", EntityTypes.PILLAGER, 1.6f, PULL, VOLLEY),
                    BossSpec.of("Machine de guerre", EntityTypes.RAVAGER, 1.2f, CHARGE, SLAM),
                    BossSpec.of("Dragon de Talikus", EntityTypes.PHANTOM, 4.0f, VOLLEY, FIRE_RING),
                    BossSpec.of("Guerrier", EntityTypes.VINDICATOR, 2.0f, CHARGE, SLAM, ROOT));
            case NIMBRIA -> List.of(
                    BossSpec.of("Seigneur de Nimbria", EntityTypes.ZOMBIE, 2.4f, SLAM, SUMMON, CHARGE),
                    BossSpec.of("Serpent", EntityTypes.SILVERFISH, 5.0f, CHARGE, ROOT, SUMMON),
                    BossSpec.of("Grolem", EntityTypes.IRON_GOLEM, 1.4f, SLAM, CHARGE),
                    BossSpec.of("Coeur de paladium", EntityTypes.MAGMA_CUBE, 2.5f, FIRE_RING, SUMMON),
                    BossSpec.of("Dragon de Nimbria", EntityTypes.PHANTOM, 4.0f, VOLLEY, FIRE_RING));
            case VITALYS -> List.of(
                    BossSpec.of("Seigneur de Vitalys", EntityTypes.HUSK, 2.4f, SLAM, SUMMON, CHARGE),
                    BossSpec.of("Phenix", EntityTypes.BLAZE, 3.0f, VOLLEY, FIRE_RING),
                    BossSpec.of("Cerbere", EntityTypes.ZOGLIN, 2.0f, CHARGE, SUMMON, FIRE_RING),
                    BossSpec.of("Mage de Vitalys", EntityTypes.EVOKER, 1.8f, TELEPORT, SUMMON, ROOT),
                    BossSpec.of("Dragon de Vitalys", EntityTypes.PHANTOM, 4.0f, VOLLEY, FIRE_RING));
            case MANELIOS -> List.of(
                    BossSpec.of("Seigneur de Manelios", EntityTypes.DROWNED, 2.4f, SLAM, SUMMON, PULL),
                    BossSpec.of("Leviathan", EntityTypes.GUARDIAN, 3.5f, PULL, ROOT),
                    BossSpec.of("Kraken", EntityTypes.ELDER_GUARDIAN, 1.5f, PULL, SUMMON, ROOT),
                    BossSpec.of("Mage de Manelios", EntityTypes.EVOKER, 1.8f, TELEPORT, SUMMON, ROOT),
                    BossSpec.of("Dragon de Manelios", EntityTypes.PHANTOM, 4.0f, VOLLEY, FIRE_RING));
        };
    }

    /** Le boss du palier, ou {@code null} hors des paliers en 5 et en 0. */
    public BossSpec bossFor(int palier) {
        if (palier % 10 == 0) {
            return bosses().get((palier / 10 - 1) % 5);
        }
        if (palier % 5 == 0) {
            return miniBosses().get((palier / 10) % 5);
        }
        return null;
    }
}
