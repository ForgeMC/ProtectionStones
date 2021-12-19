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

import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.utils.FMCTools;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class GUIScreen {

    public static void openGUI(Player player, PSRegion psRegion, GuiCategory category){
        final Inventory menu = Bukkit.createInventory(player, category.getGuiSize(), category.getGuiName());
        if (category == GuiCategory.HOME) {
            menu.setItem(0, FMCTools.formatItem(Material.ENDER_EYE, 1, (short)0, false, "§aOverview", Arrays.asList("§7Nazwa: §f" + psRegion.getName())));
            menu.setItem(1, FMCTools.formatItem(Material.REDSTONE, 1, (short)0, false, "§aSettings", Arrays.asList("§7Manage your claim settings", "§7like Build, PVP, ...")));
            menu.setItem(2, FMCTools.formatItem(Material.PLAYER_HEAD, 1, (short)0, false, "§aMembers", Arrays.asList("§7View and edit your", "§7claim members.")));
            //menu.setItem(3, FMCTools.formatItem(Material.ITEM_FRAME, 1, (short)0, false, "§aVisualize", psRegion.get ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(4, FMCTools.formatItem(Material.TNT, 1, (short)0, false, "§c§lRemove", Arrays.asList("§7Removes this claim", "§cWarning! §7It can't be undone.")));
            menu.setItem(8, FMCTools.formatItem(Material.BARRIER, 1, (short)0, false, "§cClose", null));
        }
        else if (category == GuiCategory.SETTINGS) {
            Boolean setting_build = false,
                    setting_interact = false,
                    setting_mobs_hostile = false,
                    setting_mobs_passive = false,
                    setting_tnt = false,
                    setting_pvp = false,
                    setting_teleport = false;

            ProtectedRegion wgRegion = psRegion.getWGRegion();
            if(wgRegion.getFlag(Flags.BUILD).equals(StateFlag.State.ALLOW)){
                setting_build = true;
            }
            if(wgRegion.getFlag(Flags.INTERACT).equals(StateFlag.State.ALLOW)){
                setting_interact = true;
            }
            if(wgRegion.getFlag(Flags.MOB_DAMAGE).equals(StateFlag.State.ALLOW)){
                setting_mobs_hostile = true;
            }
            if(wgRegion.getFlag(Flags.DAMAGE_ANIMALS).equals(StateFlag.State.ALLOW)){
                setting_mobs_passive = true;
            }
            if(wgRegion.getFlag(Flags.TNT).equals(StateFlag.State.ALLOW)){
                setting_tnt = true;
            }
            if(wgRegion.getFlag(Flags.PVP).equals(StateFlag.State.ALLOW)){
                setting_pvp = true;
            }
            if(wgRegion.getFlag(Flags.ENDERPEARL).equals(StateFlag.State.ALLOW)){
                setting_teleport = true;
            }
            //TODO
            menu.setItem(0, FMCTools.formatItem(Material.BRICK, 1, (short)0, setting_build, "§eBuilding", setting_build ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(1, FMCTools.formatItem(Material.REDSTONE, 1, (short)0, setting_interact, "§eInteracting", setting_interact ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(2, FMCTools.formatItem(Material.ROTTEN_FLESH, 1, (short)0, setting_mobs_hostile, "§eHostile mobs hitting", setting_mobs_hostile ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(3, FMCTools.formatItem(Material.BEEF, 1, (short)0, setting_mobs_passive, "§ePassive mobs hitting", setting_mobs_passive ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(4, FMCTools.formatItem(Material.TNT, 1, (short)0, setting_tnt, "§eTNT igniting", setting_tnt ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(5, FMCTools.formatItem(Material.IRON_SWORD, 1, (short)0, setting_pvp, "§ePVP", setting_pvp ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(6, FMCTools.formatItem(Material.ENDER_PEARL, 1, (short)0, setting_teleport, "§eTeleport", setting_teleport ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            menu.setItem(8, FMCTools.formatItem(Material.RED_BED, 1, (short)0, false, "§cReturn to home", null));

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
            Player player = Bukkit.getServer().getPlayer(uuid);
            final ItemStack skull = FMCTools.formatItem(Material.PLAYER_HEAD, 1, (short)0, false, "§a" + player.getName(), Arrays.asList("§7Click to kick this player"));
            final SkullMeta skullMeta = (SkullMeta)skull.getItemMeta();
            skullMeta.setOwner(uuid.toString());
            skull.setItemMeta(skullMeta);
            menu.setItem(count, skull);
        }
    }

}
