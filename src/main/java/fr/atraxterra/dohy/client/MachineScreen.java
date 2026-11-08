package fr.atraxterra.dohy.client;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.machine.MachineLayout;
import fr.atraxterra.dohy.palamod.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * L'ecran d'une machine : le fond, les cases, et la barre de progression.
 *
 * <p><b>C'est le premier ecran du mod, et le second morceau de rendu.</b> Le
 * projet evitait le code client depuis le depart, et le chapitre precedent s'en
 * sortait avec un coffre vanilla. La limite etait nette : un coffre ne
 * synchronise que des objets, donc aucune jauge, aucune cadence, aucun etat
 * chiffre. Une machine sans barre de progression ne dit pas si elle tourne.
 *
 * <p>Le risque est contenu : c'est un ecran de conteneur, pas du rendu en
 * monde. Le pipeline de soumission — la vraie difficulte de 26.2 — n'est pas
 * touche.
 *
 * <p><b>Ce que 26.2 change.</b> Le rendu d'interface est passe en <i>mode
 * retenu</i> : {@code GuiGraphics} n'existe plus, les ecrans n'ont plus a
 * dessiner mais a <b>extraire</b> leur etat dans un {@link GuiGraphicsExtractor}
 * que le moteur rend ensuite. {@code renderBg} devient donc
 * {@code extractBackground}, et {@code blit} prend un pipeline de rendu en
 * premier argument.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 166;

    /** La texture fait 256x256 ; les sprites vivent a droite du panneau. */
    private static final int TEXTURE_SIZE = 256;

    private final Identifier texture;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title, String name) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
        this.texture = Identifier.fromNamespaceAndPath(
                PalaMod.NAMESPACE, "textures/gui/" + name + ".png");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                  float partialTick) {
        int left = getGuiLeft();
        int top = getGuiTop();

        extractor.blit(RenderPipelines.GUI_TEXTURED, texture, left, top,
                0.0f, 0.0f, PANEL_WIDTH, PANEL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        float progress = getMenu().progress();
        MachineLayout layout = getMenu().layout();
        drawProgress(extractor, left, top, layout.arrow(), progress);

        // La flamme suit la meme progression que la fleche : le crusher consomme
        // son charbon au rythme ou il broie. Deux jauges qui disent la meme
        // chose, mais a deux endroits que l'oeil associe a deux ressources.
        if (layout.flame() != null) {
            drawProgress(extractor, left, top, layout.flame(), progress);
        }
    }

    /**
     * Une jauge : le sprite vide, puis la part pleine par-dessus.
     *
     * <p>Le rognage suit le sens declare par le sprite : une flamme monte
     * depuis le bas, une fleche se remplit dans la direction ou elle pointe.
     */
    private void drawProgress(GuiGraphicsExtractor extractor, int left, int top,
                              MachineLayout.Sprite sprite, float progress) {
        int x = left + sprite.x();
        int y = top + sprite.y();

        extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                sprite.u(), sprite.v(), sprite.width(), sprite.height(),
                TEXTURE_SIZE, TEXTURE_SIZE);

        if (progress <= 0.0f) {
            return;
        }

        if (sprite.vertical()) {
            int filled = Math.max(1, Math.round(sprite.height() * progress));
            // Une flamme monte, donc on rogne par le bas ; une fleche vers le
            // bas se remplit dans son sens, donc par le haut.
            int offset = sprite.fillUp() ? sprite.height() - filled : 0;
            extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + offset,
                    sprite.u(), sprite.fullV() + offset, sprite.width(), filled,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        } else {
            int filled = Math.max(1, Math.round(sprite.width() * progress));
            extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                    sprite.u(), sprite.fullV(), filled, sprite.height(),
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
