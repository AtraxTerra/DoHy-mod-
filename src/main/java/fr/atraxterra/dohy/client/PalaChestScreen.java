package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.storage.PalaChestMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * L'ecran des coffres de PalaMod : 12 x 9 cases, et la colonne d'anneaux.
 *
 * <p>Les coordonnees sont celles de {@code ModdedChestContainer} et ne laissent
 * aucune place au titre a l'interieur du panneau : la grille commence a 8 px du
 * haut. Le titre est donc ecrit juste au-dessus, a la couleur de son metal, et
 * le libelle « Inventaire » n'est pas ecrit — il n'y a que 4 px entre le coffre
 * et l'inventaire, comme dans l'original.
 */
public class PalaChestScreen extends AbstractContainerScreen<PalaChestMenu> {

    private static final int PANEL_HEIGHT = 256;
    private static final int TEXTURE_HEIGHT = 256;

    private final Identifier texture;

    public PalaChestScreen(PalaChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.tier().panelWidth(), PANEL_HEIGHT);
        this.texture = Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE,
                "textures/gui/" + menu.tier().key() + ".png");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        // La texture fait 256 de large, ou plus quand la colonne d'anneaux
        // elargit le panneau (ChestTier.textureWidth, tools/gen_gui.py).
        extractor.blit(RenderPipelines.GUI_TEXTURED, texture, getLeftPos(), getTopPos(),
                0.0f, 0.0f, getMenu().tier().panelWidth(), PANEL_HEIGHT,
                getMenu().tier().textureWidth(), TEXTURE_HEIGHT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(getFont(), getTitle(), PalaChestMenu.CHEST_X, -11, getMenu().tier().titleColor(), true);
    }
}
