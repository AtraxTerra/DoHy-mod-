package fr.atraxterra.dohy.palamod.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Les apparences des monstres de donjon : quel modele de Paladium pour quel
 * monstre et quel boss, et leurs sons.
 *
 * <p>La table est {@code data/palarpg/rpg_skins.json}, ecrite par
 * {@code tools/rpg_entities.py} depuis {@code config/rpgentities} de Paladium.
 * Le serveur pose l'apparence ({@link PalaDungeon#SKIN}) et joue les sons ; le
 * dessin est cote client ({@code RpgClient}).
 *
 * <p><b>Les sons.</b> Un monstre vanilla joue ses cris par position, sans dire
 * qui crie : un son de cri, de blessure ou de mort joue exactement a la
 * position d'un monstre habille est remplace par celui de son modele ; ses pas
 * sont tus (le son de marche de Paladium est une boucle). L'attaque joue quand
 * le monstre touche, l'apparition a l'apparition.
 */
@EventBusSubscriber
public final class RpgSkins {

    private static final Identifier LOCATION = Identifier.fromNamespaceAndPath("palarpg", "rpg_skins.json");

    /** Au-dela, un boss est ramene a cette taille : la salle fait 33 blocs de cote et 14 de haut. */
    private static final float MAX_HEIGHT = 6.5f;
    private static final float MAX_WIDTH = 11.0f;

    private record Model(List<String> variants, List<String> sounds, float width, float height) {
    }

    private static Map<String, Map<String, String>> mobs = Map.of();
    private static Map<String, String> bosses = Map.of();
    private static Map<String, Model> models = Map.of();

    private RpgSkins() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static void load(MinecraftServer server) {
        var resource = server.getResourceManager().getResource(LOCATION);
        if (resource.isEmpty()) {
            DoHy.LOGGER.warn("Apparences des monstres de donjon introuvables : data/palarpg/rpg_skins.json");
            return;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject root = GsonHelper.parse(reader);
            Map<String, Map<String, String>> mobTable = new HashMap<>();
            for (var dungeon : GsonHelper.getAsJsonObject(root, "mobs").entrySet()) {
                Map<String, String> kinds = new HashMap<>();
                for (var kind : dungeon.getValue().getAsJsonObject().entrySet()) {
                    kinds.put(kind.getKey(), kind.getValue().getAsString());
                }
                mobTable.put(dungeon.getKey(), kinds);
            }
            Map<String, String> bossTable = new HashMap<>();
            for (var boss : GsonHelper.getAsJsonObject(root, "bosses").entrySet()) {
                bossTable.put(boss.getKey(), boss.getValue().getAsString());
            }
            Map<String, Model> modelTable = new HashMap<>();
            for (var model : GsonHelper.getAsJsonObject(root, "models").entrySet()) {
                JsonObject body = model.getValue().getAsJsonObject();
                var size = GsonHelper.getAsJsonArray(body, "size");
                modelTable.put(model.getKey(), new Model(strings(body, "variants"), strings(body, "sounds"),
                        size.get(0).getAsFloat(), size.get(1).getAsFloat()));
            }
            mobs = mobTable;
            bosses = bossTable;
            models = modelTable;
            DoHy.LOGGER.info("Apparences des monstres de donjon : {} modeles, {} boss", models.size(), bosses.size());
        } catch (Exception error) {
            DoHy.LOGGER.error("Apparences des monstres de donjon illisibles", error);
        }
    }

