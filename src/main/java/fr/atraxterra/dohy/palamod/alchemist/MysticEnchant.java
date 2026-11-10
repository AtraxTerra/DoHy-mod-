package fr.atraxterra.dohy.palamod.alchemist;

import fr.atraxterra.dohy.job.Job;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
 *   <li><b>grass breaker</b> (epees, 3 niveaux, Fermier 20) : les hautes herbes
 *       cassees a l'epee lachent jusqu'a {@code niveau} graines de plus par
 *       graine. L'original le limitait aux epees rapides de PalaMod, non
 *       portees : ici, toute epee.</li>
 *   <li><b>unclaim helmet</b> (casques, 1 niveau, chapitre 22) : le casque porte
 *       fait office d'Unclaim Finder vert ;</li>
 *   <li><b>fake water protection</b> (armures, 1 niveau, chapitre 22) : la Fake
 *       Water ne ronge plus. L'arbre de faction le debloque (Pillage, palier 6).</li>
 * </ul>
 * Les deux derniers alimentent l'arbre de faction : pose du premier, secondes
 * de degats evitees par le second.
 */
public enum MysticEnchant {
    NO_FALL("no_fall", null, 0, null, null),
    GRASS_BREAKER("grass_breaker", Job.FARMER, 20, null, null),
    UNCLAIM_HELMET("unclaim_helmet", null, 0, null, "enchant:unclaim_helmet"),
    FAKE_WATER_PROTECTION("fake_water_protection", null, 0, "enchant:fake_water_protection", null);

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
}
