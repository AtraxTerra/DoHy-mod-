package fr.atraxterra.dohy.guide.plan;

import fr.atraxterra.dohy.guide.plan.Blueprint.Expect;
import fr.atraxterra.dohy.guide.plan.Blueprint.Variant;
import fr.atraxterra.dohy.palamod.alchemist.PalaAlchemist;
import fr.atraxterra.dohy.palamod.dimminer.PalaDimMiner;
import fr.atraxterra.dohy.palamod.dimminer.WitheredObsidianBlock;
import fr.atraxterra.dohy.palamod.fluid.PalaFluids;
import fr.atraxterra.dohy.palamod.golem.PalaGolem;
import fr.atraxterra.dohy.palamod.hunter.PalaHunter;
import fr.atraxterra.dohy.palamod.hunter.SpawnerBlueprint;
import fr.atraxterra.dohy.palamod.machine.PalaMachines;
import fr.atraxterra.dohy.palamod.mount.PalaMount;
import fr.atraxterra.dohy.palamod.reserve.PalaReserve;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Les plans de toutes les structures a plusieurs blocs du mod.
 *
 * <p>Chaque plan reprend la forme que verifie le code de sa structure
 * ({@code GrinderStructure}, {@code CauldronStructure},
 * {@code GolemBoxStructure}, {@code BarnStructure}, {@code SpawnerBlueprint},
 * {@code MinerPortalShape}, la reserve d'or de {@code GoldReserves}) : un
 * plan bati a l'identique est reconnu par la structure (la capture de la CI le
 * verifie, plan par plan).
 *
 * <p>Construits a la premiere demande, pas au chargement de la classe : les
 * blocs ne sont enregistres qu'apres.
 */
public final class Blueprints {

    private static volatile List<Blueprint> all;

    private Blueprints() {
    }

    public static List<Blueprint> all() {
        List<Blueprint> list = all;
        if (list == null) {
            synchronized (Blueprints.class) {
                if (all == null) {
                    all = List.of(grinder(), cauldron(), golemBox(), barn(), spawners(), minerPortal(), reserve());
                }
                list = all;
            }
        }
        return list;
    }

    /** Le plan de cet identifiant, ou {@code null}. */
    public static Blueprint get(String id) {
        for (Blueprint blueprint : all()) {
            if (blueprint.id().equals(id)) {
                return blueprint;
            }
        }
        return null;
    }

    private static Expect block(Block block) {
        return Expect.block(BuiltInRegistries.BLOCK.getKey(block).toString(), block);
    }

    private static Expect fluid(Fluid fluid) {
        return Expect.fluid(BuiltInRegistries.FLUID.getKey(fluid).toString(), fluid);
    }

