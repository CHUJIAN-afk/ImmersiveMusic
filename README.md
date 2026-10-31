# 沉浸式音乐

Minecraft 1.20.1 / Forge 47.4.22+ / Java 17，仅客户端。模组版本：`1.20.1.1`，模组 ID：`immersive_music`。当前分支为 `1.20.1forge`，Minecraft 1.21.1 / NeoForge 主分支为 `1.21.1neoforge`。

内置 60 首曲目的 A、B 两个版本，共 120 个中世纪凯尔特风格音乐资产。仅通过原版群系标签及温度、时间、亮度、天空可见性、水下状态、高度选择音乐，支持具有相同标签的模组群系。缺少细分标签时使用通用候选。

自动曲目不无限循环，内部基础音量为 0.5，音高为 1。原版背景音乐停用；其他模组的 MUSIC 通道播放时，本模组音乐淡出让位。

未进入世界时，从全部 120 个版本中随机选择音乐，始终等当前曲目播放完成后再随机下一首，不受提前切换配置影响。进入世界后恢复环境选曲规则。

## 配置

首次运行生成 `config/immersive_music-client.toml`：

```toml
fadeSeconds = 2.0
loop = true
allowEarlySwitch = true
```

- `fadeSeconds`：淡入淡出时长，范围 0–60 秒。
- `loop`：手动 API 音乐是否无限循环；自动音乐池始终单次播放。
- `allowEarlySwitch`：默认允许环境适配明显下降后提前切换。曲目开始 10 秒后评估，变化稳定 5 秒才淡化切换；关闭后冻结播放中的环境缓存，曲终再刷新、选曲。

允许采样时，各环境参数每 tick 独立以 0.5% 概率刷新。进入世界、维度变化及曲终按需完整采样。手动 API 音乐不会被自动池抢占。

## 客户端 API

```java
Music music = new Music(soundHolder, 0.0);
MusicManager.play(music);
MusicManager.stop(music);
```

类位于 `first.immersivemusic.client`，`soundHolder` 类型为 `Holder<SoundEvent>`。第二个参数是循环起点，单位为秒，0 表示整首循环。开始播放只需请求一次；`MusicManager.clear()` 可立即停止并释放本模组音乐。

## 构建

使用 Java 17：

```powershell
.\gradlew.bat build
```

安装包输出到 `build/libs/immersive_music-1.20.1.1.jar`。运行仅需安装客户端，服务端无需安装。

最终音乐资产位于 [sounds/music](src/main/resources/assets/immersive_music/sounds/music/)，播放映射和环境规则分别为 `sounds.json`、`music_pool.json`。

## 版权与许可

Copyright (c) 2024–2026 FirstSight。

**模组代码采用 MIT 开源许可证**，完整条款见 [LICENSE](LICENSE)。

**本模组所有音乐均由 Suno AI 创作（生成）**。保留的 120 个 Ogg 文件为最终使用版本；音乐资产不包含在模组代码的 MIT 许可范围内。
