package fr.atraxterra.dohy.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/**
 * Ce que retient un bloc de casino : a qui il est (le joueur qui l'a pose), et pour la machine
 * aleatoire, sa chance de laisser passer le signal et ce qu'elle a deja tire. Le de y garde ses
 * derniers tours de lancer.
 */
public class CasinoBlockEntity extends BlockEntity {

    /** La chance par defaut d'une machine aleatoire neuve. */
    public static final int DEFAULT_CHANCE = 50;

    private UUID owner;
    private String ownerName = "";
    private int chance = DEFAULT_CHANCE;
    private long rolls;
    private long passes;
    /** Le de : les tours de lancer qui restent (pas sauvegarde : un lancer coupe s'arrete la). */
    int tumble;

    public CasinoBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyCasino.TYPE.get(), pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean ownedBy(ServerPlayer player) {
        return owner != null && owner.equals(player.getUUID());
    }

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().name();
        setChanged();
    }

    /** La chance de passage de la machine aleatoire, de 1 a 99 %. */
    public int chance() {
        return chance;
    }

    void setChance(int value) {
        chance = Math.max(1, Math.min(99, value));
        setChanged();
    }

    public long rolls() {
        return rolls;
    }

    public long passes() {
        return passes;
    }

    /** Un tirage de la machine : vrai si le signal passe. */
    boolean roll(net.minecraft.util.RandomSource random) {
        boolean pass = random.nextInt(100) < chance;
        rolls++;
        if (pass) {
            passes++;
        }
        setChanged();
        return pass;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String raw = input.getStringOr("owner", "");
        try {
            owner = raw.isEmpty() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            owner = null;
        }
        ownerName = input.getStringOr("owner_name", "");
        chance = Math.max(1, Math.min(99, input.getIntOr("chance", DEFAULT_CHANCE)));
        rolls = Math.max(0L, input.getLongOr("rolls", 0L));
        passes = Math.max(0L, input.getLongOr("passes", 0L));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("owner", owner == null ? "" : owner.toString());
        output.putString("owner_name", ownerName);
        output.putInt("chance", chance);
        output.putLong("rolls", rolls);
        output.putLong("passes", passes);
    }
}
