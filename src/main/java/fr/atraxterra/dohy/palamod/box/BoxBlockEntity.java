package fr.atraxterra.dohy.palamod.box;

import fr.atraxterra.dohy.boost.Boosters;
import fr.atraxterra.dohy.boost.Crates;
import fr.atraxterra.dohy.palamod.dungeon.PalaDungeon;
import fr.atraxterra.dohy.palamod.dungeon.RpgSkin;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Ce qui anime une box posee.
 *
 * <p><b>Le coffre.</b> Une creature immobile, muette et invulnerable, au
 * centre du bloc, qui porte le modele de la box ({@link RpgSkin}) : le rendu
 * des monstres de donjon la dessine et l'anime. Elle n'est jamais ecrite sur
 * disque — la block entity la refait a son premier tick — donc une box cassee
 * ou un monde recharge ne laissent pas de coffre fantome.
 *
 * <p><b>L'ouverture.</b> Le tirage est celui des caisses ({@link Crates}) :
 * la cle est prise et le lot donne tout de suite, comme a la roulette. La box
 * joue ensuite l'animation de la classe du lot (commune, rare, epique,
 * legendaire : 7 a 13 secondes, de plus en plus de lumiere), avec sa
 * bande-son. Au repere {@code open} de l'animation, le lot apparait au-dessus
 * du coffre et le message du gain tombe ; au repere {@code close}, il
 * disparait. Rien de cela n'est sauvegarde : le lot est deja dans
 * l'inventaire.
 */
public class BoxBlockEntity extends BlockEntity {

    /** Le coffre et le lot montre : jamais recharges depuis le disque ({@link BoxEvents}). */
    static final String TAG = "palamod.box";
    /** Toutes les secondes : le coffre est-il toujours la ? */
    private static final int CHECK = 20;
    /** Le lot flotte au-dessus du couvercle ouvert. */
    private static final double PRIZE_HEIGHT = 1.55d;
    private static final float PRIZE_SCALE = 0.55f;

    private Mob host;
    private Display.ItemDisplay prize;
    private int ticks;
    /** Ticks ecoules depuis le debut de l'ouverture ; -1 au repos. */
    private int opening = -1;
    private BoxTimings.Clip clip;
    private Crates.Draw draw;
    /** Change a chaque ouverture : deux lots de meme classe rejouent l'animation. */
    private int nonce;

