package dev.brighten.example.listeners;

import cc.funkemunky.api.tinyprotocol.api.ProtocolVersion;
import cc.funkemunky.api.tinyprotocol.packet.types.enums.WrappedEnumParticle;
import cc.funkemunky.api.utils.Init;
import cc.funkemunky.api.utils.world.BlockData;
import lombok.val;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Collections;

@Init
public class BukkitListeners implements Listener {

    @EventHandler
    public void onPlace(PlayerInteractEvent event) {
        if(event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            val box = BlockData.getData(event.getClickedBlock().getType()).getBox(event.getClickedBlock(),
                    ProtocolVersion.getGameVersion());

            box.draw(WrappedEnumParticle.FLAME, Collections.singleton(event.getPlayer()));
        }
    }
}
