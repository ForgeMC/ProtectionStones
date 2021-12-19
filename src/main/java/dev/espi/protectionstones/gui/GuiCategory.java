package dev.espi.protectionstones.gui;

public enum GuiCategory {
    HOME("Claim Manager - Home", 9),
    SETTINGS("Claim Manager - Settings", 18),
    MEMBERS("Claim Manager - Members", 54),
    DELETE_CONFIRM("Are you sure?", 27);

    private String guiName;
    private int guiSize;

    GuiCategory(final String guiName, final int guiSize) {
        this.guiName = guiName;
        this.guiSize = guiSize;
    }

    public String getGuiName() {
        return this.guiName;
    }

    public int getGuiSize() {
        return this.guiSize;
    }
}

