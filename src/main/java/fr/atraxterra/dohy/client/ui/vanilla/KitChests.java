package fr.atraxterra.dohy.client.ui.vanilla;

import fr.atraxterra.dohy.client.ui.ContainerStyle;
import fr.atraxterra.dohy.client.ui.Paint;
import fr.atraxterra.dohy.client.ui.UiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * Les coffres du jeu (coffre, grand coffre, tonneau, coffre de l'Ender, chariot), la
 * boite de shulker, l'entonnoir et le distributeur (ou le dropper), redessines au
 * style du kit : en-tete avec l'icone du bloc vise, cases arrondies, intertitres. Les
 * cases restent celles du jeu, a leur place.
 */
public final class KitChests {

    private KitChests() {
    }

    /** Le dessin commun : fenetre, en-tete, croix, cases. */
    static void panel(UiGraphics g, AbstractContainerMenu menu, ItemStack icon, String title, int width,
                      int height, boolean overClose) {
        Paint p = g.paint();
        ContainerStyle.window(p, width, height);
        ContainerStyle.header(g, p, icon, title, width, 22);
        ContainerStyle.close(g, p, width, overClose);
        for (Slot slot : menu.slots) {
            ContainerStyle.slot(p, slot);
        }
    }

    /** Les intertitres : le contenu au-dessus des cases du coffre, l'inventaire au-dessus des siennes. */
    static void labels(UiGraphics g, AbstractContainerMenu menu, int containerSlots, String count) {
        if (containerSlots > 0) {
            ContainerStyle.labelAbove(g, menu.slots.get(0), "CONTENU", count);
        }
        if (containerSlots < menu.slots.size()) {
            ContainerStyle.inventoryLabel(g, menu.slots.get(containerSlots));
        }
    }

    /** Un coffre de 9 x n cases. */
    public static final class Chest extends ContainerScreen {
        private final ItemStack icon;

        public Chest(ChestMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.CHEST));
        }

        @Override
        protected void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            panel(g, menu, icon, title.getString(), imageWidth, imageHeight,
                    ContainerStyle.overClose(mouseX - leftPos, mouseY - topPos, imageWidth));
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            int slots = menu.getRowCount() * 9;
            labels(ContainerStyle.begin(graphics, font, mouseX, mouseY), menu, slots, slots + " CASES");
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

    /** Une boite de shulker. */
    public static final class Shulker extends ShulkerBoxScreen {
        private final ItemStack icon;

        public Shulker(ShulkerBoxMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.SHULKER_BOX));
        }

        @Override
        protected void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            panel(g, menu, icon, title.getString(), imageWidth, imageHeight,
                    ContainerStyle.overClose(mouseX - leftPos, mouseY - topPos, imageWidth));
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            labels(ContainerStyle.begin(graphics, font, mouseX, mouseY), menu, 27, "27 CASES");
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

    /** Un entonnoir : cinq cases. */
    public static final class Hopper extends HopperScreen {
        private final ItemStack icon;

        public Hopper(HopperMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.HOPPER));
        }

        @Override
        protected void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            panel(g, menu, icon, title.getString(), imageWidth, imageHeight,
                    ContainerStyle.overClose(mouseX - leftPos, mouseY - topPos, imageWidth));
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            labels(ContainerStyle.begin(graphics, font, mouseX, mouseY), menu, 5, "5 CASES");
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

    /** Un distributeur ou un dropper : neuf cases au milieu. */
    public static final class Dispenser extends DispenserScreen {
        private final ItemStack icon;

        public Dispenser(DispenserMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            this.icon = ContainerStyle.targetIcon(new ItemStack(Items.DISPENSER));
        }

        @Override
        protected void init() {
            super.init();
            topPos = ContainerStyle.top(height, imageHeight);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            ContainerStyle.veil(graphics, width, height);
            UiGraphics g = ContainerStyle.begin(graphics, font, mouseX, mouseY);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(leftPos, topPos);
            panel(g, menu, icon, title.getString(), imageWidth, imageHeight,
                    ContainerStyle.overClose(mouseX - leftPos, mouseY - topPos, imageWidth));
            pose.popMatrix();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            labels(ContainerStyle.begin(graphics, font, mouseX, mouseY), menu, 9, "9 CASES");
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
