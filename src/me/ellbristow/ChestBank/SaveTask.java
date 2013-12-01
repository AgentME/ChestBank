package me.ellbristow.ChestBank;

class SaveTask {
    private final String saveString;
    private final String playerName;
    public SaveTask(String saveString, String playerName) {
        this.saveString = saveString;
        this.playerName = playerName;
    }
    public String getSaveString() {
        return saveString;
    }
    public String getPlayerName() {
        return playerName;
    }
}
