package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Le decor anime des donjons : le coffre et les jarres de PalaRPG.
 *
 * <p>PalaRPG en fait des blocs a rendu special. Ici ce sont des objets poses,
 * portes par le rendu des monstres ({@link RpgSkin}) : une entite immobile,
 * muette et invulnerable, dont on ne voit que le modele. L'hote est un cochon
 * sans IA — le seul role qu'il joue est d'offrir une boite a viser, mise a la
 * taille de l'objet par son attribut d'echelle. Il n'est pas un {@code Enemy} :
 * il ne compte pas pour finir la salle.
 */
final class DungeonProps {

    static final String TAG = "palamod.dungeon.prop";
    static final String CHEST_TAG = "palamod.dungeon.chest";
    static final String JAR_TAG = "palamod.dungeon.jar";

    /** La hauteur de la boite du cochon, a l'echelle 1. */
    private static final double HOST_SIZE = 0.9d;

    private DungeonProps() {
    }

    /**
     * Pose un objet. {@code size} : le cote de sa boite a viser, en blocs.
     * {@code yaw} : 180 regarde le nord, d'ou viennent les joueurs.
     */
    static Mob spawn(ServerLevel level, double x, double y, double z, float yaw, RpgSkin skin, double size,
                     String kind) {
        Mob host = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (host == null) {
            return null;
        }
        host.snapTo(x, y, z, yaw, 0.0f);
        host.setYBodyRot(yaw);
        host.setYHeadRot(yaw);
        host.setNoAi(true);
        host.setSilent(true);
        host.setInvulnerable(true);
        host.setPersistenceRequired();
        AttributeInstance scale = host.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(size / HOST_SIZE);
        }
        host.addTag(DungeonRun.TAG);
        host.addTag(TAG);
        host.addTag(kind);
        host.setData(PalaDungeon.SKIN.get(), skin);
        level.addFreshEntity(host);
        return host;
    }

    static boolean is(Entity entity, String kind) {
        return entity.entityTags().contains(kind);
    }

    /** Change l'animation d'un objet : les clients qui le voient la relancent. */
    static void pose(Entity prop, String pose) {
        RpgSkin skin = RpgSkins.skin(prop);
        if (!skin.isNone()) {
            prop.setData(PalaDungeon.SKIN.get(), skin.withPose(pose));
        }
    }

    /** Un son du decor ({@code assets/palarpg/sounds.json}), a l'endroit de l'objet. */
    static void sound(ServerLevel level, Entity at, String name, float volume) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), Holder.direct(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("palarpg", name))), SoundSource.BLOCKS, volume, 1.0f);
    }
}