    public BoxBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBoxes.TYPE.get(), pos, state);
    }

    private BoxKind kind() {
        return getBlockState().getBlock() instanceof BoxBlock box ? box.kind() : BoxKind.AMETHYST;
    }

    // -- Le tick -----------------------------------------------------------------

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (ticks++ % CHECK == 0) {
            keepHost(server);
        }
        if (opening < 0) {
            return;
        }
        opening++;
        if (opening == clip.open()) {
            showPrize(server);
        }
        if (opening == clip.close()) {
            dropPrize();
        }
        if (opening >= clip.length()) {
            dropPrize();
            opening = -1;
            draw = null;
        }
    }

    // -- Le coffre ---------------------------------------------------------------

    private float yaw() {
        return getBlockState().hasProperty(BoxBlock.FACING)
                ? getBlockState().getValue(BoxBlock.FACING).toYRot() : 0.0f;
    }

    private void keepHost(ServerLevel server) {
        double x = worldPosition.getX() + 0.5d;
        double y = worldPosition.getY();
        double z = worldPosition.getZ() + 0.5d;
        float yaw = yaw();
        if (host == null || !host.isAlive()) {
            host = EntityTypes.PIG.create(server, EntitySpawnReason.MOB_SUMMONED);
            if (host == null) {
                return;
            }
            host.snapTo(x, y, z, yaw, 0.0f);
            host.setNoAi(true);
            host.setNoGravity(true);
            host.setSilent(true);
            host.setInvulnerable(true);
            host.setPersistenceRequired();
            host.addTag(TAG);
            BoxKind kind = kind();
            host.setData(PalaDungeon.SKIN.get(), new RpgSkin(kind.model(), "default", kind.scale(), pose()));
            server.addFreshEntity(host);
        } else if (host.distanceToSqr(x, y, z) > 1.0e-4d) {
            // Un piston, une commande : le coffre revient dans son bloc.
            host.snapTo(x, y, z, yaw, 0.0f);
        }
        host.setYBodyRot(yaw);
        host.setYHeadRot(yaw);
    }

    /** L'ouverture en cours puis l'attente, ou l'attente seule. */
    private String pose() {
        if (opening < 0 || draw == null) {
            return "idle";
        }
        return "n" + nonce + ">fast_" + Crates.RARITIES.get(draw.rarity()) + ">idle";
    }

    private void dropHost() {
        if (host != null) {
            host.discard();
            host = null;
        }
    }

    boolean owns(Entity entity) {
        return entity == host || entity == prize;
    }

    // -- L'ouverture ---------------------------------------------------------------

    void use(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BoxKind kind = kind();
        String tier = kind.tier();
        if (player.isShiftKeyDown()) {
            String refusal = Crates.buyKey(player, tier);
            say(player, refusal != null ? Component.literal(refusal).withStyle(ChatFormatting.RED)
                    : Component.literal(Crates.keyName(tier) + " achetee : " + Boosters.data(player).keys(tier)
                    + " en poche.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (opening >= 0) {
            say(player, Component.literal("La box est deja en train de s'ouvrir.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (Boosters.data(player).keys(tier) <= 0) {
            long price = Crates.keyPrice(tier);
            // Dans le tchat : la consigne est trop longue pour la barre d'action.
            player.sendSystemMessage(Component.literal(price < 0 ? "Aucune caisse pour cette box."
                    : "Il te faut une " + Crates.keyName(tier) + ". Accroupi, main vide, clic droit sur la box : "
                    + "en acheter une (" + MoneyFormat.exact(price) + ").").withStyle(ChatFormatting.RED));
            return;
        }
        Crates.Pull pull = Crates.openAtBox(player, tier,
                rarity -> BoxTimings.of(kind, Crates.RARITIES.get(rarity)).open());
        if (pull.refusal() != null) {
            say(player, Component.literal(pull.refusal()).withStyle(ChatFormatting.RED));
            return;
        }
        draw = pull.draw();
        String rarity = Crates.RARITIES.get(draw.rarity());
        clip = BoxTimings.of(kind, rarity);
        opening = 0;
        nonce++;
        keepHost(server);
        if (host != null) {
            host.setData(PalaDungeon.SKIN.get(), new RpgSkin(kind.model(), "default", kind.scale(), pose()));
        }
        server.playSound(null, worldPosition.getX() + 0.5d, worldPosition.getY() + 0.5d,
                worldPosition.getZ() + 0.5d, Holder.direct(SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath("palarpg", kind.sound(rarity)))),
                SoundSource.BLOCKS, 1.0f, 1.0f);
        say(player, Component.literal(Boosters.data(player).keys(tier) + " cle(s) restante(s).")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void say(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    // -- Le lot montre -------------------------------------------------------------

    private void showPrize(ServerLevel server) {
        dropPrize();
        if (draw == null) {
            return;
        }
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(server, EntitySpawnReason.MOB_SUMMONED);
        if (display == null) {
            return;
        }
        // Le lot fait face a qui le regarde, a mi-taille : ces reglages n'ont
        // pas de mutateur public, ils passent par les donnees de l'entite.
        CompoundTag data = new CompoundTag();
        data.putString("billboard", "vertical");
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0f, 0.0f, 0.0f));
        transformation.put("left_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
        transformation.put("scale", floats(PRIZE_SCALE, PRIZE_SCALE, PRIZE_SCALE));
        transformation.put("right_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
        data.put("transformation", transformation);
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), data));
        display.snapTo(worldPosition.getX() + 0.5d, worldPosition.getY() + PRIZE_HEIGHT,
                worldPosition.getZ() + 0.5d, yaw(), 0.0f);
        ItemStack icon = draw.icon().isEmpty() ? new ItemStack(Items.EMERALD) : draw.icon().copyWithCount(1);
        display.getSlot(0).set(icon);
        display.setCustomName(Component.empty().append(draw.name()).withStyle(Crates.rarityColor(draw.rarity())));
        display.setCustomNameVisible(true);
        display.addTag(TAG);
        server.addFreshEntity(display);
        prize = display;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private void dropPrize() {
        if (prize != null) {
            prize.discard();
            prize = null;
        }
    }

    /** Casse ou dechargee : ni le coffre ni le lot montre ne lui survivent. */
    @Override
    public void setRemoved() {
        dropHost();
        dropPrize();
        opening = -1;
        draw = null;
        super.setRemoved();
    }
}
