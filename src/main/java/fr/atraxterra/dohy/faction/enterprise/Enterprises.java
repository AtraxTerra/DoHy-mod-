package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionVault;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.Nation;
import fr.atraxterra.dohy.faction.org.Cadre;
import fr.atraxterra.dohy.faction.org.Organisations;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Les entreprises de la nation (lot 24b) : ouvrir, agrandir, ameliorer, embaucher, confier a un
 * gerant, renommer, fermer. Les versements horaires sont dans {@link Payout}, les organisations
 * criminelles dans {@link Syndicates}.
 *
 * <p>Une entreprise legale se paie depuis la banque nationale et y verse ses revenus ; une
 * affaire illegale se paie d'abord en argent sale et en rapporte : il faut une societe ecran ou
 * un casino pour le blanchir.
 */
public final class Enterprises {

    public static final long HOUR_MS = 3_600_000L;
    public static final long DAY_MS = 86_400_000L;
    /** Hors ligne, les entreprises tournent une semaine au plus. */
    public static final int MAX_HOURS = 168;
    /** Le salaire d'un employe PNJ, par heure. */
    static final double WAGE = 12.0d;
    /** La place de chaque stock sans entrepot, en unites. */
    static final long BASE_CAPACITY = 300L;
    /** Ce qu'un niveau multiplie : la production et les frais. */
    static final double[] OUTPUT = {1.0d, 1.8d, 2.6d, 3.4d, 4.2d};
    static final double[] COSTS = {1.0d, 1.5d, 2.0d, 2.5d, 3.0d};
    /** Le prix du niveau suivant, en part du prix d'ouverture : vers le niveau 2, 3, 4, 5. */
    static final double[] LEVEL_PRICE = {0.0d, 0.8d, 1.2d, 1.8d, 2.6d};
    /** La cle de stock des caisses d'armes. */
    public static final String CRATES = "crates";
    static final long CRATE_WHOLESALE = 500L;
    static final long CRATE_STREET = 900L;
    /** Les pieces d'une caisse achetees faute de fer, en argent sale. */
    static final long CRATE_PARTS = 150L;
    static final long CRATE_SUPPLY = 550L;
    static final long CRATE_BUYBACK = 400L;
    static final int IRON_PER_CRATE = 2;
    /** La part du capital rendue a la fermeture. */
    static final double REFUND = 0.40d;

    private Enterprises() {
    }

    public static Holdings of(ServerPlayer player) {
        return player.getData(Factions.ENTERPRISES.get());
    }

    /** Combien d'entreprises la nation peut tenir : 4, plus 2 par niveau de Gouvernement. */
    public static int cap(ServerPlayer player) {
        return 4 + 2 * Nation.of(player).government();
    }

    // -- Le lieu ----------------------------------------------------------------------------------

    /**
     * La ou le joueur ouvrirait une entreprise : le pays sous ses pieds et sa latitude.
     *
     * @param refusal pourquoi on ne peut pas ouvrir ici, ou {@code null}
     */
    public record Site(int country, String countryName, int x, int z, double latitude, Region region,
                       String refusal) {
    }

    public static Site here(ServerPlayer player) {
        int x = player.getBlockX();
        int z = player.getBlockZ();
        EarthData earth = EarthData.get();
        if (earth == null) {
            return new Site(0, "Hors carte", x, z, 0.0d, null, null);
        }
        if (!earth.covers(player.level())) {
            return new Site(0, "Hors carte", x, z, 0.0d, null, "Ouvre tes entreprises sur la carte de la Terre");
        }
        double latitude = earth.latitude(z);
        EarthData.Country country = earth.countryAt(x, z);
        if (country == null) {
            return new Site(0, "En mer", x, z, latitude, null, "Pas en pleine mer : va sur la terre ferme d'un pays");
        }
        return new Site(country.id(), country.name(), x, z, latitude,
                Region.of(player.level().getServer(), country.id()), null);
    }

    /** « a Zambie », « hors carte » : ou est le siege. */
    static String place(Enterprise enterprise) {
        return enterprise.country() > 0 ? " (" + EarthNations.countryName(enterprise.country()) + ")" : "";
    }

