package fr.atraxterra.dohy.faction;

import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.earth.mine.EarthMines;
import fr.atraxterra.dohy.faction.reserve.GoldReserve;
import fr.atraxterra.dohy.faction.reserve.GoldReserves;
import fr.atraxterra.dohy.faction.war.Sieges;
import fr.atraxterra.dohy.faction.war.WarData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Ta nation (chapitre 23, lot 23a) : la faction, ses terres sur la carte de la Terre, son
 * tresor, sa recherche et sa guerre, derriere une seule API. Les ecrans, le HUD et les
 * commandes la lisent au lieu d'aller chercher chaque morceau la ou il vit.
 *
 * <p>Les donnees restent ou elles sont : la faction et la guerre sur le joueur, les pays et les
 * mines sur le monde. Cette facade n'en possede aucune et n'ecrit rien ; elle se construit a la
 * demande, le temps d'un ecran.
 */
public final class Nation {

    private final ServerPlayer player;
    private final FactionData data;

    private Nation(ServerPlayer player) {
        this.player = player;
        this.data = FactionApi.data(player);
    }

    public static Nation of(ServerPlayer player) {
        return new Nation(player);
    }

    public ServerPlayer player() {
        return player;
    }

    public FactionData data() {
        return data;
    }

    public boolean exists() {
        return data.exists();
    }

    // -- Le nom et le rang ---------------------------------------------------------------------

    public String name() {
        return data.name();
    }

    public NationRank rank() {
        return NationRank.of(player);
    }

    /** « Royaume de Nordheim », « Empire d'Arcanum » ; au premier rang, le nom seul. */
    public String title() {
        return title(rank(), name());
    }

    public static String title(NationRank rank, String name) {
        return rank == NationRank.FACTION ? name : rank.label() + " " + EarthNations.de(name);
    }

    /**
     * Ce qu'il faut pour le rang suivant : « Royaume a 5 pays » ; sans carte de la Terre, le
     * palier de la branche Globale (« Royaume au palier 8 de la Globale »), deux par palier du
     * Gouvernement en attendant ses domaines (lot 23b).
     */
    public String nextRank() {
        NationRank next = rank().next();
        if (next == null) {
            return "";
        }
        if (earth()) {
            return next == NationRank.CITY ? next.label() + " avec une capitale"
                    : next.label() + " a " + next.countries() + " pays";
        }
        return next.label() + " au palier " + NationRank.governmentFor(next) * 2 + " de la Globale";
    }

    public ItemStack banner() {
        return FactionMenus.banner(data);
    }

    public FactionLeague league() {
        return FactionLeague.of(data.ranking().league());
    }

    public long elo() {
        return data.ranking().elo();
    }

    // -- Le territoire --------------------------------------------------------------------------

    /** Vrai sur une carte de la Terre : la nation a une capitale et des pays. */
    public boolean earth() {
        return EarthNations.active();
    }

    public int claims() {
        return data.claims().size();
    }

    public int claimCap() {
        return FactionApi.claimCap(player);
    }

    /** Le pays de la capitale, 0 sans capitale (ou sans carte de la Terre). */
    public int capital() {
        return earth() ? EarthNations.nation(player).capital() : 0;
    }

    public List<Integer> countries() {
        return EarthNations.heldBy(player);
    }

    /** Le pays vise, 0 sans cible. */
    public int target() {
        return earth() ? EarthNations.nation(player).target() : 0;
    }

    public int mines() {
        return EarthMines.active() ? EarthMines.of(player).size() : 0;
    }

    public long minesPerHour() {
        return EarthMines.active() ? EarthMines.moneyPerHour(player) : 0L;
    }

    // -- Le tresor ------------------------------------------------------------------------------

    public long bank() {
        return data.bank();
    }

    public FactionVault vault() {
        return FactionVault.of(player);
    }

    public GoldReserve reserve() {
        return GoldReserves.of(player);
    }

    /** Un placement de la reserve d'or est arrive a terme. */
    public boolean reserveRipe() {
        long now = System.currentTimeMillis();
        return reserve().investments().stream().anyMatch(investment -> investment.ripe(now));
    }

    // -- La recherche ---------------------------------------------------------------------------

    /**
     * Le palier du Gouvernement, de 0 a 6 : en attendant les neuf domaines (lot 23b), deux
     * paliers de la branche Globale en font un, comme le prevoit la conversion de l'arbre.
     */
    public int government() {
        return government(data);
    }

    public static int government(FactionData data) {
        return Math.max(0, Math.min(6, data.tier(FactionBranch.GLOBAL.key()) / 2));
    }

    /** Les paliers acquis, toutes branches confondues. */
    public int researched() {
        int done = 0;
        for (FactionBranch branch : FactionBranch.values()) {
            done += data.tier(branch.key());
        }
        return done;
    }

    public int researchTotal() {
        int total = 0;
        for (FactionBranch branch : FactionBranch.values()) {
            total += FactionTree.tiers(branch).size();
        }
        return total;
    }

    /** Les ameliorations en cours. */
    public int researching() {
        int running = 0;
        for (FactionBranch branch : FactionBranch.values()) {
            if (data.upgradeEnd(branch.key()) > 0L) {
                running++;
            }
        }
        return running;
    }

    /** Un palier peut se lancer tout de suite. */
    public boolean researchReady() {
        for (FactionBranch branch : FactionBranch.values()) {
            if (data.upgradeEnd(branch.key()) <= 0L && FactionTree.blocker(player, branch) == null) {
                return true;
            }
        }
        return false;
    }

    // -- La guerre ------------------------------------------------------------------------------

    public WarData war() {
        return player.getData(Factions.WAR.get());
    }

    /** La nation qui te menace ou te fait la guerre. */
    public Optional<WarData.Relation> conflict() {
        return war().conflict();
    }

    public boolean besieged() {
        return Sieges.besieged(player.getUUID());
    }

    /** Les nations hostiles (humeur a 60 et plus). */
    public int hostiles() {
        int hostile = 0;
        for (WarData.Relation relation : war().relations()) {
            if (relation.hostility() >= 60.0d) {
                hostile++;
            }
        }
        return hostile;
    }

    // -- Petits outils --------------------------------------------------------------------------

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};

    public static String roman(int tier) {
        return tier >= 0 && tier < ROMAN.length ? ROMAN[tier] : String.valueOf(tier);
    }
}
