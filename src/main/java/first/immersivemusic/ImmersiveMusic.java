package first.immersivemusic;

import first.immersivemusic.client.EnvironmentDetector;
import first.immersivemusic.client.MusicManager;
import first.immersivemusic.client.MusicPool;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("immersive_music")
public final class ImmersiveMusic {

    public static final MusicConfig CONFIG = new MusicConfig();

    public ImmersiveMusic(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.registerConfig(ModConfig.Type.CLIENT, CONFIG.spec);
            MusicPool.load();
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    MusicManager.tick();
                    EnvironmentDetector.tick();
                    MusicPool.tick();
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EnvironmentDetector::respawn);
            MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
                MusicManager.clear();
                EnvironmentDetector.clear();
                MusicPool.reset();
            });
            context.getModEventBus().addListener((SoundEngineLoadEvent event) -> {
                MusicManager.clear();
                MusicPool.reset();
            });
        }
    }
}