    public static String countryName(int country) {
        return country > 0 ? EarthNations.countryName(country) : "Hors carte";
    }

    // -- Ouvrir -----------------------------------------------------------------------------------

    /** Pourquoi cette entreprise ne peut pas s'ouvrir ici, ou {@code null}. */
    public static String refusal(ServerPlayer player, Sector sector, Line line, Site site) {
        if (!FactionApi.has(player)) {
            return "Il te faut une nation";
        }
        if (site.refusal() != null) {
            return site.refusal();
        }
        Holdings holdings = of(player);
        int cap = cap(player);
        if (holdings.enterprises.size() >= cap) {
            return "Ta nation tient deja " + holdings.enterprises.size() + " entreprises : le Gouvernement en "
                    + "permettra plus";
        }
        if (sector.lined() && line == null) {
            return "Choisis une drogue";
        }
        if (sector == Sector.PLANTATION) {
            if (!line.grows()) {
                return line.label() + " : rien ne pousse, ses precurseurs s'achetent";
            }
            if (site.country() > 0 && !line.growsAt(site.latitude())) {
                return line.label() + " : " + line.climate().toLowerCase();
            }
        }
        switch (sector.need()) {
            case INFLUENCE_20, INFLUENCE_30 -> {
                int need = sector.need() == Sector.Need.INFLUENCE_20 ? 20 : 30;
                int value = Influence.value(player, site.country());
                if (value < need) {
                    return "Il faut " + need + " d'influence dans ce pays (tu en as " + value + ")";
                }
            }
            case HELD -> {
                if (site.country() <= 0 || !EarthNations.holder(player.level().getServer(), site.country())
                        .ownedBy(player.getUUID())) {
                    return "Une banque ne s'ouvre que dans un pays que tient ta nation";
                }
            }
            default -> {
            }
        }
        return null;
    }

    public static String found(ServerPlayer player, Sector sector, Line line) {
        Site site = here(player);
        String refusal = refusal(player, sector, line, site);
        if (refusal != null) {
            return refusal;
        }
        Holdings holdings = of(player);
        long price = sector.price();
        String unpaid = pay(player, holdings, price, !sector.legal(), "Ouverture : " + sector.label());
        if (unpaid != null) {
            return unpaid;
        }
        long now = System.currentTimeMillis();
        if (holdings.enterprises.isEmpty()) {
            // une premiere entreprise, ou une reprise apres avoir tout ferme : le temps repart d'ici
            holdings.paidUntil = now;
            holdings.dayTick = now;
        }
        String name = defaultName(holdings, sector, line, site);
        Enterprise enterprise = new Enterprise(holdings.nextId(), name, sector.name(), line == null ? "" : line.name(),
                1, price, site.country(), site.x(), site.z(), now, sector.staff(), List.of(), 0L, 0L);
        holdings.enterprises.add(enterprise);
        holdings.log(now, name + " ouvre" + (sector.legal() ? "" : " en secret"));
        return null;
    }

    /** « Ferme de Zambie », « Laboratoire (cocaine) du Bresil 2 ». */
    static String defaultName(Holdings holdings, Sector sector, Line line, Site site) {
        String base = sector.shortLabel() + (line == null ? "" : " (" + line.label().toLowerCase() + ")")
                + (site.country() > 0 ? " " + EarthNations.de(site.countryName()) : "");
        String name = base;
        int index = 2;
        while (taken(holdings, name)) {
            name = base + " " + index++;
        }
        return name;
    }

