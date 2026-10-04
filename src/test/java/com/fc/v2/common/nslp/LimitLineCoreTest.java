package com.fc.v2.common.nslp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TNslpLimitLine;

/**
 * LimitLineCore 纯算法测试：贴层四句、精度对齐、量程、配置体检、在位三条件、撞线定序。
 */
class LimitLineCoreTest {

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TNslpLimitLine line(Long id, String code, String th1, String th2, String th3,
            String start, String end, Integer priority, Integer status) {
        TNslpLimitLine l = new TNslpLimitLine();
        l.setId(id);
        l.setRuleCode(code);
        l.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        l.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        l.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        l.setEffStart(start == null ? null : d(start));
        l.setEffEnd(end == null ? null : d(end));
        l.setPriority(priority);
        l.setStatus(status);
        l.setDelFlag(0);
        return l;
    }

    /** 规格验例：三档 45/55/70 */
    private static TNslpLimitLine sample() {
        return line(1L, "LA", "45", "55", "70", "2020-01-01 00:00:00", null, 1, 0);
    }

    @Test
    void workedExample() {
        TNslpLimitLine l = sample();
        assertEquals(1, LimitLineCore.layerOf(l, new BigDecimal("44.0")));
        assertEquals(2, LimitLineCore.layerOf(l, new BigDecimal("54.9")));
        // 恰好压上够线限值收进第 3 层，落进第 2 层即缺陷
        assertEquals(3, LimitLineCore.layerOf(l, new BigDecimal("55.0")));
        assertEquals(3, LimitLineCore.layerOf(l, new BigDecimal("55.1")));
        assertEquals(2, LimitLineCore.layerOf(l, new BigDecimal("45.0")));
        assertEquals(3, LimitLineCore.layerOf(l, new BigDecimal("69.9")));
        assertEquals(4, LimitLineCore.layerOf(l, new BigDecimal("70.0")));
        assertEquals(4, LimitLineCore.layerOf(l, new BigDecimal("99.9")));
    }

    @Test
    void roundingAlignedBeforeLayering() {
        TNslpLimitLine l = sample();
        // 前年错位那一幕：54.996 逢半进一对齐成 55.0，与定值 55.0 一样大，须出第 3 层
        assertEquals(3, LimitLineCore.layerOf(l, new BigDecimal("54.996")));
        assertEquals(2, LimitLineCore.layerOf(l, new BigDecimal("54.94")));
        assertEquals(2, LimitLineCore.layerOf(l, new BigDecimal("44.96")));
        assertEquals(1, LimitLineCore.layerOf(l, new BigDecimal("44.94")));
        // 定值一侧同样对齐：够线限值 54.96 对齐成 55.0，读数 54.95 对齐成 55.0，压线进第 3 层
        TNslpLimitLine rough = line(2L, "LR", "45", "54.96", "70", "2020-01-01 00:00:00", null, 1, 0);
        assertEquals(3, LimitLineCore.layerOf(rough, new BigDecimal("54.95")));
        assertEquals(2, LimitLineCore.layerOf(rough, new BigDecimal("54.94")));
    }

    @Test
    void rangeGate() {
        TNslpLimitLine l = sample();
        // 量程 30..120，之外不归层；对齐之后才量程
        assertEquals(0, LimitLineCore.layerOf(l, new BigDecimal("29.9")));
        assertEquals(0, LimitLineCore.layerOf(l, new BigDecimal("29.94")));
        assertEquals(1, LimitLineCore.layerOf(l, new BigDecimal("29.96")));
        assertEquals(1, LimitLineCore.layerOf(l, new BigDecimal("30")));
        assertEquals(4, LimitLineCore.layerOf(l, new BigDecimal("120")));
        assertEquals(4, LimitLineCore.layerOf(l, new BigDecimal("120.04")));
        assertEquals(0, LimitLineCore.layerOf(l, new BigDecimal("120.06")));
        assertEquals(0, LimitLineCore.layerOf(l, new BigDecimal("120.1")));
        assertEquals(0, LimitLineCore.layerOf(l, null));
        assertEquals(0, LimitLineCore.layerOf(null, new BigDecimal("50")));
    }

