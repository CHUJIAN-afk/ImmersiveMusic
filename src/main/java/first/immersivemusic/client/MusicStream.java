package first.immersivemusic.client;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

/** 在同一 PCM 缓冲中衔接循环点，避免循环时重新提交声音通道。 */
final class MusicStream implements AudioStream {

    private final ByteBuffer data;
    private final long decoder;
    private final AudioFormat format;
    private final int length;
    private final int loopStart;
    private final BooleanSupplier repeat;
    private int position;
    private boolean closed;

    MusicStream(InputStream input, double loopStartSeconds, BooleanSupplier repeat) throws IOException {
        byte[] bytes;
        try (input) {
            bytes = input.readAllBytes();
        }
        data = BufferUtils.createByteBuffer(bytes.length).put(bytes).flip();
        try (var stack = MemoryStack.stackPush()) {
            var error = stack.mallocInt(1);
            decoder = STBVorbis.stb_vorbis_open_memory(data, error, null);
            if (decoder == 0) {
                throw new IOException("Cannot open music: Vorbis error " + error.get(0));
            }
            var info = STBVorbis.stb_vorbis_get_info(decoder, STBVorbisInfo.malloc(stack));
            format = new AudioFormat(info.sample_rate(), 16, info.channels(), true, false);
        }
        length = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
        loopStart = (int) Math.min(loopStartSeconds * format.getSampleRate(), length);
        if (length <= 0 || loopStart >= length) {
            STBVorbis.stb_vorbis_close(decoder);
            throw new IOException("Music loop point must be before the end of a non-empty Ogg file");
        }
        this.repeat = repeat;
    }

    @Override
    public @NotNull AudioFormat getFormat() {
        return format;
    }

    @Override
    public synchronized @Nullable ByteBuffer read(int size) throws IOException {
        ByteBuffer result = BufferUtils.createByteBuffer(size);
        if (!closed) {
            int channels = format.getChannels();
            var samples = result.asShortBuffer();
            while (samples.remaining() >= channels) {
                if (position >= length) {
                    if (repeat.getAsBoolean()) {
                        if (!STBVorbis.stb_vorbis_seek(decoder, loopStart)) {
                            throw new IOException("Cannot seek to music loop point");
                        }
                        position = loopStart;
                    } else {
                        break;
                    }
                }
                int count = Math.min(samples.remaining() / channels, length - position);
                samples.limit(samples.position() + count * channels);
                int decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, samples);
                if (decoded == 0) {
                    break;
                }
                samples.position(samples.position() + decoded * channels);
                samples.limit(samples.capacity());
                position += decoded;
            }
            result.limit(samples.position() * Short.BYTES);
        }
        return !closed && result.hasRemaining() ? result : null;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            STBVorbis.stb_vorbis_close(decoder);
            closed = true;
        }
    }
}
