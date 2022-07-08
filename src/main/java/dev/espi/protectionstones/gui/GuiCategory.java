package dev.espi.protectionstones.gui;

public enum GuiCategory {
    HOME("Menu Działki", 9),
    SETTINGS("Ustawienia Działki", 18),
    MEMBERS("Dodani Gracze", 54),
    DELETE_CONFIRM("Potwierdź swoją decyzję", 27);

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

