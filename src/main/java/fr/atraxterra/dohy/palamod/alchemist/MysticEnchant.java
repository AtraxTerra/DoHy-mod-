package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.PalaMod;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Les enchantements de la table de l'Alchimiste ({@code EnchantMod.register}).
 * On ne les trouve ni a la table vanilla, ni en livre ; 30 niveaux
 * d'experience par niveau d'enchantement.
 *
 * <ul>
 *   <li><b>anti-chute</b> (bottes, 1 niveau) : on ne tombe plus d'un bord de
 *       bloc, comme accroupi ;</li>
 *   <li><b>grass breaker</b> (epees, 3 niveaux, Fermier 20) : une haute herbe
 *       cassee a l'epee relance son butin une fois de plus, avec Fortune I, et
 *       chaque pile gagne de 0 a {@code niveau} graines. L'original le limitait
 *       aux epees rapides de PalaMod, non portees : ici, toute epee.</li>
 *   <li><b>unclaim helmet</b> (casques, 1 niveau, chapitre 22) : fondu a
 *       l'enclume avec un Unclaim Finder, le casque porte en devient un, de la
 *       couleur du finder ;</li>
 *   <li><b>fake water protection</b> (casques, 1 niveau, chapitre 22) : le
 *       casque encaisse la Fake Water a la place du joueur, 66 points d'usure par
 *       seconde ({@code BlockSulfuricWater}). L'arbre de faction le debloque
 *       (Pillage, palier 6).</li>
 *   <li><b>experience bottler</b> (tout objet a usure, 1 niveau, Mineur 60) :
 *       tenu ou porte, l'XP de metier gagnee remplit des bouteilles d'XP de
 *       metier ({@code PMiner}, {@code AJobsEventHandler}) ;</li>
 *   <li><b>voleur d'XP</b> (epees, 4 niveaux, Chasseur 55) : en PvP, 2/4/7/10 %
 *       de chances par coup de voler 40/50/70/80 points d'XP ;</li>
 *   <li><b>overwhelmed</b> (plastrons, 3 niveaux, Chasseur 95) : une amulette de
 *       plus par niveau sur le plastron ;</li>
 *   <li><b>recuperation</b> (epees) et <b>recuperation de minage</b> (outils),
 *       1 niveau : le butin d'un monstre tue, ou d'un bloc casse, va dans le
 *       sac ;</li>
 *   <li><b>experience</b> (epees, 3 niveaux) : +1/3/5 % sur l'XP ramassee.
 *       L'original ne venait que des lucky blocks de septembre, absents ici :
 *       il passe par cette table.</li>
 * </ul>
 * Unclaim helmet et fake water protection alimentent l'arbre de faction : pose
 * du premier, secondes de degats evitees par le second.
 */
public enum MysticEnchant {
    NO_FALL("no_fall", null, 0, null, null),
    GRASS_BREAKER("grass_breaker", Job.FARMER, 20, null, null),
    UNCLAIM_HELMET("unclaim_helmet", null, 0, null, "enchant:unclaim_helmet"),
    FAKE_WATER_PROTECTION("fake_water_protection", null, 0, "enchant:fake_water_protection", null),
    XP_BOTTLER("xp_bottler", Job.MINER, 60, null, null),
    XP_STEALER("xp_stealer", Job.HUNTER, 55, null, null),
    OVERWHELMED("overwhelmed", Job.HUNTER, 95, null, null),
    RECUPERATION("recuperation", null, 0, null, null),
    RECUPERATION_DIG("recuperation_dig", null, 0, null, null),
    EXPERIENCE("experience", null, 0, null, null);

    /** {@code experienceLevel -= 30} par application. */
    public static final int COST = 30;

    private final String id;
    private final ResourceKey<Enchantment> key;
    private final Job job;
    private final int jobLevel;
    private final String unlock;
    private final String counter;

    MysticEnchant(String id, Job job, int jobLevel, String unlock, String counter) {
        this.id = id;
        this.key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, id));
        this.job = job;
        this.jobLevel = jobLevel;
        this.unlock = unlock;
        this.counter = counter;
    }

    /** La cle de l'arbre de faction qui le debloque, ou {@code null}. */
    public String unlock() {
        return unlock;
    }

    /** Le compteur de l'arbre de faction qu'une pose alimente, ou {@code null}. */
    public String counter() {
        return counter;
    }

    public String id() {
        return id;
    }

    public ResourceKey<Enchantment> key() {
        return key;
    }

    public Job job() {
        return job;
    }

    public int jobLevel() {
        return jobLevel;
    }

    public Optional<Holder.Reference<Enchantment>> holder(Player player) {
        return holder(player.level());
    }

    public Optional<Holder.Reference<Enchantment>> holder(Level level) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
    }

    /** Le niveau de cet enchantement sur l'objet, 0 s'il ne l'a pas. */
    public int levelOn(Player player, ItemStack stack) {
        return holder(player).map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    /**
     * Le niveau lu dans les enchantements de l'objet, sans passer par le
     * registre : pour une bulle d'aide ou une enclume, ou l'on n'a pas toujours
     * de monde sous la main.
     */
    public int levelOn(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