    private static boolean taken(Holdings holdings, String name) {
        for (Enterprise enterprise : holdings.enterprises) {
            if (enterprise.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Paie {@code amount} : en argent sale d'abord si {@code dirtyFirst}, le reste depuis la banque
     * nationale. La raison du refus, ou {@code null}.
     */
    static String pay(ServerPlayer player, Holdings holdings, long amount, boolean dirtyFirst, String label) {
        long fromDirty = dirtyFirst ? Math.min(holdings.dirty, amount) : 0L;
        long fromBank = amount - fromDirty;
        if (fromBank > 0L && FactionApi.data(player).bank() < fromBank) {
            return "Il faut " + MoneyFormat.exact(amount) + " $" + (dirtyFirst
                    ? " (l'argent sale d'abord, puis la banque nationale)" : " a la banque nationale");
        }
        if (fromBank > 0L && !FactionApi.payFromBank(player, fromBank, label)) {
            return "La banque nationale ne peut pas payer";
        }
        holdings.dirty -= fromDirty;
        return null;
    }

    // -- Gerer ------------------------------------------------------------------------------------

    public static Enterprise find(ServerPlayer player, String id) {
        return of(player).enterprise(id);
    }

    /** Le prix du niveau suivant, 0 au niveau maximal. */
    public static long levelPrice(Enterprise enterprise) {
        if (enterprise.level() >= Enterprise.MAX_LEVEL) {
            return 0L;
        }
        return Math.round(enterprise.kind().price() * LEVEL_PRICE[enterprise.level()] / 100.0d) * 100L;
    }

    public static String levelUp(ServerPlayer player, String id) {
        Holdings holdings = of(player);
        Enterprise enterprise = holdings.enterprise(id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        if (enterprise.level() >= Enterprise.MAX_LEVEL) {
            return enterprise.name() + " est deja au niveau maximal";
        }
        long price = levelPrice(enterprise);
        String unpaid = pay(player, holdings, price, !enterprise.legal(), "Agrandissement : " + enterprise.name());
        if (unpaid != null) {
            return unpaid;
        }
        holdings.replace(enterprise.withLevel(enterprise.level() + 1, price));
        holdings.log(System.currentTimeMillis(), enterprise.name() + " passe au niveau " + (enterprise.level() + 1));
        return null;
    }

    public static String upgrade(ServerPlayer player, String id, Upgrade upgrade) {
        Holdings holdings = of(player);
        Enterprise enterprise = holdings.enterprise(id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        if (enterprise.has(upgrade)) {
            return upgrade.label(enterprise.kind()) + " : deja fait";
        }
        long price = upgrade.price(enterprise.kind());
        String unpaid = pay(player, holdings, price, !enterprise.legal(), upgrade.label(enterprise.kind()) + " : "
                + enterprise.name());
        if (unpaid != null) {
            return unpaid;
        }
        holdings.replace(enterprise.withUpgrade(upgrade, price));
        holdings.log(System.currentTimeMillis(), enterprise.name() + " : " + upgrade.label(enterprise.kind()));
        return null;
    }

    /** Embauche ({@code delta} positif) ou renvoie des employes, sans frais : seul le salaire compte. */
    public static String staff(ServerPlayer player, String id, int delta) {
        Holdings holdings = of(player);
        Enterprise enterprise = holdings.enterprise(id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        Enterprise next = enterprise.withStaff(enterprise.staff() + delta);
        if (next.staff() == enterprise.staff()) {
            return delta > 0 ? "L'equipe est au complet pour ce niveau" : "Plus personne a renvoyer";
        }
        holdings.replace(next);
        return null;
    }

    /** Le gerant de l'entreprise. */
    public static Optional<Cadre> manager(ServerPlayer player, Enterprise enterprise) {
        return Organisations.manager(player, enterprise.id());
    }

    /** Confie l'entreprise au meilleur cadre libre. */
    public static String manage(ServerPlayer player, String id) {
        Enterprise enterprise = find(player, id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        if (manager(player, enterprise).isPresent()) {
            return enterprise.name() + " a deja un gerant";
        }
        Optional<Cadre> best = Organisations.bestFree(player);
        if (best.isEmpty()) {
            return "Aucun cadre libre : recrute-en un, ou libere-en un d'un poste";
        }
        return Organisations.manage(player, best.get().id(), enterprise.id(), enterprise.name());
    }

    public static String unmanage(ServerPlayer player, String id) {
        Enterprise enterprise = find(player, id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        return Organisations.release(player, enterprise.id(), enterprise.name());
    }

    /** Ce que vaut un gerant, de 0 a 1,25 : son talent et son grade, moitie moins s'il n'est plus loyal. */
    static double managerStrength(Cadre cadre) {
        double value = (0.5d + 0.1d * cadre.skill()) * (1.0d + 0.1d * cadre.grade());
        if (cadre.loyalty() < 30.0d) {
            value *= 0.5d;
        }
        return Math.max(0.0d, Math.min(1.25d, value));
    }

    public static String rename(ServerPlayer player, String id, String name) {
        Holdings holdings = of(player);
        Enterprise enterprise = holdings.enterprise(id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        String clean = name.strip();
        if (clean.length() < 3 || clean.length() > 32) {
            return "Un nom fait de 3 a 32 caracteres";
        }
        for (int index = 0; index < clean.length(); index++) {
            char c = clean.charAt(index);
            if (c < 32 || c > 126) {
                return "Lettres sans accent, chiffres et ponctuation simple seulement";
            }
        }
        if (!clean.equalsIgnoreCase(enterprise.name()) && taken(holdings, clean)) {
            return "Une autre entreprise porte deja ce nom";
        }
        holdings.replace(enterprise.withName(clean));
        holdings.log(System.currentTimeMillis(), enterprise.name() + " s'appelle desormais " + clean);
        return null;
    }

    /** Ce que rend la fermeture : 40 % du capital, a la banque ou en argent sale. */
    public static long refund(Enterprise enterprise) {
        return Math.round(enterprise.capital() * REFUND);
    }

    public static String close(ServerPlayer player, String id) {
        Holdings holdings = of(player);
        Enterprise enterprise = holdings.enterprise(id);
        if (enterprise == null) {
            return "Cette entreprise n'existe plus";
        }
        long refund = refund(enterprise);
        if (enterprise.legal()) {
            FactionApi.creditBank(player, refund, "Fermeture : " + enterprise.name());
        } else {
            holdings.dirty += refund;
        }
        if (manager(player, enterprise).isPresent()) {
            Organisations.release(player, enterprise.id(), enterprise.name());
        }
        holdings.enterprises.remove(enterprise);
        holdings.log(System.currentTimeMillis(), enterprise.name() + " ferme ses portes (+" + MoneyFormat.exact(refund)
                + " $" + (enterprise.legal() ? "" : " sales") + ")");
        return null;
    }

    // -- Les prix des fournisseurs ----------------------------------------------------------------

    /** Le prix d'une unite de matiere premiere achetee faute de stock : 0 si personne n'en vend. */
    static long rawPrice(Payout.World world, Line line) {
        boolean deal = world.deal(Syndicate.RECIFS);
        double cut = world.allied(Syndicate.RECIFS) ? 0.85d : 1.0d;
        if (deal) {
            return Math.round(line.raw() * cut);
        }
        return line.grey();
    }

    /** Le prix de gros d'une unite de produit fini chez le Cartel des Recifs. */
    static long wholesale(Payout.World world, Line line) {
        return Math.round(line.wholesale() * (world.allied(Syndicate.RECIFS) ? 0.85d : 1.0d));
    }

    /** Ce que rapporte une unite de surplus revendue a un fournisseur : 0 sans accord, elle se perd. */
    static double buyback(Payout.World world, String key) {
        if (key.equals(CRATES)) {
            return world.deal(Syndicate.LOTUS) ? CRATE_BUYBACK : 0.0d;
        }
        if (!world.deal(Syndicate.RECIFS)) {
            return 0.0d;
        }
        int colon = key.indexOf(':');
        Line line = colon < 0 ? null : Line.of(key.substring(colon + 1));
        if (line == null) {
            return 0.0d;
        }
        return key.startsWith("drug:") ? line.wholesale() * 0.75d : line.raw() * 0.7d;
    }

    /** La commission d'une societe ecran : 15 % au niveau 1, 2 points de moins par niveau. */
    static double facadeFee(Enterprise enterprise) {
        return 0.15d - 0.02d * (enterprise.level() - 1);
    }

    // -- Le fer du coffre -------------------------------------------------------------------------

    /** Ce qu'un objet du coffre vaut en fer : lingot et fer brut 1, blocs 9. */
    static int iron(ItemStack stack) {
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON)) {
            return 1;
        }
        if (stack.is(Items.IRON_BLOCK) || stack.is(Items.RAW_IRON_BLOCK)) {
            return 9;
        }
        return 0;
    }

    static long ironIn(FactionVault vault) {
        long total = 0L;
        for (ItemStack stack : vault.items()) {
            total += (long) iron(stack) * stack.getCount();
        }
        return total;
    }

    static void consumeIron(ServerPlayer player, int units) {
        FactionVault.consume(player, Enterprises::iron, units);
    }

    // -- Pour les autres systemes -----------------------------------------------------------------

    /** Ce que le recel ajoute aux prix du receleur du spawn : 5 % par niveau, 25 % au plus (+5 % avec le Condor). */
    public static double fenceBonus(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            return 0.0d;
        }
        Holdings holdings = of(player);
        long now = System.currentTimeMillis();
        int levels = 0;
        for (Enterprise enterprise : holdings.enterprises) {
            if (enterprise.kind() == Sector.RECEL && !enterprise.closed(now)) {
                levels += enterprise.level();
            }
        }
        double bonus = Math.min(0.25d, 0.05d * levels);
        Holdings.Status condor = holdings.standing(Syndicate.CONDOR).state();
        if (levels > 0 && (condor == Holdings.Status.ACCORD || condor == Holdings.Status.ALLIANCE)) {
            bonus += 0.05d;
        }
        return bonus;
    }

    /** Le risque d'une affaire illegale aujourd'hui, sur 100 (0 pour une entreprise legale). */
    public static double risk(ServerPlayer player, Enterprise enterprise) {
        return new Payout.World(player, of(player), System.currentTimeMillis()).factors(enterprise).risk();
    }

    /** Ce que la prochaine heure rapporterait a cette entreprise, propre ou sale, avant ses frais. */
    public static double perHour(ServerPlayer player, Enterprise enterprise) {
        return Payout.preview(player).earned().getOrDefault(enterprise.id(), 0.0d);
    }

    /** Ses frais par heure, salaires compris. */
    public static double costPerHour(ServerPlayer player, Enterprise enterprise) {
        return new Payout.World(player, of(player), System.currentTimeMillis()).factors(enterprise).cost();
    }

    /** Le net de la prochaine heure : propre, blanchi, moins les frais. */
    public static long netPerHour(ServerPlayer player) {
        return Math.round(Payout.preview(player).net());
    }

    /**
     * La pastille de l'onglet : une guerre de territoire, une entreprise fermee, ou le nombre
     * d'entreprises.
     */
    public static String badge(ServerPlayer player) {
        if (!FactionApi.has(player)) {
            return "";
        }
        Holdings holdings = of(player);
        for (Syndicate syndicate : Syndicate.values()) {
            if (holdings.standing(syndicate).state() == Holdings.Status.GUERRE) {
                return "GUERRE";
            }
        }
        long now = System.currentTimeMillis();
        for (Enterprise enterprise : holdings.enterprises) {
            if (enterprise.closed(now)) {
                return "FERMEE";
            }
        }
        return holdings.enterprises.isEmpty() ? "" : String.valueOf(holdings.enterprises.size());
    }

    /** Fait tourner les entreprises (chaque minute, et au retour du joueur). */
    public static void tick(ServerPlayer player, boolean login) {
        Payout.run(player, login);
    }

    /** Pour les operateurs : fait passer des heures pour les entreprises, pour essayer. */
    public static void advance(ServerPlayer player, int hours) {
        Holdings holdings = of(player);
        long shift = hours * HOUR_MS;
        if (holdings.paidUntil > 0L) {
            holdings.paidUntil -= shift;
        }
        if (holdings.dayTick > 0L) {
            holdings.dayTick -= shift;
        }
        for (Enterprise enterprise : List.copyOf(holdings.enterprises)) {
            holdings.replace(enterprise.shifted(shift));
        }
        for (Holdings.Standing standing : List.copyOf(holdings.standings)) {
            holdings.put(standing.shifted(shift));
        }
        FactionApi.tell(player, hours + " h passent pour tes entreprises.", ChatFormatting.GRAY);
        Payout.run(player, false);
    }

    /** La nation est dissoute : ses entreprises ferment sans rien rendre. */
    public static void disbanded(ServerPlayer player) {
        of(player).clear();
    }
}
