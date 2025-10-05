package com.extendedclip.papi.expansion.checkitem;

import com.extendedclip.papi.expansion.checkitem.cache.PlaceholderCache;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTItem;
import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CheckItemExpansion with internal arithmetic expression support via amtexpr:
 * New dynamic expression wrapper format: {cmi_equationint_<expression>}
 * Example: %checkitem_remove_amtexpr:{cmi_equationint_64-{cmi_user_metaint_test1}},mat:diamond%
 */
public class CheckItemExpansion extends PlaceholderExpansion implements Configurable {

    private static final String IDENTIFIER = "checkitem";
    private static final String AUTHOR = "cj89898";
    private static final String LEGACY_VERSION = "2.7.9";

    private static final String MSG_GIVE_DISABLED = "Give placeholders have been disabled. Check PlaceholderAPI Config.";
    private static final String MSG_REMOVE_DISABLED = "Remove placeholders have been disabled. Check PlaceholderAPI Config.";
    private static final String MSG_INVALID_SLOT = "Invalid number for slot";
    private static final String RESULT_ERROR = "error";
    private static final String RESULT_YES = "yes";

    private static final boolean USE_COMPONENTS = detectComponentsSupport();

    private PlaceholderCache parseCache;
    private boolean cacheInitialized = false;
    private boolean cacheEnabled = true;
    private int cacheMaxSize = 500;

    private static final String DYNAMIC_ARG_REGEX = ".*[%{}].*";

    private static boolean detectComponentsSupport() {
        String version = Bukkit.getServer().getBukkitVersion();
        String[] split = version.split("\\.|-");
        try {
            if (split.length >= 2) {
                int major = Integer.parseInt(split[0]);
                int minor = Integer.parseInt(split[1]);
                return (major > 1 && minor >= 20);
            }
        } catch (NumberFormatException ignored) {}
        return false;
    }

    private void ensureCache() {
        if (!cacheInitialized) {
            Object enabled = get("cache_enabled", true);
            Object maxSize = get("cache_max_size", 500);
            cacheEnabled = enabled instanceof Boolean b ? b : true;
            cacheMaxSize = (maxSize instanceof Number n) ? Math.max(50, n.intValue()) : 500;
            parseCache = new PlaceholderCache(cacheMaxSize, 0);
            cacheInitialized = true;
        }
    }

    private String resolveAllPlaceholders(Player p, String input) {
        if (input == null || input.isEmpty()) return input;
        String prev;
        String current = input;
        int safety = 10;
        do {
            prev = current;
            current = PlaceholderAPI.setBracketPlaceholders(p, current);
            current = PlaceholderAPI.setPlaceholders(p, current);
        } while (!current.equals(prev) && --safety > 0);
        return current;
    }

    public static class ItemWrapper {
        private boolean checkNameContains;
        private boolean checkNameStartsWith;
        private boolean checkNameEquals;
        private boolean checkLoreContains;
        private boolean checkLoreEquals;
        private boolean checkMaterialContains;
        private boolean checkDurability;
        private boolean checkCustomData;
        private boolean checkAmount;
        private boolean checkType;
        private boolean checkMainHand;
        private boolean checkOffHand;
        private boolean checkEnchantments;
        private boolean checkEnchanted;
        private boolean checkPotionType;
        private boolean checkPotionExtended;
        private boolean checkPotionUpgraded;
        private boolean checkNbtStrings;
        private boolean checkNbtInts;
        private boolean isStrict;
        private boolean remove;

        private boolean amountValid = true;

        private String material;
        private short data;
        private int customData;
        private int amount;
        private String name;
        private String lore;
        private String materialString;
        private HashMap<Enchantment, Integer> enchantments;
        private PotionType potionType;
        private boolean potionExtended;
        private boolean potionUpgraded;
        private int slot;
        private Map<String, String> nbtStrings;
        private Map<String, Integer> nbtInts;

        public ItemWrapper() { slot = -1; }

