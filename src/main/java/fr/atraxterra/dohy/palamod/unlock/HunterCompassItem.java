package fr.atraxterra.dohy.palamod.unlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * La boussole de chasseur ({@code ItemHunterCompass}, Chasseur 100), en cinq
 * paliers : amethyste, titane, paladium, endium et legendaire.
 *
 * <p>Ce que dit le code : une boussole a 32 images par palier, qui pointe vers
 * des coordonnees ({@code x}, {@code y}, {@code z} dans l'objet). Ces
 * coordonnees, c'est le serveur de Paladium qui les envoyait (le canal
 * {@code huntercompass}, « palier_x_y_z ») : la cible n'est pas dans le mod.
 *
 * <p><b>Adaptation.</b> Ici, la boussole suit une <b>proie</b> : accroupi, un
 * clic droit sur une creature en fait l'espece suivie (sans espece choisie,
 * le monstre le plus proche). Chaque seconde, l'aiguille pointe la plus
 * proche de cette espece dans la portee du palier, et la barre d'action dit
 * a combien elle est. Le palier ne change que la portee.
 */
public class HunterCompassItem extends Item {

    /** Les paliers, et la portee de chacun (en blocs). */
    public enum Tier {
        AMETHYST("amethyst", 48),
        TITANE("titane", 96),
        PALADIUM("paladium", 192),
        ENDIUM("endium", 384),
        LEGENDARY("legend", 768);

        private final String texture;
        private final int range;

        Tier(String texture, int range) {
            this.texture = texture;
            this.range = range;
        }

        /** Le prefixe des 32 images ({@code compass_<palier>NN}). */
        public String texture() {
            return texture;
        }

        public int range() {
            return range;
        }
    }

    private final Tier tier;

    public HunterCompassItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    /** Accroupi, sur une creature : elle devient la proie suivie. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!player.isShiftKeyDown() || target instanceof Player) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            ItemStack held = player.getItemInHand(hand);
            held.set(PalaUnlocks.PREY.get(), BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
            server.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                    "item.palamod.hunter_compass.prey", target.getType().getDescription())
                    .withStyle(ChatFormatting.GOLD)));
        }
        return InteractionResult.SUCCESS;
    }

    /** Accroupi, dans le vide : la boussole revient aux monstres. */
    @Override
    public InteractionResult use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        held.remove(PalaUnlocks.PREY.get());
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                    "item.palamod.hunter_compass.monsters").withStyle(ChatFormatting.GOLD)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        boolean held = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
        Identifier prey = stack.get(PalaUnlocks.PREY.get());
        EntityType<?> type = prey == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(prey);
        AABB box = player.getBoundingBox().inflate(tier.range());
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, box, living -> living != player
                        && living.isAlive() && (type == null ? living instanceof Enemy : living.getType() == type)
                        && living.distanceToSqr(player) <= (double) tier.range() * tier.range())
                .stream().min(Comparator.comparingDouble(living -> living.distanceToSqr(player))).orElse(null);
        if (target == null) {
            if (stack.has(DataComponents.LODESTONE_TRACKER)) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            if (held) {
                player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                        "item.palamod.hunter_compass.none", tier.range()).withStyle(ChatFormatting.GRAY)));
            }
            return;
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(level.dimension(), target.blockPosition())), false));
        if (held) {
            int distance = (int) Math.round(Math.sqrt(target.distanceToSqr(player)));
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable(
                    "item.palamod.hunter_compass.target", target.getType().getDescription(), distance)
                    .withStyle(ChatFormatting.AQUA)));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Identifier prey = stack.get(PalaUnlocks.PREY.get());
        EntityType<?> type = prey == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(prey);
        tooltip.accept(type == null
                ? Component.translatable("item.palamod.hunter_compass.tooltip_monsters").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.palamod.hunter_compass.tooltip_prey", type.getDescription())
                        .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.palamod.hunter_compass.range", tier.range())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.palamod.hunter_compass.howto").withStyle(ChatFormatting.DARK_GRAY));
    }
}
