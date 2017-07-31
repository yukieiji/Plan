package main.java.com.djrapitops.plan.data.listeners;

import com.djrapitops.plugin.utilities.player.Gamemode;
import main.java.com.djrapitops.plan.Plan;
import main.java.com.djrapitops.plan.data.cache.DataCacheHandler;
import main.java.com.djrapitops.plan.data.handling.info.GamemodeInfo;
import main.java.com.djrapitops.plan.data.handling.info.WorldInfo;
import main.java.com.djrapitops.plan.utilities.MiscUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import java.util.UUID;

/**
 * Event Listener for PlayerGameModeChangeEvents.
 *
 * @author Rsl1122
 */
public class PlanGamemodeChangeListener implements Listener {

    private final DataCacheHandler handler;

    /**
     * Class Constructor.
     *
     * @param plugin Current instance of Plan
     */
    public PlanGamemodeChangeListener(Plan plugin) {
        handler = plugin.getHandler();

    }

    /**
     * GM Change Event Listener.
     *
     * @param event Fired Event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        long time = MiscUtils.getTime();
        handler.addToPool(new GamemodeInfo(uuid, time, Gamemode.wrap(event.getNewGameMode())));
        handler.addToPool(new WorldInfo(uuid, time, p.getWorld().getName()));
    }
}
