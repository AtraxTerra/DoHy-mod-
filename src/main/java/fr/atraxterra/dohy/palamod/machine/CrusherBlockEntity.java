package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Supplier;

/**
 * Le crusher : des fruits en lingots.
 *
 * <p>C'est la seconde voie d'acces aux metaux, celle de l'agriculture — d'ou
 * son verrou sur le <b>Fermier</b> et non sur le Mineur, ce qui semblait
 * incoherent jusqu'a ce que les cultures revelent leur lot rare.
 *
 * <p>Tout vient de {@code TileCrusher} : la cadence de 100 ticks par fruit, les
 * quatre recettes de {@code EnumCrusherRecipes} et leurs rendements de
 * {@code EnumCrusherResult}. Le wiki est exact sur ce point, le code le
 * confirme.
 *
 * <table>
 *   <tr><th>Fruit</th><th>Charbon</th><th>Fruits par lot</th><th>Rendement</th></tr>
 *   <tr><td>Aubergine</td><td>or</td><td>32</td><td>5 lingots d'amethyste</td></tr>
 *   <tr><td>Cerfeuil</td><td>amethyste</td><td>32</td><td>15 lingots de titane</td></tr>
 *   <tr><td>Kiwano</td><td>titane</td><td>64</td><td>15 lingots de paladium</td></tr>
 *   <tr><td>Orangeblue</td><td>paladium</td><td>128</td><td>1 pepite d'endium</td></tr>
 * </table>
 *
 * <p><b>Une adaptation assumee : la sortie est automatique.</b> L'original
 * demandait au joueur de cliquer un bouton de son interface pour vider un bac
 * plein. Ce mod n'a pas d'interface dessinee — voir {@link MachineBlockEntity} —
 * donc le lot tombe dans l'emplacement de sortie des que le compte est atteint.
 * Rien n'est gagne ni perdu : c'est le meme nombre de fruits pour le meme
 * nombre de lingots, moins un clic.
 *
 * <p>Les bacs sont <b>independants</b> : alterner les fruits ne remet aucun
 * compteur a zero, comme dans l'original ou chaque minerai avait le sien.
 */
public class CrusherBlockEntity extends MachineBlockEntity {

    public static final int SLOT_FRUIT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Ticks par fruit traite. Releve dans le tick de TileCrusher. */
    private static final int WORK_TICKS = 100;

    /** Les quatre recettes, dans l'ordre de progression du joueur. */
    private static final Recipe[] RECIPES = {
            new Recipe(() -> PalaItems.EGGPLANT.get(), () -> PalaItems.GOLD_MIXED_COAL.get(),
                    32, () -> PalaItems.AMETHYST_INGOT.get(), 5),
            new Recipe(() -> PalaItems.CHERVIL.get(), () -> PalaItems.AMETHYST_MIXED_COAL.get(),
                    32, () -> PalaItems.TITANE_INGOT.get(), 15),
            new Recipe(() -> PalaItems.KIWANO.get(), () -> PalaItems.TITANE_MIXED_COAL.get(),
                    64, () -> PalaItems.PALADIUM_INGOT.get(), 15),
            new Recipe(() -> PalaItems.ORANGEBLUE.get(), () -> PalaItems.PALA_MIXED_COAL.get(),
                    128, () -> PalaItems.ENDIUM_NUGGET.get(), 1),
    };

