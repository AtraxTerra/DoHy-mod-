package fr.atraxterra.dohy.client.ui.vanilla;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import fr.atraxterra.dohy.client.ui.Anim;
import fr.atraxterra.dohy.client.ui.Buttons;
import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix3x2fStack;

import java.util.Locale;

/**
 * La table d'enchantement, l'enclume et l'alambic au style du kit : les trois ecrans du
 * jeu que PalaVanillaGui (Paladium) redessinait, en plus de ceux que le kit avait deja
 * (etabli, fours, coffres, entonnoir, distributeur).
 *
 * <p>Les cases sont celles du jeu ({@code Slot.x} et {@code Slot.y} sont finals) ; les
 * clics, les bulles, le champ du nom et le livre anime restent ceux du jeu. Ces trois
 * ecrans calculent leurs zones depuis le centre de l'ecran : la fenetre n'est donc pas
 * recentree comme celle des autres ({@code topPos} reste celui du jeu), l'en-tete passe
 * au-dessus.
 *
 * <p>Les runes sous le livre, le losange XP, le trace de l'alambic, ses bulles et sa
 * fleche viennent du jar PalaVanillaGui ({@code tools/palavanillagui_assets.py}).
 */
public final class KitStations {

    private static final Identifier RUNES = piece("enchant_runes");
    private static final Identifier XP = piece("xp_badge");
    private static final Identifier LINE = piece("brewing_line");
    private static final Identifier BUBBLES = piece("bubbles");
    private static final Identifier BUBBLES_BACK = piece("bubbles_back");
    private static final Identifier ARROW = piece("brew_arrow");
    private static final Identifier ARROW_BACK = piece("brew_arrow_back");

    private static final int LAPIS = Ui.Theme.BLUE;
    /** Le trace de l'alambic : la couleur de l'image du jar (le fond clair de Paladium-UI). */
    private static final int LINE_COLOR = 0xFF40404E;
    private static final int POTION = 0xFFC9A2FF;

    private KitStations() {
    }

    private static Identifier piece(String name) {
        return Ui.texture("vanilla/" + name);
    }

    /** Le titre de l'en-tete : le nom du bloc, sauf si le joueur a renomme le sien. */
    static String headerTitle(Component title, String defaultKey, Block block) {
        String text = title.getString();
        return text.equals(Component.translatable(defaultKey).getString()) ? block.getName().getString() : text;
    }

    /**
     * L'icone de l'en-tete pour un bloc sans entite de bloc (une enclume, ebrechee ou
     * abimee) : le bloc vise s'il est de ce genre, sinon {@code fallback}.
     */
    static ItemStack aimed(Class<? extends Block> kind, ItemStack fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            Block block = minecraft.level.getBlockState(hit.getBlockPos()).getBlock();
            if (kind.isInstance(block)) {
                ItemStack stack = new ItemStack(block.asItem());
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return fallback;
    }

    /** La fenetre, l'en-tete et la croix, dans le repere du rectangle du jeu. */
    static void frame(UiGraphics g, Paint p, ItemStack icon, String title, int width, int height, double mouseX,
                      double mouseY) {
        ContainerStyle.window(p, width, height);
        ContainerStyle.header(g, p, icon, title, width, 22);
        ContainerStyle.close(g, p, width, ContainerStyle.overClose(mouseX, mouseY, width));
    }

    // -- Table d'enchantement ---------------------------------------------------------------

    /**
     * La table d'enchantement : a gauche le livre du jeu, sur les runes de PalaVanillaGui,
     * les deux cases et le niveau du joueur ; a droite les trois offres. Une offre montre le
     * lapis qu'elle demande, son nom en runes (celui du jeu, tire de la meme graine) et son
     * cout en niveaux ; grisee si le lapis ou les niveaux manquent. La bulle du jeu dit
     * toujours l'enchantement, le lapis et les niveaux.
     */
    public static final class Enchanting extends EnchantmentScreen {
        private static final Identifier BOOK =
                Identifier.withDefaultNamespace("textures/entity/enchantment/enchanting_table_book.png");
        private static final float OPTION_X = 60.0f;
        private static final float OPTION_W = 108.0f;
        private final ItemStack icon;
        private final Anim.Value[] hovers = {new Anim.Value(0.0f, 16.0f), new Anim.Value(0.0f, 16.0f),
            new Anim.Value(0.0f, 16.0f)};
        private BookModel book;

        public Enchanting(EnchantmentMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.ENCHANTING_TABLE));
        }

