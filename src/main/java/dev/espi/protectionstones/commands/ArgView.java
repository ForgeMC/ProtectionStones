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

package dev.espi.protectionstones.commands;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import dev.espi.protectionstones.*;
import dev.espi.protectionstones.utils.Particles;
import dev.espi.protectionstones.utils.RegionTraverse;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.xenondevs.particle.ParticleBuilder;
import xyz.xenondevs.particle.ParticleEffect;
import xyz.xenondevs.particle.data.texture.BlockTexture;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArgView implements PSCommandArg {

    public static HashMap<UUID, List<Location>> visualisedRegions = new HashMap<>();

    private static List<UUID> cooldown = new ArrayList<>();

    public static void startDisplayBordersTask(){
        Bukkit.getScheduler().scheduleAsyncRepeatingTask(
                ProtectionStones.getInstance(),
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (UUID uuid: visualisedRegions.keySet()){
                            Player player = Bukkit.getServer().getPlayer(uuid);
                            if (player == null) continue;
                            for (Location regionHome: visualisedRegions.get(uuid)){
                                PSRegion r = PSRegion.fromLocation(regionHome);
                                if (r == null || !regionHome.getWorld().equals(player.getWorld())){
                                    visualisedRegions.get(uuid).remove(regionHome);
                                    continue;
                                }
                                displayBordersForPlayer(player, r);
                            }
                        }
                    }
                },
                0L,
                2L
        );
    }

    private static void displayBordersForPlayer(Player p, PSRegion r){

        int playerY = p.getLocation().getBlockY();
        AtomicInteger modU = new AtomicInteger(0);
        int minY = r.getWGRegion().getMinimumPoint().getBlockY(), maxY = r.getWGRegion().getMaximumPoint().getBlockY();

        RegionTraverse.traverseRegionEdge(new HashSet<>(r.getWGRegion().getPoints()), Collections.singletonList(r.getWGRegion()), tr -> {
            handlePinkParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+playerY, 0.5+tr.point.getZ()));
            handlePinkParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+minY, 0.5+tr.point.getZ()));
            handlePinkParticle(p, new Location(p.getWorld(), 0.5+tr.point.getX(), 0.5+maxY, 0.5+tr.point.getZ()));
        });
    }

    @Override
    public List<String> getNames() {
        return Collections.singletonList("view");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.view");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        Bukkit.getLogger().info(s.getName() + " " + args[0] + " " + flags);
        Player p = (Player) s;

        PSRegion r = PSRegion.fromLocationGroup(p.getLocation());

        if (!p.hasPermission("protectionstones.view")) {
            PSL.msg(p, PSL.NO_PERMISSION_VIEW.msg());
            return true;
        }
        if (r == null) {
            PSL.msg(p, PSL.NOT_IN_REGION.msg());
            return true;
        }
        if (!p.hasPermission("protectionstones.view.others") && WGUtils.hasNoAccess(r.getWGRegion(), p, WorldGuardPlugin.inst().wrapPlayer(p), true)) {
            PSL.msg(p, PSL.NO_ACCESS.msg());
            return true;
        }
        if (cooldown.contains(p.getUniqueId())) {
            PSL.msg(p, PSL.VIEW_COOLDOWN.msg());
            return true;
        }
        if (visualisedRegions.getOrDefault(p.getUniqueId(), Collections.emptyList()).contains(r.getHome())){
            visualisedRegions.get(p.getUniqueId()).remove(r.getHome());
            return true;
        }
        if (!visualisedRegions.containsKey(p.getUniqueId())){
            visualisedRegions.put(p.getUniqueId(), new ArrayList<>());
        }
        visualisedRegions.get(p.getUniqueId()).add(r.getHome());

        PSL.msg(p, PSL.VIEW_GENERATING.msg());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return null;
    }

    private static boolean handlePinkParticle(Player p, Location l) {
        if (p.getLocation().distance(l) > 100 || Math.abs(l.getY()-p.getLocation().getY()) > 30) return false;
        new ParticleBuilder(ParticleEffect.BLOCK_MARKER,l)
                .setParticleData(new BlockTexture(Material.BARRIER))
                .display(p);
        return true;
    }

    private static boolean handleBlueParticle(Player p, Location l) {
        if (p.getLocation().distance(l) > 60 || Math.abs(l.getY()-p.getLocation().getY()) > 30) return false;
        Particles.persistRedstoneParticle(p, l, new Particle.DustOptions(Color.fromRGB(0, 255, 255), 2), 30);
        return true;
    }

    private static boolean handlePurpleParticle(Player p, Location l) {
        if (p.getLocation().distance(l) > 60 || Math.abs(l.getY()-p.getLocation().getY()) > 30) return false;
        Particles.persistRedstoneParticle(p, l, new Particle.DustOptions(Color.fromRGB(255, 0, 255), 10), 30);
        return true;
    }

    /*
    private static boolean handleFakeBlock(Player p, int x, int y, int z, BlockData tempBlock, List<Block> restore, long delay, long multiplier) {
        if (p.getLocation().distance(new Location(p.getWorld(), x, y, z)) > 100 || Math.abs(y-p.getLocation().getY()) > 30) return false;

        //Particles.persistRedstoneParticle(p, new Location(p.getWorld(), x, y, z), new Particle.DustOptions(Color.fromRGB(0, 127, 255), 1), 30);

        Bukkit.getScheduler().runTaskLater(ProtectionStones.getInstance(), () -> {
            //p.spawnParticle(Particle.REDSTONE, new Location(p.getWorld(), x, y, z), 50, new Particle.DustOptions(Color.fromRGB(0, 127, 255), 1));
            if (p.getWorld().isChunkLoaded(x / 16, z / 16)) {
                restore.add(p.getWorld().getBlockAt(x, y, z));
                p.sendBlockChange(p.getWorld().getBlockAt(x, y, z).getLocation(), tempBlock);
            }
        }, delay * multiplier);
        return true;
    }*/
}
