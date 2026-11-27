package fr.atraxterra.dohy.client.wither;

import fr.atraxterra.dohy.palamod.PalaMod;
import fr.atraxterra.dohy.palamod.wither.PalaWithers;
import fr.atraxterra.dohy.palamod.wither.WitherKind;
import fr.atraxterra.dohy.palamod.wither.WitherReceptacleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Le cote client des withers de PalaWither : le rendu du receptacle, la relecture des
 * modeles avec les ressources, et la boucle sonore de l'invocation.
 *
 * <p><b>La boucle</b> : comme le {@code TileEntityWitherReceptacle} de PalaWither, un
 * receptacle qui invoque joue la musique du wither attendu, en boucle, la ou il est,
 * et se tait des que l'invocation finit, echoue ou que le receptacle disparait.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WitherClient {

    private static final Map<WitherReceptacleBlockEntity, LoopSound> LOOPS = new WeakHashMap<>();

    private WitherClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PalaWithers.RECEPTACLE_ENTITY.get(), WitherReceptacleRenderer::new);
    }

    /** Les modeles de PalaWither se relisent avec les ressources (F3 + T, pack de ressources). */
    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "wither_models"),
                (ResourceManagerReloadListener) manager -> WitherModels.clear());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            LOOPS.clear();
            return;
        }
        LOOPS.values().removeIf(LoopSound::isStopped);
        for (WitherReceptacleBlockEntity receptacle : WitherReceptacleBlockEntity.clientLoaded()) {
            WitherKind kind = receptacle.summoning();
            if (kind == null || receptacle.getLevel() != minecraft.level || LOOPS.containsKey(receptacle)) {
                continue;
            }
            PalaWithers.Sounds sounds = PalaWithers.sounds(kind);
            if (sounds == null) {
                continue;
            }
            LoopSound loop = new LoopSound(receptacle, kind, sounds.loop().get());
            LOOPS.put(receptacle, loop);
            minecraft.getSoundManager().play(loop);
        }
    }

    /** La boucle d'un receptacle, tant qu'il invoque le meme wither. */
    private static final class LoopSound extends AbstractTickableSoundInstance {

        private final WitherReceptacleBlockEntity receptacle;
        private final WitherKind kind;

        LoopSound(WitherReceptacleBlockEntity receptacle, WitherKind kind, SoundEvent sound) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.receptacle = receptacle;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0f;
            this.x = receptacle.getBlockPos().getX() + 0.5d;
            this.y = receptacle.getBlockPos().getY() + 0.5d;
            this.z = receptacle.getBlockPos().getZ() + 0.5d;
        }

        @Override
        public void tick() {
            if (receptacle.isRemoved() || receptacle.summoning() != kind) {
                stop();
            }
        }
    }
}
