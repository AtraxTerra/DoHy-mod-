package fr.atraxterra.dohy.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le texte d'une etiquette flottante.
 *
 * <p>Le texte est une entite d'affichage, tournee vers qui la regarde. Elle
 * n'est jamais ecrite sur disque : la block entity la refait a son premier
 * tick et verifie chaque seconde qu'elle est toujours la ({@link SpawnEvents}
 * refuse celles qui reviendraient d'une sauvegarde). Un spawn reconstruit ou
 * un monde recharge ne laissent donc pas de texte en double.
 */
public class HologramBlockEntity extends BlockEntity {

    /** La marque des textes flottants : jamais recharges depuis le disque. */
    static final String TAG = "dohy.hologram";
    private static final int CHECK = 20;

    private String text = "";
    private int color = 0xFFFFFF;
    private float scale = 1.0f;
    private Entity display;
    private int ticks;

    public HologramBlockEntity(BlockPos pos, BlockState state) {
        super(DoHySpawn.HOLOGRAM_TYPE.get(), pos, state);
    }

    /** Regle le texte ; l'affichage se refait au tick suivant. */
    public void set(String text, int color, float scale) {
        this.text = text;
        this.color = color;
        this.scale = scale;
        drop();
        setChanged();
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server) || ticks++ % CHECK != 0 || text.isEmpty()) {
            return;
        }
        if (display != null && display.isAlive()) {
            return;
        }
        Display.TextDisplay created = EntityTypes.TEXT_DISPLAY.create(server, EntitySpawnReason.COMMAND);
        if (created == null) {
            return;
        }
        // Le texte, son orientation et son echelle n'ont pas de mutateur
        // public : ils passent par les donnees de l'entite.
        CompoundTag data = new CompoundTag();
        Component line = Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true));
        ComponentSerialization.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                line).result().ifPresent(tag -> data.put("text", tag));
        data.putString("billboard", "center");
        data.putInt("background", 0x90101014);
        data.putBoolean("shadow", true);
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0f, 0.0f, 0.0f));
        transformation.put("left_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
        transformation.put("scale", floats(scale, scale, scale));
        transformation.put("right_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
        data.put("transformation", transformation);
        created.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), data));
        created.snapTo(worldPosition.getX() + 0.5d, worldPosition.getY() + 0.25d, worldPosition.getZ() + 0.5d,
                0.0f, 0.0f);
        created.addTag(TAG);
        server.addFreshEntity(created);
        display = created;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private void drop() {
        if (display != null) {
            display.discard();
            display = null;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        drop();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("text", text);
        output.putInt("color", color);
        output.putInt("scale", Math.round(scale * 100.0f));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        text = input.getStringOr("text", "");
        color = input.getIntOr("color", 0xFFFFFF);
        scale = input.getIntOr("scale", 100) / 100.0f;
    }
}
