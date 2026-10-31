package first.immersivemusic.client;

import com.mojang.logging.LogUtils;
import first.immersivemusic.ImmersiveMusic;
import first.immersivemusic.mixin.SoundEngineAccessor;
import first.immersivemusic.mixin.SoundManagerAccessor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** 一次请求持续播放，切曲时只保留一个主音乐和正在淡出的旧通道。 */
public final class MusicManager {

    private static final List<MusicSound> playing = new ArrayList<>();
    private static MusicSound main;

    private MusicManager() {
    }

    public static void play(Music music) {
        play(music, true);
    }

    static void play(Music music, boolean allowLoop) {
        Objects.requireNonNull(music, "music");
        Minecraft.getInstance().execute(() -> {
            if (main == null || !main.music.equals(music)) {
                if (main != null) {
                    main.retiring = true;
                    main.repeat = false;
                    main.fade(0);
                }
                main = new MusicSound(music, allowLoop);
                playing.add(main);
            } else {
                main.allowLoop = allowLoop;
            }
            update();
        });
    }

    static boolean isIdle() {
        return playing.isEmpty();
    }

    static boolean isAutomaticMain(Music music) {
        return main != null && !main.allowLoop && main.music.equals(music);
    }

    static boolean hasOtherMusic() {
        var manager = Minecraft.getInstance().getSoundManager();
        var engine = ((SoundManagerAccessor) manager).immersiveMusic$getSoundEngine();
        return ((SoundEngineAccessor) engine).immersiveMusic$getInstanceToChannel().entrySet().stream()
                .anyMatch(entry -> entry.getKey().getSource() == SoundSource.MUSIC
                        && !(entry.getKey() instanceof MusicSound) && !entry.getValue().isStopped());
    }

    public static void stop(Music music) {
        Objects.requireNonNull(music, "music");
        Minecraft.getInstance().execute(() -> {
            for (MusicSound sound : playing) {
                if (sound.music.equals(music)) {
                    sound.retiring = true;
                    sound.repeat = false;
                    sound.fade(0);
                    if (sound == main) {
                        main = null;
                    }
                }
            }
        });
    }

    public static void clear() {
        for (MusicSound sound : playing) {
            sound.close();
        }
        playing.clear();
        main = null;
    }

    public static void tick() {
        update();
    }

    private static void update() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isPaused() && !playing.isEmpty()) {
            SoundManager manager = minecraft.getSoundManager();
            var engine = ((SoundManagerAccessor) manager).immersiveMusic$getSoundEngine();
            var channels = ((SoundEngineAccessor) engine).immersiveMusic$getInstanceToChannel();
            boolean otherMusic = hasOtherMusic();
            if (main != null) {
                main.repeat = main.allowLoop && ImmersiveMusic.CONFIG.loop.get();
                main.fade(otherMusic ? 0 : 1);
            }
            playing.removeIf(sound -> {
                var channel = channels.get(sound);
                boolean finished = sound.isStopped() || sound.failed
                        || sound.started && (channel == null || channel.isStopped())
                        || sound.retiring && !sound.started;
                if (finished) {
                    sound.close();
                    if (sound == main) {
                        main = null;
                    }
                } else if (!sound.started && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                        && minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0) {
                    manager.play(sound);
                    sound.started = true;
                }
                return finished;
            });
        }
    }

    private static final class MusicSound extends AbstractTickableSoundInstance {

        private final Music music;
        private boolean allowLoop;
        private boolean started;
        private boolean retiring;
        private float targetVolume = 1;
        private int fadeTicks;
        private volatile boolean repeat;
        private volatile boolean failed;
        private CompletableFuture<MusicStream> stream;

        private MusicSound(Music music, boolean allowLoop) {
            super(music.sound().value(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.music = music;
            this.allowLoop = allowLoop;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0;
            repeat = allowLoop && ImmersiveMusic.CONFIG.loop.get();
            fadeTicks = (int) Math.round(ImmersiveMusic.CONFIG.fadeSeconds.get() * 20);
            if (fadeTicks == 0) {
                volume = 1;
            }
        }

        private void fade(float target) {
            if (targetVolume != target) {
                targetVolume = target;
                fadeTicks = (int) Math.round(ImmersiveMusic.CONFIG.fadeSeconds.get() * 20);
                if (fadeTicks == 0) {
                    volume = target;
                }
            }
        }

        @Override
        public void tick() {
            if (fadeTicks > 0) {
                volume += (targetVolume - volume) / fadeTicks;
                fadeTicks--;
            }
            if (retiring && volume <= 0) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public float getVolume() {
            return volume * 0.5F;
        }

        @Override
        public float getPitch() {
            return 1;
        }

        @Override
        public @Nullable WeighedSoundEvents resolve(@NotNull SoundManager manager) {
            WeighedSoundEvents resolved = super.resolve(manager);
            if (sound != SoundManager.EMPTY_SOUND && sound != SoundManager.INTENTIONALLY_EMPTY_SOUND && !sound.shouldStream()) {
                sound = new Sound(sound.getLocation().toString(), sound.getVolume(), sound.getPitch(), sound.getWeight(),
                        sound.getType(), true, sound.shouldPreload(), sound.getAttenuationDistance());
            }
            return resolved;
        }

        @Override
        public @NotNull CompletableFuture<AudioStream> getStream(@NotNull SoundBufferLibrary buffers, @NotNull Sound sound, boolean looping) {
            var resources = Minecraft.getInstance().getResourceManager();
            stream = CompletableFuture.supplyAsync(() -> {
                try {
                    return new MusicStream(resources.open(sound.getPath()), music.loopStartSeconds(), () -> repeat);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }, Util.ioPool());
            stream.whenComplete((value, exception) -> {
                if (exception != null) {
                    failed = true;
                    LogUtils.getLogger().error("Unable to open music {}", music.sound().value().getLocation(), exception);
                }
            });
            return stream.thenApply(value -> value);
        }

        private void close() {
            stop();
            Minecraft.getInstance().getSoundManager().stop(this);
            if (stream != null) {
                stream.thenAccept(MusicStream::close);
            }
        }
    }
}
