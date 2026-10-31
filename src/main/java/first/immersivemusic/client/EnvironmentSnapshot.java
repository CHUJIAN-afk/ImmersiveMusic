package first.immersivemusic.client;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 各项独立采样的环境缓存；温度为群系基础值，亮度为眼部位置的原版光照等级。
 */
public record EnvironmentSnapshot(Holder<Biome> biome, ResourceKey<Level> dimension, float temperature, long dayTime, int brightness, boolean skyVisible, boolean underwater, double height) {
}
