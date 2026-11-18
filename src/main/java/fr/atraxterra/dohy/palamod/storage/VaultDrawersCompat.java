package fr.atraxterra.dohy.palamod.storage;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Le pont vers Vault Drawers, le portage en 26.2 de Storage Drawers.
 *
 * <p>Paladium jouait avec Storage Drawers installe a cote ; Berskar ne l'exige
 * pas et n'en depend pas a la compilation. Tout passe donc par la reflexion,
 * sur la seule interface publique dont la cle en paladium a besoin :
 * {@code IProtectable}, que portent les tiroirs (proprietaire pose par la cle
 * personnelle, et fournisseur de securite qui va avec).
 *
 * <p>Sans le mod, ou si son API change de nom, rien ne casse : {@link #isLockable}
 * repond non, et la cle se comporte comme sur n'importe quel bloc. L'echec est
 * dit une fois dans le journal, pas a chaque clic.
 */
final class VaultDrawersCompat {
    static final String MOD_ID = "vault_drawers";
    private static final Logger LOG = LogUtils.getLogger();
    private static final String PROTECTABLE =
            "com.stalkingdragons.minecraft.vaultdrawers.api.storage.attribute.IProtectable";

    private static boolean ready;
    private static boolean failed;
    private static Class<?> protectable;
    private static Method getOwner;
    private static Method setOwner;
    private static Method setSecurityProvider;

    private VaultDrawersCompat() {
    }

    /**
     * Resout l'API une fois. {@code synchronized} parce que le client et le
     * serveur integre peuvent y passer en meme temps : sans verrou, l'un
     * pourrait lire {@code ready} avant les methodes.
     */
    private static synchronized boolean prepare() {
        if (ready || failed) {
            return ready;
        }
        if (!ModList.get().isLoaded(MOD_ID)) {
            failed = true;
            return false;
        }
        try {
            protectable = Class.forName(PROTECTABLE);
            getOwner = protectable.getMethod("getOwner");
            setOwner = protectable.getMethod("setOwner", UUID.class);
            for (Method method : protectable.getMethods()) {
                if (method.getName().equals("setSecurityProvider") && method.getParameterCount() == 1) {
                    setSecurityProvider = method;
                }
            }
            if (setSecurityProvider == null) {
                throw new NoSuchMethodException(PROTECTABLE + ".setSecurityProvider");
            }
            ready = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("Vault Drawers est charge mais son API a change : la cle en paladium n'ouvrira pas ses tiroirs", e);
            failed = true;
        }
        return ready;
    }

    /** Vrai si le bloc (peut-etre absent) est un tiroir de Vault Drawers qu'une cle personnelle peut fermer. */
    static boolean isLockable(BlockEntity blockEntity) {
        return blockEntity != null && prepare() && protectable.isInstance(blockEntity);
    }

    /** Vrai si le tiroir a un proprietaire, c'est-a-dire s'il est ferme a cle. */
    static boolean isLocked(BlockEntity blockEntity) {
        try {
            return getOwner.invoke(blockEntity) != null;
        } catch (ReflectiveOperationException e) {
            LOG.warn("Vault Drawers : lecture du proprietaire impossible", e);
            return false;
        }
    }

    /**
     * Rend le tiroir a tous, comme la cle personnelle quand son proprietaire la
     * reutilise : plus de proprietaire, plus de fournisseur de securite.
     *
     * <p>Vault Drawers ne marque pas le bloc modifie quand il change de
     * proprietaire ; on le fait ici, et on renvoie le bloc aux clients.
     */
    static boolean unlock(BlockEntity blockEntity) {
        try {
            setOwner.invoke(blockEntity, (Object) null);
            setSecurityProvider.invoke(blockEntity, (Object) null);
        } catch (ReflectiveOperationException e) {
            LOG.warn("Vault Drawers : deverrouillage impossible", e);
            return false;
        }
        blockEntity.setChanged();
        Level level = blockEntity.getLevel();
        if (level != null) {
            BlockState state = blockEntity.getBlockState();
            level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
        return true;
    }
}
