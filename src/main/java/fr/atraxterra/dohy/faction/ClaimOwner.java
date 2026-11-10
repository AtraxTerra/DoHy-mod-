package fr.atraxterra.dohy.faction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Le proprietaire d'un chunk revendique, note sur le chunk lui-meme.
 *
 * <p>La protection (explosions, monstres, autres joueurs) interroge le chunk ou
 * l'evenement se produit : elle repond sans connaitre le joueur, connecte ou non.
 *
 * @param monsters les monstres apparaissent-ils naturellement dans ce chunk
 * @param enemy    claim d'une base ennemie generee (lot 22g) : on n'y casse, n'y
 *                 pose et n'y ouvre rien a la main, mais les explosions et les
 *                 withers y passent — c'est une base a piller
 */
public record ClaimOwner(Optional<UUID> owner, String faction, boolean monsters, boolean enemy) {

    public static final ClaimOwner NONE = new ClaimOwner(Optional.empty(), "", true, false);

    public static final MapCodec<ClaimOwner> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ClaimOwner::owner),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("faction", "").forGetter(ClaimOwner::faction),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("monsters", true).forGetter(ClaimOwner::monsters),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("enemy", false).forGetter(ClaimOwner::enemy)
    ).apply(instance, ClaimOwner::new));

    /** Le claim d'un joueur : protege contre tout ce qui n'est pas lui. */
    public static ClaimOwner player(UUID owner, String faction, boolean monsters) {
        return new ClaimOwner(Optional.of(owner), faction, monsters, false);
    }

    public boolean claimed() {
        return owner.isPresent();
    }

    /** Un claim ennemi : les explosions et les withers y cassent ce qu'ils touchent. */
    public boolean raidable() {
        return claimed() && enemy;
    }

    public boolean ownedBy(UUID player) {
        return owner.map(player::equals).orElse(false);
    }
}
