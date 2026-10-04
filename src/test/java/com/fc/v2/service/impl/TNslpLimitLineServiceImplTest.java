package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fc.v2.mapper.auto.TNslpLimitLineMapper;
import com.fc.v2.model.auto.TNslpLimitLine;

/**
 * 限值线服务测试：串档回归、撞线定序、撤线口径、两入口一致、季度取线。
 * mapper 用 Mockito 挡住，断言语义全在内存算法上。
 */
class TNslpLimitLineServiceImplTest {

    private TNslpLimitLineMapper mapper;
    private TNslpLimitLineServiceImpl service;
    private List<TNslpLimitLine> rows;

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

    @BeforeEach
    void setUp() {
        rows = new ArrayList<TNslpLimitLine>();
        // 同一代号两版：v1 管 2023 全年，v2 从 2024 元旦起一直管下去
        rows.add(line(1L, "LA", "45", "55", "70", "2023-01-01 00:00:00", "2024-01-01 00:00:00", 1, 0));
        rows.add(line(2L, "LA", "50", "60", "75", "2024-01-01 00:00:00", null, 1, 0));
        // 另一条在位线，交班次序更大
        rows.add(line(3L, "LB", "40", "50", "65", "2023-01-01 00:00:00", null, 5, 0));
        // 已撤下：不参与回算、不进现行清单，照主键仍点得到
        rows.add(line(4L, "LC", "45", "55", "70", "2023-01-01 00:00:00", null, 9, 1));
        // 脏配置：封顶高过 120
        rows.add(line(5L, "LD", "45", "55", "125", "2023-01-01 00:00:00", null, 99, 0));
        // 缺档：够线限值空着
        rows.add(line(6L, "LE", "45", null, "70", "2023-01-01 00:00:00", null, 50, 0));
        // 还没到起用日
        rows.add(line(7L, "LF", "45", "55", "70", "2027-01-01 00:00:00", null, 80, 0));

        mapper = mock(TNslpLimitLineMapper.class);
        when(mapper.selectList(any())).thenAnswer(inv -> new ArrayList<TNslpLimitLine>(rows));
        when(mapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = (Long) inv.getArgument(0);
            for (TNslpLimitLine r : rows) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });

