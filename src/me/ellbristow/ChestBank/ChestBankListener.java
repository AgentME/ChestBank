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
	
    public static ChestBank plugin;

    public ChestBankListener (ChestBank instance) {
        plugin = instance;
    }

    @EventHandler (priority = EventPriority.NORMAL)
    public void onPlayerInteract (PlayerInteractEvent event) {
        if (!event.isCancelled()) { 
            if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                Block block = event.getClickedBlock();
                if ((block.getType().equals(Material.CHEST) || block.getType().equals(Material.ENDER_CHEST)) && plugin.isNetworkBank(block)) {
                    Player player = event.getPlayer();
                    boolean allowed = true;
                    String network = plugin.getNetwork(block);
                    if (plugin.useNetworkPerms == true && (!player.hasPermission("chestbank.use.networks." + network.toLowerCase()) && !player.hasPermission("chestbank.use.networks.*")))
                    {
                        player.sendMessage(ChatColor.RED + "You are not allowed to use ChestBanks on the " + ChatColor.WHITE + network + ChatColor.RED + " network!");
                        allowed = false;
                    } else if (!plugin.useNetworkPerms && !player.hasPermission("chestbank.use.networks")) {
                        player.sendMessage(ChatColor.RED + "You are not allowed to use ChestBanks on named networks!");
                        allowed = false;
                    } else if (plugin.gotVault && plugin.gotEconomy && plugin.useFee != 0 && !player.hasPermission("chestbank.free.use.networks")) {
                        if (plugin.vault.economy.getBalance(player.getName()) < plugin.useFee) {
                            player.sendMessage(ChatColor.RED + "You cannot afford the transaction fee of " + ChatColor.WHITE + plugin.vault.economy.format(plugin.useFee) + ChatColor.RED + "!");
                            allowed = false;
                        }
                    }
                    if (allowed) {
                        Inventory inv = plugin.chestAccounts.get(network + ">>" + player.getName());
                        if (inv != null && inv.getContents().length != 0) {
                            plugin.openInvs.put(player.getName(), network);
                            player.openInventory(inv);
                        } else {
                            inv = Bukkit.createInventory(player, 54, player.getName());
                            plugin.chestAccounts.put(network + ">>" + player.getName(), inv);
                            plugin.setAccounts(plugin.chestAccounts);
                            plugin.openInvs.put(player.getName(), network);
                            player.openInventory(inv);
                        }
                    }
                    event.setCancelled(true);
                } else if ((block.getType().equals(Material.CHEST) || block.getType().equals(Material.ENDER_CHEST)) && plugin.isBankBlock(block)) {
                    Player player = event.getPlayer();
                    if (!player.hasPermission("chestbank.use")) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to use ChestBanks!");
                    }
                    else {
                        boolean allowed = true;
                        if (plugin.gotVault && plugin.gotEconomy && plugin.useFee != 0) {
                            if (plugin.vault.economy.getBalance(player.getName()) < plugin.useFee && !player.hasPermission("chestbank.free.use")) {
                                player.sendMessage(ChatColor.RED + "You cannot afford the transaction fee of " + ChatColor.WHITE + plugin.vault.economy.format(plugin.useFee) + ChatColor.RED + "!");
                                allowed = false;
                            }
                        }
                        if (allowed) {
                            Inventory inv = plugin.chestAccounts.get(player.getName());
                            if (inv != null && inv.getContents().length != 0) {
                                plugin.openInvs.put(player.getName(), "");
                                player.openInventory(inv);
                            } else {
                                inv = Bukkit.createInventory(player, 54, player.getName());
                                plugin.chestAccounts.put(player.getName(), inv);
                                plugin.setAccounts(plugin.chestAccounts);
                                plugin.openInvs.put(player.getName(), "");
                                player.openInventory(inv);
                            }
                        }
                    }
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onInventoryClose (InventoryCloseEvent event) {
        Player player = (Player)event.getPlayer();
        if (plugin.openInvs != null && plugin.openInvs.containsKey(player.getName())) {
            String network = plugin.openInvs.get(event.getPlayer().getName());
            plugin.openInvs.remove(event.getPlayer().getName());
            Inventory inv = event.getInventory();
            int allowed = getAllowedSlots(player);
            if (getUsedSlots(inv) > allowed) {
                player.sendMessage(ChatColor.RED + "Sorry! You may only use " + ChatColor.WHITE + allowed + ChatColor.RED + " ChestBank slot(s)!");
                inv = trimExcess(player, inv);
                player.sendMessage(ChatColor.RED + "Excess items have been returned to you!");
                if (network.equals("")) {
                    plugin.chestAccounts.put(player.getName(), inv);
                } else {
                    plugin.chestAccounts.put(network + ">>" + player.getName(), inv);
                }
            }
            plugin.setAccounts(plugin.chestAccounts);
            plugin.saveChestBanks(player.getName());
            if (plugin.gotVault && plugin.gotEconomy && plugin.useFee != 0) {
                if ((network.equals("") && !player.hasPermission("chestbank.free.use")) || (!network.equals("") && !player.hasPermission("chestbank.free.use.networks"))) {
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

    @EventHandler (priority = EventPriority.NORMAL)
    public void onBlockPlace (BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType().equals(Material.CHEST)) {
            String blockWorld = block.getWorld().getName();
            int blockX = block.getX();
            int blockY = block.getY();
            int blockZ = block.getZ();
            if (plugin.isNetworkBank(block.getWorld().getBlockAt(blockX + 1, blockY, blockZ)) || plugin.isNetworkBank(block.getWorld().getBlockAt(blockX - 1, blockY, blockZ)) || plugin.isNetworkBank(block.getWorld().getBlockAt(blockX, blockY, blockZ + 1)) || plugin.isNetworkBank(block.getWorld().getBlockAt(blockX, blockY, blockZ - 1))) {
                // Find Network
                String network = "";
                Block neighbourBlock = null;
                if (plugin.isNetworkBank(block.getWorld().getBlockAt(blockX + 1, blockY, blockZ))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX + 1, blockY, blockZ);
                    network = plugin.getNetwork(neighbourBlock);
                } else if (plugin.isNetworkBank(block.getWorld().getBlockAt(blockX - 1, blockY, blockZ))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX - 1, blockY, blockZ);
                    network = plugin.getNetwork(neighbourBlock);
                } else if (plugin.isNetworkBank(block.getWorld().getBlockAt(blockX, blockY, blockZ + 1))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX, blockY, blockZ + 1);
                    network = plugin.getNetwork(neighbourBlock);
                } else if (plugin.isNetworkBank(block.getWorld().getBlockAt(blockX, blockY, blockZ - 1))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX, blockY, blockZ - 1);
                    network = plugin.getNetwork(neighbourBlock);
                }
                if (neighbourBlock.getType().equals(Material.ENDER_CHEST)) {
                    return;
                }
                Player player = event.getPlayer();
                if ((plugin.useNetworkPerms && (player.hasPermission("chestbank.create.networks." + network.toLowerCase()) || player.hasPermission("chestbank.create.networks.*"))) || (!plugin.useNetworkPerms && player.hasPermission("chestbank.create.networks"))) {
                    ConfigurationSection networkBank = plugin.banksConfig.getConfigurationSection("networks." + network);
                    String locsList = networkBank.getString("locations", "");
                    String[] bankSplit = locsList.split(";");
                    String newBankList = "";
                    for (String bankBlock : bankSplit) {
                        if (!newBankList.equals("")) {
                            newBankList += ";";
                        }
                        String[] blockLoc = bankBlock.split(":");
                        String oldBlockWorld = blockLoc[0];
                        int oldBlockX = Integer.parseInt(blockLoc[1]);
                        int oldBlockY = Integer.parseInt(blockLoc[2]);
                        int oldBlockZ = Integer.parseInt(blockLoc[3]);
                        newBankList += oldBlockWorld + ":" + oldBlockX + ":" + oldBlockY + ":" + oldBlockZ;
                        if (oldBlockWorld.equals(blockWorld) && (oldBlockX + 1 == blockX || oldBlockX - 1 == blockX || oldBlockZ + 1 == blockZ || oldBlockZ - 1 == blockZ)) {
                            newBankList += ":" + blockX + ":" + blockY + ":" + blockZ;
                        }
                        else if (blockLoc.length == 6) {
                            oldBlockX = Integer.parseInt(blockLoc[4]);
                            oldBlockY = Integer.parseInt(blockLoc[5]);
                            oldBlockZ = Integer.parseInt(blockLoc[6]);
                            newBankList += ":" + oldBlockX + ":" + oldBlockY + ":" + oldBlockZ;
                        }
                    }
                    plugin.banksConfig.set("networks." + network + ".locations", newBankList);
                    plugin.saveChestBanks();
                    player.sendMessage(ChatColor.GOLD + "ChestBank added to " + ChatColor.WHITE + network + ChatColor.GOLD + " Network!");
                } else {
                    player.sendMessage(ChatColor.RED + "You do not have permission to place a chest next to a network Chestbank!");
                    event.setCancelled(true);
                }
                return;
            }
            if (plugin.isBankBlock(block.getWorld().getBlockAt(blockX + 1, blockY, blockZ)) || plugin.isBankBlock(block.getWorld().getBlockAt(blockX - 1, blockY, blockZ)) || plugin.isBankBlock(block.getWorld().getBlockAt(blockX, blockY, blockZ + 1)) || plugin.isBankBlock(block.getWorld().getBlockAt(blockX, blockY, blockZ - 1))) {
                Player player = event.getPlayer();
                Block neighbourBlock = null;
                if (plugin.isBankBlock(block.getWorld().getBlockAt(blockX + 1, blockY, blockZ))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX + 1, blockY, blockZ);
                } else if (plugin.isBankBlock(block.getWorld().getBlockAt(blockX - 1, blockY, blockZ))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX - 1, blockY, blockZ);
                } else if (plugin.isBankBlock(block.getWorld().getBlockAt(blockX, blockY, blockZ + 1))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX, blockY, blockZ + 1);
                } else if (plugin.isBankBlock(block.getWorld().getBlockAt(blockX, blockY, blockZ - 1))) {
                    neighbourBlock = block.getWorld().getBlockAt(blockX, blockY, blockZ - 1);
                }
                if (neighbourBlock.getType().equals(Material.ENDER_CHEST)) {
                    return;
                }
                if (player.hasPermission("chestbank.create")) {
                    String bankList = plugin.banksConfig.getString("banks", "");
                    String[] bankSplit = bankList.split(";");
                    String newBankList = "";
                    for (String bankBlock : bankSplit) {
                        if (!newBankList.equals("")) {
                            newBankList += ";";
                        }
                        String[] blockLoc = bankBlock.split(":");
                        String oldBlockWorld = blockLoc[0];
                        int oldBlockX = Integer.parseInt(blockLoc[1]);
                        int oldBlockY = Integer.parseInt(blockLoc[2]);
                        int oldBlockZ = Integer.parseInt(blockLoc[3]);
                        newBankList += oldBlockWorld + ":" + oldBlockX + ":" + oldBlockY + ":" + oldBlockZ;
                        if (oldBlockWorld.equals(blockWorld) && (oldBlockX + 1 == blockX || oldBlockX - 1 == blockX || oldBlockZ + 1 == blockZ || oldBlockZ - 1 == blockZ)) {
                            newBankList += ":" + blockX + ":" + blockY + ":" + blockZ;
                        }
                        else if (blockLoc.length == 6) {
                            oldBlockX = Integer.parseInt(blockLoc[4]);
                            oldBlockY = Integer.parseInt(blockLoc[5]);
                            oldBlockZ = Integer.parseInt(blockLoc[6]);
                            newBankList += ":" + oldBlockX + ":" + oldBlockY + ":" + oldBlockZ;
                        }
                    }
                    plugin.banksConfig.set("banks", newBankList);
                    plugin.saveChestBanks();
                    player.sendMessage(ChatColor.GOLD + "Chest added to ChestBank!");
                } else {
                    player.sendMessage(ChatColor.RED + "You do not have permission to place a chest next to a Chestbank!");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler (priority = EventPriority.NORMAL)
    public void onBlockExplode (EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        int index = 0;
        Collection<Block> saveBanks = new HashSet<Block>();
        for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block block = it.next();
            if (plugin.isBankBlock(block)) {
                saveBanks.add(block);
            }
            index++;
        }
        if (!saveBanks.isEmpty()) {
                event.blockList().removeAll(saveBanks);
        }
    }
    
    @EventHandler (priority = EventPriority.NORMAL)
    public void onInventoryClick (InventoryClickEvent event) {
        if (!event.isCancelled()) {
            Player player = (Player)event.getWhoClicked();
            if (plugin.openInvs != null && plugin.openInvs.containsKey(player.getName())) {
                if (event.getRawSlot() > 53 && event.getCursor().getTypeId() == 0 && event.getCurrentItem().getTypeId() != 0) {
                    boolean allowed = true;
                    if (plugin.useWhitelist && !player.hasPermission("chestbank.ignore.whitelist")) {
                        allowed = false;
                        int itemId = event.getCurrentItem().getTypeId();
                        for (String whitelistId : plugin.whitelist) {
                            if ((itemId + "").equals(whitelistId)) {
                                allowed = true;
                            }
                        }
                    }
                    if (plugin.useBlacklist && allowed && !player.hasPermission("chestbank.ignore.blacklist")) {
                        int itemId = event.getCurrentItem().getTypeId();
                        for (String blacklistId : plugin.blacklist) {
                            if ((itemId + "").equals(blacklistId)) {
                                allowed = false;
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