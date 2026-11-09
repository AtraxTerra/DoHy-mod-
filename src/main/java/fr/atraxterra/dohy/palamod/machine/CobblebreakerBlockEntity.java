package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaItems;
import fr.atraxterra.dohy.palamod.miner.VoidstoneMinageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Supplier;

/**
 * Le cobblebreaker : de la pierre en particules de minerai.
 *
 * <p>Tous les chiffres viennent de {@code TileCobbleBreaker.func_145845_h} et de
 * {@code CobbleBreakerUpgrade}, lus au bytecode. Rien n'est calibre ici.
 *
 * <p><b>Le cycle.</b> Tant que l'entree contient de la pierre et qu'au moins un
 * bac de sortie n'est pas plein, un compteur monte d'un par tick. Quand il
 * atteint la cadence du palier, une pierre est consommee, un tirage decide du
 * lot, et le compteur repart a zero.
 *
 * <table>
 *   <tr><th>Palier</th><th>Cadence</th><th>Duree</th><th>Multiplicateur</th></tr>
 *   <tr><td>Defaut</td><td>400 ticks</td><td>20,0 s</td><td>x1</td></tr>
 *   <tr><td>Amethyste</td><td>266 ticks</td><td>13,3 s</td><td>x1,50</td></tr>
 *   <tr><td>Titane</td><td>160 ticks</td><td>8,0 s</td><td>x2,50</td></tr>
 *   <tr><td>Paladium</td><td>100 ticks</td><td>5,0 s</td><td>x4,00</td></tr>
 * </table>
 *
 * <p><b>Le tirage.</b> Un nombre entre 0 et 250 ; chaque particule occupe une
 * tranche, et les 220 restantes ne donnent rien. Le wiki annonce six
 * particules sans jamais donner ces nombres :
 *
 * <table>
 *   <tr><th>Tranche</th><th>Lot</th><th>Chance</th></tr>
 *   <tr><td>[0 ; 0,111[</td><td>paladium</td><td>0,044 %</td></tr>
 *   <tr><td>[0,111 ; 3[</td><td>titane</td><td>1,16 %</td></tr>
 *   <tr><td>[3 ; 7[</td><td>amethyste</td><td>1,60 %</td></tr>
 *   <tr><td>[7 ; 12[</td><td>diamant</td><td>2,00 %</td></tr>
 *   <tr><td>[12 ; 20[</td><td>or</td><td>3,20 %</td></tr>
 *   <tr><td>[20 ; 30[</td><td>fer</td><td>4,00 %</td></tr>
 *   <tr><td>[30 ; 250[</td><td>rien</td><td>88,0 %</td></tr>
 * </table>
 *
 * <p>Une pierre sur huit rend donc quelque chose, et il faut en moyenne
 * <b>20 250 pierres</b> pour neuf particules de paladium, soit un lingot. A la
 * cadence du palier paladium cela represente une trentaine d'heures de machine :
 * c'est voulu, c'est ce qui fait du cobblebreaker une rente de fond et non une
 * source de metal.
 */
public class CobblebreakerBlockEntity extends MachineBlockEntity {

    /** Emplacement de la pierre a casser. */
    public static final int SLOT_INPUT = 0;

    /** Les six bacs de sortie, du plus courant au plus rare. */
    public static final int SLOT_FIRST_OUTPUT = 1;
    public static final int SLOT_COUNT = 7;

    /** Bornes du tirage, dans l'ordre ou le PalaMod les teste. */
    private static final double ROLL_RANGE = 250.0;

    /**
     * Les lots, du plus rare au plus courant.
     *
     * <p>L'ordre compte : on cherche la premiere tranche dont la borne haute
     * depasse le tirage, donc il faut les parcourir en montant.
     */
    private static final Prize[] PRIZES = {
            new Prize(1.0 / 9.0, 6, () -> PalaItems.PALADIUM_PARTICLE.get()),
            new Prize(3.0, 5, () -> PalaItems.TITANE_PARTICLE.get()),
            new Prize(7.0, 4, () -> PalaItems.AMETHYST_PARTICLE.get()),
            new Prize(12.0, 3, () -> PalaItems.DIAMOND_PARTICLE.get()),
            new Prize(20.0, 2, () -> PalaItems.GOLD_PARTICLE.get()),
            new Prize(30.0, 1, () -> PalaItems.IRON_PARTICLE.get()),
    };

    private int work;
    private CobblebreakerTier tier = CobblebreakerTier.DEFAULT;

