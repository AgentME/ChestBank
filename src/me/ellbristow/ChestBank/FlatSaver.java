package me.ellbristow.ChestBank;

import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

class FlatSaver extends Saver {
    private boolean allBanksDirty = false;
    private Set<Bank> dirtyBanks = new HashSet<Bank>();
    private Set<String> dirtyAccounts = new HashSet<String>();

    final private ChestBank plugin;
    final private BukkitScheduler sched;
    private File bankFile;
    private FileConfiguration banksConfig;

    public FlatSaver(ChestBank instance, File bankFile, FileConfiguration banksConfig) {
        plugin = instance;
        sched = plugin.getServer().getScheduler();
        this.bankFile = bankFile;
        this.banksConfig = banksConfig;
        
        long ticsPerSave = 60*20;
        sched.runTaskTimer(plugin, new SaverSyncTask(), ticsPerSave, ticsPerSave);
    }

    private String makeLocationsString(Bank bank) {
        StringBuilder banksBuf = new StringBuilder();
        boolean first = true;
        for (Location loc : plugin.banks.getBankLocations(bank)) {
            if (!first)
                banksBuf.append(';');
            else
                first = false;
            banksBuf.append(loc.getWorld().getName()+":"+loc.getBlockX()+":"+loc.getBlockY()+":"+loc.getBlockZ());
        }
        return banksBuf.toString();
    }

    private class SaverSyncTask implements Runnable {
        private boolean asyncStep;

        public SaverSyncTask() {
            this(true);
        }

        public SaverSyncTask(boolean asyncStep) {
            this.asyncStep = asyncStep;
        }

        public void run() {
            if (!allBanksDirty && dirtyBanks.size() == 0 && dirtyAccounts.size() == 0)
                return;
            
            banksConfig.set("version", plugin.getDescription().getVersion());
            
            ConfigurationSection accounts = banksConfig.getConfigurationSection("accounts");
            if (accounts == null)
                accounts = banksConfig.createSection("accounts");
            
            for (String key : dirtyAccounts) {
                Inventory chest = plugin.chestAccounts.get(key);
                ConfigurationSection section = accounts.createSection(key);
                ItemSerialization.saveInventory(chest, section);
            }
            dirtyAccounts.clear();
            
            Set<Bank> banksToDo = dirtyBanks;
            if (allBanksDirty)
                banksToDo = plugin.banks.getAllBanks();
            
            Bank mainnet = new Bank(null);
            if (banksToDo.contains(mainnet))
                banksConfig.set("banks", makeLocationsString(new Bank(null)));
            
            ConfigurationSection networks = banksConfig.getConfigurationSection("networks");
            if (allBanksDirty || networks == null)
                networks = banksConfig.createSection("networks");
            
            for (Bank bank : banksToDo) {
                String networkName = bank.getNetwork();
                if (networkName == null)
                    continue;
                
                ConfigurationSection network = networks.createSection(networkName);
                network.set("locations", makeLocationsString(bank));
            }
            allBanksDirty = false;
            dirtyBanks.clear();
            
            if (asyncStep)
                sched.runTaskAsynchronously(plugin, new Step2());
            else
                (new Step2()).run();
        }
        
        private class Step2 implements Runnable {
            public void run() {
                try {
                    banksConfig.save(bankFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void close() {
        (new SaverSyncTask(false)).run();
    }

    public void add(Location loc, Bank bank) {
        dirtyBanks.add(bank);
    }

    public void removeLocation(Location loc) {
        allBanksDirty = true;
    }

    public void updateAccount(String player, String network) {
        if ("".equals(network))
            network = null;
        
        String account;
        if (network == null)
            account = player;
        else
            account = network + ">>" + player;
        
        dirtyAccounts.add(account);
    }
}
