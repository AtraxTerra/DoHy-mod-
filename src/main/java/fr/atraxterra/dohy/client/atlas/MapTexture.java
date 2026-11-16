package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Une texture de carte avec ses niveaux de detail (mipmaps) et son filtrage.
 *
 * <p>La carte se dessine en pixels physiques, a tous les zooms : reduite, une dalle
 * passe par ses niveaux lisses au lieu de sauter des pixels (le fourmillement des
 * anciennes cartes) ; agrandie, elle garde des blocs nets ou se lisse selon son
 * echantillonneur.
 *
 * <p>Les pixels transparents (le terrain inexplore) prennent la couleur de leurs
 * voisins avant l'envoi : le filtrage lineaire n'assombrit plus le bord de
 * l'explore.
 */
final class MapTexture extends AbstractTexture {

    /** Recoit un niveau calcule, avant qu'il soit libere (pour nourrir les vues d'ensemble). */
    interface LevelSink {
        void accept(int level, NativeImage image);
    }

    private final int width;
    private final int height;
    private final int levels;

    MapTexture(String label, int width, int height, int levels, GpuSampler sampler) {
        this.width = width;
        this.height = height;
        this.levels = Math.max(1, Math.min(levels, maxLevels(width, height)));
        GpuDevice device = RenderSystem.getDevice();
        this.texture = device.createTexture(() -> label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, width, height, 1, this.levels);
        this.textureView = device.createTextureView(this.texture);
        this.sampler = sampler;
    }

    static int maxLevels(int width, int height) {
        int levels = 1;
        while ((width >> levels) >= 1 && (height >> levels) >= 1) {
            levels++;
        }
        return levels;
    }

    int levels() {
        return levels;
    }

    /** Les blocs nets de pres, lisses de loin : les dalles fines. */
    static GpuSampler crisp() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.NEAREST, true);
    }

    /** Lisse dans les deux sens : les vues d'ensemble, le globe. */
    static GpuSampler smooth() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
    }

    /** Lisse, sans niveaux : les pictogrammes generes a leur taille d'affichage. */
    static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    /** Envoie l'image et ses niveaux. L'image doit faire la taille de la texture. */
    void upload(NativeImage image, LevelSink sink) {
        if (texture == null || texture.isClosed() || image.getWidth() != width || image.getHeight() != height) {
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        chain(image, levels, (level, mip) -> {
            encoder.writeToTexture(texture, mip, level, 0, 0, 0);
            if (sink != null && level > 0) {
                sink.accept(level, mip);
            }
        });
    }

    /**
     * Calcule les niveaux d'une image (le niveau 0 est l'image elle-meme) et les passe
     * un a un ; chaque niveau intermediaire est libere apres usage.
     */
    static void chain(NativeImage image, int levels, LevelSink sink) {
        bleed(image);
        sink.accept(0, image);
        NativeImage previous = image;
        for (int level = 1; level < levels; level++) {
            NativeImage next = downsample(previous);
            bleed(next);
            sink.accept(level, next);
            if (previous != image) {
                previous.close();
            }
            previous = next;
        }
        if (previous != image) {
            previous.close();
        }
    }

    /** Tous les niveaux d'une image (le premier est l'image elle-meme) ; l'appelant les libere. */
    static NativeImage[] levels(NativeImage image, int levels) {
        NativeImage[] out = new NativeImage[Math.max(1, levels)];
        bleed(image);
        out[0] = image;
        for (int level = 1; level < out.length; level++) {
            out[level] = downsample(out[level - 1]);
            bleed(out[level]);
        }
        return out;
    }

    /** Envoie des niveaux deja calcules ({@link #levels}). */
    void uploadLevels(NativeImage[] images) {
        if (texture == null || texture.isClosed()) {
            return;
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int level = 0; level < Math.min(levels, images.length); level++) {
            encoder.writeToTexture(texture, images[level], level, 0, 0, 0);
        }
    }

    /**
     * Un niveau deux fois plus petit : chaque pixel est la moyenne de quatre, ponderee
     * par leur opacite (un pixel transparent ne fonce pas ses voisins).
     */
    static NativeImage downsample(NativeImage source) {
        int sw = source.getWidth();
        int sh = source.getHeight();
        int w = Math.max(1, sw >> 1);
        int h = Math.max(1, sh >> 1);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            int y0 = Math.min(sh - 1, y * 2);
            int y1 = Math.min(sh - 1, y * 2 + 1);
            for (int x = 0; x < w; x++) {
                int x0 = Math.min(sw - 1, x * 2);
                int x1 = Math.min(sw - 1, x * 2 + 1);
                int c0 = source.getPixel(x0, y0);
                int c1 = source.getPixel(x1, y0);
                int c2 = source.getPixel(x0, y1);
                int c3 = source.getPixel(x1, y1);
                int a0 = c0 >>> 24;
                int a1 = c1 >>> 24;
                int a2 = c2 >>> 24;
                int a3 = c3 >>> 24;
                int alpha = a0 + a1 + a2 + a3;
                if (alpha == 0) {
                    out.setPixel(x, y, 0);
                    continue;
                }
                int r = ((c0 >> 16) & 0xFF) * a0 + ((c1 >> 16) & 0xFF) * a1 + ((c2 >> 16) & 0xFF) * a2
                        + ((c3 >> 16) & 0xFF) * a3;
                int g = ((c0 >> 8) & 0xFF) * a0 + ((c1 >> 8) & 0xFF) * a1 + ((c2 >> 8) & 0xFF) * a2
                        + ((c3 >> 8) & 0xFF) * a3;
                int b = (c0 & 0xFF) * a0 + (c1 & 0xFF) * a1 + (c2 & 0xFF) * a2 + (c3 & 0xFF) * a3;
                int half = alpha / 2;
                out.setPixel(x, y, ((alpha + 2) / 4) << 24 | ((r + half) / alpha) << 16 | ((g + half) / alpha) << 8
                        | ((b + half) / alpha));
            }
        }
        return out;
    }

    /**
     * Donne aux pixels transparents la couleur moyenne de leurs voisins visibles, sans
     * toucher a leur transparence : le filtrage ne melange plus du noir au bord.
     */
    static void bleed(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = image.getPixel(x, y);
                if ((color >>> 24) != 0 || (color & 0xFFFFFF) != 0) {
                    continue;
                }
                int r = 0;
                int g = 0;
                int b = 0;
                int count = 0;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? -1 : k == 1 ? 1 : 0);
                    int ny = y + (k == 2 ? -1 : k == 3 ? 1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        continue;
                    }
                    int near = image.getPixel(nx, ny);
                    if ((near >>> 24) == 0) {
                        continue;
                    }
                    r += (near >> 16) & 0xFF;
                    g += (near >> 8) & 0xFF;
                    b += near & 0xFF;
                    count++;
                }
                if (count > 0) {
                    image.setPixel(x, y, (r / count) << 16 | (g / count) << 8 | (b / count));
                }
            }
        }
    }
}
