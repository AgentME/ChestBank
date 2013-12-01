package me.ellbristow.ChestBank;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ItemSerialization {
    public static String saveInventory(Inventory inventory) {
        YamlConfiguration config = new YamlConfiguration();
        
        // Save every element in the list
        saveInventory(inventory, config);
        return config.saveToString();
    }
    
    public static void saveInventory(Inventory inventory, ConfigurationSection destination) {
        // Save every element in the list
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            
            destination.set(Integer.toString(i), item);
        }
    }
    
    public static ItemStack[] loadInventory(String data) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        
        // Load the string
        config.loadFromString(data);
        return loadInventory(config);
    }
    
    public static ItemStack[] loadInventory(ConfigurationSection source) throws InvalidConfigurationException {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        
        try {
            // Try to parse this inventory
            for (String key : source.getKeys(false)) {
                int number = Integer.parseInt(key);
                
                // Size should always be bigger
                while (stacks.size() <= number) {
                    stacks.add(null);
                }
                
                stacks.set(number, (ItemStack) source.get(key));
            }
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException("Expected a number.", e);
        }
        
        // Return result
        return stacks.toArray(new ItemStack[0]);
    }
}
