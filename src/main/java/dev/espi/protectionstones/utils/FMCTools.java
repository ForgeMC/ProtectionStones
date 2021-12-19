package dev.espi.protectionstones.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class FMCTools {
    public static ItemStack formatItem(final Material itemMaterial, final int itemQuantity, final short itemData, final boolean enchanted, final String itemName, final List<String> uncoloredLore) {
        final ItemStack formattedItem = new ItemStack(itemMaterial, itemQuantity, itemData);
        final ItemMeta formattedItemMeta = formattedItem.getItemMeta();
        if (itemName != null) {
            formattedItemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));
        }
        if (enchanted) {
            formattedItemMeta.addEnchant(Enchantment.DURABILITY, 1, true);
            formattedItemMeta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ENCHANTS });
        }
        formattedItemMeta.addItemFlags(new ItemFlag[] { ItemFlag.HIDE_ATTRIBUTES });
        if (uncoloredLore != null) {
            final List<String> formattedItemLoreColored = new ArrayList<String>();
            for (final String lore : uncoloredLore) {
                formattedItemLoreColored.add(ChatColor.translateAlternateColorCodes('&', lore));
            }
            formattedItemMeta.setLore((List)formattedItemLoreColored);
        }
        formattedItem.setItemMeta(formattedItemMeta);
        return formattedItem;
    }
}
