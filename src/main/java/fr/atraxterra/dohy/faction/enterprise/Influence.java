package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Nation;
import fr.atraxterra.dohy.faction.org.Diplomacy;
import fr.atraxterra.dohy.faction.org.Treaty;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.faction.war.WarStrategy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L'influence de la nation dans un pays (lot 24b), de 0 a 100 : le tenir, y avoir sa capitale,
 * ses claims et ses entreprises, les traites ou la guerre avec la nation qui le tient, la
 * relation avec l'organisation criminelle de la region. Elle se calcule, rien n'est garde :
 * elle bouge des que la carte, la diplomatie ou les affaires bougent.
 *
 * <p>Elle fait les revenus (de 80 % a 140 %) et le risque des affaires illegales (de 120 % a
 * 60 %) ; il en faut pour ouvrir une usine, un racket.
 */
public final class Influence {

    private Influence() {
    }

    /** Une part de l'influence, pour l'expliquer. */
    public record Part(String label, int value) {
    }

    /** L'influence dans un pays et ce qui la fait. */
    public record Reading(int country, int value, List<Part> parts) {
    }

    /** Ce qu'il faut savoir une fois pour toutes les lectures d'un meme calcul. */
    static final class Context {
        final ServerPlayer player;
        final MinecraftServer server;
        final Holdings holdings;
        final Map<Integer, Integer> claims;
        final int capital;
        final Map<Integer, Reading> cache = new HashMap<>();

        Context(ServerPlayer player, Holdings holdings) {
            this.player = player;
            this.server = player.level().getServer();
            this.holdings = holdings;
            this.claims = claimsByCountry(player);
            this.capital = FactionApi.has(player) ? Nation.of(player).capital() : 0;
        }

        Reading read(int country) {
            return cache.computeIfAbsent(country, this::compute);
        }

        private Reading compute(int country) {
            List<Part> parts = new ArrayList<>();
            if (country <= 0 || EarthData.get() == null || !FactionApi.has(player)) {
                return new Reading(country, 0, parts);
            }
            EarthNations.Holder holder = EarthNations.holder(server, country);
            if (holder.ownedBy(player.getUUID())) {
                parts.add(new Part("Tu tiens le pays", 40));
            }
            if (capital == country) {
                parts.add(new Part("Ta capitale", 15));
            }
            int claimed = claims.getOrDefault(country, 0);
            if (claimed > 0) {
                parts.add(new Part(claimed + " claim(s)", Math.min(15, claimed)));
            }
            int levels = 0;
            for (Enterprise enterprise : holdings.enterprises) {
                if (enterprise.country() == country) {
                    levels += enterprise.level();
                }
            }
            if (levels > 0) {
                parts.add(new Part("Tes entreprises", Math.min(30, 3 * levels)));
            }
            if (!holder.player() && holder.rival() >= 0 && holder.rival() < NationTraits.count()) {
                int nation = holder.rival();
                Treaty treaty = Diplomacy.treaty(player, nation);
                if (treaty != null) {
                    int bonus = switch (treaty.kind()) {
                        case ALLIANCE -> 10;
                        case ACCORD -> 6;
                        case PACTE -> 3;
                    };
                    parts.add(new Part(treaty.kind().label() + " avec " + NationTraits.of(nation).the(), bonus));
                }
                if (Diplomacy.rival(player, nation)) {
                    parts.add(new Part("Rivale declaree", -10));
                }
                if (WarStrategy.relationOf(player, nation).stance() == WarData.Stance.GUERRE) {
                    parts.add(new Part("En guerre avec elle", -15));
                }
            }
            Syndicate syndicate = Syndicate.in(Region.of(server, country));
            if (syndicate != null) {
                Holdings.Status status = holdings.standing(syndicate).state();
                if (status == Holdings.Status.GUERRE) {
                    parts.add(new Part("Guerre avec " + syndicate.label(), -10));
                } else if (status == Holdings.Status.ALLIANCE) {
                    parts.add(new Part("Alliance avec " + syndicate.label(), 5));
                }
            }
            int value = 0;
            for (Part part : parts) {
                value += part.value();
            }
            return new Reading(country, Math.max(0, Math.min(100, value)), parts);
        }
    }

    public static Reading of(ServerPlayer player, int country) {
        return new Context(player, Enterprises.of(player)).read(country);
    }

    public static int value(ServerPlayer player, int country) {
        return of(player, country).value();
    }

    /**
     * Les pays ou la nation compte : ceux qu'elle tient, sa capitale, ceux de ses claims et de
     * ses entreprises, du plus influent au moins influent.
     */
    public static List<Reading> all(ServerPlayer player) {
        Context context = new Context(player, Enterprises.of(player));
        Set<Integer> countries = new LinkedHashSet<>();
        if (FactionApi.has(player) && EarthData.get() != null) {
            countries.addAll(EarthNations.heldBy(player));
            if (context.capital > 0) {
                countries.add(context.capital);
            }
        }
        countries.addAll(context.claims.keySet());
        for (Enterprise enterprise : context.holdings.enterprises) {
            if (enterprise.country() > 0) {
                countries.add(enterprise.country());
            }
        }
        List<Reading> out = new ArrayList<>();
        for (int country : countries) {
            out.add(context.read(country));
        }
        out.sort((a, b) -> Integer.compare(b.value(), a.value()));
        return out;
    }

    /** Ce que l'influence fait aux revenus : de 80 % a 140 % (le commerce, de 60 % a 180 %). */
    static double revenue(Sector sector, int influence) {
        return sector == Sector.COMMERCE ? 0.6d + 0.012d * influence : 0.8d + 0.006d * influence;
    }

    /** Ce qu'elle fait au risque des affaires illegales : de 120 % a 60 %. */
    static double risk(int influence) {
        return 1.2d - 0.006d * influence;
    }

    /** Les claims de la nation sur la carte de la Terre, pays par pays. */
    static Map<Integer, Integer> claimsByCountry(ServerPlayer player) {
        Map<Integer, Integer> out = new HashMap<>();
        EarthData earth = EarthData.get();
        if (earth == null || !FactionApi.has(player)) {
            return out;
        }
        String dimension = earth.dimension().identifier().toString();
        FactionData data = FactionApi.data(player);
        for (FactionData.Claim claim : data.claims()) {
            if (!claim.dimension().equals(dimension)) {
                continue;
            }
            EarthData.Country country = earth.countryAt(claim.x() * 16 + 8, claim.z() * 16 + 8);
            if (country != null) {
                out.merge(country.id(), 1, Integer::sum);
            }
        }
        return out;
    }
}
