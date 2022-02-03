package dev.espi.protectionstones;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

public class PlayerLastTimePlayedReader {
    // wymaga essentials na serwie

    // path to essentials userdata (from server dir)
    static final String USERDATA_PATH = "/plugins/Essentials/userdata/";

    static long getLastPlayed(UUID uuid) {
        ArrayList<Long> candidates = new ArrayList<>();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        // standard method
        try {
            candidates.add(player.getLastLogin());
            candidates.add((player.getLastSeen()));
        } catch (NullPointerException e) {
            Bukkit.getLogger().warning("cannot find player " + uuid);
        }
        YamlConfiguration userdata = YamlConfiguration.loadConfiguration(new File(
                ProtectionStones.getInstance().getServer().getWorldContainer().getAbsolutePath() + USERDATA_PATH + uuid + ".yml"));
        try {
            candidates.add(userdata.getLong("timestamps.login"));
            candidates.add(userdata.getLong("timestamps.logout"));
            assert candidates.contains(null);
        } catch (Throwable e) {
            Bukkit.getLogger().info("cos losowo wywalilo");
        }
        if (candidates.size() == 0) return 0;
        try {
            return Collections.max(candidates);
        } catch (Throwable e) {
            return 0;
        }
    }
}