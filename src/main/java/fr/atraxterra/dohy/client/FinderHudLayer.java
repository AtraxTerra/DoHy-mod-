package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.client.hud.HudKit;
import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import fr.atraxterra.dohy.palamod.defense.UnclaimHelmet;
import fr.atraxterra.dohy.palamod.pillage.FinderItem;
import fr.atraxterra.dohy.palamod.pillage.FinderType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.Locale;

/**
 * Le HUD des detecteurs ({@code ModuleUnclaimFinder}, {@code ModuleSpawnerFinder}).
 *
 * <p>Un finder tenu en main — ou un casque enchante Unclaim Helmet, qui vaut un
 * finder vert — et un cartouche du kit s'affiche au milieu du bord gauche (le
 * solde occupe celui de droite) : ce que le finder trouve dans son carre de
 * chunks, une jauge a sa couleur, et pour le bleu la direction du conteneur le
 * plus proche.
 *
 * <p>Tout se lit dans les chunks du client, ou toutes les entites de bloc sont
 * presentes. Recompte deux fois par seconde.
 */
public final class FinderHudLayer implements GuiLayer {

    private static final int MIN_WIDTH = 132;
    private static final int MARGIN = 6;
    private static final long REFRESH = 10L;

    private FinderType shown;
    private ItemStack icon = ItemStack.EMPTY;
    private long computedAt = Long.MIN_VALUE;
    private int found;
    private BlockPos nearest;
    private boolean loggedFailure;
    private ItemStack active = ItemStack.EMPTY;
    private long activeAt = Long.MIN_VALUE;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            renderInner(graphics);
        } catch (Throwable error) {
            if (!loggedFailure) {
                loggedFailure = true;
                DoHy.LOGGER.error("Le HUD des finders a echoue, il est desactive pour cette session", error);
            }
        }
    }

    private void renderInner(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        // Ce que le joueur tient ne change pas entre deux ticks : on ne relit pas
        // ses mains et l'enchantement de son casque a chaque image.
        long tick = level.getGameTime();
        if (tick != activeAt) {
            activeAt = tick;
            active = activeStack(player, level);
        }
        ItemStack held = active;
        if (held.isEmpty()) {
            shown = null;
            return;
        }
        FinderType type = held.getItem() instanceof FinderItem finder ? finder.type() : FinderType.GREEN;
        long now = level.getGameTime();
        if (type != shown || now - computedAt >= REFRESH || now < computedAt) {
            shown = type;
            icon = held.copyWithCount(1);
            computedAt = now;
            scan(level, player.blockPosition(), player.chunkPosition(), type);
        }
        draw(graphics, new UiGraphics(graphics, minecraft.font, 0, 0), minecraft, player.blockPosition(), type);
    }

    /** Le finder en main (droite, puis gauche), sinon le casque Unclaim Helmet garni d'un finder. */
    private static ItemStack activeStack(LocalPlayer player, ClientLevel level) {
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof FinderItem) {
                return stack;
            }
        }
        // Le casque ne vaut finder qu'apres sa fusion avec un finder, de sa couleur.
        return UnclaimHelmet.finder(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private void scan(ClientLevel level, BlockPos center, ChunkPos origin, FinderType type) {
        found = 0;
        nearest = null;
        double best = Double.MAX_VALUE;
        for (int dx = -type.radius(); dx <= type.radius(); dx++) {
            for (int dz = -type.radius(); dz <= type.radius(); dz++) {
                int cx = origin.x() + dx;
                int cz = origin.z() + dz;
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!matches(entity, type)) {
                        continue;
                    }
                    found++;
                    double distance = entity.getBlockPos().distSqr(center);
                    if (distance < best) {
                        best = distance;
                        nearest = entity.getBlockPos();
                    }
                }
            }
        }
    }

    private static boolean matches(BlockEntity entity, FinderType type) {
        if (type.spawners()) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
            return id != null && id.getPath().contains("spawner");
        }
        return entity instanceof Container;
    }

    private void draw(GuiGraphicsExtractor graphics, UiGraphics ui, Minecraft minecraft, BlockPos from,
                      FinderType type) {
        String title = ContainerStyle.plain(icon.getHoverName().getString()).toUpperCase(Locale.ROOT);
        String line;
        float progress;
        if (type.spawners()) {
            line = found == 0 ? "Aucun spawner" : found == 1 ? "1 spawner detecte" : found + " spawners detectes";
            progress = found > 0 ? 1.0f : 0.0f;
        } else {
            int percent = Math.min(100, found * FinderType.PERCENT_PER_FIND);
            line = "Conteneurs : " + percent + " %";
            progress = percent / 100.0f;
        }
        String compass = null;
        if (type.direction() && nearest != null) {
            int distance = (int) Math.round(Math.sqrt(nearest.distSqr(from)));
            compass = "Plus proche : " + heading(from, nearest) + ", " + distance + (distance > 1 ? " blocs" : " bloc");
        }

        // Le cartouche s'elargit au texte le plus long.
        int textOffset = 31;
        float widest = Math.max(ui.width(Ui.WIDE, title, 6), ui.width(Ui.BODY, line, 7));
        if (compass != null) {
            widest = Math.max(widest, ui.width(Ui.BODY, compass, 6.5f));
        }
        int width = Math.max(MIN_WIDTH, textOffset + (int) Math.ceil(widest) + 10);
        int height = compass != null ? 46 : 36;
        int x = MARGIN;
        int y = (minecraft.getWindow().getGuiScaledHeight() - height) / 2;
        // Une carte du HUD : le lisere a la couleur du finder, sa tuile, le texte et la jauge.
        Paint paint = new Paint(graphics, HudKit.unit(minecraft));
        int color = type.color();
        HudKit.card(paint, x, y, width, height, 5.0f);
        paint.round(x + 3.0f, y + 5.0f, 1.5f, height - 10.0f, 0.75f, color);
        int tile = Paint.mix(0xFF1A1B1E, color, 0.18f);
        paint.framed(x + 7.0f, y + (height - 20) / 2.0f, 20.0f, 20.0f, 4.5f, tile, Paint.mix(tile, color, 0.45f), 1);
        ui.item(icon, x + 9, y + (height - 16) / 2.0f, 16, false);

        int textX = x + textOffset;
        ui.text(Ui.WIDE, title, textX, y + 6, 6, Ui.Theme.TEXT);
        ui.text(Ui.BODY, line, textX, y + 15, 7, found > 0 ? Ui.Theme.TEXT2 : Ui.Theme.MUTED);
        int barWidth = width - textOffset - 8;
        HudKit.bar(paint, textX, y + 25, barWidth, 5, progress, Paint.mix(color, 0xFFFFFFFF, 0.25f),
                Paint.mix(color, 0xFF000000, 0.2f), false);
        if (compass != null) {
            ui.text(Ui.BODY, compass, textX, y + 34, 6.5f, Ui.Theme.MUTED);
        }
    }

    /** La direction cardinale de {@code to} vu de {@code from}, nord en haut. */
    static String heading(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz < 4.0d) {
            return to.getY() > from.getY() ? "au-dessus" : "en dessous";
        }
        String[] names = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest"};
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(angle / 45.0d), 8L);
        return names[sector];
    }
}
