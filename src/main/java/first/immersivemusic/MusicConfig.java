package first.immersivemusic;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MusicConfig {

    public final ForgeConfigSpec.DoubleValue fadeSeconds;
    public final ForgeConfigSpec.BooleanValue loop;
    public final ForgeConfigSpec.BooleanValue allowEarlySwitch;
    public final ForgeConfigSpec spec;

    MusicConfig() {
        var builder = new ForgeConfigSpec.Builder();
        fadeSeconds = builder.comment("淡入淡出时长，单位为秒；0 表示立即切换。")
                .defineInRange("fadeSeconds", 2.0, 0.0, 60.0);
        loop = builder.comment("手动 API 主音乐是否返回循环点无限播放；自动音乐池始终完整播放一次后重新选曲。")
                .define("loop", false);
        allowEarlySwitch = builder.comment("允许自动音乐在环境适配明显下降后提前淡化切换；关闭时等待当前音乐结束再刷新环境和选曲。")
                .define("allowEarlySwitch", true);
        spec = builder.build();
    }
}
