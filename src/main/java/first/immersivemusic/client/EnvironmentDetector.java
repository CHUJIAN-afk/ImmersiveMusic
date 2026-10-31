package first.immersivemusic.client;

import first.immersivemusic.ImmersiveMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/** 根据提前切曲配置决定播放中是否刷新，各项独立以 0.5% 概率采样。 */
public final class EnvironmentDetector {

    private static final RandomSource random = RandomSource.create();
    private static volatile EnvironmentSnapshot snapshot;
    private static ClientLevel level;
    private static LocalPlayer player;
    private static boolean refreshNeeded;
    private static boolean playingLastTick;

    private EnvironmentDetector() {
    }

    public static @Nullable EnvironmentSnapshot current() {
        return snapshot;
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
        } else {
            boolean playing = !MusicManager.isIdle();
            boolean finished = playingLastTick && !playing;
            playingLastTick = playing;
            if (playing && !ImmersiveMusic.CONFIG.allowEarlySwitch.get()) {
                refreshNeeded = true;
            } else if (level != minecraft.level || player != minecraft.player || snapshot == null || refreshNeeded || finished) {
                snapshot = sample(minecraft.level, minecraft.player, null, random);
                level = minecraft.level;
                player = minecraft.player;
                refreshNeeded = false;
            } else if (!minecraft.isPaused()) {
                snapshot = sample(minecraft.level, minecraft.player, snapshot, random);
            }
        }
    }

    public static void clear() {
        snapshot = null;
        level = null;
        player = null;
        refreshNeeded = false;
        playingLastTick = false;
    }

    public static void respawn(ClientPlayerNetworkEvent.Clone event) {
        refreshNeeded = true;
        if (MusicManager.isIdle() || ImmersiveMusic.CONFIG.allowEarlySwitch.get()) {
            var newPlayer = event.getNewPlayer();
            level = (ClientLevel) newPlayer.level();
            player = newPlayer;
            snapshot = sample(level, player, null, random);
            refreshNeeded = false;
        }
    }

    static EnvironmentSnapshot sample(ClientLevel level, LocalPlayer player, @Nullable EnvironmentSnapshot previous, RandomSource random) {
        boolean initial = previous == null;
        boolean biome = initial || random.nextInt(200) == 0;
        boolean dimension = initial || random.nextInt(200) == 0;
        boolean temperature = initial || random.nextInt(200) == 0;
        boolean time = initial || random.nextInt(200) == 0;
        boolean brightness = initial || random.nextInt(200) == 0;
        boolean sky = initial || random.nextInt(200) == 0;
        boolean underwater = initial || random.nextInt(200) == 0;
        boolean height = initial || random.nextInt(200) == 0;
        if (!biome && !dimension && !temperature && !time && !brightness && !sky && !underwater && !height) {
            return previous;
        }
        BlockPos position = player.blockPosition();
        BlockPos eyes = brightness || sky ? BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()) : position;
        return new EnvironmentSnapshot(
                biome ? level.getBiome(position) : previous.biome(),
                dimension ? level.dimension() : previous.dimension(),
                temperature ? level.getBiome(position).value().getBaseTemperature() : previous.temperature(),
                time ? Math.floorMod(level.getDayTime(), 24000L) : previous.dayTime(),
                brightness ? level.getMaxLocalRawBrightness(eyes) : previous.brightness(),
                sky ? level.canSeeSky(eyes) : previous.skyVisible(),
                underwater ? player.isUnderWater() : previous.underwater(),
                height ? player.getY() : previous.height());
    }
}