        @Override
        protected void init() {
            super.init();
            book = new BookModel(minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            double mx = mouseX - leftPos;
            double my = mouseY - topPos;
            frame(g, p, icon, headerTitle(title, "container.enchant", Blocks.ENCHANTING_TABLE), imageWidth,
                    imageHeight, mx, my);

            // Le grimoire : les runes de PalaVanillaGui, les deux cases, le niveau du joueur.
            ContainerStyle.inset(p, 8, 9, 48, 66);
            p.image(RUNES, 11, 12, 42, 60, 37.5f, 0.0f, 133, 190, 208, 190, 0x80FFFFFF);
            for (Slot slot : menu.slots) {
                ContainerStyle.slot(p, slot);
            }
            p.image(XP, 12.5f, 65.7f, 8.0f, 8.0f, 0.0f, 0.0f, 40, 40, 40, 40, 0xFFFFFFFF);
            g.text(Ui.WIDE, "NIV. " + minecraft.player.experienceLevel, 22.5f, 67.4f, 4.4f, Ui.Theme.GREEN_L);

            options(graphics, g, p, mx, my);
            pose.popMatrix();

            // Le livre du jeu, qui s'ouvre et feuillette, en coordonnees d'ecran comme le jeu.
            if (book != null) {
                float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                graphics.book(book, BOOK, 40.0f, Mth.lerp(partial, oOpen, open), Mth.lerp(partial, oFlip, flip),
                        leftPos + 14, topPos + 14, leftPos + 52, topPos + 45);
            }
        }

        /** Les trois offres, aux zones de clic du jeu (60, 14 + 19 n, 108 x 19). */
        private void options(GuiGraphicsExtractor graphics, UiGraphics g, Paint p, double mx, double my) {
            // La meme graine et le meme ordre de tirage que le jeu : les memes noms en runes.
            EnchantmentNames.getInstance().initSeed(menu.getEnchantmentSeed());
            int gold = menu.getGoldCount();
            boolean creative = minecraft.player.getAbilities().instabuild;
            for (int index = 0; index < 3; index++) {
                float y = 14.0f + 19.0f * index;
                int cost = menu.costs[index];
                if (cost == 0) {
                    hovers[index].toward(0.0f);
                    p.framed(OPTION_X, y, OPTION_W, 18.0f, 3.5f, 0xFF16171A, 0xFF222428, 1);
                    continue;
                }
                String number = Integer.toString(cost);
                int room = 86 - font.width(number);
                FormattedText name = EnchantmentNames.getInstance().getRandomName(font, room);
                boolean enabled = ((gold >= index + 1 && minecraft.player.experienceLevel >= cost) || creative)
                        && menu.enchantClue[index] != -1;
                boolean over = enabled && mx >= OPTION_X && my >= y && mx < OPTION_X + OPTION_W && my < y + 19.0f;
                float hover = hovers[index].toward(over ? 1.0f : 0.0f);
                if (enabled && hover > 0.01f) {
                    p.glow(OPTION_X + OPTION_W / 2.0f, y + 9.0f, 40.0f, Paint.alpha(Ui.Theme.RED, 0.18f * hover));
                }
                int fill = enabled ? Paint.mix(Ui.Theme.CARD, Ui.Theme.CARD_HOVER, hover) : 0xFF1A1B1E;
                int edge = enabled ? Paint.mix(Ui.Theme.EDGE, Ui.Theme.RED, 0.8f * hover) : 0xFF26282C;
                p.framed(OPTION_X, y, OPTION_W, 18.0f, 3.5f, fill, edge, 1);

                // Le lapis demande : une, deux ou trois pierres.
                int tile = Paint.mix(0xFF1A1B1E, LAPIS, enabled ? 0.28f : 0.08f);
                p.framed(OPTION_X + 1.5f, y + 1.5f, 15.0f, 15.0f, 3.0f, tile,
                        Paint.mix(tile, LAPIS, enabled ? 0.55f : 0.2f), 1);
                g.centered(Ui.WIDE, Integer.toString(index + 1), OPTION_X + 9.0f, y + 6.0f, 6.0f,
                        enabled ? Ui.Theme.TEXT : Ui.Theme.DIM);

                // Le nom en runes, comme le jeu le pose, puis le cout en niveaux.
                int runes = !enabled ? 0xFF4D485C : Paint.mix(0xFF9187B0, 0xFFE6DDFF, hover);
                graphics.textWithWordWrap(font, name, (int) OPTION_X + 20, (int) y + 1, room, runes, false);
                g.right(Ui.WIDE, number, OPTION_X + OPTION_W - 2.5f, y + 10.4f, 5.0f,
                        enabled ? Ui.Theme.GREEN_L : 0xFF2F6B3B);
                if (over) {
                    graphics.requestCursor(CursorTypes.POINTING_HAND);
                }
            }
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 2) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(2));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Enclume -------------------------------------------------------------------------------

