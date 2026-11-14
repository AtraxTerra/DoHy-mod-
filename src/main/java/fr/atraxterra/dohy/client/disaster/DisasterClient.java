package fr.atraxterra.dohy.client.disaster;

import fr.atraxterra.dohy.earth.disaster.DisasterNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.List;

/**
 * Les catastrophes cote client : la liste de celles en cours (pour l'atlas et
 * l'alerte sous la minimap), la camera qui tremble pendant un seisme, le
 * brouillard de cendres pres d'un volcan en eruption, et le vent d'un ouragan
 * qui pousse le joueur quand il est dehors.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class DisasterClient {

    private static volatile List<DisasterNet.Entry> entries = List.of();
    private static DisasterNet.Feel target = DisasterNet.Feel.NONE;
    private static float shake;
    private static float ash;
    private static long clock;

    private DisasterClient() {
    }

    public static List<DisasterNet.Entry> entries() {
        return entries;
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(DisasterNet.Sync.TYPE, (payload, context) -> entries = List.copyOf(payload.entries()));
        event.register(DisasterNet.Feel.TYPE, (payload, context) -> target = payload);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        entries = List.of();
        target = DisasterNet.Feel.NONE;
        shake = 0.0f;
        ash = 0.0f;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        clock++;
        shake += (target.shake() - shake) * 0.25f;
        ash += (target.ash() - ash) * 0.05f;
        if (Math.abs(shake) < 0.002f) {
            shake = 0.0f;
        }
        if (Math.abs(ash) < 0.002f) {
            ash = 0.0f;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || target.windX() == 0.0f && target.windZ() == 0.0f) {
            return;
        }
        if (player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
            return;
        }
        double factor = player.isShiftKeyDown() ? 0.4d : 1.0d;
        if (player.isInWater()) {
            factor *= 0.5d;
        } else if (!player.onGround()) {
            // en l'air, rien ne freine : un saut ne doit pas devenir un envol
            factor *= 0.35d;
        }
        player.addDeltaMovement(new Vec3(target.windX() * factor, 0.0d, target.windZ() * factor));
    }

    /** La camera tremble : un bruit de quelques frequences, jusqu'a un degre et demi au plus fort. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.0f) {
            return;
        }
        double t = clock + event.getPartialTick();
        float yaw = (float) (Math.sin(t * 1.7d) * 0.6d + Math.sin(t * 3.1d + 1.3d) * 0.4d);
        float pitch = (float) (Math.sin(t * 2.3d + 0.7d) * 0.6d + Math.sin(t * 4.3d + 2.1d) * 0.4d);
        float roll = (float) (Math.sin(t * 1.3d + 2.9d) * 0.7d + Math.sin(t * 3.7d) * 0.3d);
        event.setYaw(event.getYaw() + yaw * shake * 1.5f);
        event.setPitch(event.getPitch() + pitch * shake * 1.1f);
        event.setRoll(event.getRoll() + roll * shake * 1.3f);
    }

    /** La cendre rapproche le brouillard : a pleine cendre, on ne voit plus qu'a vingt blocs. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (ash <= 0.0f || event.getType() != FogType.ATMOSPHERIC && event.getType() != FogType.NONE) {
            return;
        }
        float far = event.getFarPlaneDistance();
        float wanted = far + (20.0f - far) * Math.min(1.0f, ash);
        if (wanted < far) {
            event.setFarPlaneDistance(wanted);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), wanted * 0.1f));
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (ash <= 0.0f) {
            return;
        }
        float a = Math.min(1.0f, ash);
        event.setRed(event.getRed() + (0.38f - event.getRed()) * a);
        event.setGreen(event.getGreen() + (0.34f - event.getGreen()) * a);
        event.setBlue(event.getBlue() + (0.31f - event.getBlue()) * a);
    }

    /** L'alerte sous la minimap : la catastrophe ou se trouve le joueur, ou celle qui le menace. */
    public record Alert(String text, int color) {
    }

    public static Alert alert(double x, double z) {
        Alert best = null;
        double bestDistance = Double.MAX_VALUE;
        for (DisasterNet.Entry entry : entries) {
            double distance = Math.hypot(entry.x() - x, entry.z() - z);
            double reach = entry.kind() == DisasterNet.HURRICANE ? entry.radius() + 400 : entry.radius();
            if (distance > reach || distance >= bestDistance) {
                continue;
            }
            String text = switch (entry.kind()) {
                case DisasterNet.QUAKE -> "SEISME M" + (entry.level() / 10) + "," + (entry.level() % 10);
                case DisasterNet.ERUPTION -> entry.phase() == 0 ? "ERUPTION DANS " + clock(entry.seconds())
                        : entry.phase() == 1 ? "ERUPTION EN COURS" : "LAVE QUI REFROIDIT";
                case DisasterNet.HURRICANE -> distance <= entry.radius()
                        ? entry.name().toUpperCase(java.util.Locale.ROOT) + " CAT. " + entry.level()
                        : entry.name().toUpperCase(java.util.Locale.ROOT) + " A " + (int) distance + " M";
                default -> "MOUSSON";
            };
            int color = switch (entry.kind()) {
                case DisasterNet.QUAKE -> 0xFFFF9A2E;
                case DisasterNet.ERUPTION -> 0xFFFF4A2A;
                case DisasterNet.HURRICANE -> 0xFFD07CFF;
                default -> 0xFF6FB6FF;
            };
            best = new Alert(text, color);
            bestDistance = distance;
        }
        return best;
    }

    /** « 4:12 ». */
    private static String clock(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    /** La couleur d'une famille sur l'atlas. */
    public static int color(int kind) {
        return switch (kind) {
            case DisasterNet.QUAKE -> 0xFFFF8A00;
            case DisasterNet.ERUPTION -> 0xFFE5302A;
            case DisasterNet.HURRICANE -> 0xFFB04CFF;
            default -> 0xFF3D8BFF;
        };
    }
}
