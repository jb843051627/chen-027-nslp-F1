package com.fc.v2.common.nslp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpLimitLine;

/**
 * 功能区昼段与夜段限值线对照表——唯一算法核。
 *
 * <p>大屏昼段夜段两截折线、折线上点出来的数、后端逐条回算，三处都从这里过，
 * 不许各算一遍。本类不碰库，只装纯判定：</p>
 * <ul>
 *   <li>精度对齐：定值与折算读数一律取到一位小数、逢半进一，对齐之后才去贴；</li>
 *   <li>贴层四句：不到起算落第1层；到起算不到够线落第2层；到够线不到封顶落第3层；
 *       到封顶及以上落第4层。"到了"含等号，压线值收进紧的一层；</li>
 *   <li>量程：折算读数量程 30 到 120，之外不归层；</li>
 *   <li>配置体检：缺档、后档没大过前档、封顶高过 120，逐格说清；</li>
 *   <li>在位三条件：起用日已走到（含）、交班日还没到（不含，留空一直管）、情形记 0；</li>
 *   <li>撞线定序：交班次序数大的先开口，并列再比线代号字面靠后的，再并列按主键兜底，
 *       一问只许一答。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-04
 */
public final class LimitLineCore {

    private LimitLineCore() {
    }

    /** 不归层（int 口径的返回值） */
    public static final int LAYER_NONE = 0;

    /** 回话原文：一条线都贴不上时只写这三个字 */
    public static final String TEXT_UNCLASSIFIED = "不归层";

    /** 折算读数量程下限（含） */
    public static final BigDecimal RANGE_MIN = new BigDecimal("30");

    /** 折算读数量程上限（含） */
    public static final BigDecimal RANGE_MAX = new BigDecimal("120");

    /** 线的情形：0 记在位 */
    public static final int STATUS_IN_PLACE = 0;

    /**
     * 撞线定序：交班次序数大的先开口；数也并列，比线代号字面先后，靠后的说了算；
     * 再并列按主键兜底定序，保证一问一答。
     */
    public static final Comparator<TNslpLimitLine> WINNER_FIRST = new Comparator<TNslpLimitLine>() {
        @Override
        public int compare(TNslpLimitLine a, TNslpLimitLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return Integer.compare(pb, pa);
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            int byCode = cb.compareTo(ca);
            if (byCode != 0) {
                return byCode;
            }
            long ia = a.getId() == null ? 0L : a.getId();
            long ib = b.getId() == null ? 0L : b.getId();
            return Long.compare(ib, ia);
        }
    };

    /** 精度对齐：取到一位小数、逢半进一。贴层之前定值与读数都要先过这道。 */
    public static BigDecimal align(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 贴层：先把定值与读数对齐到一位小数，再按四句划档。
     * 量程之外（比 30 小、比 120 大）或线配置不当数，一律 {@link #LAYER_NONE}。
     */
    public static int layerOf(TNslpLimitLine line, BigDecimal reading) {
        if (line == null || reading == null || !configValid(line)) {
            return LAYER_NONE;
        }
        BigDecimal x = align(reading);
        if (x.compareTo(RANGE_MIN) < 0 || x.compareTo(RANGE_MAX) > 0) {
            return LAYER_NONE;
        }
        BigDecimal th1 = align(line.getTh1Max());
        BigDecimal th2 = align(line.getTh2Max());
        BigDecimal th3 = align(line.getTh3Max());
        if (x.compareTo(th1) < 0) {
            return 1;
        }
        if (x.compareTo(th2) < 0) {
            return 2;
        }
        if (x.compareTo(th3) < 0) {
            return 3;
        }
        return 4;
    }

    /** 配置当不当数：{@link #checkConfig} 一条问题都没有才算当数。 */
    public static boolean configValid(TNslpLimitLine line) {
        return checkConfig(line).isEmpty();
    }

    /**
     * 配置体检，递到前端的逐格提示：缺哪一格、哪一格没大过它前面那一格、
     * 封顶高没高过 120，逐条说清，不许只写"数值不对"。
     */
    public static List<String> checkConfig(TNslpLimitLine line) {
        List<String> problems = new ArrayList<String>();
        if (line == null) {
            problems.add("线不存在");
            return problems;
        }
        BigDecimal th1 = line.getTh1Max();
        BigDecimal th2 = line.getTh2Max();
        BigDecimal th3 = line.getTh3Max();
        if (th1 == null) {
            problems.add("缺起算限值");
        }
        if (th2 == null) {
            problems.add("缺够线限值");
        }
        if (th3 == null) {
            problems.add("缺封顶限值");
        }
        if (th1 != null && th2 != null && th2.compareTo(th1) <= 0) {
            problems.add("够线限值（" + th2.toPlainString() + "）未大过起算限值（" + th1.toPlainString() + "）");
        }
        if (th2 != null && th3 != null && th3.compareTo(th2) <= 0) {
            problems.add("封顶限值（" + th3.toPlainString() + "）未大过够线限值（" + th2.toPlainString() + "）");
        }
        if (th3 != null && th3.compareTo(RANGE_MAX) > 0) {
            problems.add("封顶限值（" + th3.toPlainString() + "）高过120，属脏配置");
        }
        return problems;
    }

    /**
     * 一条线在 at 时刻在不在位，看三件事：起用那一一日走到 at 之前了（含等号）、
     * 交班那一一日还没到（不含等号，留空按一直管下去认）、情形记为 0。
     */
    public static boolean inEffect(TNslpLimitLine line, Date at) {
        if (line == null || at == null || !statusInPlace(line)) {
            return false;
        }
        Date start = line.getEffStart();
        if (start == null || start.after(at)) {
            return false;
        }
        Date end = line.getEffEnd();
        return end == null || end.after(at);
    }

    /** 情形记 0 且未被删除（在位三条件里不随时刻动的那部分）。 */
    public static boolean statusInPlace(TNslpLimitLine line) {
        if (line == null) {
            return false;
        }
        if (line.getDelFlag() != null && line.getDelFlag() != 0) {
            return false;
        }
        return line.getStatus() != null && line.getStatus() == STATUS_IN_PLACE;
    }

    /**
     * 线负责的那一截 [起用, 交班) 与窗口 [from, toExclusive) 有没有交叠。
     * 交班留空按一直管下去认。补算往年点位按窗口取线用这道。
     */
    public static boolean windowOverlaps(TNslpLimitLine line, Date from, Date toExclusive) {
        if (line == null || from == null || toExclusive == null || !statusInPlace(line)) {
            return false;
        }
        Date start = line.getEffStart();
        if (start == null || !start.before(toExclusive)) {
            return false;
        }
        Date end = line.getEffEnd();
        return end == null || end.after(from);
    }

    /** at 时刻可开口的线（在位且配置当数），按 {@link #WINNER_FIRST} 排好，头一名就是开口人。 */
    public static List<TNslpLimitLine> available(List<TNslpLimitLine> lines, Date at) {
        List<TNslpLimitLine> out = new ArrayList<TNslpLimitLine>();
        if (lines == null || at == null) {
            return out;
        }
        for (TNslpLimitLine line : lines) {
            if (inEffect(line, at) && configValid(line)) {
                out.add(line);
            }
        }
        out.sort(WINNER_FIRST);
        return out;
    }

    /** at 时刻的开口人：可开口的线里按 {@link #WINNER_FIRST} 数到头一名；没有可开口的返回 null。 */
    public static TNslpLimitLine pickWinner(List<TNslpLimitLine> lines, Date at) {
        List<TNslpLimitLine> avail = available(lines, at);
        return avail.isEmpty() ? null : avail.get(0);
    }
}
