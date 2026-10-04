package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpLimitLine;

/**
 * 功能区昼夜间限值线 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * <p>贴层、在位、撞线定序统一走 {@link com.fc.v2.common.nslp.LimitLineCore}，
 * 大屏折线、折线上的数、后端逐条回算出自同一次计算。
 * int 口径里 0 即"不归层"（无在位线 / 折算读数越出量程 30..120 / 参数缺失）。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITNslpLimitLineService {

    /** 按主键查询规则（已撤下的线仍是一行，照主键点得到） */
    TNslpLimitLine selectTNslpLimitLineById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判档（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失一律返回 0（不归层）。
     * 形参与返回不动；按区册、按季度取线另起新方法承接。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中优先级最高者的档位；无可用规则返回 0（不归层） */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（优先级降序、ruleCode 降序）；看板现行清单用这份，已撤下的不进 */
    List<TNslpLimitLine> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径须与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板） */
    int countAvailable(Date at);

    /** 按线代号取 at 时刻在位的那一版；没有在位版本返回 null（补算往年按 at 取，不串档） */
    TNslpLimitLine selectEffective(String ruleCode, Date at);

    /** 整表取优：at 时刻开口的那条线；没有可开口的返回 null */
    TNslpLimitLine selectTop(Date at);

    /**
     * 配置体检：缺哪一格、哪一格没大过它前面那一格、封顶高没高过 120，
     * 逐条说清递到前端；空列表表示这条线当数。
     */
    List<String> checkLineConfig(TNslpLimitLine line);

    /** 按季度取线：负责的那一截与该季度窗口有交叠、且配置当数的在位线（新方法承接，不碰 evaluate 签名） */
    List<TNslpLimitLine> listAvailableInQuarter(int year, int quarter);
}
