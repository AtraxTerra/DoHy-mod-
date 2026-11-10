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
 */
public record ClaimOwner(Optional<UUID> owner, String faction, boolean monsters) {

    public static final ClaimOwner NONE = new ClaimOwner(Optional.empty(), "", true);

    public static final MapCodec<ClaimOwner> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ClaimOwner::owner),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("faction", "").forGetter(ClaimOwner::faction),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("monsters", true).forGetter(ClaimOwner::monsters)
    ).apply(instance, ClaimOwner::new));

    public boolean claimed() {
        return owner.isPresent();
    }

    public boolean ownedBy(UUID player) {
        return owner.map(player::equals).orElse(false);
    }
}
