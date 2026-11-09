package fr.atraxterra.dohy.palamod.mount;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La monture equipee, gardee sur le joueur ({@code ExtendedMountPlayerData}).
 *
 * <p>Dans Mount, l'entite invoquee n'est qu'une projection : ses PV, sa
 * nourriture, son XP et son inventaire vivent ici, et tous les degats qu'elle
 * recoit s'ajoutent a {@link #damage()}. L'inventaire et la corne appartiennent
 * au <b>joueur</b> : ils restent quand on desequipe, et la monture equipee
 * suivante les retrouve. Les cases au-dela du niveau de la monture gardent
 * leur contenu sans etre accessibles.
 *
 * <p><b>Ecart</b> : l'original deplacait objets et corne dans l'entite a
 * l'invocation (avec une copie de secours) et les rendait a la
 * desinvocation. Ici ils ne quittent jamais le joueur ; l'inventaire de la
 * monture les lit et les ecrit directement. Une monture qui disparait (monde
 * ferme, chunk decharge, plantage) ne peut donc plus rien perdre.
 */
public final class MountData {

    public static final int INVENTORY_SIZE = MountLevels.SLOTS_AT_MAX;
    /** {@code MountManager.MOUNT_FOOD}. */
    public static final int MAX_FOOD = 10_000;
    public static final int DEFAULT_SHARED_XP = 50;

    /**
     * Un objet illisible (mod retire) devient une case vide, au lieu de faire
     * echouer toute la monture et son inventaire avec lui.
     */
    private static final Codec<ItemStack> LENIENT_STACK = ItemStack.OPTIONAL_CODEC.orElse(ItemStack.EMPTY);

    public static final MapCodec<MountData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("equipped").forGetter(data -> data.equipped),
            Codec.STRING.optionalFieldOf("name", "").forGetter(data -> data.name),
            Codec.FLOAT.optionalFieldOf("xp", 0.0f).forGetter(data -> data.xp),
            Codec.INT.optionalFieldOf("shared_xp", DEFAULT_SHARED_XP).forGetter(data -> data.sharedXp),
            Codec.INT.optionalFieldOf("food", MAX_FOOD).forGetter(data -> data.food),
            Codec.FLOAT.optionalFieldOf("damage", 0.0f).forGetter(data -> data.damage),
            LENIENT_STACK.optionalFieldOf("horn", ItemStack.EMPTY).forGetter(data -> data.horn),
            LENIENT_STACK.listOf().optionalFieldOf("inventory", List.of())
                    .forGetter(data -> List.copyOf(data.inventory))
    ).apply(instance, MountData::new));

    private Optional<UUID> equipped;
    private String name;
    private float xp;
    private int sharedXp;
    private int food;
    private float damage;
    private ItemStack horn;
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    /** L'entite invoquee. Pas sauvegardee : une monture ne survit pas a un rechargement. */
    private UUID summoned;

    public MountData() {
        this(Optional.empty(), "", 0.0f, DEFAULT_SHARED_XP, MAX_FOOD, 0.0f, ItemStack.EMPTY, List.of());
    }

    private MountData(Optional<UUID> equipped, String name, float xp, int sharedXp, int food, float damage,
                      ItemStack horn, List<ItemStack> stacks) {
        this.equipped = equipped;
        this.name = name;
        this.xp = xp;
        this.sharedXp = sharedXp;
        this.food = food;
        this.damage = damage;
        this.horn = horn;
        for (int slot = 0; slot < INVENTORY_SIZE && slot < stacks.size(); slot++) {
            inventory.set(slot, stacks.get(slot));
        }
    }

    /** Rien a sauvegarder : un joueur qui n'a jamais eu de monture. */
    public boolean isBlank() {
        return equipped.isEmpty() && horn.isEmpty() && inventory.stream().allMatch(ItemStack::isEmpty);
    }

    public boolean isEquipped() {
        return equipped.isPresent();
    }

    public Optional<UUID> equipped() {
        return equipped;
    }

    public String name() {
        return name.isEmpty() ? MountGem.DEFAULT_NAME : name;
    }

    public float xp() {
        return xp;
    }

    public int level() {
        return MountLevels.level(xp);
    }

    public float maxHealth() {
        return MountLevels.maxHealth(level());
    }

    public int slots() {
        return MountLevels.slots(level());
    }

    public int sharedXp() {
        return sharedXp;
    }

    public int food() {
        return food;
    }

    public float damage() {
        return damage;
    }

    public boolean isDead() {
        return damage >= maxHealth();
    }

    public ItemStack horn() {
        return horn;
    }

    public NonNullList<ItemStack> inventory() {
        return inventory;
    }

    public UUID summoned() {
        return summoned;
    }

    public void setSummoned(UUID entity) {
        this.summoned = entity;
    }

    public void setName(String value) {
        this.name = value;
    }

    public void setSharedXp(int value) {
        this.sharedXp = Math.clamp(value, 0, 100);
    }

    public void setFood(int value) {
        this.food = Math.clamp(value, 0, MAX_FOOD);
    }

    public void setDamage(float value) {
        this.damage = Math.clamp(value, 0.0f, maxHealth());
    }

    public void setHorn(ItemStack stack) {
        this.horn = stack;
    }

    /** {@code incrementXP} : ajoute, plafonne, et rend vrai si le niveau monte. */
    public boolean addXp(float amount) {
        int before = level();
        xp = MountLevels.cap(xp + amount);
        return level() > before;
    }

    /** {@code equipMount} : la gemme passe dans le joueur. Objets et corne ne bougent pas. */
    public void equip(MountGem gem) {
        equipped = Optional.of(gem.mountId());
        name = gem.name();
        xp = gem.xp();
        sharedXp = gem.sharedXp();
        food = gem.food();
        damage = gem.damage();
    }

    /**
     * {@code unequipMount} : la gemme rendue, puis les donnees remises a zero
     * (nom vide, XP 0, partage 50 %, nourriture pleine, PV pleins). Objets et
     * corne restent au joueur.
     */
    public MountGem unequip(UUID owner) {
        MountGem gem = new MountGem(equipped.orElseGet(UUID::randomUUID), Optional.of(owner), name(), xp, sharedXp,
                food, damage, -1L, -1L, -1L);
        equipped = Optional.empty();
        name = "";
        xp = 0.0f;
        sharedXp = DEFAULT_SHARED_XP;
        food = MAX_FOOD;
        damage = 0.0f;
        return gem;
    }
}