        public String getType() { return material; }
        protected void setType(String material) { this.material = material.toUpperCase(Locale.ROOT); }
        public short getDurability() { return data; }
        protected void setDurability(short durability) { this.data = durability; }
        public int getCustomData() { return customData; }
        protected void setCustomData(int customData) { this.customData = customData; }
        public int getAmount() { return amount; }
        protected void setAmount(int amount) { this.amount = amount; }
        public boolean isAmountValid() { return amountValid; }
        protected void setAmountValid(boolean valid) { this.amountValid = valid; }
        public String getName() { return name; }
        protected void setName(String name) { this.name = name; }
        public String getLore() { return lore; }
        protected void setLore(String lore) { this.lore = lore; }
        public String getMaterialString() { return materialString; }
        protected void setMaterialString(String materialString) { this.materialString = materialString; }
        protected void setEnchantments(HashMap<Enchantment, Integer> enchantments) { this.enchantments = enchantments; }
        public HashMap<Enchantment, Integer> getEnchantments() { return enchantments; }
        protected void setPotionType(PotionType potionType) { this.potionType = potionType; }
        public PotionType getPotionType() { return potionType; }
        protected void setPotionExtended(boolean potionExtended) { this.potionExtended = potionExtended; }
        public boolean getPotionExtended() { return potionExtended; }
        protected void setPotionUpgraded(boolean potionUpgraded) { this.potionUpgraded = potionUpgraded; }
        public boolean getPotionUpgraded() { return potionUpgraded; }
        public void setNbtStrings(Map<String, String> nbtStrings) { this.nbtStrings = nbtStrings; }
        public Map<String, String> getNbtStrings() { return nbtStrings; }
        public void setNbtInts(Map<String, Integer> nbtInts) { this.nbtInts = nbtInts; }
        public Map<String, Integer> getNbtInts() { return nbtInts; }
        protected void setCheckDurability(boolean b) { this.checkDurability = b; }
        public boolean shouldCheckDurability() { return checkDurability; }
        protected void setCheckCustomData(boolean b) { this.checkCustomData = b; }
        public boolean shouldCheckCustomData() { return checkCustomData; }
        protected void setCheckAmount(boolean b) { this.checkAmount = b; }
        public boolean shouldCheckAmount() { return checkAmount; }
        protected void setCheckNameContains(boolean b) { this.checkNameContains = b; }
        public boolean shouldCheckNameContains() { return checkNameContains; }
        protected void setCheckNameStartsWith(boolean b) { this.checkNameStartsWith = b; }
        public boolean shouldCheckNameStartsWith() { return checkNameStartsWith; }
        protected void setCheckNameEquals(boolean b) { this.checkNameEquals = b; }
        public boolean shouldCheckNameEquals() { return checkNameEquals; }
        protected void setCheckLoreContains(boolean b) { this.checkLoreContains = b; }
        public boolean shouldCheckLoreContains() { return checkLoreContains; }
        protected void setCheckLoreEquals(boolean b) { this.checkLoreEquals = b; }
        public boolean shouldCheckLoreEquals() { return checkLoreEquals; }
        protected void setCheckMaterialContains(boolean b) { this.checkMaterialContains = b; }
        public boolean shouldCheckMaterialContains() { return checkMaterialContains; }
        protected void setCheckType(boolean b) { this.checkType = b; }
        public boolean shouldCheckType() { return checkType; }
        protected void setCheckMainHand(boolean b) { this.checkMainHand = b; }
        public boolean shouldCheckMainHand() { return checkMainHand; }
        protected void setCheckOffHand(boolean b) { this.checkOffHand = b; }
        public boolean shouldCheckOffHand() { return checkOffHand; }
        protected void setIsStrict(boolean b) { this.isStrict = b; }
        public boolean isStrict() { return isStrict; }
        protected void setCheckEnchantments(boolean b) { this.checkEnchantments = b; }
        public boolean shouldCheckEnchantments() { return checkEnchantments; }
        protected void setCheckEnchanted(boolean b) { this.checkEnchanted = b; }
        public boolean shouldCheckEnchanted() { return checkEnchanted; }
        protected void setCheckPotionType(boolean b) { this.checkPotionType = b; }
        public boolean shouldCheckPotionType() { return checkPotionType; }
        protected void setCheckPotionExtended(boolean b) { this.checkPotionExtended = b; }
        public boolean shouldCheckPotionExtended() { return checkPotionExtended; }
        protected void setCheckPotionUpgraded(boolean b) { this.checkPotionUpgraded = b; }
        public boolean shouldCheckPotionUpgraded() { return checkPotionUpgraded; }
        protected void setCheckNbtStrings(boolean b) { this.checkNbtStrings = b; }
        public boolean shouldCheckNbtStrings() { return checkNbtStrings; }
        protected void setCheckNbtInts(boolean b) { this.checkNbtInts = b; }
        public boolean shouldCheckNbtInts() { return checkNbtInts; }
        protected void setRemove(boolean remove) { this.remove = remove; }
        public boolean shouldRemove() { return remove; }
        protected void setSlot(int slot) { this.slot = slot; }
        public int getSlot() { return slot; }
    }

    private record PotionReflectionInfo(String type, boolean extended, boolean upgraded){}

    @Override public boolean canRegister() { return true; }
    @Override public String getAuthor() { return AUTHOR; }
    @Override public String getIdentifier() { return IDENTIFIER; }
    @Override public String getVersion() { return LEGACY_VERSION; }

    @SuppressWarnings("deprecation")
    @Override
    public String onPlaceholderRequest(Player p, String args) {
        if (p == null) {
            return "%" + getIdentifier() + "_" + args + "%";
        }
        ensureCache();

        args = resolveAllPlaceholders(p, args);

        boolean amountMode = false;
        boolean removeFlag = false;

        if (args.startsWith("give_")) {
            if (!(boolean) get("give_enabled", true)) {
                return MSG_GIVE_DISABLED;
            }
            String clean = ChatColor.translateAlternateColorCodes('&', args.substring("give_".length()));
            clean = resolveAllPlaceholders(p, clean);
            ItemWrapper w = getWrapper(new ItemWrapper(), clean, p);
            if (w == null) return null;
            return giveItem(w, p);
        }

        if (args.startsWith("getinfo:")) {
            return handleGetInfo(p, args.substring("getinfo:".length()), new ItemWrapper());
        }

        if (args.startsWith("amount_")) {
            amountMode = true;
            args = args.substring("amount_".length());
        }

        if (args.startsWith("remove_")) {
            removeFlag = true;
            if (!(boolean) get("remove_enabled", true)) {
                return MSG_REMOVE_DISABLED;
            }
            args = args.substring("remove_".length());
        }

        final String finalArgs = ChatColor.translateAlternateColorCodes('&', args);
        boolean cacheable = cacheEnabled
                && !finalArgs.matches(DYNAMIC_ARG_REGEX)
                && !removeFlag;

        ItemWrapper wrapper = cacheable
                ? parseCache.get(finalArgs, key -> getWrapper(new ItemWrapper(), key, p))
                : getWrapper(new ItemWrapper(), finalArgs, p);

        if (wrapper == null) return null;
        if (removeFlag) wrapper.setRemove(true);

        ItemStack[] itemsToCheck = resolveItemsToCheck(p, wrapper);

        if (amountMode) {
            return String.valueOf(getItemAmount(wrapper, p, itemsToCheck));
        }
        return checkItem(wrapper, p, itemsToCheck)
                ? PlaceholderAPIPlugin.booleanTrue()
                : PlaceholderAPIPlugin.booleanFalse();
    }

