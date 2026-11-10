package fr.atraxterra.dohy.palamod.wither;

import net.minecraft.world.item.Item;

/** Une gemme de wither : elle se pose dans un receptacle ({@link WitherReceptacleBlock}). */
public class WitherGemItem extends Item {

    private final WitherKind kind;

    public WitherGemItem(WitherKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public WitherKind kind() {
        return kind;
    }
}
