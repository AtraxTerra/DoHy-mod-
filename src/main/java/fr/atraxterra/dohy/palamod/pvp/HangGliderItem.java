package fr.atraxterra.dohy.palamod.pvp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Le deltaplane ({@code ItemHangGlider}) : clic droit pour l'ouvrir ou le
 * replier ; ouvert et tenu en main, il freine la chute et pousse vers l'avant.
 *
 * <p>A chaque tick, tant que le joueur tombe ({@code motionY < 0}) sans toucher
 * le sol : la vitesse verticale est multipliee par 0,6 et la vitesse
 * horizontale augmente de 0,03 dans le sens du regard ; accroupi, 0,8 et 0,1 —
 * on va plus vite et on descend plus vite. La chute est annulee tant qu'il est
 * ouvert. Il se replie seul en touchant le sol, et aussi, lache, a
 * l'atterrissage.
 *
 * <p><b>Ou tourne la physique.</b> Le mouvement d'un joueur est calcule par son
 * client : c'est la que la vitesse doit etre modifiee, faute de quoi le
 * serveur ecrirait dans une copie que personne ne lit. L'original le faisait
 * dans {@code onUpdate}, qui tournait des deux cotes ; ici le client applique
 * la physique ({@link #glide}, appelee par {@link PvpEvents}) et le serveur
 * garde ce qui lui revient : l'etat ouvert et l'annulation des degats de chute.
 *
 * <p>Pas de durabilite, comme dans l'original. Le modele 3D tenu au-dessus de
 * la tete n'est pas porte : l'objet garde son icone.
 */
public class HangGliderItem extends Item {

    public HangGliderItem(Properties properties) {
        super(properties);
    }

    public static boolean isOpen(ItemStack stack) {
        return stack.getOrDefault(PalaPvp.GLIDER_OPEN.get(), false);
    }

    private static void close(ItemStack stack) {
        stack.remove(PalaPvp.GLIDER_OPEN.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            if (isOpen(stack)) {
                close(stack);
            } else {
                stack.set(PalaPvp.GLIDER_OPEN.get(), true);
            }
            player.resetFallDistance();
        }
        return InteractionResult.SUCCESS;
    }

    /** Cote serveur : l'atterrissage replie, et la chute ne compte pas. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player) || !isOpen(stack)) {
            return;
        }
        boolean held = slot == EquipmentSlot.MAINHAND || player.getMainHandItem() == stack;
        if (held && inRange(player)) {
            if (player.onGround()) {
                close(stack);
            }
            player.resetFallDistance();
        } else if (player.onGround()) {
            close(stack);
            player.resetFallDistance();
        }
    }

    /** {@code posY > 0 && posY < 256} : la hauteur du monde de 1.7.10, ici ses limites. */
    private static boolean inRange(Player player) {
        return player.getY() > player.level().getMinY() && player.getY() < player.level().getMaxY();
    }

    /**
     * Cote client, sur le joueur local, avant son deplacement du tick.
     *
     * <p>Le vol en creatif est ignore : l'original n'avait pas a s'en soucier,
     * mais descendre en volant accroupi declencherait le planeur.
     */
    static void glide(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HangGliderItem) || !isOpen(held) || !inRange(player)
                || player.onGround() || player.getAbilities().flying) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y >= 0.0d) {
            return;
        }
        boolean sneaking = player.isShiftKeyDown();
        double horizontal = sneaking ? 0.1d : 0.03d;
        double vertical = sneaking ? 0.8d : 0.6d;
        double angle = Math.toRadians(player.getYHeadRot() + 90.0f);
        player.setDeltaMovement(motion.x + Math.cos(angle) * horizontal, motion.y * vertical,
                motion.z + Math.sin(angle) * horizontal);
        player.resetFallDistance();
    }

    /** {@code hasEffect} : ouvert, il brille. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return isOpen(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(isOpen(stack)
                ? "tooltip.palamod.hang_glider.open" : "tooltip.palamod.hang_glider.closed"));
    }
}
