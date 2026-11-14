# Create: Mechanical Principles / 机械动力：力学原理

**Minecraft 1.21.1 · NeoForge 21.1.197+ · Create 6.0.10+**

Create 的应力网络原本使用“超载即卡死”的硬判定。本模组把网络级过载改造成连续动力学：过载时网络不会瞬间归零，而是按统一的加速度模型平滑降到应力平衡转速；负载消失后再平滑加速回空载转速。所有速度仍写回 Create 的 `speed` 字段，因此转速表、应力表和现有机器读取到的都是实际值。

> 版本 0.1.0 是“动力学核心 + 安全组件过渡”的第一个可发布版本。开发任务书中明确排除的内容仍未实现，详见[已知限制](#已知限制)。

## 玩法变化

- **软降速**：水车 + 多台冲压机过载时，整条网络平滑减速到 `ω_eq = C / K`，而不是瞬间停转。
- **过载锁存与滞回**：`S ≥ C` 时锁存过载；只有 `S < 0.95 × C` 才释放，避免平衡点附近抖动。
- **失速与恢复**：平衡转速低于空载转速的 10% 时进入 `STALLED`，负载下降并超过恢复阈值后自动重启。
- **惯性感**：加速、降速、软启动共用同一个公式 `a = α × (C - S) / K`，只改变目标速度。
- **热插拔规则**：把传动部件接到运行中的网络时，超过阈值的接入会被拒绝并掉落；连接两个转速不同的网络也会被拒绝。接入已运行网络时保持当前软转速，不会先把整网拉回空载转速。
- **组件过渡**：离合器“延时 + 输出侧斜坡”、反转齿轮箱先归零再反向、可编程齿轮箱保持 Create 原生动力传输、转速控制器过载时削减目标输出。
- **可观测**：`/cmp status` 显示每条网络的转速、平衡目标、C、K、S 和状态。

## 前置要求

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.197 或更高 |
| Create | 6.0.10 或更高（6.x） |
| Java | 21 |

Ponder、Flywheel、Registrate 由 Create 自身提供。单独安装本模组时不需要额外前置。

## 安装

1. 安装 NeoForge 21.1.197+ 的 1.21.1 客户端/服务端。
2. 将 Create 6.0.10+ 与本模组的 jar 放入 `mods/`。
3. 启动游戏。默认配置即启用软动力学。

## 构建

```bash
./gradlew build
```

产物在 `build/libs/create-mechanical-principles-0.1.0.jar`。构建使用 NeoForge ModDevGradle 2.0.148、Gradle 8.14.3 和 Java 21。

如果开发机无法访问 NeoForge Maven，可把本地 21.1.262 安装所需的编译 jar 放入 `libs/compile/`，然后用离线编译模式：

```bash
./gradlew -PcmpLocal build
```

`libs/compile/` 已被 `.gitignore` 忽略，普通用户和 CI 不需要它。

## 配置

配置文件由 NeoForge 生成：

- `config/create_mechanical_principles-common.toml`
- `config/create_mechanical_principles-server.toml`（每个世界）

### `[dynamics]` — 核心动力学（common）

| 键 | 默认 | 说明 |
|---|---:|---|
| `enabled` | `true` | 总开关。关闭后完全使用 Create 原生硬过载。 |
| `preset` | `BALANCED` | `REALISTIC` / `BALANCED` / `ARCADE` / `CUSTOM` |
| `alpha` | `0.05` | `a = α(C-S)/K` 的响应系数 |
| `accelCeilingUpMultiplier` | `0.20` | 加速上限，相对于空载 RPM / tick |
| `accelCeilingDownMultiplier` | `0.30` | 减速上限 |
| `accelFloorMultiplier` | `0.01` | 非零加速度下限 |
| `stallRpmMultiplier` | `0.10` | 失速阈值，相对于空载 RPM |
| `stallRecoveryMultiplier` | `1.5` | 失速恢复阈值倍数 |
| `overloadReleaseRatio` | `0.95` | 过载锁存释放比例 |
| `kEpsilon` | `1e-4` | K 的数值下限 |
| `sleepTicks` | `40` | 稳态休眠判定 tick 数 |
| `sleepEpsilon` | `0.01` | 稳态速度误差（RPM） |
| `maxComponentsPerTick` | `8` | 每 tick 最多解算的活跃网络数 |
| `topologyRebuildCooldown` | `10` | 拓扑重建冷却 tick |
| `syncThreshold` | `0.1` | 触发客户端同步的速度差（RPM） |
| `syncInterval` | `20` | 强制同步间隔 |
| `speedInterpolation` | `0.3` | 客户端插值系数（预留给后续客户端平滑层） |
| `automaticSafeMode` | `true` | 连续内部异常后自动回退原生行为 |

预设只覆盖 `alpha` 与三个加速度字段：

| 预设 | α | 加速上限 | 减速上限 | 加速度下限 |
|---|---:|---:|---:|---:|
| `REALISTIC` | 0.03 | 0.10 | 0.20 | 0.005 |
| `BALANCED` | 0.05 | 0.20 | 0.30 | 0.01 |
| `ARCADE` | 0.10 | 0.40 | 0.60 | 0.02 |

### `[components]` — 组件（server）

| 键 | 默认 | 说明 |
|---|---:|---|
| `clutchSoftStart` | `true` | 离合器软启动 |
| `clutchEngageDelayTicks` | `5` | 离合器输出保持断开的 tick 数 |
| `clutchAccelFraction` | `0.15` | 离合器输出目标速度的比例加速度（每 tick；约 7 tick 到全速） |
| `syncEpsilon` | `0.5` | 同步/冲突判定速度差 |
| `autoMergeOnSync` | `true` | 同步后合并为单一网络（第一版由 Create 原生网络行为满足） |
| `reverseAccelMultiplier` | `1.5` | 反转过渡加速度倍数（1.0–3.0） |
| `reverseTransitionTicks` | `5` | 反转前零输出 tick 数 |
| `refuseReverseWithForeignSource` | `true` | 输出侧有其它源时拒绝软反转 |
| `sequencerAcceleration` | `0.5` | 可编程齿轮箱规划加速度（RPM/tick，规划器已实现；运行时门控见限制） |
| `sequencerStartupTicks` | `1` | 预留；0.1.0 运行时不再用零输出门控，以保动力传输 |
| `overloadReplanThreshold` | `0.5` | 预留的重规划阈值（尚未接入运行时执行） |
| `maintainRatioAfterStep` | `false` | 第一版不维持步骤结束后的传动比 |
| `maxRatioDelta` | `0.1` | 可调链式传动比变化率上限（第一版仅校验，不驱动传播） |
| `rotationSpeedControllerSoftThrottle` | `true` | 转速控制器过载削减 |

### `[hotplug]` — 热插拔（server）

| 键 | 默认 | 说明 |
|---|---:|---|
| `hotPlugSpeedThreshold` | `64` | 运行中网络高于此 RPM 时拒绝接入；`0` 禁用一切热插拔，`256` 不限制 |
| `enableOnContraptions` | `false` | 第一版保留字段，动态结构软动力学不启用 |

所有数值都有 `defineInRange` 绝对边界；跨项约束（例如加速度下限不得高于上限、恢复阈值不得低于失速阈值）在 `DynamicsSettings` 构造器和 `effectiveStallRecoveryThreshold()` 中再次 clamp。

## 命令

需要权限等级 2：

```text
/cmp status
/cmp reload
/cmp preset <realistic|balanced|arcade|custom>
```

- `/cmp status`：输出当前维度中每条网络的 `ω`、目标、缩放系数、C、K、S、锁存/失速状态。
- `/cmp reload`：清除运行时预设，使缓存失效；磁盘配置的重新读取仍由 NeoForge 的配置/世界重载流程完成。
- `/cmp preset`：只改运行时预设，不写文件；重启后恢复 TOML 中的 `preset`。

## 工作原理

设：

- `C`：网络总容量（SU）
- `K = Σ(baseStress_i × |nominalSpeed_i|) / ω0`（SU/RPM）
- `S = K × ω_current`
- `ω_eq = C / K`
- `ω0`：多源时的最大空载转速

每 tick：

```text
a_raw = α × (C - S) / max(K, kEpsilon)
a = clamp(a_raw, -accelCeilingDown × ω0, accelCeilingUp × ω0)
a = max(|a|, accelFloor × ω0) while approaching
ω_target = stalled ? 0 : min(ω0, ω_eq)
ω_current = approach(ω_current, ω_target, a)
```

然后对网络中每个成员写入 `nominalSpeed × (ω_current / ω0)`。Create 自己的应力聚合、同步、转速表和后续机器读取的仍是 `speed` 字段，因此软动力学不需要重定向任何仪表 getter。

- 每个成员通过 `KineticBlockEntityMixin` 保存 `SoftDynamicsNominalSpeed`，当前显示速度仍由 Create 的 `Speed` 保存。
- `SoftDynamicsSpeed` 也写入 NBT，便于调试与兼容。
- 过载期间 `overStressed` 仍由 Create 设置，仪表会显示过载；但 `getSpeed()` 在软动力学启用时返回实际缩放速度，而不是 0。
- 失速期间 `validateKinetics()` 被临时保护，避免 Create 在负载恢复前拆掉零速源。
- 网络拓扑版本、C/K 缓存、休眠、tick 预算和增量应力更新在 `SoftNetworkManager` 中实现。

## 已知限制

1. **动态结构（Contraption）**上的软动力学未实现；`enableOnContraptions` 为保留字段。
2. **离合器/反转齿轮箱**是安全的第一版模型：离合器是“延时 + 输出侧比例斜坡”，反转齿轮箱是“零输出停留后反向”，不是两侧独立网络的完整滑差模型。Create 的 `SplitShaftBlockEntity` 只有一个速度字段，真正双网络滑差需要额外的网络拆分层。
3. **可编程齿轮箱**的梯形/三角形速度规划已在 `SequencerPlan` 中实现并有单元测试；0.1.0 运行时不再注入零输出门控（此前的门控会导致输出侧无法重新接入动力），仍使用 Create 原生执行，严重过载暂停重规划尚未接入。
4. **可调链式传动箱**的连续比值变化率尚未驱动每 tick 的传播；`maxRatioDelta` 已预留并校验。
5. **转速控制器**只做目标速度削减，不参与完整的多网络约束解算。
6. **多源**策略只实现 `MAX`；同一网络内不同源支路的非 1:1 传动比会被统一缩放，可能出现近似误差。
7. 配置屏幕使用 NeoForge 标准 `ConfigurationScreen`，不是 Create 风格 UI。
8. 组件状态机本身不单独写 NBT：1.0 只持久化方块实体的当前/名义速度；加载后状态机按方块状态重建。过渡通常只有几个 tick，重新加载可能从“已同步/静止”状态重新开始。
9. 热插拔准入走 NeoForge 的实体放置事件与 `RotationPropagator` 前置检查；由活塞/命令直接改写方块、且没有实体放置事件的路径没有额外的刚接合并保护。
10. 已在 Create 6.0.10 客户端整合包中确认模组被发现并进入 Mod List；由于该测试包自带输入法模组的 JNA 原生库问题，窗口初始化阶段中止，尚未完成进入世界后的长时间生存验收。提交的验证覆盖编译、JUnit 动力学/规划/配置测试和 jar 资源检查。

欢迎在 Issue 中附上 `/cmp status` 输出、Create 版本和最小复现结构。

## 仓库结构

```text
src/main/java/dev/cmp/
  dynamics/      纯求解器、网络状态、管理器
  components/    离合器/齿轮箱/序列器/转速控制器状态机
  hotplug/       放置与刚接网络准入
  mixin/         Create 6.0.10 注入口
  config/        NeoForge common/server 配置
src/test/java/   不依赖 Minecraft 的动力学与规划测试
.github/workflows/build.yml
```

## 许可

MIT。见 [LICENSE](LICENSE)。
