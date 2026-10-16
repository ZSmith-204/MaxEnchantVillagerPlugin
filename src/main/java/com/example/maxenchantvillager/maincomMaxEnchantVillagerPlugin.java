package com.example.maxenchantvillager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * MaxEnchantVillagerPlugin is a single-file Paper plugin that introduces:
 * 1. A custom shaped crafting recipe to create a secure "Master Enchanter Spawn Egg".
 * 2. An event listener that intercepts the use of this custom spawn egg.
 * 3. Spawning of a custom Librarian villager loaded with trades for EVERY max-level enchantment for 10 emeralds each.
 */
public final class MaxEnchantVillagerPlugin extends JavaPlugin implements Listener {

    // A unique namespaced key used for Persistent Data Container tagging to prevent anvil forgery exploits.
    private NamespacedKey eggTagKey;

    @Override
    public void onEnable() {
        // Initialize the NamespacedKey using this plugin instance
        this.eggTagKey = new NamespacedKey(this, "is_master_enchanter_egg");

        // Register this class as an event listener so onEggUse() receives player interaction events
        getServer().getPluginManager().registerEvents(this, this);

        // Register the custom crafting recipe upon server startup
        registerSpawnEggRecipe();

        getLogger().info("MaxEnchantVillagerPlugin has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MaxEnchantVillagerPlugin has been disabled.");
    }

    /**
     * Registers a custom shaped crafting recipe for the Master Enchanter Spawn Egg.
     * Recipe Pattern:
     * [Emerald Block] [Book] [Emerald Block]
     * [Book]          [Egg]  [Book]
     * [Emerald Block] [Book] [Emerald Block]
     */
    private void registerSpawnEggRecipe() {
        ItemStack customEgg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = customEgg.getItemMeta();
        
        if (meta != null) {
            // Set custom display name using Adventure components (modern Paper text standard)
            meta.displayName(Component.text("Master Enchanter Egg", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            
            // Add lore describing the egg's purpose
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Spawns a villager with every max-level enchantment book!", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            // BUG FIX (Security): Use PersistentDataContainer to securely tag the item.
            // This prevents players from renaming regular spawn eggs in an anvil to cheat this item.
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(eggTagKey, PersistentDataType.BYTE, (byte) 1);

            customEgg.setItemMeta(meta);
        }

        // Define the recipe key and build the shaped recipe
        NamespacedKey recipeKey = new NamespacedKey(this, "max_enchant_spawn_egg_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, customEgg);

        recipe.shape("EBE", "BSB", "EBE");
        recipe.setIngredient('E', Material.EMERALD_BLOCK);
        recipe.setIngredient('B', Material.BOOK);
        recipe.setIngredient('S', Material.EGG);

        // Add the recipe to the server's recipe registry
        Bukkit.addRecipe(recipe);
    }

    /**
     * Event listener triggered when a player interacts (right-clicks) with items or blocks.
     * Handles spawning the custom villager when the custom spawn egg is used.
     */
    @EventHandler
    public void onEggUse(PlayerInteractEvent event) {
        // BUG FIX: Ensure the interaction is a right-click (either block or air)
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        // BUG FIX: Ensure this code only runs for the main hand to prevent double-firing bugs (Off-hand + Main hand)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.VILLAGER_SPAWN_EGG) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // BUG FIX (Security): Check the PersistentDataContainer tag instead of just checking the display name string.
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(eggTagKey, PersistentDataType.BYTE)) {
            return;
        }

        // Cancel the vanilla villager spawn behavior since we are handling spawning manually
        event.setCancelled(true);

        Player player = event.getPlayer();
        Location spawnLocation;

        // BUG FIX: Safely determine spawn location whether they clicked a block or the air
        if (event.getClickedBlock() != null && event.getBlockFace() != null) {
            spawnLocation = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        } else {
            // If they clicked air, spawn the villager a short distance in front of the player's eye location
            spawnLocation = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2));
            spawnLocation.setY(player.getLocation().getY()); // Keep alignment consistent with floor level
        }

        // Spawn the villager entity at the calculated location
        Villager villager = (Villager) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);

        // Configure villager appearance, title, and profession
        villager.customName(Component.text("Master Enchanter", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true));
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerLevel(5); // Max tier librarian

        // Build the merchant trades list
        List<MerchantRecipe> merchantRecipes = new ArrayList<>();

        // Iterate through every official enchantment registered in modern Paper/Bukkit registries
        Registry.ENCHANTMENT.iterator().forEachRemaining(enchantment -> {
            // Cost: 10 Emeralds per book
            ItemStack cost = new ItemStack(Material.EMERALD, 10);

            // Result: Enchanted book initialized with the maximum level for this specific enchantment
            ItemStack resultBook = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) resultBook.getItemMeta();
            
            if (bookMeta != null) {
                bookMeta.addStoredEnchant(enchantment, enchantment.getMaxLevel(), true);
                resultBook.setItemMeta(bookMeta);
            }

            // Create a merchant recipe with high max uses (virtually unlimited)
            MerchantRecipe merchantRecipe = new MerchantRecipe(resultBook, 99999);
            merchantRecipe.addIngredient(cost);
            
            merchantRecipes.add(merchantRecipe);
        });

        // Assign all generated trades to the villager
        villager.setRecipes(merchantRecipes);

        // BUG FIX: Properly handle item consumption and state sync for Survival/Adventure mode players
        if (player.getGameMode() != GameMode.CREATIVE) {
            int currentAmount = item.getAmount();
            if (currentAmount > 1) {
                item.setAmount(currentAmount - 1);
                player.getInventory().setItemInMainHand(item);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        }
    }
}
