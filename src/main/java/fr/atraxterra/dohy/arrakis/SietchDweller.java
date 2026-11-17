package fr.atraxterra.dohy.arrakis;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Un habitant d'un sietch. Ceux qui echangent se tiennent a leur poste (le porteur d'eau a la
 * citerne, l'artisan, l'ancienne et l'equipeur dans leurs alcoves) ; les autres vont et viennent
 * dans la salle et parlent a qui les aborde.
 *
 * <p>On ne les blesse pas (sauf en creatif), on ne les pousse pas, ils ne s'en vont jamais :
 * un sietch garde les siens. S'il en manque un, il revient quand quelqu'un approche
 * ({@link Sietches#upkeep}).
 */
public class SietchDweller extends PathfinderMob {

    /** Ce que fait l'habitant au sietch. */
    public enum Role {
        WATER("Le porteur d'eau"),
        ARTISAN("L'artisan"),
        OUTFITTER("L'equipeur"),
        ELDER("L'ancienne"),
        DWELLER("Un habitant du sietch");

        private final String title;

        Role(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        static Role of(int ordinal) {
            Role[] roles = values();
            return roles[Math.floorMod(ordinal, roles.length)];
        }
    }

    /** Les visages des habitants sans poste ; ceux qui echangent ont chacun le leur. */
    public static final int VARIANTS = 4;

    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(SietchDweller.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(SietchDweller.class, EntityDataSerializers.INT);

    private static final String[] NAMES = {"Yasir", "Nadra", "Tamir", "Hesna", "Idrak", "Samra", "Kadim", "Leyla",
            "Orun", "Zafira", "Bahir", "Rawa"};

    /** Ce que disent ceux qu'on aborde dans la salle. */
    private static final String[] LINES = {
            "L'eau d'un homme appartient a sa tribu. Ici, rien ne se perd.",
            "Le ver vient au rythme du marteleur. Ne marche jamais en cadence sur le sable.",
            "Les pieges a vent ne dorment que le jour. La nuit, ils boivent l'air pour nous.",
            "Un distille bien ajuste ne perd qu'un de a coudre d'eau par jour.",
            "L'ancienne sait ou sont les autres sietchs. Offre de l'eau, elle parlera.",
            "L'epice a l'odeur de la cannelle. On la boit en cafe, les soirs de conseil.",
            "Marche a l'ombre des rochers. Le soleil d'Arrakis boit plus vite que toi.",
            "La dent d'un ver vaut une vie. L'artisan en taille des krys.",
            "Nos portes ne s'ouvrent qu'aux amis. Tu es entre : sois-en digne.",
            "Quand le sable chante, cours vers la roche. Le ver ne monte pas sur la pierre.",
    };

    private int sietch = -1;
    private long post;

    public SietchDweller(EntityType<? extends SietchDweller> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.5d)
                .add(Attributes.FOLLOW_RANGE, 16.0d);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, Role.DWELLER.ordinal());
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6.0f, 1.0f));
        goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.55d));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.45d) {
            @Override
            public boolean canUse() {
                return role() == Role.DWELLER && super.canUse();
            }
        });
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** L'installe a son poste : son sietch, son role, sa place, son visage. */
    void settle(int sietch, Role role, BlockPos post, int variant) {
        this.sietch = sietch;
        this.post = post.asLong();
        entityData.set(DATA_ROLE, role.ordinal());
        entityData.set(DATA_VARIANT, variant);
        setHomeTo(post, role == Role.DWELLER ? 7 : 1);
        String name = NAMES[Math.floorMod(sietch * 7 + role.ordinal() * 3 + variant, NAMES.length)];
        setCustomName(Component.literal(role == Role.DWELLER ? name : name + ", " + role.title().toLowerCase(
                java.util.Locale.ROOT)).withStyle(ChatFormatting.GOLD));
        setCustomNameVisible(false);
    }

    public Role role() {
        return Role.of(entityData.get(DATA_ROLE));
    }

    public int variant() {
        return Math.floorMod(entityData.get(DATA_VARIANT), VARIANTS);
    }

    public int sietch() {
        return sietch;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            if (role() == Role.DWELLER) {
                String line = LINES[server.getRandom().nextInt(LINES.length)];
                server.sendSystemMessage(Component.literal("").append(getDisplayName())
                        .append(Component.literal(" : « " + line + " »").withStyle(ChatFormatting.YELLOW)));
                lookAt(player, 30.0f, 30.0f);
            } else {
                SietchMenu.open(server, this);
            }
        }
        return InteractionResult.SUCCESS;
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

    @Override
    public void tick() {
        super.tick();
        // revenu a son poste s'il en a ete chasse (un piston, une chute dans la citerne)
        if (!level().isClientSide() && tickCount % 200 == 0 && post != 0L && role() != Role.DWELLER) {
            BlockPos home = BlockPos.of(post);
            if (distanceToSqr(home.getX() + 0.5d, home.getY(), home.getZ() + 0.5d) > 9.0d) {
                snapTo(home.getX() + 0.5d, home.getY(), home.getZ() + 0.5d, getYRot(), 0.0f);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("sietch", sietch);
        output.putInt("role", entityData.get(DATA_ROLE));
        output.putInt("variant", entityData.get(DATA_VARIANT));
        output.putLong("post", post);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        sietch = input.getIntOr("sietch", -1);
        entityData.set(DATA_ROLE, input.getIntOr("role", Role.DWELLER.ordinal()));
        entityData.set(DATA_VARIANT, input.getIntOr("variant", 0));
        post = input.getLongOr("post", 0L);
        if (post != 0L) {
            setHomeTo(BlockPos.of(post), role() == Role.DWELLER ? 7 : 1);
        }
    }
}
