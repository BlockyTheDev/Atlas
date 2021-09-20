package cc.funkemunky.api.utils.blockbox;

import cc.funkemunky.api.tinyprotocol.api.ProtocolVersion;
import cc.funkemunky.api.tinyprotocol.reflection.Reflection;
import lombok.Getter;

@Getter
public class BlockBoxManager {
    private BlockBox blockBox;

    public BlockBoxManager() {
        try {
            blockBox = (BlockBox) Reflection.getClass("cc.funkemunky.api.utils.blockbox.boxes.BlockBox"
                    + ProtocolVersion.getGameVersion()
                    .getServerVersion().replaceAll("v", "")).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            System.out.println("There was an error loading BlockBox API for version "
                    + ProtocolVersion.getGameVersion().name());
        }
    }
}
