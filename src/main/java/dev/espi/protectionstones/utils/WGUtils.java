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

package dev.espi.protectionstones.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

public class WGUtils {

    // Integer.MAX_VALUE/MIN_VALUE causes strange issues with WG not detecting players in regions,
    // so we use the 16 bit limit, which is more than enough.
    public static final int MAX_BUILD_HEIGHT = Short.MAX_VALUE;
    public static final int MIN_BUILD_HEIGHT = Short.MIN_VALUE;

    public static FlagRegistry getFlagRegistry() {
        return WorldGuard.getInstance().getFlagRegistry();
    }

    public static RegionManager getRegionManagerWithPlayer(Player p) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(p.getWorld()));
    }

    /**
     * Get a RegionManager from a world.
     *
     * @param w the world
     * @return the region manager, or null if it is not found
     */

    public static RegionManager getRegionManagerWithWorld(World w) {
        if (w == null) return null;
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(w));
    }

    /**
     * Get all region managers for all worlds.
     * Use this instead of looping worlds manually because some worlds may not have a region manager.
     *
     * @return returns all region managers from all worlds
     */

    public static HashMap<World, RegionManager> getAllRegionManagers() {
        HashMap<World, RegionManager> m = new HashMap<>();
        for (World w : Bukkit.getWorlds()) {
            RegionManager rgm = getRegionManagerWithWorld(w);
            if (rgm != null) m.put(w, rgm);
        }
        return m;
    }

    // Turn WG region name into a location (ex. ps138x35y358z)
    public static PSLocation parsePSRegionToLocation(String regionName) {
        int psx = Integer.parseInt(regionName.substring(2, regionName.indexOf("x")));
        int psy = Integer.parseInt(regionName.substring(regionName.indexOf("x") + 1, regionName.indexOf("y")));
        int psz = Integer.parseInt(regionName.substring(regionName.indexOf("y") + 1, regionName.length() - 1));
        return new PSLocation(psx, psy, psz);
    }

    // TODO: BUG
    // We have a major issue with detecting adjacent regions when the other regions are PSGroupRegion (the one adjacent
    // to the current one), which is likely a WorldGuard bug?
    //
    // If you create an adjacent region south or east of the region, it seems that it doesn't detect the adjacent edge
    // overlap, you need to increase the edge by one more block (2 blocks from region edge).

    /**
     * Find regions that are overlapping or adjacent to the region given.
     * @param r
     * @param rgm
     * @param w
     * @return the list of regions
     */
    public static Set<ProtectedRegion> findOverlapOrAdjacentRegions(ProtectedRegion r, RegionManager rgm, World w) {
        Set<ProtectedRegion> overlappingRegions =  rgm.getApplicableRegions(r).getRegions();

        // find adjacent regions (not overlapping, but bordering)
        for (var edgeRegion : getTransientEdgeRegions(w, r)) {
            overlappingRegions.addAll(rgm.getApplicableRegions(edgeRegion).getRegions());
        }
        return overlappingRegions;
    }

    /**
     * Find regions that are overlapping or adjacent to the region given.
     * @param r
     * @param regionsToCheck
     * @param w
     * @return the list of regions
     */
    public static Set<ProtectedRegion> findOverlapOrAdjacentRegions(ProtectedRegion r, List<ProtectedRegion> regionsToCheck, World w) {
        Set<ProtectedRegion> overlappingRegions = new HashSet<>(r.getIntersectingRegions(regionsToCheck));

        // find adjacent regions (not overlapping, but bordering)
        for (var edgeRegion : getTransientEdgeRegions(w, r)) {
            overlappingRegions.addAll(edgeRegion.getIntersectingRegions(regionsToCheck));
        }
        return overlappingRegions;
    }

    /**
     * Find the list of regions that border `r` (adjacent to the edge), but do not include the corners.
     * @param r region
     * @return the list of regions
     */
    public static List<ProtectedRegion> getTransientEdgeRegions(World w, ProtectedRegion r) {
        List<ProtectedRegion> toReturn = new ArrayList<>();

        if (r instanceof ProtectedCuboidRegion) {
            BlockVector3 minPoint = r.getMinimumPoint(), maxPoint = r.getMaximumPoint();
            int minX = minPoint.getX(), maxX = maxPoint.getX(), minY = minPoint.getY(),
                maxY = maxPoint.getY(), minZ = minPoint.getZ(), maxZ = maxPoint.getZ();

            toReturn = Arrays.asList(
                new ProtectedCuboidRegion(r.getId() + "-edge-0", true, BlockVector3.at(minX, minY - 1, minZ), BlockVector3.at(maxX, maxY + 1, maxZ)),
                new ProtectedCuboidRegion(r.getId() + "-edge-1", true, BlockVector3.at(minX - 1, minY, minZ), BlockVector3.at(maxX + 1, maxY, maxZ)),
                new ProtectedCuboidRegion(r.getId() + "-edge-2", true, BlockVector3.at(minX, minY, minZ - 1), BlockVector3.at(maxX, maxY, maxZ + 1))
            );

        } else if (r instanceof ProtectedPolygonalRegion) {

            if (ProtectionStones.isPSRegion(r)) {
                PSGroupRegion psr = (PSGroupRegion) PSRegion.fromWGRegion(w, r);
                for (PSMergedRegion psmr : psr.getMergedRegions()) {
                    var testRegion = getDefaultProtectedRegion(psmr.getTypeOptions(), WGUtils.parsePSRegionToLocation(psmr.getId()));
                    toReturn.addAll(getTransientEdgeRegions(w, testRegion));
                }
            }
        }

        return toReturn;
    }

    // whether region overlaps an unowned region that is more priority
    public static boolean overlapsStrongerRegion(World w, ProtectedRegion r, LocalPlayer lp) {
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(w);

        ApplicableRegionSet rp = rgm.getApplicableRegions(r);

        // loop through all regions to check for "none" option
        for (ProtectedRegion rg : rp.getRegions()) {
            if (rg.getId().equals(r.getId())) continue;

            if (ProtectionStones.isPSRegion(rg)) {
                PSRegion psr = PSRegion.fromWGRegion(w, rg);

                // if no overlap allowed by this region type, even if owner
                if (psr.getTypeOptions().allowOtherRegionsToOverlap.equals("none")) {
                    return true;
                }
            }
        }

        if (rgm.overlapsUnownedRegion(r, lp)) { // check if the lp is not owner of a intersecting region
            for (ProtectedRegion rg : rp) {
                // ignore itself it has already has been added to the rgm
                if (rg.getId().equals(r.getId())) continue;
                if (rg.isOwner(lp)) continue;

                if (rg.getPriority() > r.getPriority()) { // if protection priority < overlap priority
                    return true;
                }

                // check ProtectionStones allow_other_regions_to_overlap settings
                if (ProtectionStones.isPSRegion(rg)) {
                    PSRegion pr = PSRegion.fromWGRegion(w, rg);

                    // don't need to check for owner, since all of these are unowned regions.
                    if (pr.isMember(lp.getUniqueId()) && pr.getTypeOptions().allowOtherRegionsToOverlap.equals("member")) {
                        // if members are allowed to overlap this region
                        continue;
                    }
                    if (pr.getTypeOptions().allowOtherRegionsToOverlap.equals("all")) {
                        // if everyone is allowed to overlap this region
                        continue;
                    }
                    // otherwise, this region is not allowed to be overlapped
                    return true;
                } else if (rg.getPriority() >= r.getPriority()) { // if the priorities are the same for plain WorldGuard regions, prevent overlap
                    return true;
                }
            }
        }
        return false;
    }

    // returns "" if there is no psregion
    public static String matchLocationToPSID(Location l) {
        BlockVector3 v = BlockVector3.at(l.getX(), l.getY(), l.getZ());
        String currentPSID = "";
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(l.getWorld());
        List<String> idList = rgm.getApplicableRegionsIDs(v);
        if (idList.size() == 1) { // if the location is only in one region
            if (ProtectionStones.isPSRegionFormat(rgm.getRegion(idList.get(0)))) {
                currentPSID = idList.get(0);
            }
        } else {
            // Get the nearest protection stone if in overlapping region
            double distanceToPS = -1, tempToPS;
            for (String currentID : idList) {
                if (ProtectionStones.isPSRegionFormat(rgm.getRegion(currentID))) {
                    PSLocation psl = WGUtils.parsePSRegionToLocation(currentID);
                    Location psLocation = new Location(l.getWorld(), psl.x, psl.y, psl.z);
                    tempToPS = l.distance(psLocation);
                    if (distanceToPS == -1 || tempToPS < distanceToPS) {
                        distanceToPS = tempToPS;
                        currentPSID = currentID;
                    }
                }
            }
        }
        return currentPSID;
    }

    // remember to call with offsets
    public static BlockVector3 getMinVector(double bx, double by, double bz, long xRadius, long yRadius, long zRadius) {
        if (yRadius == -1) {
            return BlockVector3.at(bx - xRadius, MIN_BUILD_HEIGHT, bz - zRadius);
        } else {
            return BlockVector3.at(bx - xRadius, by - yRadius, bz - zRadius);
        }
    }

    // remember to call with offsets
    public static BlockVector3 getMaxVector(double bx, double by, double bz, long xRadius, long yRadius, long zRadius) {
        if (yRadius == -1) {
            return BlockVector3.at(bx + xRadius, MAX_BUILD_HEIGHT, bz + zRadius);
        } else {
            return BlockVector3.at(bx + xRadius, by + yRadius, bz + zRadius);
        }
    }

    // create PS ids without making the numbers have scientific notation (addressed with long)
    public static String createPSID(double bx, double by, double bz) {
        return "ps" + (long) bx + "x" + (long) by + "y" + (long) bz + "z";
    }

    public static String createPSID(Location l) {
        return createPSID(l.getX(), l.getY(), l.getZ());
    }

    public static boolean hasNoAccess(ProtectedRegion region, Player p, LocalPlayer lp, boolean canBeMember) {
        // Region is not valid
        if (region == null) return true;

        return !p.hasPermission("protectionstones.superowner") && !region.isOwner(lp) && (!canBeMember || !region.isMember(lp));
    }

    // get the overlapping sets of groups of regions a player owns
    public static HashMap<String, ArrayList<String>> getPlayerAdjacentRegionGroups(Player p, RegionManager rm) {
        PSPlayer psp = PSPlayer.fromPlayer(p);

        List<PSRegion> pRegions = psp.getPSRegions(p.getWorld(), false);
        HashMap<String, String> idToGroup = new HashMap<>();
        HashMap<String, ArrayList<String>> groupToIDs = new HashMap<>();

        for (PSRegion r : pRegions) {
            // create fake region to test overlap (to check for adjacent since borders will need to be 1 block larger)
            double fbx = r.getProtectBlock().getLocation().getX(),
                    fby = r.getProtectBlock().getLocation().getY(),
                    fbz = r.getProtectBlock().getLocation().getZ();

            BlockVector3 minT = WGUtils.getMinVector(fbx, fby, fbz, r.getTypeOptions().xRadius + 1, r.getTypeOptions().yRadius + 1, r.getTypeOptions().zRadius + 1);
            BlockVector3 maxT = WGUtils.getMaxVector(fbx, fby, fbz, r.getTypeOptions().xRadius + 1, r.getTypeOptions().yRadius + 1, r.getTypeOptions().zRadius + 1);

            ProtectedRegion td = new ProtectedCuboidRegion("regionOverlapTest", true, minT, maxT);
            ApplicableRegionSet overlapping = rm.getApplicableRegions(td);

            // algorithm to find adjacent regions
            String adjacentGroup = idToGroup.get(r.getId());
            for (ProtectedRegion pr : overlapping) {
                if (ProtectionStones.isPSRegion(pr) && pr.isOwner(WorldGuardPlugin.inst().wrapPlayer(p)) && !pr.getId().equals(r.getId())) {

                    if (adjacentGroup == null) { // if the region hasn't been found to overlap a region yet

                        if (idToGroup.get(pr.getId()) == null) { // if the overlapped region isn't part of a group yet
                            idToGroup.put(pr.getId(), r.getId());
                            idToGroup.put(r.getId(), r.getId());
                            groupToIDs.put(r.getId(), new ArrayList<>(Arrays.asList(pr.getId(), r.getId()))); // create new group
                        } else { // if the overlapped region is part of a group
                            String groupID = idToGroup.get(pr.getId());
                            idToGroup.put(r.getId(), groupID);
                            groupToIDs.get(groupID).add(r.getId());
                        }

                        adjacentGroup = idToGroup.get(r.getId());
                    } else { // if the region is part of a group already

                        if (idToGroup.get(pr.getId()) == null) { // if the overlapped region isn't part of a group
                            idToGroup.put(pr.getId(), adjacentGroup);
                            groupToIDs.get(adjacentGroup).add(pr.getId());
                        } else if (!idToGroup.get(pr.getId()).equals(adjacentGroup)) { // if the overlapped region is part of a group, merge the groups
                            String mergeGroupID = idToGroup.get(pr.getId());
                            for (String gid : groupToIDs.get(mergeGroupID)) {
                                idToGroup.put(gid, adjacentGroup);
                            }
                            groupToIDs.get(adjacentGroup).addAll(groupToIDs.get(mergeGroupID));
                            groupToIDs.remove(mergeGroupID);
                        }

                    }
                }
            }
            if (adjacentGroup == null) {
                idToGroup.put(r.getId(), r.getId());
                groupToIDs.put(r.getId(), new ArrayList<>(Collections.singletonList(r.getId())));
            }
        }
        return groupToIDs;
    }

    public static ProtectedCuboidRegion getDefaultProtectedRegion(PSProtectBlock b, PSLocation v) {
        int bx = v.x, by = v.y, bz = v.z;
        int bxo = b.xOffset, bxy = b.yOffset, bxz = b.zOffset;

        BlockVector3 min = WGUtils.getMinVector(bx + bxo, by + bxy, bz + bxz, b.xRadius, b.yRadius, b.zRadius);
        BlockVector3 max = WGUtils.getMaxVector(bx + bxo, by + bxy, bz + bxz, b.xRadius, b.yRadius, b.zRadius);

        return new ProtectedCuboidRegion(createPSID(bx, by, bz), min, max);
    }

    public static List<BlockVector2> getPointsFromDecomposedRegion(PSRegion r) {
        assert r.getPoints().size() == 4;
        List<Integer> xs = new ArrayList<>(), zs = new ArrayList<>();
        for (BlockVector2 p : r.getPoints()) {
            if (!xs.contains(p.getX())) xs.add(p.getX());
            if (!zs.contains(p.getZ())) zs.add(p.getZ());
        }

        List<BlockVector2> points = new ArrayList<>();
        for (int x = xs.get(0); x != xs.get(1); x += (xs.get(0) > xs.get(1)) ? -1 : 1) {
            points.add(BlockVector2.at(x, zs.get(0)));
            points.add(BlockVector2.at(x, zs.get(1)));
        }
        for (int z = zs.get(0); z != zs.get(1); z += (zs.get(0) > zs.get(1)) ? -1 : 1) {
            points.add(BlockVector2.at(xs.get(0), z));
            points.add(BlockVector2.at(xs.get(1), z));
        }

        return points;
    }

    public static boolean canMergeRegionTypes(PSProtectBlock current, PSRegion mergeInto) {
        if (current.allowedMergingIntoTypes.contains("all"))
            return true;

        if (mergeInto instanceof PSGroupRegion) {
            for (PSMergedRegion r : ((PSGroupRegion) mergeInto).getMergedRegions()) {
                if (!current.allowedMergingIntoTypes.contains(r.getTypeOptions().alias))
                    return false;
            }
        }
        return current.allowedMergingIntoTypes.contains(mergeInto.getTypeOptions().alias);
    }
}
