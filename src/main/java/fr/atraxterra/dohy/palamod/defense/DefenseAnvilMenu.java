package fr.atraxterra.dohy.palamod.defense;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * L'enclume vanilla, ouverte sur une enclume de PalaMod.
 *
 * <p>Deux differences, une seule methode : le menu reste valide devant nos
 * enclumes, qui ne sont pas dans le tag {@code minecraft:anvil}. Et parce
 * qu'elles n'y sont pas, le tirage de 12 % qui ebreche l'enclume vanilla a
 * chaque usage ne les concerne pas — une enclume en paladium ne s'use pas.
 * Le client ouvre l'ecran de l'enclume vanilla : le type de menu est le meme.
 */
public class DefenseAnvilMenu extends AnvilMenu {

    private final ContainerLevelAccess where;

    public DefenseAnvilMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(id, inventory, access);
        this.where = access;
    }

    @Override
    public boolean stillValid(Player player) {
        return where.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof DefenseAnvilBlock
                && player.canInteractWithBlock(pos, 4.0d), true);
    }
}
