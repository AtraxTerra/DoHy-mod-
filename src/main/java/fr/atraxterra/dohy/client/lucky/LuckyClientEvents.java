package fr.atraxterra.dohy.client.lucky;

import fr.atraxterra.dohy.palamod.lucky.PalaLucky;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Les effets des lucky blocks qui se voient ou se sentent chez le client
 * ({@code EventsManager} de PalaMod, ses parties cote client).
 *
 * <ul>
 *   <li>Walk in Music : des notes sous les pieds ; Tout feu tout flamme : des
 *       flammes autour du corps ;</li>
 *   <li>Bunny Life : on saute tout seul, une fois sur dix a chaque tic au sol ;</li>
 *   <li>Sonic : le saut ne repond plus ;</li>
 *   <li>Sol glace : le sol glisse ({@code motion * 1,47} a chaque tic au sol) ;</li>
 *   <li>Prendre du recul : la camera ne quitte pas la vue de dos ;</li>
 *   <li>Ou donner de la tete : la tete du joueur disparait ; Fantome : il ne
 *       reste qu'elle ; T'as un probleme nan ? : le corps disparait ;</li>
 *   <li>Steve Jobs : le skin par defaut ; Le monde a l'envers : le joueur se voit
 *       la tete en bas ;</li>
 *   <li>Inversion : les touches de deplacement s'inversent.</li>
 * </ul>
 *
 * <p>Le client ne connait que ses propres effets : la tete d'un autre joueur ne
 * disparait pas a l'ecran. C'est un mod solo.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class LuckyClientEvents {

    /** {@code motionX *= 1.47} au sol : la glace de PalaMod. */
    private static final double ICE = 1.47d;
    /** Ce que Fantome cache ({@code isHidden} sur le corps, les bras et les jambes). */
    private static final List<String> GHOST_PARTS = List.of("body", "left_arm", "right_arm", "left_leg",
            "right_leg");
    private static final List<String> COW_HIDDEN = List.of("head", "body", "left_arm", "right_arm", "left_leg",
            "right_leg");
    /** Le vecteur de marche de {@link ClientInput}, protege : retrouve une fois, par son type. */
    private static Field moveVector;

    private LuckyClientEvents() {
    }

    /** « Ca fait flipper » : la camera a l'envers ({@code PLuckyBlock.FLIP}). */
    @SubscribeEvent
    public static void onCamera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.FLIP)) {
            event.setRoll(event.getRoll() + 180.0f);
        }
    }

    /** La langue d'avant le cours d'anglais, rendue a la fin de l'effet. */
    private static net.minecraft.locale.Language beforeEnglish;
    /** L'anglais charge pour l'effet : une autre langue en place veut dire un rechargement des ressources. */
    private static net.minecraft.locale.Language english;

    /**
     * « Test d'anglais » rate ({@code SCTimedLanguagePacket}) : le jeu passe en anglais le temps de
     * l'effet, sans toucher aux options du joueur.
     */
    @SubscribeEvent
    public static void onEnglish(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.player != null
                && minecraft.player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.ENGLISH);
        net.minecraft.locale.Language current = net.minecraft.locale.Language.getInstance();
        if (active && current != english) {
            beforeEnglish = current;
            english = net.minecraft.client.resources.language.ClientLanguage.loadFrom(minecraft.getResourceManager(),
                    List.of("en_us"), false);
            net.minecraft.locale.Language.inject(english);
        } else if (!active && english != null) {
            if (current == english) {
                net.minecraft.locale.Language.inject(beforeEnglish);
            }
            beforeEnglish = null;
            english = null;
        }
    }

    /** L'inventaire etait-il ouvert au tic precedent ? */
    private static boolean inventoryWasOpen;

    /**
     * L'inventaire vient de s'ouvrir : « Spooky Spook » ({@code ClientProxy.spooky}) fait hurler,
     * « Inventaire explosif » previent le serveur, qui fait tomber une dynamite.
     */
    @SubscribeEvent
    public static void onInventory(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen screen = minecraft.screen;
        boolean open = screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                || screen instanceof fr.atraxterra.dohy.client.ui.screen.KitInventoryScreen;
        if (open && !inventoryWasOpen && minecraft.player != null) {
            if (minecraft.player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.SPOOKY)) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.GHAST_SCREAM, 0.8f, 1.0f));
            }
            if (minecraft.player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.EXPLOSIVE)) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                        fr.atraxterra.dohy.palamod.lucky.LuckyPayloads.InventoryOpened.INSTANCE);
            }
        }
        inventoryWasOpen = open;
    }

    /** « Cinema muet » : plus aucun son ({@code PLuckyBlock.CINEMA_MUET}). */
    @SubscribeEvent
    public static void onSound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.SILENT_MOVIE)) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        RandomSource random = player.getRandom();
        if (player.hasEffect(PalaLucky.WALK_IN_MUSIC) && player.onGround()) {
            minecraft.level.addParticle(ParticleTypes.NOTE, player.getX(), player.getY() + 0.05d, player.getZ(),
                    random.nextInt(25) / 24.0d, 0.0d, 0.0d);
        }
        if (player.hasEffect(PalaLucky.FIRE_MAN)) {
            // En premiere personne, les flammes restent sous les yeux : devant la camera, elles cachent tout.
            float height = minecraft.options.getCameraType() == CameraType.FIRST_PERSON ? 1.1f : 3.0f;
            minecraft.level.addParticle(ParticleTypes.FLAME, player.getX() + random.nextGaussian() * 0.3d,
                    player.getY() + random.nextFloat() * height,
                    player.getZ() + random.nextGaussian() * 0.3d, 0.0d, 0.01d, 0.0d);
        }
        if (player.hasEffect(PalaLucky.THIRD_PERSON) && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    /** Bunny Life saute pour le joueur ; Sonic l'en empeche. */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        ClientInput input = event.getInput();
        if (player.hasEffect(PalaLucky.INVERSION)) {
            invert(input);
        }
        if (player.hasEffect(fr.atraxterra.dohy.palamod.lucky.SeasonEffects.NO_FORWARD)) {
            noForward(input);
        }
        if (player.hasEffect(PalaLucky.NO_JUMP)) {
            Input keys = input.keyPresses;
            if (keys.jump()) {
                input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), false,
                        keys.shift(), keys.sprint());
            }
            return;
        }
        if (player.hasEffect(PalaLucky.BUNNY_LIFE) && player.onGround() && player.getRandom().nextInt(10) == 5) {
            input.makeJump();
        }
    }

    /**
     * Inversion ({@code CustomMovementInput.confused}) : avant et arriere, gauche
     * et droite s'echangent, le saut et l'accroupi aussi. Le vecteur de marche,
     * deja calcule, est retourne avec.
     */
    /** « Aucune avancee » ({@code CANCEL_FORWARD}) : la touche avancer ne fait plus rien. */
    private static void noForward(ClientInput input) {
        Input keys = input.keyPresses;
        input.keyPresses = new Input(false, keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(),
                false);
        Vec2 move = input.getMoveVector();
        if (move.y <= 0.0f) {
            return;
        }
        try {
            if (moveVector == null) {
                for (Field field : ClientInput.class.getDeclaredFields()) {
                    if (field.getType() == Vec2.class && !Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        moveVector = field;
                        break;
                    }
                }
            }
            if (moveVector != null) {
                moveVector.set(input, new Vec2(move.x, 0.0f));
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            moveVector = null;
        }
    }

    private static void invert(ClientInput input) {
        Input keys = input.keyPresses;
        input.keyPresses = new Input(keys.backward(), keys.forward(), keys.right(), keys.left(), keys.shift(),
                keys.jump(), keys.sprint());
        Vec2 move = input.getMoveVector();
        try {
            if (moveVector == null) {
                for (Field field : ClientInput.class.getDeclaredFields()) {
                    if (field.getType() == Vec2.class && !Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        moveVector = field;
                        break;
                    }
                }
            }
            if (moveVector != null) {
                moveVector.set(input, new Vec2(-move.x, -move.y));
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            moveVector = null;
        }
    }

    /** Sol glace : au sol, l'elan grandit au lieu de retomber. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.onGround()
                || !player.hasEffect(PalaLucky.FROZEN_GROUND)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * ICE, motion.y, motion.z * ICE);
    }

    /** Ou donner de la tete : la tete (et le chapeau, son enfant) cachee. */
    @SubscribeEvent
    public static void onRenderState(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                // Cow Player : le joueur disparait derriere la vache qui le suit (LuckyEffectsExtra).
                if (avatar.hasEffect(PalaLucky.COW_PLAYER)) {
                    for (String part : COW_HIDDEN) {
                        state.overrideModelPartVisibility(part, false);
                    }
                }
                if (avatar.hasEffect(PalaLucky.HIDE_HEAD)) {
                    state.overrideModelPartVisibility("head", false);
                }
                // Fantome : la tete seule ; Rayman : le corps seul disparait (bras et jambes flottent).
                if (avatar.hasEffect(PalaLucky.GHOST)) {
                    for (String part : GHOST_PARTS) {
                        state.overrideModelPartVisibility(part, false);
                    }
                } else if (avatar.hasEffect(PalaLucky.RAYMAN)) {
                    state.overrideModelPartVisibility("body", false);
                }
                if (avatar.hasEffect(PalaLucky.STEVE)) {
                    state.skin = DefaultPlayerSkin.getDefaultSkin();
                }
                // Le monde a l'envers : PalaMod ne retourne que le joueur local, a ses propres yeux.
                LocalPlayer local = Minecraft.getInstance().player;
                if (local != null && avatar == local && local.hasEffect(PalaLucky.ROTATE)) {
                    state.isUpsideDown = true;
                }
            }
        });
    }
}
