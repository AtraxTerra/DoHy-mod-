package fr.atraxterra.dohy.client.ui.vanilla;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.Ui;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix3x2fStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * L'etabli et les trois fours du jeu, redessines au style du kit.
 *
 * <p>Le livre de recettes du jeu reste entier : son panneau, ses recettes fantomes et
 * son bouton. Le bouton passe dans l'en-tete, a cote de la croix, dessine comme elle ;
 * c'est toujours le bouton du jeu (clics, position quand le livre s'ouvre), simplement
 * retire de la liste de ce que le jeu dessine.
 */
public final class KitWorkstations {

    /** Les boutons de l'en-tete : la croix, puis le livre a sa gauche. */
    private static final int HEADER_BUTTONS = 22 + ContainerStyle.BUTTON + 4;
    /** L'icone du bouton, creee au premier dessin (jamais a l'initialisation de la classe). */
    private static ItemStack bookIcon;

    private KitWorkstations() {
    }

    // -- Le livre de recettes ------------------------------------------------------------

    /** Le bouton du livre parmi les elements de l'ecran (le seul bouton image de ces ecrans). */
    static ImageButton book(List<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof ImageButton button) {
                return button;
            }
        }
        return null;
    }

    /** Le panneau du livre, pour savoir s'il est ouvert. */
    static RecipeBookComponent<?> recipes(List<? extends GuiEventListener> children) {
        for (GuiEventListener child : children) {
            if (child instanceof RecipeBookComponent<?> component) {
                return component;
            }
        }
        return null;
    }

    /** La place du bouton : dans l'en-tete, a gauche de la croix. */
    static ScreenPosition bookPosition(int leftPos, int imageWidth, int screenHeight, int imageHeight) {
        return new ScreenPosition(leftPos + imageWidth - 21 - 4 - ContainerStyle.BUTTON,
                ContainerStyle.top(screenHeight, imageHeight) + ContainerStyle.BUTTON_Y);
    }

    /**
     * Le bouton, dessine a sa place. Le survol se calcule ici : retire de la liste de
     * dessin, le bouton du jeu ne tient plus son etat de survol a jour.
     */
    static void drawBook(UiGraphics g, ImageButton book, RecipeBookComponent<?> recipes, int leftPos, int topPos,
                         int mouseX, int mouseY) {
        if (book == null) {
            return;
        }
        if (bookIcon == null) {
            bookIcon = new ItemStack(Items.BOOK);
        }
        ContainerStyle.iconButton(g, g.paint(), book.getX() - leftPos, book.getY() - topPos, bookIcon,
                book.isMouseOver(mouseX, mouseY), recipes != null && recipes.isVisible());
    }

    static void bookTip(UiGraphics g, ImageButton book, int mouseX, int mouseY) {
        if (book != null && book.isMouseOver(mouseX, mouseY)) {
            g.tooltip(List.of(Component.literal("Livre de recettes")));
        }
    }

    // -- Etabli ----------------------------------------------------------------------------

    /** L'etabli : la grille, une fleche, le resultat en grande case. */
    public static final class Crafting extends CraftingScreen {
        private final ItemStack icon = new ItemStack(Items.CRAFTING_TABLE);
        private ImageButton book;
        private RecipeBookComponent<?> recipes;

        public Crafting(CraftingMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
            book = book(children());
            recipes = recipes(children());
            if (book != null) {
                removeWidget(book);
                addWidget(book);
                book.setSize(ContainerStyle.BUTTON, ContainerStyle.BUTTON);
            }
        }

        @Override
        protected ScreenPosition getRecipeBookButtonPosition() {
            return bookPosition(leftPos, imageWidth, height, imageHeight);
        }

        /** Le titre du jeu est « Fabrication » : l'en-tete porte le nom du bloc, comme les autres. */
        private String headerTitle() {
            String text = title.getString();
            return text.equals(Component.translatable("container.crafting").getString())
                    ? Blocks.CRAFTING_TABLE.getName().getString() : text;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Paint p = g.paint();
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            ContainerStyle.window(p, imageWidth, imageHeight);
            ContainerStyle.header(g, p, icon, headerTitle(), imageWidth, HEADER_BUTTONS);
            ContainerStyle.close(g, p, imageWidth, ContainerStyle.overClose(mouseX - leftPos, mouseY - topPos,
                    imageWidth));
            drawBook(g, book, recipes, leftPos, topPos, mouseX, mouseY);
            ContainerStyle.inset(p, 8, 12, imageWidth - 16, 60.5f);
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                if (index == 0) {
                    ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
                } else {
                    ContainerStyle.slot(p, slot);
                }
            }
            p.arrow(91, 38, 20, 10, menu.getSlot(0).hasItem() ? Ui.Theme.TEXT2 : 0xFF3A3C42);
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            if (menu.slots.size() > 10) {
                ContainerStyle.inventoryLabel(g, menu.slots.get(10));
            }
            bookTip(g, book, mouseX, mouseY);
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

    // -- Fours -----------------------------------------------------------------------------

    /** Le temps de cuisson et de flamme, lus dans les donnees du menu (le jeu ne les expose pas). */
    private static Field dataField;
    private static boolean dataMissing;

    static ContainerData data(AbstractFurnaceMenu menu) {
        if (dataMissing) {
            return null;
        }
        try {
            if (dataField == null) {
                Field field = AbstractFurnaceMenu.class.getDeclaredField("data");
                field.setAccessible(true);
                dataField = field;
            }
            return (ContainerData) dataField.get(menu);
        } catch (ReflectiveOperationException | RuntimeException error) {
            dataMissing = true;
            return null;
        }
    }

    /** Une duree en secondes : « 10 », « 7,5 ». */
    static String seconds(int ticks) {
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        return tenths(ticks);
    }

    /** Une duree au dixieme de seconde : « 6,2 ». */
    static String tenths(int ticks) {
        return String.format(Locale.FRANCE, "%.1f", ticks / 20.0f);
    }

    /**
     * Le four : l'entree et le combustible avec la flamme entre eux, a gauche ce qu'il
     * reste de flamme, au milieu la barre de cuisson et son temps, le resultat en
     * grande case.
     */
    static void furnace(GuiGraphicsExtractor graphics, UiGraphics g, AbstractFurnaceMenu menu, Identifier flame,
                        int imageWidth) {
        Paint p = g.paint();
        ContainerStyle.inset(p, 8, 12, imageWidth - 16, 60.5f);
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (index == AbstractFurnaceMenu.RESULT_SLOT) {
                ContainerStyle.bigSlot(p, slot, Ui.Theme.GREEN);
            } else {
                ContainerStyle.slot(p, slot);
            }
        }
        boolean lit = menu.isLit();
        ContainerStyle.flame(graphics, flame, 56, 36, lit, menu.getLitProgress());
        ContainerData data = data(menu);

        // Ce qu'il reste de flamme.
        String left;
        if (!lit) {
            left = "ETEINTE";
        } else if (data != null && data.getCount() >= 2) {
            left = Math.max(1, Math.round(data.get(0) / 20.0f)) + " S";
        } else {
            left = Math.round(menu.getLitProgress() * 100) + " %";
        }
        g.centered(Ui.PIXEL, "FLAMME", 31.0f, 37.0f, 3.3f, Ui.Theme.DIM);
        g.centered(Ui.WIDE, left, 31.0f, 42.5f, 4.5f, lit ? Ui.Theme.ORANGE : Ui.Theme.MUTED);

        // La cuisson.
        float progress = menu.getBurnProgress();
        ContainerStyle.bar(p, 80.0f, 41.5f, 26.0f, 3.0f, progress, Ui.Theme.RED);
        if (data != null && data.getCount() >= 4 && data.get(3) > 0) {
            String time = tenths(data.get(2)) + " / " + seconds(data.get(3)) + " S";
            g.centered(Ui.PIXEL, time, 93.0f, 47.5f, 3.3f, progress > 0 ? Ui.Theme.TEXT2 : Ui.Theme.DIM);
        }
    }

    /** Le dessin commun d'un four, fenetre et en-tete compris. */
    static void furnaceScreen(GuiGraphicsExtractor graphics, UiGraphics g, AbstractFurnaceMenu menu, ItemStack icon,
                              String title, Identifier flame, int imageWidth, int imageHeight, int mouseX,
                              int mouseY, ImageButton book, RecipeBookComponent<?> recipes, int leftPos, int topPos) {
        Paint p = g.paint();
        ContainerStyle.window(p, imageWidth, imageHeight);
        ContainerStyle.header(g, p, icon, title, imageWidth, HEADER_BUTTONS);
        ContainerStyle.close(g, p, imageWidth, ContainerStyle.overClose(mouseX - leftPos, mouseY - topPos, imageWidth));
        drawBook(g, book, recipes, leftPos, topPos, mouseX, mouseY);
        furnace(graphics, g, menu, flame, imageWidth);
    }

    private static Identifier flame(String furnace) {
        return Identifier.withDefaultNamespace("container/" + furnace + "/lit_progress");
    }

    /** Le fourneau. */
    public static final class Furnace extends FurnaceScreen {
        private static final Identifier FLAME = flame("furnace");
        private final ItemStack icon = new ItemStack(Items.FURNACE);
        private ImageButton book;
        private RecipeBookComponent<?> recipes;

        public Furnace(FurnaceMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
            book = book(children());
            recipes = recipes(children());
            if (book != null) {
                removeWidget(book);
                addWidget(book);
                book.setSize(ContainerStyle.BUTTON, ContainerStyle.BUTTON);
            }
        }

        @Override
        protected ScreenPosition getRecipeBookButtonPosition() {
            return bookPosition(leftPos, imageWidth, height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            furnaceScreen(graphics, g, menu, icon, title.getString(), FLAME, imageWidth, imageHeight, mouseX, mouseY,
                    book, recipes, leftPos, topPos);
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            ContainerStyle.inventoryLabel(g, menu.slots.get(AbstractFurnaceMenu.SLOT_COUNT));
            bookTip(g, book, mouseX, mouseY);
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

    /** Le haut fourneau. */
    public static final class Blast extends BlastFurnaceScreen {
        private static final Identifier FLAME = flame("blast_furnace");
        private final ItemStack icon = new ItemStack(Items.BLAST_FURNACE);
        private ImageButton book;
        private RecipeBookComponent<?> recipes;

        public Blast(BlastFurnaceMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
            book = book(children());
            recipes = recipes(children());
            if (book != null) {
                removeWidget(book);
                addWidget(book);
                book.setSize(ContainerStyle.BUTTON, ContainerStyle.BUTTON);
            }
        }

        @Override
        protected ScreenPosition getRecipeBookButtonPosition() {
            return bookPosition(leftPos, imageWidth, height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            furnaceScreen(graphics, g, menu, icon, title.getString(), FLAME, imageWidth, imageHeight, mouseX, mouseY,
                    book, recipes, leftPos, topPos);
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            ContainerStyle.inventoryLabel(g, menu.slots.get(AbstractFurnaceMenu.SLOT_COUNT));
            bookTip(g, book, mouseX, mouseY);
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

    /** Le fumoir. */
    public static final class Smoker extends SmokerScreen {
        private static final Identifier FLAME = flame("smoker");
        private final ItemStack icon = new ItemStack(Items.SMOKER);
        private ImageButton book;
        private RecipeBookComponent<?> recipes;

        public Smoker(SmokerMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
            book = book(children());
            recipes = recipes(children());
            if (book != null) {
                removeWidget(book);
                addWidget(book);
                book.setSize(ContainerStyle.BUTTON, ContainerStyle.BUTTON);
            }
        }

        @Override
        protected ScreenPosition getRecipeBookButtonPosition() {
            return bookPosition(leftPos, imageWidth, height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            furnaceScreen(graphics, g, menu, icon, title.getString(), FLAME, imageWidth, imageHeight, mouseX, mouseY,
                    book, recipes, leftPos, topPos);
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            ContainerStyle.inventoryLabel(g, menu.slots.get(AbstractFurnaceMenu.SLOT_COUNT));
            bookTip(g, book, mouseX, mouseY);
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