    private String handleGetInfo(Player p, String argRest, ItemWrapper wrapper) {
        String[] split = argRest.split("_", 2);
        int slot = resolveSlot(p, split[0]);
        if (slot < 0) return MSG_INVALID_SLOT;

        boolean multiMod;
        if (split.length == 2) {
            String params = resolveAllPlaceholders(p, ChatColor.translateAlternateColorCodes('&', split[1]));
            wrapper = getWrapper(wrapper, params, p);
            multiMod = params.split(",").length > 1;
        } else {
            wrapper.setCheckNameContains(true);
            wrapper.setCheckType(true);
            wrapper.setCheckAmount(true);
            wrapper.setCheckDurability(true);
            wrapper.setCheckCustomData(true);
            wrapper.setCheckLoreContains(true);
            wrapper.setCheckEnchantments(true);
            wrapper.setCheckEnchanted(true);
            wrapper.setCheckPotionType(true);
            wrapper.setCheckPotionExtended(true);
            wrapper.setCheckPotionUpgraded(true);
            wrapper.setCheckNbtStrings(true);
            wrapper = getWrapper(wrapper, "", p);
            multiMod = true;
        }

        ItemStack item = p.getInventory().getItem(slot);
        if (item == null) return "";
        return buildItemInfo(wrapper, item, multiMod);
    }

    private int resolveSlot(Player p, String raw) {
        return switch (raw) {
            case "mainhand" -> p.getInventory().getHeldItemSlot();
            case "offhand" -> 40;
            default -> {
                try { yield Integer.parseInt(raw); }
                catch (NumberFormatException e) { yield -1; }
            }
        };
    }

    private String buildItemInfo(ItemWrapper wrapper, ItemStack item, boolean multiMod) {
        StringBuilder sb = new StringBuilder();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if ((wrapper.shouldCheckNameContains()
                    || wrapper.shouldCheckNameEquals()
                    || wrapper.shouldCheckNameStartsWith())
                    && meta.hasDisplayName()) {
                label(sb, multiMod, "name:").append(meta.getDisplayName()).append(" &r");
            }
            if (wrapper.shouldCheckCustomData() && meta.hasCustomModelData()) {
                label(sb, multiMod, "custommodeldata:").append(meta.getCustomModelData()).append(" &r");
            }
            if ((wrapper.shouldCheckLoreContains() || wrapper.shouldCheckLoreEquals()) && meta.hasLore()) {
                label(sb, multiMod, "lore:");
                List<String> loreLines = meta.getLore();
                if (loreLines != null) {
                    for (int i = 0; i < loreLines.size(); i++) {
                        sb.append(loreLines.get(i));
                        if (i < loreLines.size() - 1) sb.append("|");
                    }
                }
                sb.append(" &r");
            }
            if (wrapper.shouldCheckEnchantments()) {
                appendEnchantmentsInfo(wrapper, item, meta, sb, multiMod);
            }
            if (wrapper.shouldCheckEnchanted()) {
                boolean enchanted = meta.hasEnchants()
                        || (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants());
                label(sb, multiMod, "enchanted:").append(enchanted).append(" &r");
            }
            if ((wrapper.shouldCheckPotionType()
                    || wrapper.shouldCheckPotionExtended()
                    || wrapper.shouldCheckPotionUpgraded())
                    && meta instanceof PotionMeta pm) {
                PotionReflectionInfo info = readPotionMeta(pm);
                if (info != null) {
                    if (wrapper.shouldCheckPotionType()) {
                        label(sb, multiMod, "potiontype:").append(info.type()).append(" &r");
                    }
                    if (wrapper.shouldCheckPotionExtended()) {
                        label(sb, multiMod, "potionextended:").append(info.extended()).append(" &r");
                    }
                    if (wrapper.shouldCheckPotionUpgraded()) {
                        label(sb, multiMod, "potionupgraded:").append(info.upgraded()).append(" &r");
                    }
                }
            }
        }

        if (wrapper.shouldCheckType()) {
            label(sb, multiMod, "mat:").append(item.getType()).append(" &r");
        }
        if (wrapper.shouldCheckAmount()) {
            label(sb, multiMod, "amt:").append(item.getAmount()).append(" &r");
        }
        if (wrapper.shouldCheckDurability()) {
            int dmg = 0;
            if (meta instanceof Damageable d) dmg = d.getDamage();
            label(sb, multiMod, "data:").append(dmg).append(" &r");
        }

        if (wrapper.shouldCheckNbtStrings() || wrapper.shouldCheckNbtInts()) {
            appendNbtInfo(wrapper, item, sb, multiMod);
        }

