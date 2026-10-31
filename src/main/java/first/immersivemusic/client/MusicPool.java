package first.immersivemusic.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import first.immersivemusic.ImmersiveMusic;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 按标签与环境偏好选曲，提前切换需满足适配下降和稳定等待。 */
public final class MusicPool {

    private static final List<Track> tracks = new ArrayList<>();
    private static final RandomSource random = RandomSource.create();
    private static Music previous;
    private static int previousTrack;
    private static long retryAfter;
    private static long startedAt;
    private static long pendingSince;
    private static int acceptedSignature;
    private static int pendingSignature;

    private MusicPool() {
    }

    public static void load() {
        if (tracks.isEmpty()) {
            try (var reader = new InputStreamReader(Objects.requireNonNull(MusicPool.class.getResourceAsStream(
                    "/assets/immersive_music/music_pool.json")), StandardCharsets.UTF_8)) {
                for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject rule = value.getAsJsonObject();
                    Music[] variants = new Music[2];
                    for (int index = 0; index < variants.length; index++) {
                        var id = ResourceLocation.fromNamespaceAndPath("immersive_music",
                                "music." + rule.get("id").getAsString() + "_" + (char) ('a' + index));
                        variants[index] = new Music(Holder.direct(SoundEvent.createVariableRangeEvent(id)), 0);
                    }
                    tracks.add(new Track(rule.get("number").getAsInt(), variants, rule,
                            tags(rule, "tagsAny"), tags(rule, "tagsNone"), tags(rule, "preferredTags")));
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load the immersive music pool", exception);
            }
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean idle = MusicManager.isIdle();
        boolean menu = minecraft.level == null;
        boolean configLoaded = ImmersiveMusic.CONFIG.spec.isLoaded();
        boolean allowEarlySwitch = !menu && minecraft.player != null && configLoaded
                && ImmersiveMusic.CONFIG.allowEarlySwitch.get();
        long now = Util.getMillis();
        if (!allowEarlySwitch) {
            pendingSince = 0;
        }
        if ((menu || minecraft.player != null) && configLoaded && !minecraft.isPaused()
                && (idle || allowEarlySwitch && MusicManager.isAutomaticMain(previous) && now - startedAt >= 10000)
                && now >= retryAfter
                && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                && !MusicManager.hasOtherMusic()) {
            retryAfter = now + 1000;
            Track chosen = null;
            int signature = 0;
            if (menu) {
                if (!tracks.isEmpty()) {
                    chosen = tracks.get(random.nextInt(tracks.size()));
                }
            } else {
                EnvironmentSnapshot environment = EnvironmentDetector.current();
                if (environment != null) {
                    var biome = environment.biome();
                    String realm = environment.dimension().equals(Level.NETHER) || biome.is(BiomeTags.IS_NETHER) ? "nether"
                            : environment.dimension().equals(Level.END) || biome.is(BiomeTags.IS_END) ? "end" : "overworld";
                    String layer = environment.underwater() ? "underwater"
                            : !environment.skyVisible() && (environment.height() < 48
                            || biome.is(BiomeTags.ALLOWS_TROPICAL_FISH_SPAWNS_AT_ANY_HEIGHT)
                            || biome.is(BiomeTags.HAS_ANCIENT_CITY)) ? "underground" : "surface";
                    double[] weights = new double[tracks.size()];
                    double bestWeight = 0;
                    double currentWeight = 0;
                    signature = 1;
                    for (int index = 0; index < tracks.size(); index++) {
                        Track track = tracks.get(index);
                        double weight = track.weight(environment, realm, layer);
                        weights[index] = weight;
                        bestWeight = Math.max(bestWeight, weight);
                        signature = 31 * signature + Double.hashCode(weight);
                        if (track.number == previousTrack) {
                            currentWeight = weight;
                        }
                    }
                    boolean select = idle;
                    if (!idle) {
                        if (signature == acceptedSignature || currentWeight >= bestWeight * 0.5) {
                            acceptedSignature = signature;
                            pendingSince = 0;
                        } else if (pendingSince == 0 || pendingSignature != signature) {
                            pendingSignature = signature;
                            pendingSince = now;
                        } else {
                            select = now - pendingSince >= 5000;
                        }
                    }
                    if (select) {
                        double total = 0;
                        for (int index = 0; index < tracks.size(); index++) {
                            Track track = tracks.get(index);
                            double weight = weights[index];
                            if (track.number == previousTrack) {
                                weight *= 0.2;
                            }
                            if (weight > 0 && (idle || weights[index] >= bestWeight * 0.5)) {
                                total += weight;
                                if (random.nextDouble() * total < weight) {
                                    chosen = track;
                                }
                            }
                        }
                    }
                }
            }
            if (chosen != null) {
                int index = random.nextInt(2);
                if (chosen.variants[index] == previous) {
                    index ^= 1;
                }
                previous = chosen.variants[index];
                previousTrack = chosen.number;
                acceptedSignature = signature;
                pendingSince = 0;
                startedAt = now;
                MusicManager.play(previous, false);
            }
        }
    }

    public static void reset() {
        previous = null;
        previousTrack = 0;
        retryAfter = 0;
        startedAt = 0;
        pendingSince = 0;
        acceptedSignature = 0;
        pendingSignature = 0;
    }

    private static List<TagKey<Biome>> tags(JsonObject rule, String name) {
        List<TagKey<Biome>> result = new ArrayList<>();
        if (rule.has(name)) {
            for (var value : rule.getAsJsonArray(name)) {
                var id = ResourceLocation.parse(value.getAsString());
                if (!id.getNamespace().equals("minecraft")) {
                    throw new IllegalArgumentException("Only vanilla biome tags are allowed: " + id);
                }
                result.add(TagKey.create(Registries.BIOME, id));
            }
        }
        return List.copyOf(result);
    }

    private static boolean within(JsonObject values, String name, double value) {
        if (!values.has(name)) {
            return true;
        }
        var range = values.getAsJsonObject(name);
        return (!range.has("gte") || value >= range.get("gte").getAsDouble())
                && (!range.has("lt") || value < range.get("lt").getAsDouble());
    }

    private record Track(int number, Music[] variants, JsonObject rule, List<TagKey<Biome>> any,
                         List<TagKey<Biome>> excluded, List<TagKey<Biome>> preferred) {

        private double weight(EnvironmentSnapshot environment, String realm, String layer) {
            if (!rule.get("realm").getAsString().equals(realm)
                    || !rule.get("layer").getAsString().equals("any") && !rule.get("layer").getAsString().equals(layer)
                    || !any.isEmpty() && any.stream().noneMatch(environment.biome()::is)
                    || excluded.stream().anyMatch(environment.biome()::is)
                    || !within(rule, "temperatureLimit", environment.temperature())
                    || !within(rule, "heightLimit", environment.height())) {
                return 0;
            }
            double weight = rule.get("weight").getAsDouble();
            if (preferred.stream().anyMatch(environment.biome()::is)) {
                weight *= 2;
            }
            JsonObject preferences = rule.getAsJsonObject("preferences");
            if (!within(preferences, "temperature", environment.temperature())) {
                weight *= 0.25;
            }
            if (!within(preferences, "height", environment.height())) {
                weight *= 0.35;
            }
            if (!within(preferences, "brightness", environment.brightness())) {
                weight *= 0.35;
            }
            if (preferences.has("skyVisible") && preferences.get("skyVisible").getAsBoolean() != environment.skyVisible()) {
                weight *= 0.35;
            }
            if (preferences.has("dayTimeAny")) {
                boolean matches = false;
                for (var value : preferences.getAsJsonArray("dayTimeAny")) {
                    var range = value.getAsJsonArray();
                    matches |= environment.dayTime() >= range.get(0).getAsLong() && environment.dayTime() < range.get(1).getAsLong();
                }
                if (!matches) {
                    weight *= 0.15;
                }
            }
            return weight;
        }
    }
}