    public CobblebreakerBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.COBBLEBREAKER.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.cobblebreaker"));
    }

    public CobblebreakerTier tier() {
        return tier;
    }

    /**
     * Applique une amelioration.
     *
     * <p>Rend {@code false} si elle n'apporte rien : on ne consomme pas
     * l'objet du joueur pour un palier qu'il a deja, ni pour redescendre.
     */
    public boolean upgradeTo(CobblebreakerTier candidate) {
        if (candidate.ordinal() <= tier.ordinal()) {
            return false;
        }
        tier = candidate;
        setChanged();
        return true;
    }

    // -- Interface -----------------------------------------------------------

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaMenus.COBBLEBREAKER.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.COBBLEBREAKER;
    }

    @Override
    public int work() {
        return work;
    }

    /**
     * Zero quand la machine est a l'arret, sinon la cadence du palier.
     *
     * <p>C'est ce qui rend le palier visible : la meme barre se remplit quatre
     * fois plus vite sur un cobblebreaker en paladium. Jusqu'ici l'amelioration
     * ne se mesurait qu'au chronometre.
     */
    @Override
    public int cycleDuration() {
        return canWork() ? tier.workTicks() : 0;
    }

    /**
     * Seule la cobblestone entre — ou une Voidstone minage, qui en tient lieu —
     * et seulement par la case d'entree.
     *
     * <p>Sans ce filtre, le shift-clic deposerait n'importe quoi dans l'entree
     * et la machine resterait bloquee sans rien dire — {@code canWork} exige de
     * la pierre, mais ne la reclame pas.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT
                && (stack.is(Items.COBBLESTONE) || stack.getItem() instanceof VoidstoneMinageItem);
    }

    /** Les particules seulement, jamais la cobblestone ni la Voidstone de l'entree. */
    @Override
    protected boolean hopperMayTake(int slot) {
        return slot >= SLOT_FIRST_OUTPUT;
    }

    @Override
    public void serverTick() {
        if (level == null || !canWork()) {
            return;
        }
        work++;
        if (work < tier.workTicks()) {
            setChanged();
            return;
        }
        work = 0;
        // Une Voidstone minage sert d'entree : une pierre de sa reserve par
        // cycle, au lieu d'une cobblestone (TileCobbleBreaker, 871-925).
        ItemStack input = getItem(SLOT_INPUT);
        if (input.getItem() instanceof VoidstoneMinageItem) {
            VoidstoneMinageItem.setStone(input, VoidstoneMinageItem.stone(input) - 1);
        } else {
            removeItem(SLOT_INPUT, 1);
        }
        award(level.getRandom().nextDouble() * ROLL_RANGE);
        setChanged();
    }

    /**
     * La machine tourne si elle a de la pierre et de la place.
     *
     * <p>« De la place » veut dire : au moins un bac non plein. Le PalaMod
     * s'arrete des que les six sont pleins, meme si le lot tire aurait pu aller
     * ailleurs — reproduit tel quel, sinon une machine laissee seule finirait
     * par ne plus produire que les metaux rares.
     */
    private boolean canWork() {
        ItemStack input = getItem(SLOT_INPUT);
        boolean fed = input.is(Items.COBBLESTONE)
                || (input.getItem() instanceof VoidstoneMinageItem && VoidstoneMinageItem.stone(input) > 0);
        if (!fed) {
            return false;
        }
        for (int slot = SLOT_FIRST_OUTPUT; slot < SLOT_COUNT; slot++) {
            if (getItem(slot).getCount() < getItem(slot).getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** Depose le lot correspondant au tirage, s'il y en a un et s'il tient. */
    private void award(double roll) {
        for (Prize prize : PRIZES) {
            if (roll >= prize.upperBound()) {
                continue;
            }
            ItemStack current = getItem(prize.slot());
            if (current.isEmpty()) {
                setItem(prize.slot(), new ItemStack(prize.item().get()));
            } else if (current.getCount() < current.getMaxStackSize()) {
                current.grow(1);
                setChanged();
            }
            // Bac plein : le lot est perdu, comme dans l'original. La pierre a
            // deja ete consommee — c'est ce qui punit une machine laissee sans
            // surveillance, et c'est deliberement conserve.
            return;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work = input.getIntOr("Work", 0);
        tier = CobblebreakerTier.byKey(input.getStringOr("Tier", ""));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Work", work);
        output.putString("Tier", tier.key());
    }

    /** Une tranche du tirage : sa borne haute, son bac, et ce qu'elle depose. */
    private record Prize(double upperBound, int slot, Supplier<Item> item) {
    }
}
