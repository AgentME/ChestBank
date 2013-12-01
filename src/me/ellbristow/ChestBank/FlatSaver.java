package me.ellbristow.ChestBank;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

class FlatSaver extends Saver {
    private boolean dirty = false;

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
            if (!dirty)
                return;
            
            dirty = false;

            banksConfig.set("version", plugin.getDescription().getVersion());
            
            ConfigurationSection accounts = banksConfig.createSection("accounts");
            for (String key : plugin.chestAccounts.keySet()) {
                Inventory chest = plugin.chestAccounts.get(key);
                ConfigurationSection section = accounts.createSection(key);
                ItemSerialization.saveInventory(chest, section);
            }
            
            banksConfig.set("banks", makeLocationsString(new Bank(null)));
            
            ConfigurationSection networks = banksConfig.createSection("networks");
            for (Bank bank : plugin.banks.getAllBanks()) {
                String networkName = bank.getNetwork();
                if (networkName == null)
                    continue;

                ConfigurationSection network = networks.createSection(networkName);
                network.set("locations", makeLocationsString(bank));
            }
            
            if (asyncStep)
                sched.runTaskAsynchronously(plugin, new SaverAsyncTask());
            else
                (new SaverAsyncTask()).run();
        }
    }

    private class SaverAsyncTask implements Runnable {
        public void run() {
            try {
                banksConfig.save(bankFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void close() {
        (new SaverSyncTask(false)).run();
    }

    public void add(Location loc, Bank bank) {
        dirty = true;
    }

    public void removeLocation(Location loc) {
        dirty = true;
    }

    public void updateAccount(String player, String network) {
        // if ("".equals(network))
        //     network = null;
        dirty = true;
    }
}
