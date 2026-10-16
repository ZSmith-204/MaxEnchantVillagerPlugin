package ca.zacksmith.villagerRework;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class villagerRework extends JavaPlugin implements Listener {

    private static final NamespacedKey CUSTOM_EGG_KEY = new NamespacedKey("villagerrework", "custom_librarian_tag");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        createCustomSpawnEggRecipe();
        getLogger().info(getDescription().getName() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(getDescription().getName() + " has been disabled!");
    }

    private void createCustomSpawnEggRecipe() {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Master Librarian Spawn Egg");
            meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Spawns a custom Librarian with maxed books & boosted attributes!"));
            meta.getPersistentDataContainer().set(CUSTOM_EGG_KEY, PersistentDataType.BYTE, (byte) 1);
            egg.setItemMeta(meta);
        }

        NamespacedKey recipeKey = new NamespacedKey("villagerrework", "master_librarian_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, egg);

        recipe.shape("BEB", "BDB", "BEB");
        recipe.setIngredient('E', Material.EMERALD);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('B', Material.BOOKSHELF);

        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.VILLAGER_SPAWN_EGG) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Byte isCustom = meta.getPersistentDataContainer().get(CUSTOM_EGG_KEY, PersistentDataType.BYTE);
        if (isCustom == null || isCustom != 1) return;

        // Prevent standard spawn and handle it customly to ensure tag verification works
        event.setCancelled(true);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            Block targetBlock = clickedBlock.getRelative(event.getBlockFace());
            Villager villager = (Villager) targetBlock.getWorld().spawnEntity(
                    targetBlock.getLocation().add(0.5, 0, 0.5), 
                    EntityType.VILLAGER
            );
            setupCustomLibrarian(villager);
        }
    }

    private void setupCustomLibrarian(Villager villager) {
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerLevel(5);
        villager.setVillagerExperience(9999);
        villager.setCustomName(ChatColor.LIGHT_PURPLE + "Master Librarian");
        villager.setCustomNameVisible(true);

        // Modern attribute assignment (Paper 1.21+ style without GENERIC_ prefix)
        AttributeInstance healthAttr = villager.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(40.0);
            villager.setHealth(40.0);
        }

        AttributeInstance speedAttr = villager.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.3);
        }

        // Generate all max-level enchantment trades for 10 emeralds
        List<MerchantRecipe> trades = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            int maxLevel = enchantment.getMaxLevel();

            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            if (meta != null) {
                meta.addStoredEnchant(enchantment, maxLevel, true);
                book.setItemMeta(meta);
            }

            ItemStack cost = new ItemStack(Material.EMERALD, 10);

            MerchantRecipe recipe = new MerchantRecipe(book, 0, 999999, true, 1, 0.05F);
            recipe.addIngredient(cost);

            trades.add(recipe);
        }

        villager.setRecipes(trades);
    }
}