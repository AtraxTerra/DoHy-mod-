package fr.atraxterra.dohy.palamod.machine;

import fr.atraxterra.dohy.palamod.PalaBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Le totem de fertilite : il accelere les cultures posees sur de la terre
 * fertilisee, et brule du paladium pour le faire.
 *
 * <p><b>Tout ce qui suit est releve au bytecode de {@code TileEntityTotem}</b>,
 * y compris l'ambiguite que le chapitre 4 avait laissee ouverte en refusant de
 * la deviner : {@code waiting} et {@code timeforgrowth} sont des
 * <b>millisecondes</b>, pas des ticks. Le tick d'origine commence par
 * {@code System.currentTimeMillis()} et compare les deux champs a cette
 * horloge. Donc 10 s entre deux poussees de croissance et 1 s entre deux
 * balayages de la zone — et non 10 000 et 1 000 ticks, qui auraient fait un
 * totem inutilisable.
 *
 * <p><b>Le totem ne travaille que sur la terre fertilisee.</b> Le balayage
 * d'origine ne retient que les {@code BlockConnectedDirt}. Le totem et la terre
 * fertilisee du chapitre 3 sont donc un couple : un totem alimente pose sur un
 * champ ordinaire ne fait rien. Ce n'est ecrit nulle part sur le wiki.
 *
 * <p>Trois ecarts avec l'original, tous volontaires :
 *
 * <p><b>1. Le temps est compte en ticks de jeu.</b> L'original lit l'horloge du
 * systeme, ce qui fait tourner le totem quand le monde est en pause et le laisse
 * bruler du carburant pendant qu'on regarde un menu. Les durees sont identiques
 * — 200 et 20 ticks — mais rattachees au temps du jeu.
 *
 * <p><b>2. La zone est rebalayee a chaque cycle.</b> L'original ne rebalaye que
 * {@code if (blockList.isEmpty())} ; la liste ne faisant que grossir, de la
 * terre fertilisee posee <b>apres</b> le totem n'etait jamais prise en compte.
 * C'est un piege, pas un equilibrage, et rebalayer 225 blocs toutes les 10 s ne
 * coute rien.
 *
 * <p><b>3. Le carburant paie les croissances reelles, pas les tentatives.</b>
 * L'original incremente son compteur quand la plante <i>pourrait</i> pousser,
 * en interrogeant {@code IGrowable.canGrow}. On compte ici les cases dont
 * l'etat a <b>effectivement</b> change. C'est plus avantageux pour le joueur a
 * la marge, mesurable plutot que suppose, et ca evite de dependre d'une
 * signature de {@code BonemealableBlock} qu'il aurait fallu deviner.
 */
public class TotemBlockEntity extends BlockEntity implements ServerTicking {

    /** Rayon du balayage : de -7 a +7, soit une zone de 15x15. */
    public static final int RADIUS = 7;

    /** Poussees de croissance payees par un lingot. {@code DEFAULT_MAX_BOOSTED_BLOCKS}. */
    public static final int BOOSTS_PER_INGOT = 2048;

    /** 10 s dans l'original, donc 200 ticks. */
    private static final int GROWTH_PERIOD = 200;

    /** Le rythme de l'apparence (visage, etincelles) : une fois par seconde. */
    private static final int SCAN_PERIOD = 20;

    /** Le stock de carburant, un lingot de paladium a la fois. */
    private ItemStack fuel = ItemStack.EMPTY;

    /** Poussees deja payees sur le lingot en cours. */
    private int boosts;

    private long nextGrowth;
    private long nextScan;

    /** Les sols fertilises de la zone, releves au dernier balayage. */
    private final List<BlockPos> soils = new ArrayList<>();

    public TotemBlockEntity(BlockPos pos, BlockState state) {
        super(PalaMachines.TOTEM.get(), pos, state);
    }

    // -- Carburant -----------------------------------------------------------

    public ItemStack fuel() {
        return fuel;
    }

    public int boosts() {
        return boosts;
    }

    /** Poussees restantes sur le stock entier, pour l'afficher au joueur. */
    public int remainingBoosts() {
        if (fuel.isEmpty()) {
            return 0;
        }
        return (fuel.getCount() - 1) * BOOSTS_PER_INGOT + (BOOSTS_PER_INGOT - boosts);
    }

    /**
     * Ajoute un lingot au stock. Rend {@code false} si le stock est plein.
     *
     * <p>Un lingot par clic, comme dans l'original, et jamais au-dela de la
     * pile : sans ce plafond, le clic droit avalerait silencieusement un lingot
     * que le totem ne peut pas stocker.
     */
    public boolean addFuel(ItemStack held) {
        if (fuel.isEmpty()) {
            fuel = held.copyWithCount(1);
            setChanged();
            syncCharge();
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(fuel, held)
                || fuel.getCount() >= fuel.getMaxStackSize()) {
            return false;
        }
        fuel.grow(1);
        setChanged();
        syncCharge();
        return true;
    }

