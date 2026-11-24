package fr.atraxterra.dohy.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Un PNJ du spawn : le guichetier d'un service. Il tient la place des anciennes bornes —
 * un clic droit ouvre le meme ecran — et il a un visage : le marchand en tablier, le
 * banquier en costume, le receleur sous sa capuche (des peaux originales,
 * {@code tools/spawn_npc_assets.py}).
 *
 * <p>Il ne bouge pas de son poste, ne se blesse pas, ne se pousse pas ; il suit des yeux
 * qui s'approche et, seul, regarde devant lui. Il n'est jamais ecrit sur disque : c'est son
 * poste ({@link NpcPostBlockEntity}) qui le fait venir quand le coin du spawn se charge, et
 * le reprend quand il se decharge. Un spawn reconstruit ne laisse donc aucun PNJ en double.
 */
public class SpawnNpc extends PathfinderMob {

    /** Les guichets : le nom affiche, la peau, et le service qu'ils ouvrent ({@code null} : les kits). */
    public enum Role {
        SHOP("shop", "Marchand", SpawnTerminalBlock.Kind.SHOP),
        BOUTIQUE("boutique", "Boutiquiere", SpawnTerminalBlock.Kind.BOUTIQUE),
        MARKET("market", "Commissaire-priseur", SpawnTerminalBlock.Kind.MARKET),
        BANK("bank", "Banquier", SpawnTerminalBlock.Kind.BANK),
        CONTRACTS("contracts", "Agent des contrats", SpawnTerminalBlock.Kind.CONTRACTS),
        MUSEUM("museum", "Conservatrice", SpawnTerminalBlock.Kind.MUSEUM),
        RETURN("return", "Passeur", SpawnTerminalBlock.Kind.RETURN),
        DAILY("daily", "Hotesse", SpawnTerminalBlock.Kind.DAILY),
        PORTALS("portals", "Gardien des donjons", SpawnTerminalBlock.Kind.PORTALS),
        MINE("mine", "Contremaitre", SpawnTerminalBlock.Kind.MINE),
        BLACK_MARKET("black_market", "Receleur", SpawnTerminalBlock.Kind.BLACK_MARKET),
        JOB_ALTAR("job_altar", "Pretresse", SpawnTerminalBlock.Kind.JOB_ALTAR),
        SHUTTLE("shuttle", "Pilote", SpawnTerminalBlock.Kind.SHUTTLE),
        KITS("kits", "Intendant", null),
        /** Le Pere Noel du parvis, en decembre : le calendrier de l'Avent ({@link SpawnChristmas}). */
        ADVENT("advent", "Pere Noel", null);

        /** La couleur des kits : le rouge de la marque. */
        private static final int KITS_COLOR = 0xEF3826;
        /** Le rouge du Pere Noel. */
        private static final int ADVENT_COLOR = 0xD42A2A;

        private final String id;
        private final String title;
        private final SpawnTerminalBlock.Kind service;

        Role(String id, String title, SpawnTerminalBlock.Kind service) {
            this.id = id;
            this.title = title;
            this.service = service;
        }

        /** Le nom de sa peau ({@code textures/entity/spawn_npc/<id>.png}) et de sa sauvegarde. */
        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        /** Le service de borne qu'il ouvre ; {@code null} pour l'intendant des kits et le Pere Noel. */
        public SpawnTerminalBlock.Kind service() {
            return service;
        }

        /** Le mot de son enseigne : celui de l'ancienne borne. */
        public String label() {
            return service != null ? service.label() : this == ADVENT ? "CALENDRIER" : "KITS";
        }

        public int color() {
            return service != null ? service.color() : this == ADVENT ? ADVENT_COLOR : KITS_COLOR;
        }

        public static Role of(int ordinal) {
            Role[] roles = values();
            return roles[Math.floorMod(ordinal, roles.length)];
        }

        public static Role byId(String id) {
            for (Role role : values()) {
                if (role.id.equals(id)) {
                    return role;
                }
            }
            return SHOP;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(SpawnNpc.class, EntityDataSerializers.INT);
    /** Hors de ce rayon, il revient a son poste (rien ne devrait l'en faire sortir). */
    private static final double DRIFT = 0.5d;

    private BlockPos post = BlockPos.ZERO;
    private float facing;

    public SpawnNpc(EntityType<? extends SpawnNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.0d)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0d)
                .add(Attributes.FOLLOW_RANGE, 12.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, Role.SHOP.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 8.0f, 1.0f));
        goalSelector.addGoal(1, new FacePostGoal());
    }

    /** L'installe a son poste : son role, sa place, la direction ou il regarde. */
    void settle(Role role, BlockPos post, float facing) {
        this.post = post.immutable();
        this.facing = facing;
        entityData.set(DATA_ROLE, role.ordinal());
        setCustomName(Component.literal(role.title()).withColor(role.color()));
        setCustomNameVisible(true);
        snapTo(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d, facing, 0.0f);
        setYHeadRot(facing);
        setYBodyRot(facing);
    }

    public Role role() {
        return Role.of(entityData.get(DATA_ROLE));
    }

    /** Le bloc ou il se tient ; {@link BlockPos#ZERO} tant qu'il n'est pas installe. */
    public BlockPos post() {
        return post;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            Role role = role();
            if (role.service() != null) {
                SpawnTerminalBlock.open(server, role.service());
            } else if (role == Role.ADVENT) {
                fr.atraxterra.dohy.noel.AdventMenu.INSTANCE.open(server);
            } else {
                fr.atraxterra.dohy.kits.PlayerKitsMenu.INSTANCE.open(server);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        // revenu a son poste s'il en a ete deplace (un piston, une commande)
        if (!level().isClientSide() && tickCount % 40 == 0 && !post.equals(BlockPos.ZERO)
                && distanceToSqr(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d) > DRIFT * DRIFT) {
            snapTo(post.getX() + 0.5d, post.getY(), post.getZ() + 0.5d, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Jamais sur disque : son poste le refait (voir la classe). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    /** Sans personne a regarder, il fait face a son guichet. */
    private final class FacePostGoal extends Goal {

        FacePostGoal() {
            setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            double radians = Math.toRadians(facing);
            getLookControl().setLookAt(getX() - Math.sin(radians) * 4.0d, getEyeY(),
                    getZ() + Math.cos(radians) * 4.0d);
        }
    }
}
