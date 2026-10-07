package com.sorrowmist.useless.compat.ars;

import com.hollingsworth.arsnouveau.common.block.tile.DrygmyTile;
import com.hollingsworth.arsnouveau.common.block.tile.WhirlisprigTile;
import com.hollingsworth.arsnouveau.common.entity.Alakarkinos;
import com.hollingsworth.arsnouveau.common.entity.EntityDrygmy;
import com.hollingsworth.arsnouveau.common.entity.Whirlisprig;
import com.hollingsworth.arsnouveau.setup.config.Config;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@link ArsCreatureTimeBridge} 的 Ars Nouveau 实现。
 *
 * <p>本类是全模组<b>唯一</b>引用新生魔艺类型的地方（{@code compat/ars} 里已有的
 * {@code ArsSourceCompat} 是另一处、面向魔源搬运）。只有 Ars Nouveau 加载时，
 * {@link ArsCreatureTimeCompatLoader} 才会通过 {@code Class.forName} 把本类载入 JVM。</p>
 *
 * <h2>三只工作生物为什么各自推不同的东西</h2>
 * <ul>
 *   <li><b>德格米</b>：工作进度不在实体上，而在「家」方块实体 {@link DrygmyTile} 的
 *       {@code progress} 字段上。原版节奏是「引导 100 tick + 冷却 100 tick = 200 tick 才 +1 点，
 *       默认 20 点约 4000 tick 产一批」，所以直接把 {@code progress} 推满即可。</li>
 *   <li><b>探宝蟹</b>：由状态机驱动，主节流是实体字段 {@code findBlockCooldown}
 *       （一轮成功转换后设 1200 tick，搜索失败设 100~200 tick），只压这个冷却。
 *       状态机内部的 {@code waitTicks} 与寻路<b>刻意不动</b>——驱动它们等于额外跑 AI。</li>
 *   <li><b>旋风精灵</b>：与德格米同构，进度在 {@link WhirlisprigTile} 的 {@code progress} 上，
 *       但原版每次 {@code addProgress()} 加的是 {@code moodScore / 30}（林地心情），
 *       所以按同一比例放大，而不是按 1 加。</li>
 * </ul>
 *
 * <p>所有被读写的字段与 {@code updateBlock()} 都是 public，<b>不需要任何 mixin / accessor</b>。</p>
 */
public final class ArsCreatureTimeCompat implements ArsCreatureTimeBridge {

    /** 旋风精灵原版 {@code addProgress()} 的换算基准（见 {@code WhirlisprigTile#addProgress}）。 */
    private static final int WHIRLISPRIG_MOOD_DIVISOR = 30;

    @Override
    public void accelerate(LivingEntity target, int extra) {
        if (extra <= 0) {
            return;
        }
        if (target instanceof EntityDrygmy drygmy) {
            accelerateDrygmy(drygmy, extra);
        } else if (target instanceof Alakarkinos alakarkinos) {
            accelerateAlakarkinos(alakarkinos, extra);
        } else if (target instanceof Whirlisprig whirlisprig) {
            accelerateWhirlisprig(whirlisprig, extra);
        }
    }

    /**
     * 德格米：把「家」方块实体上的进度推满。
     *
     * <p>{@code getHome()} 自带「坐标处的方块实体是不是 DrygmyTile」校验，区块未加载或罐子被拆
     * 都会返回 {@code null}，这里直接跳过。产出本身仍受原版 {@code gameTime % 100 == 0} 的窗口
     * 与每批 1000 魔源的限制，本方法只负责让进度不再是瓶颈。</p>
     */
    private void accelerateDrygmy(EntityDrygmy drygmy, int extra) {
        DrygmyTile home = drygmy.getHome();
        if (home == null || home.isOff) {
            return;
        }
        int max = home.getMaxProgress();
        if (home.progress >= max) {
            return;
        }
        // 等价于循环调用 giveProgress()，但只发一次方块更新包（giveProgress 每次都 updateBlock）。
        home.progress = Math.min(max, home.progress + extra);
        home.updateBlock();
    }

    /**
     * 探宝蟹：只缩短「找方块」冷却。
     *
     * <p>未绑定容器（{@code getHome() == null}）时状态机在 {@code DecideCrabActionState} 里直接空转，
     * 压冷却不会有任何可观察效果，故跳过。冷却已经是 0 时也没什么可推的。</p>
     */
    private void accelerateAlakarkinos(Alakarkinos alakarkinos, int extra) {
        if (alakarkinos.getHome() == null || alakarkinos.findBlockCooldown <= 0) {
            return;
        }
        alakarkinos.findBlockCooldown = Math.max(0, alakarkinos.findBlockCooldown - extra);
    }

    /**
     * 旋风精灵：按原版 {@code moodScore / 30} 的同比例放大推进 {@link WhirlisprigTile} 的进度。
     *
     * <p>{@code perEvent <= 0}（林地心情不足 30）时什么都不做 —— 与原版 {@code addProgress()}
     * 每次加 0 的行为一致，这种林地本来就不产出，加速它没有意义。</p>
     */
    private void accelerateWhirlisprig(Whirlisprig whirlisprig, int extra) {
        WhirlisprigTile tile = whirlisprig.getTile();
        if (tile == null) {
            return;
        }
        int perEvent = tile.moodScore / WHIRLISPRIG_MOOD_DIVISOR;
        if (perEvent <= 0) {
            return;
        }
        int max = Config.WHIRLISPRIG_MAX_PROGRESS.get();
        if (tile.progress >= max) {
            return;
        }
        // perEvent 与 extra 都可能很大（extra 上限 32768），用 long 累加后再夹取，避免 int 溢出。
        long advanced = (long) tile.progress + (long) perEvent * extra;
        tile.progress = (int) Math.min(max, advanced);
        tile.updateBlock();
    }
}