    @Test
    void configCheckSpeaksPlainly() {
        // 缺档：说清缺的是哪一格
        TNslpLimitLine miss2 = line(1L, "L", "45", null, "70", "2020-01-01 00:00:00", null, 1, 0);
        List<String> p1 = LimitLineCore.checkConfig(miss2);
        assertTrue(p1.contains("缺够线限值"), p1.toString());
        assertFalse(LimitLineCore.configValid(miss2));
        assertEquals(0, LimitLineCore.layerOf(miss2, new BigDecimal("50")));

        TNslpLimitLine miss13 = line(1L, "L", null, "55", null, "2020-01-01 00:00:00", null, 1, 0);
        List<String> p2 = LimitLineCore.checkConfig(miss13);
        assertTrue(p2.contains("缺起算限值"), p2.toString());
        assertTrue(p2.contains("缺封顶限值"), p2.toString());

        // 后一档没大过前一档：说清是哪一格没大过它前面那一格
        TNslpLimitLine flat = line(1L, "L", "45", "45", "70", "2020-01-01 00:00:00", null, 1, 0);
        List<String> p3 = LimitLineCore.checkConfig(flat);
        assertEquals(1, p3.size());
        assertTrue(p3.get(0).contains("够线限值"), p3.get(0));
        assertTrue(p3.get(0).contains("未大过"), p3.get(0));
        assertTrue(p3.get(0).contains("起算限值"), p3.get(0));

        TNslpLimitLine down = line(1L, "L", "45", "55", "50", "2020-01-01 00:00:00", null, 1, 0);
        List<String> p4 = LimitLineCore.checkConfig(down);
        assertEquals(1, p4.size());
        assertTrue(p4.get(0).contains("封顶限值"), p4.get(0));
        assertTrue(p4.get(0).contains("够线限值"), p4.get(0));

        // 封顶高过 120 属脏配置
        TNslpLimitLine dirty = line(1L, "L", "45", "55", "120.1", "2020-01-01 00:00:00", null, 1, 0);
        List<String> p5 = LimitLineCore.checkConfig(dirty);
        assertEquals(1, p5.size());
        assertTrue(p5.get(0).contains("高过120"), p5.get(0));
        assertTrue(p5.get(0).contains("脏配置"), p5.get(0));
        assertEquals(0, LimitLineCore.layerOf(dirty, new BigDecimal("50")));

        // 当数的线一条问题都没有
        assertTrue(LimitLineCore.checkConfig(sample()).isEmpty());
        assertTrue(LimitLineCore.configValid(sample()));
    }

    @Test
    void inEffectRules() {
        TNslpLimitLine l = line(1L, "L", "45", "55", "70",
                "2024-01-01 00:00:00", "2025-01-01 00:00:00", 1, 0);
        // 起用那一一日含等号，交班那一一日不含
        assertTrue(LimitLineCore.inEffect(l, d("2024-01-01 00:00:00")));
        assertFalse(LimitLineCore.inEffect(l, d("2023-12-31 23:59:59")));
        assertTrue(LimitLineCore.inEffect(l, d("2024-12-31 23:59:59")));
        assertFalse(LimitLineCore.inEffect(l, d("2025-01-01 00:00:00")));
        // 交班留空按一直管下去认
        TNslpLimitLine open = line(1L, "L", "45", "55", "70", "2024-01-01 00:00:00", null, 1, 0);
        assertTrue(LimitLineCore.inEffect(open, d("2035-06-01 00:00:00")));
        // 情形记 0 才在位
        assertFalse(LimitLineCore.inEffect(
                line(1L, "L", "45", "55", "70", "2024-01-01 00:00:00", null, 1, 1), d("2024-06-01 00:00:00")));
        assertFalse(LimitLineCore.inEffect(
                line(1L, "L", "45", "55", "70", "2024-01-01 00:00:00", null, 1, null), d("2024-06-01 00:00:00")));
        // 起用日空着不算走到
        assertFalse(LimitLineCore.inEffect(
                line(1L, "L", "45", "55", "70", null, null, 1, 0), d("2024-06-01 00:00:00")));
        // 已删除不算
        TNslpLimitLine del = sample();
        del.setDelFlag(1);
        assertFalse(LimitLineCore.inEffect(del, d("2024-06-01 00:00:00")));
    }

    @Test
    void winnerOrder() {
        Date at = d("2024-06-01 00:00:00");
        TNslpLimitLine p1 = line(1L, "LA", "45", "55", "70", "2024-01-01 00:00:00", null, 1, 0);
        TNslpLimitLine p5 = line(2L, "LB", "45", "55", "70", "2024-01-01 00:00:00", null, 5, 0);
        // 交班次序数大的先开口
        assertEquals(2L, LimitLineCore.pickWinner(Arrays.asList(p1, p5), at).getId());
        // 数也并列，线代号字面靠后的说了算
        TNslpLimitLine p5z = line(3L, "LZ", "45", "55", "70", "2024-01-01 00:00:00", null, 5, 0);
        assertEquals(3L, LimitLineCore.pickWinner(Arrays.asList(p5, p5z), at).getId());
        // 再并列按主键兜底，一问只许一答
        TNslpLimitLine p5zBig = line(9L, "LZ", "45", "55", "70", "2024-01-01 00:00:00", null, 5, 0);
        assertEquals(9L, LimitLineCore.pickWinner(Arrays.asList(p5z, p5zBig), at).getId());
        // 撤下的、脏配置的、缺档的、没走到起用日的都开不了口
        TNslpLimitLine withdrawn = line(4L, "LC", "45", "55", "70", "2024-01-01 00:00:00", null, 99, 1);
        TNslpLimitLine dirty = line(5L, "LD", "45", "55", "121", "2024-01-01 00:00:00", null, 99, 0);
        TNslpLimitLine miss = line(6L, "LE", "45", null, "70", "2024-01-01 00:00:00", null, 99, 0);
        TNslpLimitLine future = line(7L, "LF", "45", "55", "70", "2027-01-01 00:00:00", null, 99, 0);
        assertEquals(2L, LimitLineCore.pickWinner(
                Arrays.asList(withdrawn, dirty, miss, future, p5), at).getId());
        assertNull(LimitLineCore.pickWinner(Arrays.asList(withdrawn, dirty, miss, future), at));
    }
}
