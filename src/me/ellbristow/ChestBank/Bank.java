package me.ellbristow.ChestBank;

public class Bank {
    private final String network;
    
    public Bank(String network) {
        this.network = network;
    }

    public String getNetwork() {
        return network;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(obj instanceof Bank))
            return false;

        Bank bObj = (Bank) obj;
        return (network == bObj.network) || (network != null && network.equals(bObj.network));
    }

    @Override
    public int hashCode() {
        if (network == null)
            return ":".hashCode();
        else
            return ("::"+network).hashCode();
    }
}
