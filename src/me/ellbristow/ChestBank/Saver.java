package me.ellbristow.ChestBank;

import org.bukkit.Location;

public abstract class Saver {
    public abstract void add(Location loc, Bank bank);
    public abstract void removeLocation(Location loc);
    public abstract void updateAccount(String player, String network);
    
    public abstract void close();
    
    public void updateAccount(String player, Bank bank) {
        updateAccount(player, bank.getNetwork());
    }
}
