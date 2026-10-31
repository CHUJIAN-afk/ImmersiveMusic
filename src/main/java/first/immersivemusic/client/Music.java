package first.immersivemusic.client;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

import java.util.Objects;

/** 音乐定义，循环点以秒计；首次播放从文件开头开始。 */
public record Music(Holder<SoundEvent> sound, double loopStartSeconds) {

    public Music {
        Objects.requireNonNull(sound, "sound");
        if (!Double.isFinite(loopStartSeconds) || loopStartSeconds < 0) {
            throw new IllegalArgumentException("Music loop point must be finite and non-negative");
        }
    }
}
