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

package dev.espi.protectionstones;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.commands.ArgSethome;
import dev.espi.protectionstones.commands.ArgView;
import dev.espi.protectionstones.event.PSCreateEvent;
import dev.espi.protectionstones.event.PSRemoveEvent;
import dev.espi.protectionstones.gui.GUIScreen;
import dev.espi.protectionstones.gui.GuiCategory;
import dev.espi.protectionstones.utils.FMCTools;
import dev.espi.protectionstones.utils.UUIDCache;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class ListenerClass implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUIDCache.removeUUID(p.getUniqueId());
        UUIDCache.removeName(p.getName());
        UUIDCache.storeUUIDNamePair(p.getUniqueId(), p.getName());

        // allow worldguard to resolve all UUIDs to names
        Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> UUIDCache.storeWGProfile(p.getUniqueId(), p.getName()));

        PSPlayer psp = PSPlayer.fromPlayer(p);

        // if by default, players should have protection block placement toggled off
        if (ProtectionStones.getInstance().getConfigOptions().defaultProtectionBlockPlacementOff) {
            ProtectionStones.toggleList.add(p.getUniqueId());
        }

        // tax join message
        if (ProtectionStones.getInstance().getConfigOptions().taxEnabled && ProtectionStones.getInstance().getConfigOptions().taxMessageOnJoin) {
            Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> {
                int amount = 0;
                for (PSRegion psr : psp.getTaxEligibleRegions()) {
                    for (PSRegion.TaxPayment tp : psr.getTaxPaymentsDue()) {
                        amount += tp.getAmount();
                    }
                }

                if (amount != 0) {
                    PSL.msg(psp, PSL.TAX_JOIN_MSG_PENDING_PAYMENTS.msg().replace("%money%", "" + amount));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        BlockHandler.createPSRegion(e);
    }

    // helper method for breaking protection blocks
    // IMPLEMENTATION NOTES: r may be not configured
    private boolean playerBreakProtection(Player p, PSRegion r) {
        PSProtectBlock blockOptions = r.getTypeOptions();

        // check for destroy permission
        if (!p.hasPermission("protectionstones.destroy")) {
            PSL.msg(p, PSL.NO_PERMISSION_DESTROY.msg());
            return false;
        }

        // check if player is owner of region
        if (!r.isOwner(p.getUniqueId()) && !p.hasPermission("protectionstones.superowner")) {
            PSL.msg(p, PSL.NO_REGION_PERMISSION.msg());
            return false;
        }

        // cannot break region being rented (prevents splitting merged regions, and breaking as tenant owner)
        if (r.getRentStage() == PSRegion.RentStage.RENTING && !p.hasPermission("protectionstones.superowner")) {
            PSL.msg(p, PSL.RENT_CANNOT_BREAK_WHILE_RENTING.msg());
            return false;
        }

        // return protection stone if no drop option is off
        if (blockOptions != null && !blockOptions.noDrop) {
            if (!p.getInventory().addItem(blockOptions.createItem()).isEmpty()) {
                // method will return not empty if item couldn't be added
                if (ProtectionStones.getInstance().getConfigOptions().dropItemWhenInventoryFull) {
                    PSL.msg(p, PSL.NO_ROOM_DROPPING_ON_FLOOR.msg());
                    p.getWorld().dropItem(r.getProtectBlock().getLocation(), blockOptions.createItem());
                } else {
                    PSL.msg(p, PSL.NO_ROOM_IN_INVENTORY.msg());
                    return false;
                }
            }
        }

        // check if removing the region and firing region remove event blocked it
        if (!r.deleteRegion(true, p)) {
            if (!ProtectionStones.getInstance().getConfigOptions().allowMergingHoles) { // side case if the removing creates a hole and those are prevented
                PSL.msg(p, PSL.DELETE_REGION_PREVENTED_NO_HOLES.msg());
            }
            return false;
        }

        PSL.msg(p, PSL.NO_LONGER_PROTECTED.msg());
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        // shift-right click block with hand to break
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !e.isBlockInHand()
                && e.getClickedBlock() != null && ProtectionStones.isProtectBlock(e.getClickedBlock())
                && e.getHand() == EquipmentSlot.HAND) {

            PSProtectBlock ppb = ProtectionStones.getBlockOptions(e.getClickedBlock());
            PSRegion r = PSRegion.fromLocation(e.getClickedBlock().getLocation());
            if (ppb.allowShiftRightBreak && e.getPlayer().isSneaking()) {
                if (r != null && playerBreakProtection(e.getPlayer(), r)) { // successful
                    e.getClickedBlock().setType(Material.AIR);
                }
            }
            Player player = e.getPlayer();
            // opening gui; currently work in progress; todo
            //GUIScreen.openGUI(player, r, GuiCategory.HOME);
        }
    }

    @EventHandler
    public void onGuiClick(final InventoryClickEvent event) {
        final Player player = (Player)event.getWhoClicked();
        final Inventory menu = event.getClickedInventory();
        final ItemStack clicked = event.getCurrentItem();
        PSRegion r = PSRegion.fromLocation(player.getLocation());
        if (event.getView().getTitle().equals(GuiCategory.HOME.getGuiName()) || event.getView().getTitle().equals(GuiCategory.SETTINGS.getGuiName()) || event.getView().getTitle().equals(GuiCategory.MEMBERS.getGuiName()) || event.getView().getTitle().equals(GuiCategory.DELETE_CONFIRM.getGuiName())) {
            event.setCancelled(true);
        }
        if (event.getView().getTitle().equals(GuiCategory.HOME.getGuiName())) {
            if (clicked == null){
                event.setCancelled(true);
                return;
            }
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
            }
            else if (clicked.getType() == Material.REDSTONE) {
                GUIScreen.openGUI(player, r, GuiCategory.SETTINGS);
            }
            else if (clicked.getType() == Material.PLAYER_HEAD) {
                GUIScreen.openGUI(player, r, GuiCategory.MEMBERS);
            }
            else if (clicked.getType() == Material.ITEM_FRAME) {
                if (ArgView.visualisedRegions.getOrDefault(player.getUniqueId(), Collections.emptyList()).contains(r.getHome())){
                    ArgView.visualisedRegions.get(player.getUniqueId()).remove(r.getHome());
                }else {
                    if (!ArgView.visualisedRegions.containsKey(player.getUniqueId()))
                        ArgView.visualisedRegions.put(player.getUniqueId(), new ArrayList<>());
                    ArgView.visualisedRegions.get(player.getUniqueId()).add(r.getHome());
                    //menu.setItem(event.getRawSlot(), this.formatItem(Material.ITEM_FRAME, 1, (short)0, false, "§aVisualize", instance.isVisualize().get(player) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.ITEM_FRAME, 1, (short)0, false, "§aVisualize", ArgView.visualisedRegions.getOrDefault(player.getUniqueId(), Collections.emptyList()).contains(PSRegion.fromLocation(player.getLocation()).getHome()) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.TNT) {
                GUIScreen.openGUI(player, r, GuiCategory.DELETE_CONFIRM);
            }
        }
        else if (event.getView().getTitle().equals(GuiCategory.SETTINGS.getGuiName())) {
            if (clicked == null){
                event.setCancelled(true);
                return;
            }
            ProtectedRegion wgRegion = r.getWGRegion();
            if (clicked.getType() == Material.RED_BED) {
                GUIScreen.openGUI(player, r,GuiCategory.HOME);
                return;
            }
            if (clicked.getType() == Material.BRICK) {
                wgRegion.setFlag(Flags.BUILD, wgRegion.getFlag(Flags.BUILD).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.BRICK, 1, (short)0, wgRegion.getFlag(Flags.BUILD).equals(StateFlag.State.ALLOW), "§eBuilding", wgRegion.getFlag(Flags.BUILD).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.REDSTONE) {
                wgRegion.setFlag(Flags.INTERACT, wgRegion.getFlag(Flags.INTERACT).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.REDSTONE, 1, (short)0, wgRegion.getFlag(Flags.INTERACT).equals(StateFlag.State.ALLOW), "§eBuilding", wgRegion.getFlag(Flags.INTERACT).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.ROTTEN_FLESH) {
                wgRegion.setFlag(Flags.MOB_DAMAGE, wgRegion.getFlag(Flags.MOB_DAMAGE).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.ROTTEN_FLESH, 1, (short)0, wgRegion.getFlag(Flags.MOB_DAMAGE).equals(StateFlag.State.ALLOW), "§eHostile mobs hitting", wgRegion.getFlag(Flags.MOB_DAMAGE).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.BEEF) {
                wgRegion.setFlag(Flags.DAMAGE_ANIMALS, wgRegion.getFlag(Flags.DAMAGE_ANIMALS).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.BEEF, 1, (short)0, wgRegion.getFlag(Flags.DAMAGE_ANIMALS).equals(StateFlag.State.ALLOW), "§ePassive mobs hitting", wgRegion.getFlag(Flags.DAMAGE_ANIMALS).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.TNT) {
                wgRegion.setFlag(Flags.TNT, wgRegion.getFlag(Flags.TNT).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.TNT, 1, (short)0, wgRegion.getFlag(Flags.TNT).equals(StateFlag.State.ALLOW), "§eTNT igniting", wgRegion.getFlag(Flags.TNT).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.IRON_SWORD) {
                wgRegion.setFlag(Flags.PVP, wgRegion.getFlag(Flags.PVP).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.IRON_SWORD, 1, (short)0, wgRegion.getFlag(Flags.PVP).equals(StateFlag.State.ALLOW), "§ePVP", wgRegion.getFlag(Flags.PVP).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
            else if (clicked.getType() == Material.ENDER_PEARL) {
                wgRegion.setFlag(Flags.ENDERPEARL, wgRegion.getFlag(Flags.ENDERPEARL).equals(StateFlag.State.ALLOW) ? StateFlag.State.DENY : StateFlag.State.ALLOW);
                menu.setItem(event.getRawSlot(), FMCTools.formatItem(Material.ENDER_PEARL, 1, (short)0, wgRegion.getFlag(Flags.ENDERPEARL).equals(StateFlag.State.ALLOW), "§eTeleport", wgRegion.getFlag(Flags.ENDERPEARL).equals(StateFlag.State.ALLOW) ? Arrays.asList("§aEnabled") : Arrays.asList("§cDisabled")));
            }
        }
        else if (event.getView().getTitle().equals(GuiCategory.MEMBERS.getGuiName())) {
            if (clicked != null) {
                if (clicked.getType() == Material.RED_BED) {
                    GUIScreen.openGUI(player, r, GuiCategory.HOME);
                } else if (clicked.getType() == Material.PLAYER_HEAD) {
                    final SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                    for (UUID member : r.getMembers()) {
                        if (member.equals(meta.getOwningPlayer().getUniqueId())) {
                            r.removeMember(member);
                            break;
                        }
                    }
                    menu.remove(clicked);
                }
            }
        }
        else if (event.getView().getTitle().equals(GuiCategory.DELETE_CONFIRM.getGuiName())) {
            if (clicked == null){
                event.setCancelled(true);
                return;
            }
            if (clicked.getType() == Material.REDSTONE_BLOCK) {
                GUIScreen.openGUI(player, r, GuiCategory.HOME);
            }
            else if (clicked.getType() == Material.EMERALD_BLOCK) {
                player.closeInventory();
                r.deleteRegion(true);
                player.sendMessage("§cYou removed this claim.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block pb = e.getBlock();

        PSProtectBlock blockOptions = ProtectionStones.getBlockOptions(pb);

        // check if block broken is protection stone type
        if (blockOptions == null) return;

        // check if that is actually a protection stone block (owns a region)
        if (!ProtectionStones.isProtectBlock(pb)) {
            // prevent silk touching of protection stone blocks (that aren't holding a region)
            if (blockOptions.preventSilkTouch) {
                ItemStack left = p.getInventory().getItemInMainHand();
                ItemStack right = p.getInventory().getItemInOffHand();
                if (!left.containsEnchantment(Enchantment.SILK_TOUCH) && !right.containsEnchantment(Enchantment.SILK_TOUCH)) {
                    return;
                }
                e.setDropItems(false);
            }
            return;
        }

        PSRegion r = PSRegion.fromLocation(pb.getLocation());

        // break protection
        if (r != null && playerBreakProtection(p, r)) { // successful
            e.setDropItems(false);
            e.setExpToDrop(0);
        } else { // unsuccessful
            e.setCancelled(true);
        }
    }

    // -=-=-=- prevent smelting protection blocks -=-=-=-

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent e) {
        // prevent protect block item to be smelt
        PSProtectBlock options = ProtectionStones.getBlockOptions(e.getSource());
        if (options != null && !options.allowSmeltItem) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnaceBurnItem(FurnaceBurnEvent e) {
        // prevent protect block item to be smelt
        Furnace f = (Furnace) e.getBlock().getState();
        if (f.getInventory().getSmelting() != null) {
            PSProtectBlock options = ProtectionStones.getBlockOptions(f.getInventory().getSmelting());
            PSProtectBlock fuelOptions = ProtectionStones.getBlockOptions(f.getInventory().getFuel());
            if ((options != null && !options.allowSmeltItem) || (fuelOptions != null && !fuelOptions.allowSmeltItem)) {
                e.setCancelled(true);
            }
        }
    }

    // -=-=-=- prevent crafting using protection blocks -=-=-=-

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            PSProtectBlock options = ProtectionStones.getBlockOptions(s);
            if (options != null && !options.allowUseInCrafting) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    // -=-=-=- block changes to protection block related events -=-=-=-

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketEmptyEvent e) {
        Block clicked = e.getBlockClicked();
        BlockFace bf = e.getBlockFace();
        Block check = clicked.getWorld().getBlockAt(clicked.getX() + e.getBlockFace().getModX(), clicked.getY() + bf.getModY(), clicked.getZ() + e.getBlockFace().getModZ());
        if (ProtectionStones.isProtectBlock(check)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (ProtectionStones.isProtectBlock(e.getToBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (ProtectionStones.isProtectBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent e) {
        // unfortunately, the below fix does not really work because Spigot only triggers for the source block, despite
        // what the documentation says: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/block/BlockDropItemEvent.html

        // we want to replace protection blocks that have their protection block broken (ex. signs, banners)
        // the block may not exist anymore, and so we have to recreate the isProtectBlock method here
        BlockState bs = e.getBlockState();
        if (!ProtectionStones.isProtectBlockType(bs.getType().toString())) return;

        RegionManager rgm = WGUtils.getRegionManagerWithWorld(bs.getWorld());
        if (rgm == null) return;

        // check if the block is a source block
        ProtectedRegion br = rgm.getRegion(WGUtils.createPSID(bs.getLocation()));
        if (!ProtectionStones.isPSRegion(br) && PSMergedRegion.getMergedRegion(bs.getLocation()) == null) return;

        PSRegion r = PSRegion.fromLocation(bs.getLocation());
        if (r == null) return;

        // puts the block back
        r.unhide();
        e.setCancelled(true);
    }

    // -=-=- prevent protection block piston effects -=-=-

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        pistonUtil(e.getBlocks(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        pistonUtil(e.getBlocks(), e);
    }

    private void pistonUtil(List<Block> pushedBlocks, BlockPistonEvent e) {
        for (Block b : pushedBlocks) {
            PSProtectBlock cpb = ProtectionStones.getBlockOptions(b);
            if (cpb != null && ProtectionStones.isProtectBlock(b) && cpb.preventPistonPush) {
                e.setCancelled(true);
            }
        }
    }

    // -=-=- prevent protection blocks from exploding -=-=-

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explodeUtil(e.blockList(), e.getBlock().getLocation().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explodeUtil(e.blockList(), e.getLocation().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        // events like ender dragon block break, wither running into block break, etc.
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            if (!blockExplodeUtil(e.getBlock().getWorld(), e.getBlock())) {
                // if block shouldn't be exploded, cancel the event
                e.setCancelled(true);
            }
        }
    }

    private void explodeUtil(List<Block> blockList, World w) {
        // loop through exploded blocks
        for (int i = 0; i < blockList.size(); i++) {
            Block b = blockList.get(i);

            if (ProtectionStones.isProtectBlock(b)) {
                // always remove protection block from exploded list
                blockList.remove(i);
                i--;
            }

            blockExplodeUtil(w, b);
        }
    }

    // returns whether the block is exploded
    private boolean blockExplodeUtil(World w, Block b) {
        if (ProtectionStones.isProtectBlock(b)) {
            String id = WGUtils.createPSID(b.getLocation());
            PSProtectBlock blockOptions = ProtectionStones.getBlockOptions(b);

            // if prevent explode
            if (blockOptions.preventExplode) {
                return false;
            }

            // manually set to air if exploded so there is no natural item drop
            b.setType(Material.AIR);

            // manually add drop
            if (!blockOptions.noDrop) {
                b.getWorld().dropItem(b.getLocation(), blockOptions.createItem());
            }
            // remove region from worldguard if destroy_region_when_explode is enabled
            if (blockOptions.destroyRegionWhenExplode) {
                ProtectionStones.removePSRegion(w, id);
            }
        }
        return true;
    }

    // check player teleporting into region behaviour
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // we only want plugin triggered teleports, ignore natural teleportation
        if (event.getCause() == TeleportCause.ENDER_PEARL || event.getCause() == TeleportCause.CHORUS_FRUIT) return;

        if (event.getPlayer().hasPermission("protectionstones.tp.bypassprevent")) return;

        WorldGuardPlugin wg = WorldGuardPlugin.inst();
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(event.getTo().getWorld());
        BlockVector3 v = BlockVector3.at(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());

        if (rgm != null) {
            // check if player can teleport into region (no region with preventTeleportIn = true)
            ApplicableRegionSet regions = rgm.getApplicableRegions(v);
            if (regions.getRegions().isEmpty()) return;
            boolean foundNoTeleport = false;
            for (ProtectedRegion r : regions) {
                String f = r.getFlag(FlagHandler.PS_BLOCK_MATERIAL);
                if (f != null && ProtectionStones.getBlockOptions(f) != null && ProtectionStones.getBlockOptions(f).preventTeleportIn)
                    foundNoTeleport = true;
                if (r.getOwners().contains(wg.wrapPlayer(event.getPlayer()))) return;
            }

            if (foundNoTeleport) {
                PSL.msg(event.getPlayer(), PSL.REGION_CANT_TELEPORT.msg());
                event.setCancelled(true);
            }
        }
    }

    // -=-=-=- player defined events -=-=-=-

    private void execEvent(String action, CommandSender s, String player, PSRegion region) {
        if (player == null) player = "";

        // split action_type: action
        String[] sp = action.split(": ");
        if (sp.length == 0) return;

        StringBuilder act = new StringBuilder(sp[1]);
        for (int i = 2; i < sp.length; i++) act.append(": ").append(sp[i]); // add anything extra that has a colon

        act = new StringBuilder(act.toString()
                .replace("%player%", player)
                .replace("%world%", region.getWorld().getName())
                .replace("%region%", region.getName() == null ? region.getId() : region.getName() + " (" + region.getId() + ")")
                .replace("%block_x%", region.getProtectBlock().getX() + "")
                .replace("%block_y%", region.getProtectBlock().getY() + "")
                .replace("%block_z%", region.getProtectBlock().getZ() + ""));

        switch (sp[0]) {
            case "player_command":
                if (s != null) Bukkit.getServer().dispatchCommand(s, act.toString());
                break;
            case "console_command":
                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), act.toString());
                break;
            case "message":
                if (s != null) s.sendMessage(ChatColor.translateAlternateColorCodes('&', act.toString()));
                break;
            case "global_message":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', act.toString()));
                }
                ProtectionStones.getPluginLogger().info(ChatColor.translateAlternateColorCodes('&', act.toString()));
                break;
            case "console_message":
                ProtectionStones.getPluginLogger().info(ChatColor.translateAlternateColorCodes('&', act.toString()));
                break;
        }
    }

    @EventHandler
    public void onPSCreate(PSCreateEvent event) {
        if (event.isCancelled()) return;
        if (!event.getRegion().getTypeOptions().eventsEnabled) return;

        // run on next tick (after the region is created to allow for edits to the region)
        Bukkit.getServer().getScheduler().runTask(ProtectionStones.getInstance(), () -> {
            // run custom commands (in config)
            for (String action : event.getRegion().getTypeOptions().regionCreateCommands) {
                execEvent(action, event.getPlayer(), event.getPlayer().getName(), event.getRegion());
            }
        });
    }

    @EventHandler
    public void onPSRemove(PSRemoveEvent event) {
        if (event.isCancelled()) return;
        if (event.getRegion().getTypeOptions() == null) return;
        if (!event.getRegion().getTypeOptions().eventsEnabled) return;

        // run custom commands (in config)
        for (String action : event.getRegion().getTypeOptions().regionDestroyCommands) {
            if (event.getPlayer() == null) {
                execEvent(action, null, null, event.getRegion());
            } else {
                execEvent(action, event.getPlayer(), event.getPlayer().getName(), event.getRegion());
            }
        }
    }

}
