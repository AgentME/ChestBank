package me.ellbristow.ChestBank;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

class Banks {
    private boolean useSave = true;
    
    protected Map<Location, Bank> byLocation;
    protected Map<Bank, Set<Location>> byBank;

    private ChestBank plugin;

    public Banks(ChestBank instance) {
        plugin = instance;
        byLocation = new HashMap<Location, Bank>();
        byBank = new HashMap<Bank, Set<Location>>();
    }

    public void add(Location loc, Bank bank) {
        // Make sure we don't save a location that has extra info like yaw or pitch.
        final Location fl = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        byLocation.put(fl, bank);
        Set<Location> set = byBank.get(bank);
        if (set == null) {
            set = new HashSet<Location>();
            byBank.put(bank, set);
        }
        set.add(fl);
        if (useSave)
            plugin.saver.add(fl, bank);
    }

    public boolean removeLocation(Location loc) {
        final Location fl = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Bank bank = byLocation.remove(fl);
        if (bank != null) {
            Set<Location> set = byBank.get(bank);
            if (set.size() <= 1)
                byBank.remove(bank);
            else
                set.remove(fl);
            if (useSave)
                plugin.saver.removeLocation(loc);
            return true;
        }
        return false;
    }

    public Bank getBankAt(Location loc) {
        final Location fl = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        return byLocation.get(fl);
    }

    public Set<Location> getBankLocations(Bank bank) {
        Set<Location> set = byBank.get(bank);
        if (set == null)
            return new HashSet<Location>();
        else
            return new HashSet<Location>(set);
    }

    public Set<Bank> getAllBanks() {
        return byBank.keySet();
    }

    protected void setSave(boolean useSave) {
        this.useSave = useSave;
    }

    public void loadNetworks(ConfigurationSection config) {
        setSave(false);
        Bank bank = new Bank(null);
        String banksString = config.getString("banks");
        if (banksString != null)
            loadLocationsString(bank, banksString);
        
        ConfigurationSection networks = config.getConfigurationSection("networks");
        if (networks != null) {
            for (String networkName : networks.getKeys(false)) {
                ConfigurationSection network = networks.getConfigurationSection(networkName);
                if (network == null)
                    continue;
                loadNetwork(new Bank(networkName), network);
            }
        }
        setSave(true);
    }

    private void loadNetwork(Bank bank, ConfigurationSection network) {
        if (network.isString("locations"))
            loadLocationsString(bank, network.getString("locations"));
        else
            throw new RuntimeException("Invalid value in chests config");
    }

    private void loadLocationsString(Bank bank, String locations) {
        for (String location : locations.split(";")) {
            String[] loc = location.split(":");
            
            if (loc.length >= 4) {
                add(new Location(Bukkit.getServer().getWorld(loc[0]), Integer.parseInt(loc[1]), Integer.parseInt(loc[2]), Integer.parseInt(loc[3])), bank);
                
                if (loc.length == 7)
                    add(new Location(Bukkit.getServer().getWorld(loc[0]), Integer.parseInt(loc[4]), Integer.parseInt(loc[5]), Integer.parseInt(loc[6])), bank);
            }
        }
    }
}
