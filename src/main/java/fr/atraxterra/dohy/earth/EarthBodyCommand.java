package fr.atraxterra.dohy.earth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code /gisements [ressource]} : les gisements geants les plus proches, leur
 * direction et leur distance — « Kiruna, fer (Suede) : 412 blocs au nord ».
 * Seulement sur la carte de la Terre.
 */
final class EarthBodyCommand {

    private static final int SHOWN = 6;

    private EarthBodyCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("gisements")
                .executes(context -> list(context.getSource().getPlayerOrException(), ""))
                .then(Commands.argument("ressource", StringArgumentType.greedyString())
                        .executes(context -> list(context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "ressource"))));
    }

    private static int list(ServerPlayer player, String filter) {
        EarthData earth = EarthData.get();
        if (earth == null || !earth.covers(player.level())) {
            player.sendSystemMessage(Component.literal("Les gisements geants n'existent que sur la carte de la Terre.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String wanted = filter.trim().toLowerCase(Locale.ROOT);
        List<EarthBodies.Body> found = new ArrayList<>();
        for (EarthBodies.Body body : EarthBodies.all()) {
            if (wanted.isEmpty() || body.resource().contains(wanted) || body.name().toLowerCase(Locale.ROOT)
                    .contains(wanted)) {
                found.add(body);
            }
        }
        if (found.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucun gisement geant de " + filter.trim()
                    + ". Ressources : fer, cuivre, or, diamant, charbon, petrole, gaz, uranium, bauxite...")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        double x = player.getX();
        double z = player.getZ();
        found.sort(Comparator.comparingDouble(body -> distance(body, x, z)));
        player.sendSystemMessage(Component.literal(wanted.isEmpty() ? "Gisements geants les plus proches :"
                : "Gisements geants (" + filter.trim() + ") les plus proches :").withStyle(ChatFormatting.GOLD));
        for (EarthBodies.Body body : found.subList(0, Math.min(SHOWN, found.size()))) {
            EarthData.Country country = earth.byCode(body.country());
            double dx = body.centerX() - x;
            double dz = body.centerZ() - z;
            String where = Math.round(Math.sqrt(dx * dx + dz * dz)) + " blocs " + direction(dx, dz);
            player.sendSystemMessage(Component.literal("  " + body.name() + ", " + body.resource()
                    + (country == null ? "" : " (" + country.name() + ")") + " : ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(where).withStyle(ChatFormatting.GRAY)));
        }
        return found.size();
    }

    private static double distance(EarthBodies.Body body, double x, double z) {
        double dx = body.centerX() - x;
        double dz = body.centerZ() - z;
        return dx * dx + dz * dz;
    }

    /** La direction a prendre, le nord vers les z negatifs. */
    private static String direction(double dx, double dz) {
        if (dx * dx + dz * dz < 64.0d) {
            return "(tu y es)";
        }
        String[] names = {"a l'est", "au sud-est", "au sud", "au sud-ouest", "a l'ouest", "au nord-ouest", "au nord",
                "au nord-est"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        int index = (int) Math.round(angle / 45.0d);
        return names[Math.floorMod(index, 8)];
    }
}