    /**
     * Met le visage du totem a la couleur de son carburant
     * ({@link TotemBlock#CHARGE}). Un totem pose avant que la propriete
     * existe se met a jour au premier balayage.
     */
    private void syncCharge() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(TotemBlock.CHARGE)) {
            return;
        }
        int charge = TotemBlock.chargeOf(fuel.getCount());
        if (state.getValue(TotemBlock.CHARGE) != charge) {
            level.setBlock(worldPosition, state.setValue(TotemBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
        }
    }

    // -- Travail -------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel server) || fuel.isEmpty()) {
            return;
        }
        long now = server.getGameTime();

        if (now >= nextScan) {
            nextScan = now + SCAN_PERIOD;
            syncCharge();
            // Un totem au travail se voit : quelques etincelles vertes autour du mat.
            if (!soils.isEmpty()) {
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, worldPosition.getX() + 0.5d,
                        worldPosition.getY() + 1.3d, worldPosition.getZ() + 0.5d, 3, 0.45d, 0.6d, 0.45d, 0.0d);
            }
        }
        if (now >= nextGrowth) {
            nextGrowth = now + GROWTH_PERIOD;
            // La zone n'est relue qu'au moment de s'en servir : 225 blocs toutes
            // les dix secondes, pas toutes les secondes.
            scan(server);
            grow(server);
        }
    }

    /**
     * Releve les sols fertilises de la zone.
     *
     * <p>Au niveau {@code y - 1}, comme l'original : le totem regarde le sol
     * sur lequel il est pose, pas celui de son propre niveau.
     */
    private void scan(ServerLevel server) {
        soils.clear();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos soil = worldPosition.offset(dx, -1, dz);
                if (isFertilizedSoil(server.getBlockState(soil))) {
                    soils.add(soil);
                }
            }
        }
    }

    /**
     * Les sols que le totem surveille.
     *
     * <p>Publique parce que l'affichage de la zone s'en sert : montrer un carre
     * de 15x15 laisserait croire que tout le carre est accelere, alors que
     * seules les cases fertilisees comptent. Une seule definition pour les deux
     * usages, sinon l'affichage finirait par mentir.
     */
    public static boolean isFertilizedSoil(BlockState state) {
        return state.is(PalaBlocks.FERTILIZED_DIRT.get())
                || state.is(PalaBlocks.FERTILIZED_FARMLAND.get());
    }

    /**
     * Une poussee de croissance sur chaque culture de la zone.
     *
     * <p>Un ou deux tirages par case ({@code random.nextInt(2) + 1} dans
     * l'original), par {@code randomTick} et non par engrais : la croissance
     * reste celle de la plante, simplement plus souvent appelee. Une plante qui
     * ne pousse pas la nuit ne poussera pas davantage ici.
     */
    private void grow(ServerLevel server) {
        RandomSource random = server.getRandom();
        for (BlockPos soil : soils) {
            if (fuel.isEmpty()) {
                return;
            }
            BlockPos plantPos = soil.above();
            BlockState before = server.getBlockState(plantPos);
            if (before.isAir() || !before.isRandomlyTicking()) {
                continue;
            }

            int draws = random.nextInt(2) + 1;
            for (int draw = 0; draw < draws; draw++) {
                server.getBlockState(plantPos).randomTick(server, plantPos, random);
            }

            if (!server.getBlockState(plantPos).equals(before)) {
                server.levelEvent(2005, plantPos, 0);
                charge();
            }
        }
        setChanged();
    }

    /** Compte une poussee, et consomme un lingot quand le compte est atteint. */
    private void charge() {
        boosts++;
        if (boosts < BOOSTS_PER_INGOT) {
            return;
        }
        boosts = 0;
        fuel.shrink(1);
        syncCharge();
    }

    // -- Sauvegarde ----------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel = input.read("Fuel", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        boosts = input.getIntOr("Boosts", 0);
        // Les echeances ne sont pas sauvegardees : le temps de jeu repart d'ou
        // il s'etait arrete, mais rien ne garantit que ces valeurs aient un
        // sens relatif a lui apres un changement de monde. Les remettre a zero
        // fait travailler le totem au prochain tick, ce qui est le bon defaut.
        nextGrowth = 0;
        nextScan = 0;
        soils.clear();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!fuel.isEmpty()) {
            output.store("Fuel", ItemStack.CODEC, fuel);
        }
        output.putInt("Boosts", boosts);
    }
}