    /** Le grinder : un cube de 3, le grinder au milieu de la face avant, la lave au centre. */
    private static Blueprint grinder() {
        List<int[]> offsets = new ArrayList<>();
        List<Expect> expects = new ArrayList<>();
        offsets.add(new int[]{0, 0, 0});
        expects.add(block(PalaMachines.GRINDER_BLOCK.get()));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int z = dz + 1;
                    if (dx == 0 && dy == 0 && z == 0) {
                        continue;
                    }
                    int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    offsets.add(new int[]{dx, dy, z});
                    expects.add(axes == 0 ? fluid(Fluids.LAVA)
                            : axes == 1 ? block(PalaMachines.GRINDER_CASING.get())
                            : block(PalaMachines.GRINDER_FRAME.get()));
                }
            }
        }
        return new Blueprint("grinder", "Grinder", "grinder", PalaMachines.GRINDER_BLOCK.get(),
                List.of(Blueprint.normalize("3 x 3 x 3", offsets, expects)));
    }

    /** Le chaudron : un plancher de 7 x 7, le coeur au milieu, un anneau de 5 x 5 et son bassin. */
    private static Blueprint cauldron() {
        List<int[]> offsets = new ArrayList<>();
        List<Expect> expects = new ArrayList<>();
        Block cauldron = PalaAlchemist.CAULDRON.get();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                offsets.add(new int[]{dx, 0, dz});
                expects.add(dx == 0 && dz == 0 ? block(PalaAlchemist.CAULDRON_CORE.get()) : block(cauldron));
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == 2) {
                    offsets.add(new int[]{dx, 1, dz});
                    expects.add(block(cauldron));
                } else if (ring <= 1) {
                    offsets.add(new int[]{dx, 1, dz});
                    expects.add(fluid(PalaFluids.ANGELIC_WATER.get()));
                }
            }
        }
        return new Blueprint("chaudron", "Chaudron de l'alchimiste", "alchimie", PalaAlchemist.CAULDRON_CORE.get(),
                List.of(Blueprint.normalize("7 x 7", offsets, expects)));
    }

    /** La boite a golem : un cube de 3, la boite au milieu d'une face, l'eau angelique au centre. */
    private static Blueprint golemBox() {
        List<int[]> offsets = new ArrayList<>();
        List<Expect> expects = new ArrayList<>();
        offsets.add(new int[]{0, 0, 0});
        expects.add(block(PalaGolem.GOLEM_BOX.get()));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int z = dz + 1;
                    if (dx == 0 && dy == 0 && z == 0) {
                        continue;
                    }
                    offsets.add(new int[]{dx, dy, z});
                    expects.add(dx == 0 && dy == 0 && dz == 0 ? fluid(PalaFluids.ANGELIC_WATER.get())
                            : block(PalaGolem.GOLEM_BOX_FRAME.get()));
                }
            }
        }
        return new Blueprint("boite_golem", "Boite a golem", "golem", PalaGolem.GOLEM_BOX.get(),
                List.of(Blueprint.normalize("3 x 3 x 3", offsets, expects)));
    }

    /** L'enclos : un carre de foin de 3 a 7, un anneau de fondations et leurs barrieres, le gestionnaire. */
    private static Blueprint barn() {
        List<Variant> variants = new ArrayList<>();
        for (int side = 3; side <= 7; side++) {
            List<int[]> offsets = new ArrayList<>();
            List<Expect> expects = new ArrayList<>();
            for (int x = -1; x <= side; x++) {
                for (int z = 0; z <= side + 1; z++) {
                    boolean ring = x == -1 || x == side || z == 0 || z == side + 1;
                    offsets.add(new int[]{x, 0, z});
                    if (ring) {
                        expects.add(block(PalaMount.BARN_FOUNDATION.get()));
                        offsets.add(new int[]{x, 1, z});
                        expects.add(block(PalaMount.BARN_FENCE.get()));
                    } else {
                        expects.add(block(Blocks.HAY_BLOCK));
                    }
                }
            }
            offsets.add(new int[]{(side - 1) / 2, 1, 1 + (side - 1) / 2});
            expects.add(block(PalaMount.BARN_CONTROLLER.get()));
            variants.add(Blueprint.normalize("Foin " + side + " x " + side, offsets, expects));
        }
        return new Blueprint("enclos", "Enclos", "monture", PalaMount.BARN_CONTROLLER.get(), List.copyOf(variants));
    }

    /** Les quatre structures de spawners, du palier I au palier IV. */
    private static Blueprint spawners() {
        List<Variant> variants = new ArrayList<>();
        String[] roman = {"I", "II", "III", "IV"};
        List<SpawnerBlueprint> ordered = new ArrayList<>(SpawnerBlueprint.ALL);
        java.util.Collections.reverse(ordered);
        for (int index = 0; index < ordered.size(); index++) {
            List<int[]> offsets = new ArrayList<>();
            List<Expect> expects = new ArrayList<>();
            for (SpawnerBlueprint.PlanCell cell : ordered.get(index).planCells()) {
                offsets.add(new int[]{cell.dx(), cell.dy(), cell.dz()});
                expects.add(block(cell.block()));
            }
            Variant raw = Blueprint.normalize("", offsets, expects);
            variants.add(new Variant("Palier " + roman[index] + " (" + raw.width() + " x " + raw.depth() + ")",
                    raw.cells(), raw.width(), raw.depth(), raw.height()));
        }
        return new Blueprint("spawners", "Structure de spawners", "spawners", PalaHunter.SPAWNER_CONTROLLER.get(),
                List.copyOf(variants));
    }

    /** Le portail du Mineur, au plus petit : 2 de large et 3 de haut a l'interieur, les coins libres. */
    private static Blueprint minerPortal() {
        Block obsidian = PalaDimMiner.WITHERED_OBSIDIAN.get();
        String key = BuiltInRegistries.BLOCK.getKey(obsidian).toString();
        Expect frame = Expect.charged(key, obsidian,
                state -> state.getValue(WitheredObsidianBlock.FEED) >= WitheredObsidianBlock.FULL,
                obsidian.defaultBlockState().setValue(WitheredObsidianBlock.FEED, WitheredObsidianBlock.FULL));
        Expect corner = Expect.optional(key, obsidian);
        List<int[]> offsets = new ArrayList<>();
        List<Expect> expects = new ArrayList<>();
        for (int x = 0; x <= 3; x++) {
            for (int y = 0; y <= 4; y++) {
                boolean side = x == 0 || x == 3;
                boolean edge = y == 0 || y == 4;
                if (!side && !edge) {
                    continue;
                }
                offsets.add(new int[]{x, y, 0});
                expects.add(side && edge ? corner : frame);
            }
        }
        return new Blueprint("portail_mineur", "Portail du Mineur", "dimension_mineur", obsidian,
                List.of(Blueprint.normalize("4 x 5", offsets, expects)));
    }

    /** La reserve d'or : un cube de 5, 6 ou 7, des coffres-forts, des vitres au milieu des faces, l'or dedans. */
    private static Blueprint reserve() {
        List<Variant> variants = new ArrayList<>();
        for (int n = 5; n <= 7; n++) {
            List<int[]> offsets = new ArrayList<>();
            List<Expect> expects = new ArrayList<>();
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    for (int z = 0; z < n; z++) {
                        boolean ix = x > 0 && x < n - 1;
                        boolean iy = y > 0 && y < n - 1;
                        boolean iz = z > 0 && z < n - 1;
                        offsets.add(new int[]{x, y, z});
                        if (ix && iy && iz) {
                            // Le coeur : de l'or liquide, ou rien ; tout autre bloc casse la reserve.
                            expects.add(Expect.emptyOr(
                                    BuiltInRegistries.BLOCK.getKey(PalaReserve.LIQUID_GOLD.get()).toString(),
                                    PalaReserve.LIQUID_GOLD.get()));
                        } else if (iy && (ix || iz) && (!ix || !iz)) {
                            expects.add(block(PalaReserve.VAULT_GLASS.get()));
                        } else {
                            expects.add(block(PalaReserve.FACTION_SAFE.get()));
                        }
                    }
                }
            }
            variants.add(Blueprint.normalize("Palier " + (n - 4) + " (" + n + " x " + n + " x " + n + ")",
                    offsets, expects));
        }
        return new Blueprint("reserve", "Reserve d'or", "reserve_or", PalaReserve.FACTION_SAFE.get(),
                List.copyOf(variants));
    }
}