    /** Fruits deja broyes, un compteur par recette. */
    private final int[] tanks = new int[RECIPES.length];
    private int work;

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.CRUSHER.get(), pos, state, SLOT_COUNT,
                Component.translatable("block.palamod.crusher"));
    }

    /** Remplissage d'un bac, pour l'affichage. */
    public int tank(int recipe) {
        return tanks[recipe];
    }

    // -- Interface -----------------------------------------------------------

    @Override
    protected MenuType<MachineMenu> menuType() {
        return PalaMenus.CRUSHER.get();
    }

    @Override
    protected MachineLayout layout() {
        return MachineLayout.CRUSHER;
    }

    @Override
    public int work() {
        return work;
    }

    @Override
    public int cycleDuration() {
        return matchingRecipe() >= 0 ? WORK_TICKS : 0;
    }

    /**
     * Le bac a afficher : celui de la recette en cours, sinon le plus rempli.
     *
     * <p>Les quatre compteurs sont independants, l'ecran n'en montre qu'un. La
     * recette chargee passe devant, parce que c'est celle que le joueur
     * regarde avancer. Machine vide, on montre le bac le plus avance plutot
     * que rien : un joueur qui revient apres une pause veut savoir ou il en
     * etait, pas decouvrir un compteur a zero qui lui ferait croire son
     * travail perdu.
     */
    private int displayedRecipe() {
        int current = matchingRecipe();
        if (current >= 0) {
            return current;
        }
        int best = -1;
        for (int index = 0; index < tanks.length; index++) {
            if (tanks[index] > 0 && (best < 0 || tanks[index] > tanks[best])) {
                best = index;
            }
        }
        return best;
    }

    @Override
    public int tankFill() {
        int index = displayedRecipe();
        return index < 0 ? 0 : tanks[index];
    }

    @Override
    public int tankGoal() {
        int index = displayedRecipe();
        return index < 0 ? 0 : RECIPES[index].fruitsPerBatch();
    }

    /** Le resultat seulement, ni le fruit ni le combustible. */
    @Override
    protected boolean hopperMayTake(int slot) {
        return slot == SLOT_OUTPUT;
    }

    /**
     * Le fruit a gauche en haut, son charbon en dessous.
     *
     * <p>On accepte <b>tous</b> les fruits dans la case du haut et tous les
     * charbons melanges dans celle du bas, sans verifier qu'ils vont ensemble :
     * c'est {@code matchingRecipe} qui exige le couple. Filtrer plus tot
     * empecherait de preparer la machine en deux gestes.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        for (Recipe recipe : RECIPES) {
            if (slot == SLOT_FRUIT && stack.is(recipe.fruit().get())) {
                return true;
            }
            if (slot == SLOT_FUEL && stack.is(recipe.fuel().get())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void serverTick() {
        int index = matchingRecipe();
        if (index < 0) {
            // Rien a faire : on remet le compteur de cycle a zero plutot que de
            // le figer, sinon changer de fruit en cours de route offrirait les
            // ticks deja faits a la recette suivante.
            if (work != 0) {
                work = 0;
                setChanged();
            }
            return;
        }

        work++;
        if (work < WORK_TICKS) {
            setChanged();
            return;
        }
        work = 0;

        Recipe recipe = RECIPES[index];
        removeItem(SLOT_FRUIT, 1);
        removeItem(SLOT_FUEL, 1);
        tanks[index]++;

        if (tanks[index] >= recipe.fruitsPerBatch()) {
            tanks[index] -= recipe.fruitsPerBatch();
            output(recipe);
        }
        setChanged();
    }

    /**
     * La recette applicable, ou -1.
     *
     * <p>Il faut le fruit, <b>son</b> charbon, et de la place en sortie. Un
     * charbon qui ne correspond pas au fruit ne fait rien : c'est le couple qui
     * definit la recette, pas l'un ou l'autre.
     */
    private int matchingRecipe() {
        ItemStack fruit = getItem(SLOT_FRUIT);
        ItemStack fuel = getItem(SLOT_FUEL);
        if (fruit.isEmpty() || fuel.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < RECIPES.length; index++) {
            Recipe recipe = RECIPES[index];
            if (fruit.is(recipe.fruit().get()) && fuel.is(recipe.fuel().get())
                    && hasRoomFor(recipe)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Place pour le lot entier, verifiee avant de commencer.
     *
     * <p>C'est volontairement strict : on refuse de broyer le 32e fruit si les
     * cinq lingots ne tiennent pas, plutot que de les perdre. Le cobblebreaker
     * fait l'inverse, et c'est aussi son comportement d'origine — ici le lot
     * represente des dizaines de minutes de recolte, la le cout est une pierre.
     */
    private boolean hasRoomFor(Recipe recipe) {
        ItemStack output = getItem(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        if (!output.is(recipe.result().get())) {
            return false;
        }
        return output.getCount() + recipe.resultCount() <= output.getMaxStackSize();
    }

    private void output(Recipe recipe) {
        ItemStack current = getItem(SLOT_OUTPUT);
        if (current.isEmpty()) {
            setItem(SLOT_OUTPUT, new ItemStack(recipe.result().get(), recipe.resultCount()));
        } else {
            current.grow(recipe.resultCount());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work = input.getIntOr("Work", 0);
        int[] saved = input.getIntArray("Tanks").orElse(new int[0]);
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = index < saved.length ? saved[index] : 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Work", work);
        output.putIntArray("Tanks", tanks);
    }

    /** Un couple fruit + charbon, son compte et son rendement. */
    private record Recipe(Supplier<Item> fruit, Supplier<Item> fuel, int fruitsPerBatch,
                          Supplier<Item> result, int resultCount) {
    }
}
