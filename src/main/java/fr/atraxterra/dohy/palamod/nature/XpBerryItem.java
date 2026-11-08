package fr.atraxterra.dohy.palamod.nature;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Baie d'XP ({@code BaseItemBerry}) : un clic droit la change en orbe
 * d'experience, {@code nextInt(2) + 2} points — 2 ou 3. La baie compressee
 * (neuf baies) en vaut neuf tirages, de 18 a 27.
 */
public class XpBerryItem extends Item {

    private final int berries;

    public XpBerryItem(int berries, Properties properties) {
        super(properties);
        this.berries = berries;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            int experience = 0;
            for (int berry = 0; berry < berries; berry++) {
                experience += player.getRandom().nextInt(2) + 2;
            }
            ExperienceOrb.award(server, new Vec3(player.getX(), player.getY() + 1.0d, player.getZ()), experience);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
