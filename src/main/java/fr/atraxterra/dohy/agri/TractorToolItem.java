package fr.atraxterra.dohy.agri;

import net.minecraft.world.item.Item;

/** Un outil de tracteur : il s'attelle d'un clic sur le tracteur, l'outil en main. */
public class TractorToolItem extends Item {

    private final TractorTool tool;

    public TractorToolItem(Properties properties, TractorTool tool) {
        super(properties);
        this.tool = tool;
    }

    public TractorTool tool() {
        return tool;
    }
}
