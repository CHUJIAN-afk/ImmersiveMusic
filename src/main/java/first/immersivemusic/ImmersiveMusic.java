package first.immersivemusic;

import first.immersivemusic.client.EnvironmentDetector;
import first.immersivemusic.client.MusicManager;
import first.immersivemusic.client.MusicPool;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "immersive_music", dist = Dist.CLIENT)
public final class ImmersiveMusic {

    public static final MusicConfig CONFIG = new MusicConfig();

    public ImmersiveMusic(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CONFIG.spec);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        MusicPool.load();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            MusicManager.tick(event);
            EnvironmentDetector.tick(event);
            MusicPool.tick(event);
        });
        NeoForge.EVENT_BUS.addListener(EnvironmentDetector::respawn);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            MusicManager.clear();
            EnvironmentDetector.clear();
            MusicPool.reset();
        });
        modBus.addListener((SoundEngineLoadEvent event) -> {
            MusicManager.clear();
            MusicPool.reset();
        });
    }
}