    /**
     * L'enclume : le champ du nouveau nom (celui du jeu, sur un champ du kit), les deux
     * objets, la fleche et le resultat ; la croix rouge du jeu quand rien ne sort ; le cout
     * en niveaux sous le resultat, en rouge s'il est trop cher ou que les niveaux manquent.
     */
    public static final class Anvil extends AnvilScreen {
        private static final Identifier ERROR = Identifier.withDefaultNamespace("container/anvil/error");
        private final ItemStack icon;
        private EditBox name;

        public Anvil(AnvilMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = aimed(AnvilBlock.class, new ItemStack(Items.ANVIL));
        }

        @Override
        protected void init() {
            super.init();
            name = null;
            for (GuiEventListener child : children()) {
                if (child instanceof EditBox box) {
                    name = box;
                }
            }
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.repair", Blocks.ANVIL), imageWidth, imageHeight,
                    mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66);

            // Le nouveau nom : le champ du jeu passe dessus.
            boolean editable = menu.getSlot(0).hasItem();
            g.text(Ui.PIXEL, "NOUVEAU NOM", 60.0f, 13.5f, 3.3f, editable ? Ui.Theme.MUTED : Ui.Theme.DIM);
            if (editable) {
                Buttons.field(p, 59, 20, 110, 16, name != null && name.isFocused());
            } else {
                p.framed(59, 20, 110, 16, 4.0f, 0xFF141518, 0xFF202226, 1);
            }

