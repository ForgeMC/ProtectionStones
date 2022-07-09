/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.espi.protectionstones.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.commands.ArgView;
import dev.espi.protectionstones.utils.FMCTools;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GUIScreen {

    public static void openGUI(Player player, PSRegion psRegion, GuiCategory category){
        final Inventory menu = Bukkit.createInventory(player, category.getGuiSize(), category.getGuiName());
        if (category == GuiCategory.HOME) {
            menu.setItem(0, FMCTools.formatItem(Material.ENDER_EYE, 1, (short) 0, false, "§aDziałka", Arrays.asList("§7Nazwa: §f" + psRegion.getName())));
            menu.setItem(1, FMCTools.formatItem(Material.REDSTONE, 1, (short) 0, false, "§aFlagi", Arrays.asList("§7Ustawisz tutaj flagi ", "§7np. Build, PVP, ...")));
            menu.setItem(2, FMCTools.formatItem(Material.PLAYER_HEAD, 1, (short) 0, false, "§aGracze", Arrays.asList("§7Zarządzaj", "§7dodanymi graczami.")));
            menu.setItem(3, FMCTools.formatItem(Material.ITEM_FRAME, 1, (short) 0, false, "§aZwizualizuj Działkę", ArgView.visualisedRegions.getOrDefault(player.getUniqueId(), Collections.emptyList()).contains(PSRegion.fromLocation(player.getLocation()).getHome()) ? Arrays.asList("§aWłączone") : Arrays.asList("§cWyłączone")));
            menu.setItem(4, FMCTools.formatItem(Material.TNT, 1, (short) 0, false, "§c§lUsuń Działkę", Arrays.asList("§7Usuwa działkę.", "§cOstrożnie!")));
            menu.setItem(8, FMCTools.formatItem(Material.BARRIER, 1, (short) 0, false, "§cWyjdź", null));
        }
        else if (category == GuiCategory.SETTINGS) {
            boolean setting_pvp = false,
                    setting_enderpearl = false,
                    setting_greet = false,
                    setting_sitting = false,
                    setting_chest_access = false,
                    setting_chorus_fruit = false,
                    setting_ice_form = true,
                    setting_snow_fall = true,
                    setting_use_dripleaf = true,
                    setting_crops_growth = true,
                    setting_vine_growth = true;

            ProtectedRegion wgRegion = psRegion.getWGRegion();
            try {
                if (wgRegion.getFlag(Flags.USE_DRIPLEAF).equals(StateFlag.State.DENY)) {
                    setting_use_dripleaf = false;
                }
                if (wgRegion.getFlag(Flags.CHEST_ACCESS).equals(StateFlag.State.ALLOW)) {
                    setting_chest_access = true;
                }
                if (wgRegion.getFlag(Flags.CHORUS_TELEPORT).equals(StateFlag.State.ALLOW)) {
                    setting_chorus_fruit = true;
                }
                if (wgRegion.getFlag(Flags.ICE_FORM).equals(StateFlag.State.DENY)) {
                    setting_ice_form = false;
                }
                if (wgRegion.getFlag(Flags.SNOW_FALL).equals(StateFlag.State.DENY)) {
                    setting_snow_fall = false;
                }
                if (wgRegion.getFlag(Flags.PVP).equals(StateFlag.State.ALLOW)) {
                    setting_pvp = true;
                }
                if (wgRegion.getFlag(Flags.ENDERPEARL).equals(StateFlag.State.ALLOW)) {
                    setting_enderpearl = true;
                }
                if (wgRegion.getFlag(Flags.CHORUS_TELEPORT).equals(StateFlag.State.ALLOW)) {
                    setting_chorus_fruit = true;
                }
                if (wgRegion.getFlag(Flags.CROP_GROWTH).equals(StateFlag.State.DENY)) {
                    setting_crops_growth = false;
                }
                if (wgRegion.getFlag(Flags.VINE_GROWTH).equals(StateFlag.State.DENY)) {
                    setting_vine_growth = false;
                }
                setting_greet = wgRegion.getFlag(Flags.GREET_MESSAGE) != null && !wgRegion.getFlag(Flags.GREET_MESSAGE).equals("");
                if (wgRegion.getFlag(WorldGuard.getInstance().getFlagRegistry().get("sit")).equals(StateFlag.State.ALLOW)) {
                    setting_sitting = true;
                }
            } catch (NullPointerException e) {
                Bukkit.getLogger().warning("dzialka " + wgRegion.getId() + " nie posiada odpowiednich flag!");
                return; // return if invalid
            }
            menu.setItem(0, FMCTools.formatItem(Material.OAK_STAIRS, 1, (short) 0, setting_sitting, "§eSiadanie", setting_sitting ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(1, FMCTools.formatItem(Material.ACACIA_SIGN, 1, (short) 0, setting_greet, "§eWiadomość Powitalna", setting_greet ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            if (player.hasPermission("protectionstones.premium_flags")) {
                menu.setItem(2, FMCTools.formatItem(Material.CHEST, 1, (short) 0, setting_chest_access, "§eDostęp do skrzynek", setting_chest_access ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(3, FMCTools.formatItem(Material.ICE, 1, (short) 0, setting_ice_form, "§eFormowanie się lodu", setting_ice_form ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(4, FMCTools.formatItem(Material.SNOW, 1, (short) 0, setting_snow_fall, "§ePadający śnieg", setting_snow_fall ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(5, FMCTools.formatItem(Material.IRON_SWORD, 1, (short) 0, setting_pvp, "§ePVP", setting_pvp ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(6, FMCTools.formatItem(Material.ENDER_PEARL, 1, (short) 0, setting_enderpearl, "§eTeleport", setting_enderpearl ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(7, FMCTools.formatItem(Material.BIG_DRIPLEAF, 1, (short) 0, setting_use_dripleaf, "§eUżywanie spadkoliści (xd?)", setting_use_dripleaf ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(8, FMCTools.formatItem(Material.BEETROOT_SEEDS, 1, (short) 0, setting_crops_growth, "§eRosnące uprawy", setting_crops_growth ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(9, FMCTools.formatItem(Material.VINE, 1, (short) 0, setting_vine_growth, "§eRosnące pnącza", setting_vine_growth ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(9, FMCTools.formatItem(Material.CHORUS_FRUIT, 1, (short) 0, setting_chorus_fruit, "§eUżywanie owoców refrenu", setting_chorus_fruit ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                menu.setItem(17, FMCTools.formatItem(Material.RED_BED, 1, (short) 0, false, "§cReturn to home", null));
            }

        }
        else if (category == GuiCategory.MEMBERS) {
            fillMembersGui(menu, psRegion);
            menu.setItem(8, FMCTools.formatItem(Material.RED_BED, 1, (short)0, false, "§cReturn to home", null));
        }
        else if (category == GuiCategory.DELETE_CONFIRM) {
            final ItemStack yes = FMCTools.formatItem(Material.EMERALD_BLOCK, 1, (short)0, false, "§a§lYes", null);
            final ItemStack no = FMCTools.formatItem(Material.REDSTONE_BLOCK, 1, (short)0, false, "§c§lNo", null);
            menu.setItem(0, yes);
            menu.setItem(1, yes);
            menu.setItem(2, yes);
            menu.setItem(6, no);
            menu.setItem(7, no);
            menu.setItem(8, no);
            menu.setItem(9, yes);
            menu.setItem(10, yes);
            menu.setItem(11, yes);
            menu.setItem(13, FMCTools.formatItem(Material.TNT, 1, (short)0, false, "§c§lRemove", Arrays.asList("§7Removes this claim", "§cWarning! §7It can't be undone.")));
            menu.setItem(15, no);
            menu.setItem(16, no);
            menu.setItem(17, no);
            menu.setItem(18, yes);
            menu.setItem(19, yes);
            menu.setItem(20, yes);
            menu.setItem(24, no);
            menu.setItem(25, no);
            menu.setItem(26, no);
        }
        player.openInventory(menu);
    }

    private static void fillMembersGui(Inventory menu, PSRegion psRegion) {
        int count = -1;
        for (UUID uuid : psRegion.getMembers()) {
            if (++count == 8) {
                ++count;
            }
            // todo: glowa nie ma tekstury
            OfflinePlayer player = Bukkit.getServer().getOfflinePlayer(uuid);
            final ItemStack skull = FMCTools.formatItem(Material.PLAYER_HEAD, 1, (short)0, false, "§a" + player.getName(), Arrays.asList("§7Click to kick this player"));
            final SkullMeta skullMeta = (SkullMeta)skull.getItemMeta();
            PlayerProfile profile = Bukkit.createProfile(uuid, player.getName());
            skullMeta.setPlayerProfile(profile);
            skull.setItemMeta(skullMeta);
            menu.setItem(count, skull);
        }
    }

}