        service = new TNslpLimitLineServiceImpl();
        ReflectionTestUtils.setField(service, "nslpLimitLineMapper", mapper);
    }

    @Test
    void asOfQueryNeverBorrowsNewestVersion() {
        // 补算 2023 年的点位，用 2023 年立着的那一版（45/55/70），不拿此刻最新版（50/60/75）去套
        assertEquals(2, service.evaluate("LA", new BigDecimal("47"), d("2023-06-01 00:00:00")));
        assertEquals(1, service.evaluate("LA", new BigDecimal("47"), d("2024-06-01 00:00:00")));
        // 交班那一刻不含：2024 元旦零点整已经轮到 v2 开口
        assertEquals(1, service.evaluate("LA", new BigDecimal("47"), d("2024-01-01 00:00:00")));
        assertEquals(2, service.evaluate("LA", new BigDecimal("47"), d("2023-12-31 23:59:59")));
        // 同一版式在两年回出两种层，正是串档要挡的事
        assertEquals(2L, service.selectEffective("LA", d("2024-06-01 00:00:00")).getId());
        assertEquals(1L, service.selectEffective("LA", d("2023-06-01 00:00:00")).getId());
    }

    @Test
    void withdrawnLineKeepsRowButKeepsQuiet() {
        // 已撤下不参与回算
        assertEquals(0, service.evaluate("LC", new BigDecimal("60"), d("2023-06-01 00:00:00")));
        assertNull(service.selectEffective("LC", d("2023-06-01 00:00:00")));
        assertFalse(service.usable(4L, d("2023-06-01 00:00:00")));
        // 也不进看板那份现行清单
        for (TNslpLimitLine r : service.listAvailable(d("2023-06-01 00:00:00"))) {
            assertFalse("LC".equals(r.getRuleCode()));
        }
        // 但库里仍是一行，照主键点得到
        assertNotNull(service.selectTNslpLimitLineById(4L));
        assertEquals("LC", service.selectTNslpLimitLineById(4L).getRuleCode());
    }

    @Test
    void dirtyAndIncompleteLinesStayOut() {
        // 封顶高过 120 的脏配置本轮不进来
        assertEquals(0, service.evaluate("LD", new BigDecimal("60"), d("2023-06-01 00:00:00")));
        assertFalse(service.usable(5L, d("2023-06-01 00:00:00")));
        // 缺档的不进来
        assertEquals(0, service.evaluate("LE", new BigDecimal("60"), d("2023-06-01 00:00:00")));
        // 没到起用日的不进来；走到了就进来
        assertEquals(0, service.evaluate("LF", new BigDecimal("60"), d("2023-06-01 00:00:00")));
        assertEquals(3, service.evaluate("LF", new BigDecimal("60"), d("2027-06-01 00:00:00")));
        // 体检提示逐格说清
        List<String> dirtyMsg = service.checkLineConfig(service.selectTNslpLimitLineById(5L));
        assertEquals(1, dirtyMsg.size());
        assertTrue(dirtyMsg.get(0).contains("高过120"), dirtyMsg.get(0));
        List<String> missMsg = service.checkLineConfig(service.selectTNslpLimitLineById(6L));
        assertTrue(missMsg.contains("缺够线限值"), missMsg.toString());
    }

    @Test
    void twoEntrancesOneAnswer() {
        Date at = d("2023-06-01 00:00:00");
        // 这一刻 LB（次序 5）压过 LA-v1（次序 1），整表取优开口的是 LB
        assertEquals("LB", service.selectTop(at).getRuleCode());
        // 同一刻、同一处点位：按线代号取值与整表取优两答必须一致
        assertEquals(service.evaluate("LB", new BigDecimal("47"), at),
                service.evaluateTop(new BigDecimal("47"), at));
        assertEquals(2, service.evaluateTop(new BigDecimal("47"), at));
        assertEquals(3, service.evaluateTop(new BigDecimal("60"), at));
        // 量程之外不归层
        assertEquals(0, service.evaluateTop(new BigDecimal("29.9"), at));
        assertEquals(0, service.evaluateTop(new BigDecimal("120.1"), at));
        assertEquals(1, service.evaluateTop(new BigDecimal("30"), at));
        // 现行清单与角标
        List<TNslpLimitLine> avail = service.listAvailable(at);
        assertEquals(2, avail.size());
        assertEquals("LB", avail.get(0).getRuleCode());
        assertEquals("LA", avail.get(1).getRuleCode());
        assertEquals(2, service.countAvailable(at));
        assertTrue(service.usable(1L, at));
        assertFalse(service.usable(1L, d("2024-06-01 00:00:00")));
        assertTrue(service.usable(2L, d("2024-06-01 00:00:00")));
    }

    @Test
    void collisionBreaksByPriorityThenCode() {
        // 撞在同一刻：先数交班次序
        rows.add(line(8L, "LG", "30", "40", "50", "2023-01-01 00:00:00", null, 5, 0));
        Date at = d("2023-06-01 00:00:00");
        // LG 与 LB 次序并列 5，线代号字面靠后的 LG 说了算
        assertEquals("LG", service.selectTop(at).getRuleCode());
        assertEquals(3, service.evaluateTop(new BigDecimal("47"), at));
        assertEquals(service.evaluate("LG", new BigDecimal("47"), at),
                service.evaluateTop(new BigDecimal("47"), at));
    }

    @Test
    void quarterListing() {
        // 2023 一季度窗口内立着 LB 与 LA-v1
        List<TNslpLimitLine> q1 = service.listAvailableInQuarter(2023, 1);
        assertEquals(2, q1.size());
        assertEquals("LB", q1.get(0).getRuleCode());
        assertEquals("LA", q1.get(1).getRuleCode());
        // 2024 二季度：LA-v1 已交班，轮到 LA-v2
        List<TNslpLimitLine> q2 = service.listAvailableInQuarter(2024, 2);
        assertEquals(2, q2.size());
        assertEquals("LB", q2.get(0).getRuleCode());
        assertEquals(2L, q2.get(1).getId());
        // 季度参数不正回空，不硬答
        assertTrue(service.listAvailableInQuarter(2023, 5).isEmpty());
        // 2026 四季度：LF 起用日 2027 元旦，窗口右沿不含，进不来
        List<TNslpLimitLine> q4 = service.listAvailableInQuarter(2026, 4);
        assertEquals(2, q4.size());
    }
}