            // Les deux objets, le plus, la fleche et le resultat.
            Slot result = menu.getSlot(menu.getResultSlot());
            boolean takeable = result.hasItem() && result.mayPickup(minecraft.player);
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == menu.getResultSlot()) {
                    ContainerStyle.bigSlot(p, slot, result.hasItem() && !takeable ? Ui.Theme.RED : Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }
            int plus = menu.getSlot(1).hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42;
            p.rect(56.0f, 54.35f, 7.0f, 1.3f, plus);
            p.rect(58.85f, 51.5f, 1.3f, 7.0f, plus);
            boolean failed = (menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) && !result.hasItem();
            p.arrow(99.0f, 50.5f, 24.0f, 9.0f, failed ? 0xFF4A2A2A : result.hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42);
            if (failed) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR, 99, 45, 28, 21);
            }
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 3) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(3));
            }
            // Le cout, comme le jeu : trop cher a 40 niveaux (sauf en creatif), en rouge si
            // le resultat ne peut pas etre pris.
            int cost = menu.getCost();
            if (cost <= 0) {
                return;
            }
            String text;
            int color = Ui.Theme.GREEN_L;
            if (cost >= 40 && !minecraft.player.hasInfiniteMaterials()) {
                text = Component.translatable("container.repair.expensive").getString();
                color = Ui.Theme.RED_LIGHT;
            } else if (!menu.getSlot(2).hasItem()) {
                return;
            } else {
                text = Component.translatable("container.repair.cost", cost).getString();
                if (!menu.getSlot(2).mayPickup(minecraft.player)) {
                    color = Ui.Theme.RED_LIGHT;
                }
            }
            text = ContainerStyle.plain(text).toUpperCase(Locale.ROOT);
            float right = imageWidth - 10.0f;
            float textWidth = g.width(Ui.WIDE, text, 4.2f);
            g.paint().framed(right - textWidth - 6.0f, 67.5f, textWidth + 8.0f, 7.0f, 3.5f,
                    Paint.mix(0xFF141518, color, 0.12f), Paint.mix(0xFF24262A, color, 0.35f), 1);
            g.right(Ui.WIDE, text, right, 69.0f, 4.2f, color);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }

    // -- Alambic ------------------------------------------------------------------------------

    /**
     * L'alambic : le carburant (poudre de blaze) et sa jauge, l'ingredient entre les bulles et
     * la fleche de PalaVanillaGui, le trace qui descend vers les trois fioles. Les bulles
     * montent et la fleche se remplit comme celles du jeu ; le temps qui reste a cote.
     */
    public static final class Brewing extends BrewingStandScreen {
        /** Les hauteurs des bulles du jeu, image par image (BUBBLELENGTHS). */
        private static final int[] BUBBLE_LENGTHS = {29, 24, 20, 16, 11, 6, 0};
        private final ItemStack icon;

        public Brewing(BrewingStandMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.BREWING_STAND));
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            frame(g, p, icon, headerTitle(title, "container.brewing", Blocks.BREWING_STAND), imageWidth, imageHeight,
                    mouseX - leftPos, mouseY - topPos);
            ContainerStyle.inset(p, 8, 9, imageWidth - 16, 66.5f);

            // Le trace de l'ingredient aux fioles (l'image du jar relie les deux du bord ; le
            // trait du milieu descend jusqu'a la fiole centrale, plus basse).
            p.image(LINE, 62.6f, 35.3f, 49.0f, 15.2f, 0.0f, 0.0f, 196, 61, 196, 61, 0xFFFFFFFF);
            p.rect(85.6f, 43.5f, 3.0f, 14.0f, LINE_COLOR);
            for (Slot slot : menu.slots) {
                ContainerStyle.slot(p, slot);
            }

            // Le carburant : vingt distillations par poudre de blaze.
            int fuel = Mth.clamp(menu.getFuel(), 0, 20);
            ContainerStyle.bar(p, 13.0f, 37.0f, 24.0f, 3.0f, fuel / 20.0f, Ui.Theme.ORANGE);
            g.centered(Ui.PIXEL, "CARBURANT", 25.0f, 43.0f, 3.3f, Ui.Theme.DIM);
            g.centered(Ui.WIDE, fuel + " / 20", 25.0f, 48.5f, 4.2f, fuel > 0 ? Ui.Theme.ORANGE : Ui.Theme.MUTED);

            // Les bulles et la fleche, aux places du jeu.
            int ticks = menu.getBrewingTicks();
            p.image(BUBBLES_BACK, 63.0f, 14.0f, 12.0f, 29.0f, 0.0f, 0.0f, 48, 120, 48, 120, 0xFFFFFFFF);
            p.image(ARROW_BACK, 97.0f, 16.0f, 9.8f, 28.0f, 0.0f, 0.0f, 39, 112, 39, 112, 0xFFFFFFFF);
            if (ticks > 0) {
                float done = Math.max(0.0f, Math.min(1.0f, 1.0f - ticks / 400.0f));
                if (done > 0.0f) {
                    int region = Math.max(1, Math.round(112 * done));
                    p.image(ARROW, 97.0f, 16.0f, 9.8f, 28.0f * done, 0.0f, 0.0f, 39, region, 39, 112, POTION);
                }
                int bubbles = BUBBLE_LENGTHS[ticks / 2 % 7];
                if (bubbles > 0) {
                    float part = bubbles / 29.0f;
                    int region = Math.max(1, Math.round(120 * part));
                    p.image(BUBBLES, 63.0f, 14.0f + 29.0f - bubbles, 12.0f, bubbles, 0.0f, 120 - region, 48, region,
                            48, 120, 0xFFE7D8FF);
                }
                g.text(Ui.PIXEL, String.format(Locale.FRANCE, "%.1f S", ticks / 20.0f), 110.0f, 27.0f, 3.3f,
                        Ui.Theme.TEXT2);
            }
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 5) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(5));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == 0 && ContainerStyle.overClose(event.x() - leftPos, event.y() - topPos, imageWidth)) {
                onClose();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
            return super.hasClickedOutside(mouseX, mouseY, left, top)
                    && !ContainerStyle.inHeader(mouseX, mouseY, left, top, imageWidth);
        }
    }
}