    private static List<String> strings(JsonObject body, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(body, key)) {
            out.add(element.getAsString());
        }
        return out;
    }

    /** Habille un monstre d'une vague. {@code split} : un des petits noyes nes d'un guerrier de Manelios. */
    public static void dress(Mob mob, Dungeon dungeon, MobKind kind, boolean split, Band band) {
        Map<String, String> kinds = mobs.get(dungeon.name().toLowerCase(Locale.ROOT));
        if (kinds == null) {
            return;
        }
        String model = kinds.get(split ? "split" : kind.name().toLowerCase(Locale.ROOT));
        if (model == null && split) {
            model = kinds.get("melee");
        }
        // Le colosse est grossi par son attribut d'echelle : son modele aussi.
        set(mob, model, band, (float) mob.getAttributeValue(Attributes.SCALE));
    }

    /**
     * Habille un mini-boss ou un boss, d'apres son nom. Le modele garde sa
     * taille de Paladium, bornee a ce que la salle contient ; la boite de
     * collision reste celle du monstre vanilla, plus petite.
     */
    public static void dressBoss(Mob mob, String name, Band band) {
        String model = bosses.get(name);
        Model info = model == null ? null : models.get(model);
        if (info != null) {
            set(mob, model, band, Math.min(1.0f, Math.min(MAX_HEIGHT / info.height(), MAX_WIDTH / info.width())));
        }
    }

    private static void set(Mob mob, String model, Band band, float scale) {
        Model info = model == null ? null : models.get(model);
        if (info == null) {
            return;
        }
        String variant = band.name().toLowerCase(Locale.ROOT);
        if (!info.variants().contains(variant)) {
            variant = "default";
        }
        mob.setData(PalaDungeon.SKIN.get(), new RpgSkin(model, variant, scale));
    }

    public static RpgSkin skin(Entity entity) {
        return entity.getExistingDataOrNull(PalaDungeon.SKIN.get()) instanceof RpgSkin skin ? skin : RpgSkin.NONE;
    }

    // -- Sons ---------------------------------------------------------------------

    private static final Pattern IDLE = Pattern.compile("idle\\d*");
    private static final Pattern HURT = Pattern.compile("hurt|hit");
    private static final Pattern DEATH = Pattern.compile("death");
    private static final Pattern ATTACK = Pattern.compile("(atk|attack)\\d*");
    private static final Pattern SPAWN = Pattern.compile("spawn");

    /** Un son du modele dont le nom repond au motif, au hasard ; {@code null} s'il n'y en a pas. */
    private static Holder<SoundEvent> sound(String model, RandomSource random, Pattern pattern) {
        Model info = models.get(model);
        if (info == null) {
            return null;
        }
        List<String> matches = new ArrayList<>();
        for (String name : info.sounds()) {
            if (pattern.matcher(name).matches()) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        String name = matches.get(random.nextInt(matches.size()));
        return Holder.direct(SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("palarpg", "entity." + model + "." + name)));
    }

    private static void play(Mob mob, Pattern pattern) {
        RpgSkin skin = skin(mob);
        if (skin.isNone() || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Holder<SoundEvent> sound = sound(skin.model(), mob.getRandom(), pattern);
        if (sound != null) {
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.0f,
                    0.95f + mob.getRandom().nextFloat() * 0.1f);
        }
    }

    /** Le son d'apparition du modele, s'il en a un. */
    public static void playSpawn(Mob mob) {
        play(mob, SPAWN);
    }

    /**
     * Les cris d'un monstre habille : remplaces par ceux de son modele. On
     * retrouve le monstre a sa position exacte (le jeu passe ses coordonnees).
     */
    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        if (models.isEmpty() || event.getSound() == null || !(event.getLevel() instanceof ServerLevel level)
                || !DungeonManager.levelIs(level)) {
            return;
        }
        String path = event.getSound().value().location().getPath();
        if (!path.startsWith("entity.")) {
            return;
        }
        Pattern role;
        if (path.endsWith(".ambient")) {
            role = IDLE;
        } else if (path.endsWith(".hurt")) {
            role = HURT;
        } else if (path.endsWith(".death")) {
            role = DEATH;
        } else if (path.endsWith(".step")) {
            role = null;
        } else {
            return;
        }
        Vec3 position = event.getPosition();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(position, position).inflate(0.01d),
                candidate -> !skin(candidate).isNone())) {
            if (mob.position().distanceToSqr(position) > 1.0e-6d) {
                continue;
            }
            if (role == null) {
                event.setCanceled(true);
                return;
            }
            Holder<SoundEvent> sound = sound(skin(mob).model(), mob.getRandom(), role);
            if (sound != null) {
                event.setSound(sound);
            }
            return;
        }
    }

    /** L'attaque : le son quand le coup (ou le projectile) d'un monstre habille porte. */
    @SubscribeEvent
    public static void onHit(LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        LivingEntity target = event.getEntity();
        if (source instanceof Mob mob && mob != target && !skin(mob).isNone()) {
            play(mob, ATTACK);
        }
    }
}
