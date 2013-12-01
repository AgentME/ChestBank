package me.ellbristow.ChestBank;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ChestBankListener implements Listener {
	
    private ChestBank plugin;

    public ChestBankListener (ChestBank instance) {
        plugin = instance;
    }

    @EventHandler (priority = EventPriority.NORMAL)
    public void onPlayerInteract (PlayerInteractEvent event) {
        if (event.isCancelled())
            return;
        
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || (block.getType() != Material.CHEST && block.getType() != Material.ENDER_CHEST))
            return;
        
        Bank bank = plugin.getNetwork(block);
        if (bank == null)
            return;

        event.setCancelled(true);
        
        Player player = event.getPlayer();
        plugin.openNetworkInventory(player, bank);
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onInventoryClose (InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        
        Player player = (Player)event.getPlayer();
        String network = plugin.openInvs.remove(player.getName());
        if (network != null) {
            Inventory inv = event.getInventory();
            int allowed = getAllowedSlots(player);
            if (getUsedSlots(inv) > allowed) {
                player.sendMessage(ChatColor.RED + "Sorry! You may only use " + ChatColor.WHITE + allowed + ChatColor.RED + " ChestBank slot(s)!");
                inv = trimExcess(player, inv);
                player.sendMessage(ChatColor.RED + "Excess items have been returned to you!");
                if (network.equals(""))
                    plugin.chestAccounts.put(player.getName(), inv);
                else
                    plugin.chestAccounts.put(network + ">>" + player.getName(), inv);
            }
            
            plugin.saver.updateAccount(player.getName(), network);
            
            if (plugin.gotVault && plugin.gotEconomy && plugin.useFee != 0) {
                if (network.equals("") ? (!player.hasPermission("chestbank.free.use")) : (!player.hasPermission("chestbank.free.use.networks"))) {
                    plugin.vault.economy.withdrawPlayer(player.getName(), plugin.useFee);
                    player.sendMessage(ChatColor.GOLD + "Thank you for using ChestBank!");
                    player.sendMessage(ChatColor.GOLD + "This transaction cost you " + ChatColor.WHITE + plugin.vault.economy.format(plugin.useFee) + ChatColor.GOLD + "!");
                }
            }
        }
    }
    
    private int getUsedSlots(Inventory inv) {
        ItemStack[] contents = inv.getContents();
        int count = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getTypeId() != 0) {
                count++;
            }
        }
        return count;
    }
    
    private int getAllowedSlots(Player player) {
        int limit = 54;
        if (player.hasPermission("chestbank.limited.normal")) {
            limit = plugin.limits.get(0);
        }
        if (player.hasPermission("chestbank.limited.elevated")) {
            limit = plugin.limits.get(1);
        }
        if (player.hasPermission("chestbank.limited.vip")) {
            limit = plugin.limits.get(2);
        }
        if (plugin.limits.size() > 3) {
            for (int i = 3; i < plugin.limits.size(); i++) {
                int thisLimit = plugin.limits.get(i);
                if (player.hasPermission("chestbank.limited."+thisLimit)) {
                    limit = thisLimit;
                }
            }
        }
        if (player.hasPermission("chestbank.limited.override")) {
            limit = 54;
        }
        if (limit > 54) {
            limit = 54;
        }
        return limit;
    }
    
    private Inventory trimExcess(Player player, Inventory inv) {
        int allowed = getAllowedSlots(player);
        int newInvCount = 0;
        Inventory newInv = Bukkit.createInventory(player, 54, player.getName());
        for (ItemStack stack : inv.getContents()) {
            if (stack != null) {
                if (newInvCount < allowed) {
                    newInv.setItem(newInvCount, stack);
                    newInvCount++;
                } else {
                    int id = stack.getTypeId();
                    int amount = stack.getAmount();
                    short damage = (short)stack.getDurability();
                    org.bukkit.inventory.ItemStack result = new org.bukkit.inventory.ItemStack(id, amount, damage);
                    Map<Enchantment, Integer> enchantments = stack.getEnchantments();
                    if (!enchantments.isEmpty()) {
                        Set<Enchantment> keys = enchantments.keySet();
                        for (int i = 0; i < enchantments.size(); i++) {
                            Enchantment ench = keys.iterator().next();
                            int enchLvl = enchantments.get(ench);
                            result.addUnsafeEnchantment(ench, enchLvl);
                        }
                    }
                    player.getInventory().addItem(result);
                }
            }
        }
        return newInv;
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onBlockBreak (BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.ENDER_CHEST)) {
            // Chest Broken
            if (plugin.isBankBlock(block)) {
                event.getPlayer().sendMessage(ChatColor.RED + "This is a ChestBank and cannot be destroyed!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler (priority = EventPriority.NORMAL)
    public void onBlockIgnite (BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (block.getType().equals(Material.CHEST) || block.getType().equals(Material.ENDER_CHEST)) {
            // Chest Ignited
            if (plugin.isBankBlock(block)) {
                if (event.getCause().equals(BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "This is a ChestBank and is fireproof!");
                }
                event.setCancelled(true);
            }
        }
    }

    // If a chest is placed near an existing bank chest, we need to make them both bank chests.
    @EventHandler (priority = EventPriority.NORMAL)
    public void onBlockPlace (BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (event.isCancelled() || !block.getType().equals(Material.CHEST))
            return;

        Player player = event.getPlayer();
        Block doubleChest = plugin.getDoubleChest(block);
        if (doubleChest != null) {
            Bank doubleChestBank = plugin.getNetwork(doubleChest);
            if (doubleChestBank != null) {
                String network = doubleChestBank.getNetwork();
                if (network != null) {
                    if (plugin.useNetworkPerms
                        ? (!player.hasPermission("chestbank.create.networks.*") && !player.hasPermission("chestbank.create.networks."+network.toLowerCase()))
                        : (!player.hasPermission("chestbank.create.networks"))) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to place a chest next to a chestbank of that network!");
                        event.setCancelled(true);
                        return;
                    }
                    player.sendMessage(ChatColor.GOLD + "ChestBank added to " + ChatColor.WHITE + network + ChatColor.GOLD + " Network!");
                } else {
                    if (!player.hasPermission("chestbank.create")) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to place a chest next to a Chestbank!");
                        event.setCancelled(true);
                        return;
                    }
                    player.sendMessage(ChatColor.GOLD + "Chest added to ChestBank!");
                }
                plugin.banks.add(block.getLocation(), doubleChestBank);
            }
        }
    }

    @EventHandler (priority = EventPriority.NORMAL)
    public void onBlockExplode (EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        Collection<Block> saveBanks = new HashSet<Block>();
        for (Block block : blocks) {
            if (plugin.isBankBlock(block))
                saveBanks.add(block);
        }
        if (!saveBanks.isEmpty())
            event.blockList().removeAll(saveBanks);
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onInventoryClick (InventoryClickEvent event) {
        if (!event.isCancelled()) {
            Player player = (Player)event.getWhoClicked();
            if (plugin.openInvs.containsKey(player.getName())) {
                if (event.getRawSlot() > 53 && event.getCursor().getTypeId() == 0 && event.getCurrentItem().getTypeId() != 0) {
                    boolean allowed = true;
                    if (plugin.useWhitelist && !player.hasPermission("chestbank.ignore.whitelist")) {
                        allowed = false;
                        int itemId = event.getCurrentItem().getTypeId();
                        for (String whitelistId : plugin.whitelist) {
                            if ((itemId + "").equals(whitelistId)) {
                                allowed = true;
                                break;
                            }
                        }
                    }
                    if (plugin.useBlacklist && allowed && !player.hasPermission("chestbank.ignore.blacklist")) {
                        int itemId = event.getCurrentItem().getTypeId();
                        for (String blacklistId : plugin.blacklist) {
                            if ((itemId + "").equals(blacklistId)) {
                                allowed = false;
                                break;
                            }
                        }
                    }
                    if (!allowed) {
                        player.sendMessage(ChatColor.RED + "You cannot deposit that item in a ChestBank!");
                        event.setCancelled(true);
                    } else {
                        int limit = getAllowedSlots(player);
                        if (getUsedSlots(event.getInventory()) >= limit && limit != 54) {
                            player.sendMessage(ChatColor.RED + "Your ChestBank is Full!");
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }    
}
