package me.ellbristow.ChestBank;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestBank extends JavaPlugin {

    private HashSet<Byte> trans;

    protected ChestBank plugin;
    protected FileConfiguration config;
    protected HashMap<Integer, Integer> limits = new HashMap<Integer, Integer>();
    protected final ChestBankListener playerListener = new ChestBankListener(this);

    // <Player name, network name ("" if main)>
    // Used to detect where to save inventory when inventory screen is closed.
    protected HashMap<String, String> openInvs = new HashMap<String, String>();
    
    // <Player name|network + ">>" + Player name, inventory>
    // Used to cache all account inventories.
    protected HashMap<String, Inventory> chestAccounts;

    // Stores the mappings of banks and locations
    protected Banks banks;

    protected Saver saver;

    protected boolean useWhitelist = false;
    protected boolean useBlacklist = false;
    protected String[] whitelist = new String[]{"41", "264", "266", "371"};
    protected String[] blacklist = new String[]{"8", "9", "10", "11", "51"};
    protected boolean gotVault = false;
    protected boolean gotEconomy = false;
    protected boolean useNetworkPerms = false;
    protected vaultBridge vault;
    protected double createFee;
    protected double useFee;
    protected boolean useEnderChests;

    @Override
    public void onDisable() {
        saver.close();
    }

    @Override
    public void onEnable() {
        plugin = this;
        PluginManager pm = getServer().getPluginManager();
        
        // List "transparent" blocks"
        trans = new HashSet<Byte>();
        trans.add((byte)Material.AIR.getId());
        trans.add((byte)Material.TORCH.getId());
        trans.add((byte)Material.STONE_PLATE.getId());
        trans.add((byte)Material.WOOD_PLATE.getId());
        trans.add((byte)Material.REDSTONE_TORCH_ON.getId());
        trans.add((byte)Material.REDSTONE_TORCH_OFF.getId());
        trans.add((byte)Material.REDSTONE_WIRE.getId());
        trans.add((byte)Material.TRIPWIRE.getId());
        trans.add((byte)Material.VINE.getId());
        trans.add((byte)Material.STONE_BUTTON.getId());
        trans.add((byte)Material.WOOD_BUTTON.getId());

        config = getConfig();
        int thisLimit;
        thisLimit = config.getInt("normal_limit", 10);
        limits.put(0, thisLimit);
        config.set("normal_limit", thisLimit);
        thisLimit = config.getInt("elevated_limit", 25);
        limits.put(1, thisLimit);
        config.set("elevated_limit", thisLimit);
        thisLimit = config.getInt("vip_limit", 35);
        limits.put(2, thisLimit);
        config.set("vip_limit", thisLimit);
        String[] otherLimits = config.getString("limits", "10,25,35").split(",");
        String newLimits = "";
        int i = 2;
        for (String limit : otherLimits) {
            i++;
            if (!newLimits.equals("")) {
                newLimits += ",";
            }
            newLimits += limit;
            try {
                limits.put(i, Integer.parseInt(limit));
            } catch (NumberFormatException ex) {
                getLogger().severe("Limits config setting must contain numbers separated by commas!");
            }
        }
        config.set("limits", newLimits);
        useWhitelist = config.getBoolean("use_whitelist", false);
        useBlacklist = config.getBoolean("use_blacklist", false);
        useNetworkPerms = config.getBoolean("use_network_perms", false);
        config.set("use_whitelist", useWhitelist);
        config.set("use_blacklist", useBlacklist);
        config.set("use_network_perms", useNetworkPerms);
        String whitelistString = config.getString("whitelist", "41,264,266,371");
        if (useWhitelist) {
            whitelist = whitelistString.split(",");
            whitelistString = "";
            if (whitelist.length != 0) {
                for (String item : whitelist) {
                    if (!"".equals(whitelistString)) {
                        whitelistString += ",";
                    }
                    whitelistString += item;
                }
            }
        }
        config.set("whitelist", whitelistString);
        String blacklistString = config.getString("blacklist", "8,9,10,11,51");
        if (useBlacklist) {
            blacklist = blacklistString.split(",");
            blacklistString = "";
            if (blacklist.length != 0) {
                for (String item : blacklist) {
                    if (!"".equals(blacklistString)) {
                        blacklistString += ",";
                    }
                    blacklistString += item;
                }
            }
        }
        config.set("blacklist", blacklistString);
        
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            gotVault = true;
            getLogger().info("[Vault] found and hooked!");
            vault = new vaultBridge(this);
            gotEconomy = vault.foundEconomy;
            createFee = config.getDouble("creation_fee", 0.0);
            useFee = config.getDouble("transaction_fee", 0.0);
            config.set("creation_fee", createFee);
            config.set("transaction_fee", useFee);
        }
        
        useEnderChests = config.getBoolean("use_ender_chests", false);
        config.set("use_ender_chests", useEnderChests);
        saveConfig();

        
        {
            File bankFile = new File(getDataFolder(), "chests.yml");
            FileConfiguration banksConfig = YamlConfiguration.loadConfiguration(bankFile);
            banks = new Banks(plugin);
            banks.loadNetworks(banksConfig);
            chestAccounts = getAccounts(banksConfig);
            saver = new FlatSaver(plugin, bankFile, banksConfig);
        }
        
        pm.registerEvents(playerListener, this);
        
        try {
            Metrics metrics = new Metrics(this);
            metrics.start();
        } catch (IOException e) {
            // Failed to submit the stats :-(
        }
        
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Sorry! The console can't use this command!");
            return true;
        }

        Player player = (Player) sender;

        /*
         * CHEST
         */

        if (commandLabel.equalsIgnoreCase("chest")) {
            Bank bank;
            if (args.length == 0)
                bank = new Bank(null);
            else
                bank = new Bank(args[0]);
            openNetworkInventory(player, bank);
            return true;
        }

        /*
         * CHESTBANK
         */

        if (args.length == 0) {
            // Command list requested
            PluginDescriptionFile pdfFile = this.getDescription();
            player.sendMessage(ChatColor.GOLD + pdfFile.getName() + " version " + pdfFile.getVersion() + " by " + pdfFile.getAuthors());
            boolean found = false;
            if (player.hasPermission("chestbank.info")) {
                player.sendMessage(ChatColor.GOLD + "  /chestbank info " + ChatColor.GRAY + ": Get targetted ChestBank's info.");
                found = true;
            }
            if (player.hasPermission("chestbank.list")) {
                player.sendMessage(ChatColor.GOLD + "  /chestbank list " + ChatColor.GRAY + ": List all existing ChestBank networks.");
                found = true;
            }
            if (player.hasPermission("chestbank.create")) {
                player.sendMessage(ChatColor.GOLD + "  /chestbank create " + ChatColor.GRAY + ": Make targetted chest a ChestBank.");
                found = true;
            }
            if ((!useNetworkPerms && player.hasPermission("chestbank.create.networks")) || useNetworkPerms) {
                player.sendMessage(ChatColor.GOLD + "  /chestbank create {network}" + ChatColor.GRAY + ": Create a Chestbank on the");
                player.sendMessage(ChatColor.GRAY + "                                        named network.");
                found = true;
            }
            if (player.hasPermission("chestbank.remove")) {
                player.sendMessage(ChatColor.GOLD + "  /chestbank remove " + ChatColor.GRAY + ": Make targetted ChestBank a chest.");
                found = true;
            }
            if (player.hasPermission("chestbank.see")) {
                player.sendMessage(ChatColor.GOLD + "  /chestbank see [player] {network}" + ChatColor.GRAY + ": View player's ChestBank account.");
                found = true;
            }
            if (!found) {
                player.sendMessage(ChatColor.GOLD + "There are no ChestBank commands you can use!");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (!player.hasPermission("chestbank.create")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to create a ChestBank!");
                return true;
            }
            if (args.length > 2) {
                player.sendMessage(ChatColor.RED + "Too many arguments!");
                return true;
            }
            String network = null;
            if (args.length == 2)
                network = args[1];
            
            if (network != null && useNetworkPerms && (!player.hasPermission("chestbank.create.networks." + network.toLowerCase()) && !player.hasPermission("chestbank.create.networks.*"))) {
                player.sendMessage(ChatColor.RED + "You do not have permission to create a ChestBank on the");
                player.sendMessage(ChatColor.WHITE + args[1].toLowerCase() + ChatColor.RED + " network!");
                return true;
            }
            if (network != null && !useNetworkPerms && !player.hasPermission("chestbank.create.networks")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to create a ChestBank on named");
                player.sendMessage(ChatColor.RED + "networks!");
                return true;
            }
            Block block = player.getTargetBlock(trans, 4);
            if (block.getType() != Material.CHEST && block.getType() != Material.ENDER_CHEST) {
                player.sendMessage(ChatColor.RED + "You're not looking at a chest!");
                player.sendMessage(ChatColor.GRAY + "(Found: " + block.getType() + ")");
                return true;
            }
            if (isBankBlock(block)) {
                player.sendMessage(ChatColor.RED + "That is already a ChestBank!");
                return true;
            }
            Block doubleChest = getDoubleChest(block);
            if (useEnderChests && doubleChest != null) {
                player.sendMessage(ChatColor.RED + "You cannot turn a double chest into an Ender ChestBank!");
                return true;
            }
            if (gotVault && gotEconomy && createFee != 0) {
                if ((args.length == 2 && !player.hasPermission("chestbank.free.create.networks")) || (args.length == 1 && !player.hasPermission("chestbank.free.create"))) {
                    if (vault.economy.getBalance(player.getName()) < createFee) {
                        player.sendMessage(ChatColor.RED + "You cannot afford the ChestBank creation fee of");
                        player.sendMessage(ChatColor.WHITE + vault.economy.format(createFee) + ChatColor.RED + "!");
                        return true;
                    }
                }
            }
            
            if (useEnderChests) {
                byte data = block.getData();
                block.setType(Material.AIR);
                block.setType(Material.ENDER_CHEST);
                block.setData(data);
            }
            
            Bank bank = new Bank(network);
            
            banks.add(block.getLocation(), bank);
            if (doubleChest != null)
                banks.add(doubleChest.getLocation(), bank);
            
            if (network == null)
                player.sendMessage(ChatColor.GOLD + "ChestBank created!");
            else
                player.sendMessage(ChatColor.GOLD + "ChestBank created on the " + ChatColor.WHITE + network + ChatColor.GOLD + " network!");
            
            if (gotVault && gotEconomy && createFee != 0 && !player.hasPermission("chestbank.free.create")) {
                vault.economy.withdrawPlayer(player.getName(), createFee);
                player.sendMessage(ChatColor.GOLD + "You were charged " + ChatColor.WHITE + vault.economy.format(createFee) + ChatColor.GOLD + " for ChestBank creation!");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("remove")) {
            if (!player.hasPermission("chestbank.remove")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to remove a ChestBank!");
                return true;
            }
            if (args.length > 1) {
                player.sendMessage(ChatColor.RED + "Too many arguments!");
                return true;
            }
            Block block = player.getTargetBlock(trans, 4);
            Bank bank = getNetwork(block);
            if (bank == null) {
                player.sendMessage(ChatColor.RED + "You're not looking at a ChestBank!");
                player.sendMessage(ChatColor.GRAY + "(Found: " + block.getType() + ")");
                return true;
            }
            String network = bank.getNetwork();
            if (network != null && useNetworkPerms && !player.hasPermission("chestbank.remove.networks." + network.toLowerCase()) && !player.hasPermission("chestbank.remove.networks.*")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to remove a ChestBank on the");
                player.sendMessage(ChatColor.WHITE + network + ChatColor.RED + " network!");
                return true;
            }
            if (network != null && !useNetworkPerms && !player.hasPermission("chestbank.remove.networks")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to remove a ChestBank on named networks!");
                return true;
            }
            Block doubleChest = getDoubleChest(block);
            
            banks.removeLocation(block.getLocation());
            if (doubleChest != null)
                banks.removeLocation(doubleChest.getLocation());
            
            if (network == null)
                player.sendMessage(ChatColor.GOLD + "ChestBank removed!");
            else
                player.sendMessage(ChatColor.GOLD + "ChestBank removed from " + ChatColor.WHITE + network + ChatColor.GOLD + " network!");
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (args.length > 1) {
                player.sendMessage(ChatColor.RED + "Too many arguments!");
                return true;
            }
            if (!player.hasPermission("chestbank.info")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to get ChestBank info!");
                return true;
            }
            Block block = player.getTargetBlock(trans, 4);
            Bank bank = getNetwork(block);
            if (bank == null) {
                player.sendMessage(ChatColor.RED + "This block is not a ChestBank!");
                player.sendMessage(ChatColor.GRAY + "(Found: " + block.getType() + ")");
                return true;
            }
            String network = bank.getNetwork();
            if (network == null) {
                player.sendMessage(ChatColor.GOLD + "This ChestBank is on the main network!");
                return true;
            } else {
                player.sendMessage(ChatColor.GOLD + "This ChestBank is on the " + ChatColor.WHITE + network + ChatColor.GOLD + " network!");
                return true;
            }
        } else if (args[0].equalsIgnoreCase("list")) {
            if (args.length > 1) {
                player.sendMessage(ChatColor.RED + "Too many arguments!");
                return true;
            }
            if (!player.hasPermission("chestbank.list")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to get the ChestBank list!");
                return true;
            }
            
            int mainCount = banks.getBankLocations(new Bank(null)).size();
            player.sendMessage(ChatColor.GOLD + "  Main Network: " + ChatColor.WHITE + mainCount + " Location(s)");
            
            Set<Bank> allbanks = banks.getAllBanks();
            for (Bank bank : allbanks) {
                String networkName = bank.getNetwork();
                if (networkName == null) continue;

                int count = banks.getBankLocations(bank).size();
                player.sendMessage(ChatColor.GOLD + "  " + networkName + " Network: " + ChatColor.WHITE + count + " Location(s)");
            }
            return true;
        } else if (args[0].equalsIgnoreCase("see")) {
            // see [playername [network]]
            
            if (args.length == 1) {
                player.sendMessage(ChatColor.RED + "Please specify a player!");
                return true;
            }

            if (args.length > 3) {
                player.sendMessage(ChatColor.RED + "Too many arguments!");
                return true;
            }
            
            if (!player.hasPermission("chestbank.see")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to access other players' accounts!");
                return true;
            }
            
            OfflinePlayer target = getServer().getOfflinePlayer(args[1]);
            String account = "";
            String accountFail = "";
            if (args.length == 3) {
                String network = args[2];
                if (!player.hasPermission("chestbank.see.networks")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to access other players' " + ChatColor.WHITE + args[2] + ChatColor.GOLD + " network accounts!");
                    return true;
                }
                if (!isNetwork(network)) {
                    player.sendMessage(ChatColor.RED + "There is no ChestBank network called " + ChatColor.WHITE + args[2]);
                    return true;
                }
                account = args[2] + ">>" + target.getName();
                accountFail = " in the " + ChatColor.WHITE + args[2] + ChatColor.RED + " network";
            } else {
                account = target.getName();
            }
            Inventory lc = chestAccounts.get(account);
            if (lc != null) {
                player.openInventory(lc);
            } else {
                player.sendMessage(ChatColor.RED + target.getName() + " does not have a ChestBank account" + accountFail + "!");
            }
            return true;
        }
        return false;
    }

    public boolean isNetwork(String networkName) {
        Set<Location> set = banks.getBankLocations(new Bank(networkName));
        return set != null && set.size() > 0;
    }

    public boolean isBankBlock(Block block) {
        return getNetwork(block) != null;
    }

    public Bank getNetwork(Block block) {
        return getNetwork(block.getLocation());
    }

    public Bank getNetwork(Location location) {
        return banks.getBankAt(location);
    }

    public Block getDoubleChest(Block block) {
        if (block.getType() == Material.CHEST) {
            int blockX = block.getX();
            int blockY = block.getY();
            int blockZ = block.getZ();
            if (block.getWorld().getBlockAt(blockX + 1, blockY, blockZ).getType().equals(Material.CHEST)) {
                return block.getWorld().getBlockAt(blockX + 1, blockY, blockZ);
            }
            if (block.getWorld().getBlockAt(blockX - 1, blockY, blockZ).getType().equals(Material.CHEST)) {
                return block.getWorld().getBlockAt(blockX - 1, blockY, blockZ);
            }
            if (block.getWorld().getBlockAt(blockX, blockY, blockZ + 1).getType().equals(Material.CHEST)) {
                return block.getWorld().getBlockAt(blockX, blockY, blockZ + 1);
            }
            if (block.getWorld().getBlockAt(blockX, blockY, blockZ - 1).getType().equals(Material.CHEST)) {
                return block.getWorld().getBlockAt(blockX, blockY, blockZ - 1);
            }
        }
        return null;
    }

    public HashMap<String, Inventory> getAccounts(FileConfiguration banksConfig) {
        String version = banksConfig.getString("version");
        if (version == null) {
            HashMap<String, Inventory> oldAccounts = getOldAccounts(banksConfig);
            if (!oldAccounts.isEmpty()) {
                getLogger().info("ChestBank account data updated to latest version");
            }
            setAccounts(banksConfig, oldAccounts);
            return oldAccounts;
        } else {
            return getNewAccounts(banksConfig);
        }
    }

    public HashMap<String, Inventory> getNewAccounts(FileConfiguration banksConfig) {
        HashMap<String, Inventory> chests = new HashMap<String, Inventory>();
        ConfigurationSection chestSection = banksConfig.getConfigurationSection("accounts");
        if (chestSection != null) {
            Set<String> fileChests = chestSection.getKeys(false);
            if (fileChests != null) {
                for (String playerName : fileChests) {
                    String account;
                    if (playerName.contains(">>")) {
                        account = playerName.split(">>")[1];
                    } else {
                        account = playerName;
                    }
                    Player player = getServer().getOfflinePlayer(account).getPlayer();
                    Inventory returnInv = Bukkit.createInventory(player, 54, account);
                    returnInv.setMaxStackSize(127);
                    ConfigurationSection chestInv = banksConfig.getConfigurationSection("accounts." + playerName);
                    try {
                        returnInv.setContents(ItemSerialization.loadInventory(chestInv));
                    } catch (InvalidConfigurationException ex) {
                        getLogger().severe("Error fetching inventory data!");
                        ex.printStackTrace();
                    }
                    chests.put(playerName, returnInv);
                }
            }
        }
        return chests;
    }

    public HashMap<String, Inventory> getOldAccounts(FileConfiguration banksConfig) {
        HashMap<String, Inventory> chests = new HashMap<String, Inventory>();
        ConfigurationSection chestSection = banksConfig.getConfigurationSection("accounts");
        if (chestSection != null) {
            Set<String> fileChests = chestSection.getKeys(false);
            if (fileChests != null) {
                for (String playerName : fileChests) {
                    String account;
                    if (playerName.contains(">>")) {
                        account = playerName.split(">>")[1];
                    } else {
                        account = playerName;
                    }
                    Player player = getServer().getOfflinePlayer(account).getPlayer();
                    Inventory returnInv = Bukkit.createInventory(player, 54, account);
                    returnInv.setMaxStackSize(127);
                    String[] chestInv = banksConfig.getString("accounts." + playerName).split(";");
                    int i = 0;
                    for (String items : chestInv) {
                        String[] item = items.split(":", 5);
                        int i0 = Integer.parseInt(item[0]);
                        int i1 = Integer.parseInt(item[1]);
                        short i2 = Short.parseShort(item[2]);
                        if (i0 != 0) {
                            ItemStack stack = new ItemStack(i0, i1, i2);
                            if (item.length >= 4) {
                                if (!item[3].equals("NONE")) {
                                    String[] enchArray = item[3].split(",");
                                    for (String ench : enchArray) {
                                        String[] bits = ench.split("~");
                                        int enchId = Integer.parseInt(bits[0]);
                                        int enchLvl = Integer.parseInt(bits[1]);
                                        stack.addUnsafeEnchantment(Enchantment.getById(enchId), enchLvl);
                                    }
                                }
                                if (item.length >= 5) {
                                    String[] metaSplit = item[4].split(":META:");
                                    if (metaSplit[0].equals("BOOK")) {
                                        BookMeta book = (BookMeta) stack.getItemMeta();
                                        book.setTitle(metaSplit[1]);
                                        book.setAuthor(metaSplit[2]);
                                        String[] pages = metaSplit[3].split(":PAGE:");
                                        for (int p = 0; p < pages.length; p++) {
                                            book.addPage(pages[p]);
                                        }
                                        stack.setItemMeta(book);
                                    } else if (metaSplit[0].equals("ARMOR")) {
                                        LeatherArmorMeta armor = (LeatherArmorMeta) stack.getItemMeta();
                                        armor.setColor(Color.fromRGB(Integer.parseInt(metaSplit[2]), Integer.parseInt(metaSplit[3]), Integer.parseInt(metaSplit[4])));
                                        if (!metaSplit[1].equals("")) {
                                            armor.setDisplayName(metaSplit[1]);
                                        }
                                        stack.setItemMeta(armor);
                                    } else if (metaSplit[0].equals("MAP")) {
                                        MapMeta map = (MapMeta) stack.getItemMeta();
                                        if (!metaSplit[1].equals("")) {
                                            map.setDisplayName(metaSplit[1]);
                                        }
                                        map.setScaling(Boolean.parseBoolean(metaSplit[2]));
                                        stack.setItemMeta(map);
                                    } else if (metaSplit[0].equals("EBOOK")) {
                                        EnchantmentStorageMeta ench = (EnchantmentStorageMeta) stack.getItemMeta();
                                        for (String e : metaSplit[1].split(":ENCH:")) {
                                            String[] enchantment = e.split(":");
                                            ench.addStoredEnchant(Enchantment.getById(Integer.parseInt(enchantment[0])), Integer.parseInt(enchantment[1]), false);
                                        }
                                        stack.setItemMeta(ench);
                                    } else if (metaSplit[0].equals("ITEM") && metaSplit.length > 1) {
                                        stack.getItemMeta().setDisplayName(metaSplit[1]);
                                    }
                                }
                            }
                            returnInv.setItem(i, stack);
                        }
                        i++;
                    }
                    chests.put(playerName, returnInv);
                }
            }
        }
        return chests;
    }
    
    // Old method for converting old data. Will be removed.
    public void setAccounts(FileConfiguration banksConfig, HashMap<String, Inventory> chests) {
        banksConfig.set("version", this.getDescription().getVersion());
        for (String key : chests.keySet()) {
            Inventory chest = chests.get(key);
            ConfigurationSection section = banksConfig.getConfigurationSection("accounts." + key);
            if (section == null) {
                section = banksConfig.createSection("accounts."+key);
            }
            ItemSerialization.saveInventory(chest, section);
        }
    }
    
    protected void openNetworkInventory(Player player, Bank bank) {
        String network = bank.getNetwork();
        String openInvsKey;
        
        if (network == null) {
            if (!player.hasPermission("chestbank.use")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use ChestBanks!");
                return;
            }
            if (plugin.gotVault && plugin.gotEconomy && plugin.useFee != 0) {
                if (plugin.vault.economy.getBalance(player.getName()) < plugin.useFee && !player.hasPermission("chestbank.free.use")) {
                    player.sendMessage(ChatColor.RED + "You cannot afford the transaction fee of " + ChatColor.WHITE + plugin.vault.economy.format(plugin.useFee) + ChatColor.RED + "!");
                    return;
                }
            }
            openInvsKey = player.getName();
        } else {
            if (plugin.useNetworkPerms == true && !player.hasPermission("chestbank.use.networks." + network.toLowerCase()) && !player.hasPermission("chestbank.use.networks.*")) {
                player.sendMessage(ChatColor.RED + "You are not allowed to use ChestBanks on the " + ChatColor.WHITE + network + ChatColor.RED + " network!");
                return;
            }
            if (!plugin.useNetworkPerms && !player.hasPermission("chestbank.use.networks")) {
                player.sendMessage(ChatColor.RED + "You are not allowed to use ChestBanks on named networks!");
                return;
            }
            if (plugin.gotVault && plugin.gotEconomy && plugin.useFee != 0 && !player.hasPermission("chestbank.free.use.networks")) {
                if (plugin.vault.economy.getBalance(player.getName()) < plugin.useFee) {
                    player.sendMessage(ChatColor.RED + "You cannot afford the transaction fee of " + ChatColor.WHITE + plugin.vault.economy.format(plugin.useFee) + ChatColor.RED + "!");
                    return;
                }
            }
            openInvsKey = network + ">>" + player.getName();
        }
        Inventory inv = plugin.chestAccounts.get(openInvsKey);
        if (inv == null) {
            inv = Bukkit.createInventory(player, 54, player.getName());
            plugin.chestAccounts.put(openInvsKey, inv);
        }
        openInvs.put(player.getName(), network == null ? "" : network);
        player.openInventory(inv);
    }
}