        String result = sb.toString();
        if (result.endsWith(" &r")) {
            result = result.substring(0, result.length() - 3);
        }
        return result;
    }

    private StringBuilder label(StringBuilder sb, boolean multi, String label) {
        if (multi) sb.append(label);
        return sb;
    }

    private void appendEnchantmentsInfo(ItemWrapper wrapper, ItemStack item, ItemMeta meta, StringBuilder sb, boolean multiMod) {
        if (!meta.hasEnchants()
                && (!(meta instanceof EnchantmentStorageMeta)
                || !((EnchantmentStorageMeta) meta).hasStoredEnchants())) {
            if (!multiMod && wrapper.getEnchantments() != null && wrapper.getEnchantments().size() == 1) {
                sb.append("0");
            }
            return;
        }

        Map<Enchantment, Integer> enchants = (meta instanceof EnchantmentStorageMeta esm)
                ? esm.getStoredEnchants()
                : meta.getEnchants();

        if (!multiMod && wrapper.getEnchantments() != null && wrapper.getEnchantments().size() == 1) {
            for (Entry<Enchantment, Integer> e : wrapper.getEnchantments().entrySet()) {
                if (enchants.containsKey(e.getKey())) {
                    sb.append(enchants.get(e.getKey()));
                    return;
                }
            }
            sb.append("0");
        } else {
            label(sb, multiMod, "enchantments:");
            int i = 0;
            for (Entry<Enchantment, Integer> e : enchants.entrySet()) {
                sb.append(e.getKey().getKey()).append(":").append(e.getValue());
                if (++i < enchants.size()) sb.append("|");
            }
            sb.append(" &r");
        }
    }

    private void appendNbtInfo(ItemWrapper wrapper, ItemStack item, StringBuilder sb, boolean multiMod) {
        NBTItem nbtItem = new NBTItem(item);
        if (!multiMod) {
            int criteriaCount = 0;
            if (wrapper.shouldCheckNbtStrings() && wrapper.getNbtStrings() != null)
                criteriaCount += wrapper.getNbtStrings().size();
            if (wrapper.shouldCheckNbtInts() && wrapper.getNbtInts() != null)
                criteriaCount += wrapper.getNbtInts().size();
            if (criteriaCount == 1) {
                if (wrapper.shouldCheckNbtStrings()) {
                    for (Entry<String, String> e : wrapper.getNbtStrings().entrySet()) {
                        sb.append(resolveSingleNbtString(nbtItem, e.getKey()));
                    }
                } else {
                    for (Entry<String, Integer> e : wrapper.getNbtInts().entrySet()) {
                        sb.append(resolveSingleNbtInt(nbtItem, e.getKey()));
                    }
                }
                return;
            }
        }

        if (!nbtItem.getKeys().isEmpty()) {
            if (multiMod) sb.append("nbt:");
            int count = 0;
            for (String key : nbtItem.getKeys()) {
                NBTType type = nbtItem.getType(key);
                switch (type) {
                    case NBTTagString -> sb.append("STRING:").append(key).append(":").append(nbtItem.getString(key));
                    case NBTTagInt -> sb.append("INTEGER:").append(key).append(":").append(nbtItem.getInteger(key));
                    case NBTTagCompound -> {
                        if (!"display".equalsIgnoreCase(key)) {
                            sb.append("NBTTagCompound:").append(key).append(":").append(nbtItem.getCompound(key));
                        } else {
                            count--;
                        }
                    }
                    default -> count--;
                }
                if (++count > 0) sb.append("|");
            }
            if (sb.charAt(sb.length() - 1) == '|') sb.setLength(sb.length() - 1);
            sb.append(" &r");
        }
    }

    private String resolveSingleNbtString(NBTItem nbtItem, String key) {
        if (key.contains("..")) {
            String[] s = key.split("\\.\\.");
            return nbtItem.getCompound(s[0]).getString(s[1]);
        }
        return nbtItem.getString(key);
    }

    private String resolveSingleNbtInt(NBTItem nbtItem, String key) {
        if (key.contains("..")) {
            String[] s = key.split("\\.\\.");
            return String.valueOf(nbtItem.getCompound(s[0]).getInteger(s[1]));
        }
        return String.valueOf(nbtItem.getInteger(key));
    }

    @SuppressWarnings({"deprecation","removal","rawtypes","unchecked"})
    private String giveItem(ItemWrapper wrapper, Player p) {
        Material mat = Material.getMaterial(wrapper.getType());
        if (mat == null) return RESULT_ERROR;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return RESULT_ERROR;

        if (wrapper.shouldCheckDurability()) {
            if (meta instanceof Damageable dmg) {
                dmg.setDamage(wrapper.getDurability());
            } else {
                item.setDurability(wrapper.getDurability());
            }
        }
        if (wrapper.shouldCheckCustomData()) meta.setCustomModelData(wrapper.getCustomData());
        if (wrapper.shouldCheckNameEquals()) meta.setDisplayName(wrapper.getName());
        if (wrapper.shouldCheckLoreEquals()) {
            List<String> lore = Stream.of(wrapper.getLore().split("\\|", -1)).collect(Collectors.toList());
            meta.setLore(lore);
        }
        if (wrapper.shouldCheckEnchantments() && wrapper.getEnchantments() != null) {
            wrapper.getEnchantments().forEach((ench, level) -> {
                if (ench != null) meta.addEnchant(ench, (level == -1 ? 1 : level), true);
            });
        }
        if ((wrapper.shouldCheckPotionType()
                || wrapper.shouldCheckPotionExtended()
                || wrapper.shouldCheckPotionUpgraded())
                && meta instanceof PotionMeta pm
                && wrapper.getPotionType() != null) {
            try {
                Class<?> potionDataClass = Class.forName("org.bukkit.potion.PotionData");
                Constructor<?> ctor = potionDataClass.getConstructor(PotionType.class, boolean.class, boolean.class);
                Object potionDataInstance = ctor.newInstance(wrapper.getPotionType(),
                        wrapper.getPotionExtended(), wrapper.getPotionUpgraded());
                Method setBasePotionData = PotionMeta.class.getMethod("setBasePotionData", potionDataClass);
                setBasePotionData.invoke(pm, potionDataInstance);
            } catch (Throwable ignored) {}
        }
        item.setItemMeta(meta);

        if (wrapper.shouldCheckNbtStrings() || wrapper.shouldCheckNbtInts()) {
            NBTItem nbtItem = new NBTItem(item);
            if (wrapper.shouldCheckNbtStrings() && wrapper.getNbtStrings() != null) {
                for (Entry<String, String> e : wrapper.getNbtStrings().entrySet()) {
                    applyNbtSetString(nbtItem, e.getKey(), e.getValue());
                }
            }
            if (wrapper.shouldCheckNbtInts() && wrapper.getNbtInts() != null) {
                for (Entry<String, Integer> e : wrapper.getNbtInts().entrySet()) {
                    applyNbtSetInt(nbtItem, e.getKey(), e.getValue());
                }
            }
            item = nbtItem.getItem();
        }

        if (wrapper.shouldCheckAmount()) {
            distributeItemAmount(p, item, wrapper.getAmount());
        } else {
            p.getInventory().addItem(item);
        }

        return RESULT_YES;
    }

    private void distributeItemAmount(Player p, ItemStack prototype, int total) {
        int maxStack = prototype.getMaxStackSize();
        int remaining = total;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack stack = prototype.clone();
            stack.setAmount(give);
            Map<Integer, ItemStack> leftover = p.getInventory().addItem(stack);
            remaining -= give;
            if (!leftover.isEmpty()) break;
        }
    }

    private void applyNbtSetString(NBTItem item, String key, String value) {
        if (key.contains("..")) {
            String[] s = key.split("\\.\\.");
            item.addCompound(s[0]).setString(s[1], value);
        } else {
            item.setString(key, value);
        }
    }

    private void applyNbtSetInt(NBTItem item, String key, int value) {
        if (key.contains("..")) {
            String[] s = key.split("\\.\\.");
            item.addCompound(s[0]).setInteger(s[1], value);
        } else {
            item.setInteger(key, value);
        }
    }

    private boolean checkItem(ItemWrapper wrapper, Player p, ItemStack... items) {
        if (wrapper.shouldCheckAmount() && !wrapper.isAmountValid()) {
            return false;
        }
        if (wrapper.shouldRemove() && wrapper.shouldCheckAmount() && wrapper.getAmount() <= 0) {
            return false;
        }

        int total = getItemAmount(wrapper, p, items);
        if (wrapper.shouldCheckAmount()) {
            return wrapper.isStrict() ? total == wrapper.getAmount() : total >= wrapper.getAmount();
        }
        return total >= 1;
    }

    private int getItemAmount(ItemWrapper wrapper, Player p, ItemStack... items) {
        int total = 0;
        List<ItemStack> matched = new ArrayList<>();

        for (ItemStack stack : items) {
            if (stack == null) {
                if (wrapper.shouldCheckType() && "AIR".equals(wrapper.getType())) {
                    return Integer.MAX_VALUE;
                }
                continue;
            }
            if (stack.getType() == Material.AIR) {
                if (wrapper.shouldCheckType() && "AIR".equals(wrapper.getType())) {
                    return Integer.MAX_VALUE;
                }
                continue;
            }

            if (!matchesBasic(wrapper, stack)) continue;
            ItemMeta meta = stack.getItemMeta();
            if (!matchesMeta(wrapper, meta)) continue;
            if (!matchesEnchantments(wrapper, meta)) continue;
            if (!matchesPotions(wrapper, meta)) continue;
            if (!matchesEnchanted(wrapper, meta)) continue;
            if (!matchesNbt(wrapper, stack)) continue;

            if (wrapper.isStrict() && wrapper.shouldCheckType() && !matchesStrictWrapper(wrapper, stack, meta)) continue;

            total += stack.getAmount();
            matched.add(stack);
        }

        if (wrapper.shouldRemove()) total = handleRemoval(wrapper, p, matched, total);
        return total;
    }

    private boolean matchesBasic(ItemWrapper w, ItemStack stack) {
        if (w.shouldCheckType() && !w.getType().equals(stack.getType().name())) return false;

        if (w.shouldCheckDurability()) {
            ItemMeta meta = stack.getItemMeta();
            int dmg = 0;
            if (meta instanceof Damageable d) dmg = d.getDamage();
            if (dmg != w.getDurability()) return false;
        }

        if (w.shouldCheckMaterialContains() && !stack.getType().name().contains(w.getMaterialString())) return false;
        return true;
    }

    private boolean matchesMeta(ItemWrapper w, ItemMeta meta) {
        if (meta == null) {
            return !(w.shouldCheckNameContains() || w.shouldCheckNameEquals() || w.shouldCheckNameStartsWith()
                    || w.shouldCheckLoreContains() || w.shouldCheckLoreEquals()
                    || w.shouldCheckCustomData());
        }

        if (w.shouldCheckCustomData()) {
            if (!meta.hasCustomModelData()) return false;
            if (meta.getCustomModelData() != w.getCustomData()) return false;
        }

        if (w.shouldCheckLoreContains()) {
            if (!meta.hasLore()) return false;
            List<String> loreLines = meta.getLore();
            if (loreLines == null) return false;
            boolean match = false;
            for (String line : loreLines) {
                if (line.contains(w.getLore())) { match = true; break; }
            }
            if (!match) return false;
        }

        if (w.shouldCheckLoreEquals()) {
            if (!meta.hasLore()) return false;
            List<String> loreList = meta.getLore();
            if (loreList == null) return false;
            String lore = String.join("|", loreList);
            if (!w.getLore().equals(lore)) return false;
        }

        if (w.shouldCheckNameContains()) {
            if (!(meta.hasDisplayName() && meta.getDisplayName().contains(w.getName()))) return false;
        } else if (w.shouldCheckNameStartsWith()) {
            if (!(meta.hasDisplayName() && meta.getDisplayName().startsWith(w.getName()))) return false;
        } else if (w.shouldCheckNameEquals()) {
            if (!(meta.hasDisplayName() && meta.getDisplayName().equals(w.getName()))) return false;
        }

        return true;
    }

    private boolean matchesEnchantments(ItemWrapper w, ItemMeta meta) {
        if (!w.shouldCheckEnchantments()) return true;
        if (meta == null) return false;

        Map<Enchantment, Integer> present = (meta instanceof EnchantmentStorageMeta esm)
                ? esm.getStoredEnchants()
                : meta.getEnchants();
        if (present.isEmpty()) return false;

        for (Entry<Enchantment, Integer> e : w.getEnchantments().entrySet()) {
            if (!present.containsKey(e.getKey())) return false;
            if (e.getValue() != -1 && !Objects.equals(present.get(e.getKey()), e.getValue())) return false;
        }
        return true;
    }

    @SuppressWarnings({"deprecation","removal","rawtypes","unchecked"})
    private boolean matchesPotions(ItemWrapper w, ItemMeta meta) {
        if (!(w.shouldCheckPotionType() || w.shouldCheckPotionExtended() || w.shouldCheckPotionUpgraded())) {
            return true;
        }
        if (!(meta instanceof PotionMeta pm)) return false;

        PotionReflectionInfo info = readPotionMeta(pm);
        if (info == null) return false;

        if (w.shouldCheckPotionType()) {
            if (w.getPotionType() == null || !info.type().equals(w.getPotionType().name())) return false;
        }
        if (w.shouldCheckPotionExtended() && info.extended() != w.getPotionExtended()) return false;
        if (w.shouldCheckPotionUpgraded() && info.upgraded() != w.getPotionUpgraded()) return false;
        return true;
    }

    @SuppressWarnings({"removal","deprecation","rawtypes","unchecked"})
    private PotionReflectionInfo readPotionMeta(PotionMeta pm) {
        try {
            Class<?> potionDataClass = Class.forName("org.bukkit.potion.PotionData");
            Method getBasePotionData = PotionMeta.class.getMethod("getBasePotionData");
            Object pd = getBasePotionData.invoke(pm);
            if (pd == null) return null;

            Method getType = potionDataClass.getMethod("getType");
            Method isExtended = potionDataClass.getMethod("isExtended");
            Method isUpgraded = potionDataClass.getMethod("isUpgraded");

            Object typeEnum = getType.invoke(pd);
            String typeName = (typeEnum instanceof Enum<?> e) ? e.name() : String.valueOf(typeEnum);
            boolean ext = (boolean) isExtended.invoke(pd);
            boolean upg = (boolean) isUpgraded.invoke(pd);
            return new PotionReflectionInfo(typeName, ext, upg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean matchesEnchanted(ItemWrapper w, ItemMeta meta) {
        if (!w.shouldCheckEnchanted()) return true;
        if (meta == null) return false;
        return meta.hasEnchants() ||
                (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants());
    }

    private boolean matchesNbt(ItemWrapper w, ItemStack stack) {
        if (!(w.shouldCheckNbtStrings() || w.shouldCheckNbtInts())) return true;

        ReadableNBT nbtReadable;
        if (USE_COMPONENTS) {
            nbtReadable = NBT.modifyComponents(stack,
                    (java.util.function.Function<ReadWriteNBT, ReadableNBT>)
                            nbt -> nbt.getCompound("minecraft:custom_data"));
            if (nbtReadable == null) return false;
        } else {
            nbtReadable = new NBTItem(stack);
        }

        if (w.shouldCheckNbtStrings() && w.getNbtStrings() != null) {
            for (Entry<String, String> entry : w.getNbtStrings().entrySet()) {
                if (!checkNbtEntryString(entry.getKey(), entry.getValue(), nbtReadable)) return false;
            }
        }

        if (w.shouldCheckNbtInts() && w.getNbtInts() != null) {
            for (Entry<String, Integer> entry : w.getNbtInts().entrySet()) {
                if (!checkNbtEntryInt(entry.getKey(), entry.getValue(), nbtReadable)) return false;
            }
        }
        return true;
    }

    private boolean matchesStrictWrapper(ItemWrapper w, ItemStack stack, ItemMeta meta) {
        if (meta == null) return false;
        if (!w.shouldCheckNameContains()
                && !w.shouldCheckNameEquals()
                && !w.shouldCheckNameStartsWith()
                && meta.hasDisplayName()) return false;
        if (!w.shouldCheckLoreContains() && meta.hasLore()) return false;
        if (!w.shouldCheckDurability() && meta instanceof Damageable d && d.getDamage() != 0) return false;
        if (!w.shouldCheckEnchantments() && !stack.getEnchantments().isEmpty()) return false;
        return true;
    }

    private int handleRemoval(ItemWrapper w, Player p, List<ItemStack> matched, int total) {
        boolean remove = true;
        if (w.shouldCheckAmount()) {
            remove = total >= w.getAmount();
            if (remove) total = w.getAmount();
        }
        if (!remove || matched.isEmpty()) return total;

        int toRemove = w.shouldCheckAmount() ? w.getAmount() : Integer.MAX_VALUE;

        ItemStack[] armor = p.getInventory().getArmorContents();
        for (int i = 0; i < armor.length && toRemove > 0; i++) {
            ItemStack piece = armor[i];
            if (piece != null && matched.contains(piece)) {
                int amt = piece.getAmount();
                if (amt > toRemove) {
                    piece.setAmount(amt - toRemove);
                    toRemove = 0;
                } else {
                    toRemove -= amt;
                    armor[i] = null;
                }
            }
        }
        p.getInventory().setArmorContents(armor);

        ItemStack off = p.getInventory().getItemInOffHand();
        if (off != null && toRemove > 0 && matched.contains(off)) {
            int amt = off.getAmount();
            if (amt > toRemove) {
                off.setAmount(amt - toRemove);
                toRemove = 0;
            } else {
                toRemove -= amt;
                off = null;
            }
            p.getInventory().setItemInOffHand(off);
        }

        if (toRemove > 0) {
            for (int slot = 0; slot < p.getInventory().getSize() && toRemove > 0; slot++) {
                ItemStack current = p.getInventory().getItem(slot);
                if (current == null) continue;
                if (!matched.contains(current)) continue;
                int amt = current.getAmount();
                if (amt > toRemove) {
                    current.setAmount(amt - toRemove);
                    toRemove = 0;
                } else {
                    toRemove -= amt;
                    p.getInventory().clear(slot);
                }
            }
        }
        return total;
    }

    private int getInt(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException ex) { return -1; }
    }

    private ItemWrapper getWrapper(ItemWrapper wrapper, String input, Player p) {
        input = input.replaceAll("__", " ");
        input = resolveAllPlaceholders(p, input);
        if (input.isEmpty()) return wrapper;
        String[] parts = input.split("(?<!\\\\),");
        for (String raw : parts) {
            String part = raw.replace("\\,", ",");
            if (part.isEmpty()) continue;
            parseSinglePart(wrapper, part, p);
        }
        return wrapper;
    }

    private void parseSinglePart(ItemWrapper wrapper, String part, Player p) {
        if (part.startsWith("data:")) {
            String value = resolveAllPlaceholders(p, part.substring(5));
            try {
                int parsed = Integer.parseInt(value);
                wrapper.setDurability((short) parsed);
                wrapper.setCheckDurability(true);
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (part.startsWith("custommodeldata:")) {
            String v = resolveAllPlaceholders(p, part.substring(15));
            int parsed = getInt(v);
            if (parsed >= 0) {
                wrapper.setCustomData(parsed);
                wrapper.setCheckCustomData(true);
            }
            return;
        }
        if (part.startsWith("mat:")) {
            String v = resolveAllPlaceholders(p, part.substring(4));
            wrapper.setType(v);
            wrapper.setCheckType(true);
            return;
        }

        // amtexpr: internal arithmetic expression system
        // Supports wrapper: {cmi_equationint_<expression>}
        if (part.startsWith("amtexpr:")) {
            String rawExpr = part.substring(8).trim();
            rawExpr = resolveAllPlaceholders(p, rawExpr);

            int amount = -1;
            boolean valid = true;

            if (rawExpr.startsWith("{cmi_equationint_") && rawExpr.endsWith("}")) {
                String inner = rawExpr.substring(1, rawExpr.length() - 1); // remove {}
                String exprBody = inner.substring("cmi_equationint_".length());
                exprBody = exprBody.replaceAll("\\{([a-zA-Z0-9_:\\-]+)\\}", "%$1%");
                exprBody = resolveAllPlaceholders(p, exprBody);
                amount = evalExpression(exprBody);
                if (amount < 0) valid = false;
            } else {
                String expr = rawExpr.replaceAll("\\{([a-zA-Z0-9_:\\-]+)\\}", "%$1%");
                expr = resolveAllPlaceholders(p, expr);
                amount = evalExpression(expr);
                if (amount < 0) valid = false;
            }

            if (!valid) {
                wrapper.setAmount(0);
                wrapper.setAmountValid(false);
            } else {
                wrapper.setAmount(amount);
                wrapper.setAmountValid(true);
            }
            wrapper.setCheckAmount(true);
            return;
        }

        if (part.startsWith("amt:")) {
            String v = resolveAllPlaceholders(p, part.substring(4));
            int parsed = getInt(v);
            if (parsed >= 0) {
                wrapper.setAmount(parsed);
                wrapper.setCheckAmount(true);
                wrapper.setAmountValid(true);
            } else {
                wrapper.setAmount(0);
                wrapper.setCheckAmount(true);
                wrapper.setAmountValid(false);
            }
            return;
        }
        if (part.startsWith("namestartswith:")) {
            wrapper.setName(resolveAllPlaceholders(p, part.substring(14)));
            wrapper.setCheckNameStartsWith(true);
            return;
        }
        if (part.startsWith("namecontains:")) {
            wrapper.setName(resolveAllPlaceholders(p, part.substring(13)));
            wrapper.setCheckNameContains(true);
            return;
        }
        if (part.startsWith("nameequals:")) {
            wrapper.setName(resolveAllPlaceholders(p, part.substring(11)));
            wrapper.setCheckNameEquals(true);
            return;
        }
        if (part.startsWith("lorecontains:")) {
            wrapper.setLore(resolveAllPlaceholders(p, part.substring(13)));
            wrapper.setCheckLoreContains(true);
            return;
        }
        if (part.startsWith("loreequals:")) {
            wrapper.setLore(resolveAllPlaceholders(p, part.substring(11)));
            wrapper.setCheckLoreEquals(true);
            return;
        }
        if (part.startsWith("matcontains:")) {
            wrapper.setMaterialString(resolveAllPlaceholders(p, part.substring(12)));
            wrapper.setCheckMaterialContains(true);
            return;
        }
        if (part.startsWith("enchantments:")) {
            String body = resolveAllPlaceholders(p, part.substring(13));
            parseEnchantments(wrapper, p, body);
            return;
        }
        if (part.startsWith("potiontype:")) {
            String v = resolveAllPlaceholders(p, part.substring(11)).toUpperCase(Locale.ROOT);
            try {
                wrapper.setPotionType(PotionType.valueOf(v));
            } catch (IllegalArgumentException ignored) {}
            wrapper.setCheckPotionType(true);
            return;
        }
        if (part.startsWith("potionextended:")) {
            wrapper.setPotionExtended(Boolean.parseBoolean(resolveAllPlaceholders(p, part.substring(15))));
            wrapper.setCheckPotionExtended(true);
            return;
        }
        if (part.startsWith("potionupgraded:")) {
            wrapper.setPotionUpgraded(Boolean.parseBoolean(resolveAllPlaceholders(p, part.substring(15))));
            wrapper.setCheckPotionUpgraded(true);
            return;
        }
        if (part.startsWith("nbtstrings:")) {
            parseNbtStrings(wrapper, p, resolveAllPlaceholders(p, part.substring(11)));
            return;
        }
        if (part.startsWith("nbtints:")) {
            parseNbtInts(wrapper, p, resolveAllPlaceholders(p, part.substring(8)));
            return;
        }
        if (part.startsWith("inslot:")) {
            String v = resolveAllPlaceholders(p, part.substring(7));
            wrapper.setSlot(getInt(v));
            return;
        }
        if (part.startsWith("inhand")) {
            if (part.startsWith("inhand:")) {
                String side = part.substring(7);
                if (side.equals("main")) wrapper.setCheckMainHand(true);
                else if (side.equals("off")) wrapper.setCheckOffHand(true);
            } else {
                wrapper.setCheckMainHand(true);
                wrapper.setCheckOffHand(true);
            }
            return;
        }
        if (part.equals("strict")) {
            wrapper.setIsStrict(true);
            return;
        }
        if (part.equals("enchanted")) {
            wrapper.setCheckEnchanted(true);
        }
    }

    private void parseEnchantments(ItemWrapper wrapper, Player p, String body) {
        HashMap<Enchantment, Integer> enchantments = new HashMap<>();
        String[] enchArray = body.split("(?<!\\\\);");
        for (String s : enchArray) {
            if (s.isEmpty()) continue;
            String[] split = s.split("=");
            String id = resolveAllPlaceholders(p, split[0]);
            int level = -1;
            if (split.length > 1) {
                try {
                    level = Integer.parseInt(resolveAllPlaceholders(p, split[1]));
                } catch (NumberFormatException ignored) {}
            }
            Enchantment ench = resolveEnchantment(id);
            if (ench != null) enchantments.put(ench, level);
        }
        wrapper.setEnchantments(enchantments);
        wrapper.setCheckEnchantments(true);
    }

    private Enchantment resolveEnchantment(String token) {
        NamespacedKey key = NamespacedKey.minecraft(token.toLowerCase(Locale.ROOT));
        Enchantment ench = Enchantment.getByKey(key);
        if (ench != null) return ench;
        return Enchantment.getByName(token.toUpperCase(Locale.ROOT));
    }

    private void parseNbtStrings(ItemWrapper wrapper, Player p, String body) {
        HashMap<String, String> map = new HashMap<>();
        String[] arr = body.split("(?<!\\\\);");
        for (String s : arr) {
            if (s.isEmpty()) continue;
            String[] kv = s.split("=");
            if (kv.length > 1) {
                String k = resolveAllPlaceholders(p, kv[0]);
                String v = resolveAllPlaceholders(p, kv[1]);
                map.put(k, v);
            }
        }
        wrapper.setNbtStrings(map);
        wrapper.setCheckNbtStrings(true);
    }

    private void parseNbtInts(ItemWrapper wrapper, Player p, String body) {
        HashMap<String, Integer> map = new HashMap<>();
        String[] arr = body.split("(?<!\\\\);");
        for (String s : arr) {
            if (s.isEmpty()) continue;
            String[] kv = s.split("=");
            if (kv.length > 1) {
                String k = resolveAllPlaceholders(p, kv[0]);
                String v = resolveAllPlaceholders(p, kv[1]);
                try {
                    map.put(k, Integer.parseInt(v));
                } catch (NumberFormatException ignored) {}
            }
        }
        wrapper.setNbtInts(map);
        wrapper.setCheckNbtInts(true);
    }

    @Override
    public Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("give_enabled", false);
        defaults.put("remove_enabled", false);
        defaults.put("cache_enabled", true);
        defaults.put("cache_max_size", 500);
        return defaults;
    }

    private boolean checkNbtEntryString(String key, String value, ReadableNBT nbt) {
        String[] nested = key.split("\\.\\.");
        if (nested.length > 1) {
            ReadableNBT sub = nbt.getCompound(nested[0]);
            if (sub == null) return false;
            return checkNbtEntryString(String.join("..", Arrays.copyOfRange(nested, 1, nested.length)), value, sub);
        }
        if (!nbt.hasTag(key)) return false;
        return Objects.equals(nbt.getString(key), value);
    }

    private boolean checkNbtEntryInt(String key, int value, ReadableNBT nbt) {
        String[] nested = key.split("\\.\\.");
        if (nested.length > 1) {
            ReadableNBT sub = nbt.getCompound(nested[0]);
            if (sub == null) return false;
            return checkNbtEntryInt(String.join("..", Arrays.copyOfRange(nested, 1, nested.length)), value, sub);
        }
        if (!nbt.hasTag(key)) return false;
        return nbt.getInteger(key) == value;
    }

    private ItemStack[] resolveItemsToCheck(Player p, ItemWrapper wrapper) {
        if (wrapper.shouldCheckMainHand() || wrapper.shouldCheckOffHand()) {
            if (wrapper.shouldCheckMainHand() && wrapper.shouldCheckOffHand()) {
                return new ItemStack[]{
                        p.getInventory().getItem(p.getInventory().getHeldItemSlot()),
                        p.getInventory().getItem(40)
                };
            } else if (wrapper.shouldCheckMainHand()) {
                return new ItemStack[]{p.getInventory().getItem(p.getInventory().getHeldItemSlot())};
            } else {
                return new ItemStack[]{p.getInventory().getItem(40)};
            }
        }
        if (wrapper.getSlot() != -1) {
            return new ItemStack[]{p.getInventory().getItem(wrapper.getSlot())};
        }
        return p.getInventory().getContents();
    }

    // ================= Arithmetic Expression Parser =================

    private int evalExpression(String expr) {
        if (expr == null) return -1;
        expr = expr.replaceAll("\\s+", "");
        if (expr.isEmpty()) return -1;
        try {
            double val = parseExpr(new TokenStream(expr));
            if (Double.isNaN(val) || Double.isInfinite(val)) return -1;
            if (val < 0) val = 0;
            return (int) Math.floor(val);
        } catch (Exception e) {
            return -1;
        }
    }

    private double parseExpr(TokenStream ts) {
        double value = parseTerm(ts);
        while (ts.hasNext()) {
            char c = ts.peek();
            if (c == '+' || c == '-') {
                ts.next();
                double rhs = parseTerm(ts);
                value = (c == '+') ? value + rhs : value - rhs;
            } else break;
        }
        return value;
    }

    private double parseTerm(TokenStream ts) {
        double value = parseFactor(ts);
        while (ts.hasNext()) {
            char c = ts.peek();
            if (c == '*' || c == '/') {
                ts.next();
                double rhs = parseFactor(ts);
                if (c == '*') value *= rhs;
                else value /= rhs;
            } else break;
        }
        return value;
    }

    private double parseFactor(TokenStream ts) {
        if (!ts.hasNext()) throw new IllegalArgumentException("Unexpected end");
        char c = ts.peek();
        if (c == '(') {
            ts.next();
            double v = parseExpr(ts);
            if (!ts.hasNext() || ts.next() != ')') throw new IllegalArgumentException("Missing ')'");
            return v;
        } else if (c == '+' || c == '-') {
            ts.next();
            double v = parseFactor(ts);
            return (c == '-') ? -v : v;
        } else if (Character.isDigit(c)) {
            return parseNumber(ts);
        } else {
            throw new IllegalArgumentException("Unexpected char: " + c);
        }
    }

    private double parseNumber(TokenStream ts) {
        StringBuilder sb = new StringBuilder();
        while (ts.hasNext()) {
            char c = ts.peek();
            if (Character.isDigit(c) || c == '.') {
                sb.append(c);
                ts.next();
            } else break;
        }
        return Double.parseDouble(sb.toString());
    }

    private static class TokenStream {
        private final String s;
        private int i = 0;
        TokenStream(String s) { this.s = s; }
        boolean hasNext() { return i < s.length(); }
        char next() { return s.charAt(i++); }
        char peek() { return s.charAt(i); }
    }
}