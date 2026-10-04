package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.service.ITNslpLimitLineService;

/**
 * 点位回话测试：归层钉两格、贴不上回"不归层"并清成空白、孤儿点位逐条列出。
 */
class TNslpPointServiceImplTest {

    private TNslpPointMapper pointMapper;
    private ITNslpLimitLineService limitLineService;
    private TNslpPointServiceImpl service;

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TNslpLimitLine winnerLine() {
        TNslpLimitLine l = new TNslpLimitLine();
        l.setId(3L);
        l.setRuleCode("LB");
        l.setTh1Max(new BigDecimal("40"));
        l.setTh2Max(new BigDecimal("50"));
        l.setTh3Max(new BigDecimal("65"));
        l.setEffStart(d("2023-01-01 00:00:00"));
        l.setPriority(5);
        l.setStatus(0);
        l.setDelFlag(0);
        return l;
    }

    private static TNslpPoint point(Long id, String pointNo, String ruleCode) {
        TNslpPoint p = new TNslpPoint();
        p.setId(id);
        p.setPointNo(pointNo);
        p.setRuleCode(ruleCode);
        p.setDelFlag(0);
        return p;
    }

    @BeforeEach
    void setUp() {
        pointMapper = mock(TNslpPointMapper.class);
        TNslpZoneBookMapper zoneBookMapper = mock(TNslpZoneBookMapper.class);
        limitLineService = mock(ITNslpLimitLineService.class);
        service = new TNslpPointServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", pointMapper);
        ReflectionTestUtils.setField(service, "nslpZoneBookMapper", zoneBookMapper);
        ReflectionTestUtils.setField(service, "nslpLimitLineService", limitLineService);
        when(pointMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void layeredReplyNailsCodeAndStartDate() {
        Date at = d("2023-06-01 00:00:00");
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P01", null));
        when(limitLineService.selectTop(at)).thenReturn(winnerLine());

        // LB 三档 40/50/65，读数 60 落第 3 层
        String reply = service.replyPointLevel(1L, new BigDecimal("60"), at);
        assertEquals("3", reply);

        // 归到哪一层，就把开口那条线的线代号与起用那一一日钉进点位行
        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(pointMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("rule_code"), sqlSet);
        assertTrue(sqlSet.contains("eff_start"), sqlSet);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("LB"),
                captor.getValue().getParamNameValuePairs().toString());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void noLineReplyIsThreeCharsAndBlanksTwoCells() {
        Date at = d("2023-06-01 00:00:00");
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P01", "LZ"));
        // 一条线都贴不上（整表没有可开口的线）
        when(limitLineService.selectTop(at)).thenReturn(null);

        String reply = service.replyPointLevel(1L, new BigDecimal("60"), at);
        // 回话只写"不归层"三个字
        assertEquals("不归层", reply);

        // 点位行上的线代号与起用那一一日两格清成空白，不拿邻线数值凑答复
        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(pointMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("rule_code="), sqlSet);
        assertTrue(sqlSet.contains("eff_start="), sqlSet);
        // 两格都按空值落库（参数化的 null）
        for (Object v : captor.getValue().getParamNameValuePairs().values()) {
            assertTrue(v == null, captor.getValue().getParamNameValuePairs().toString());
        }
    }

    @Test
    void outOfRangeReadingIsUnclassified() {
        Date at = d("2023-06-01 00:00:00");
        when(pointMapper.selectById(1L)).thenReturn(point(1L, "P01", null));
        when(limitLineService.selectTop(at)).thenReturn(winnerLine());

        // 量程之外（比 30 小、比 120 大）不归层，别硬安一个层
        assertEquals("不归层", service.replyPointLevel(1L, new BigDecimal("29.9"), at));
        assertEquals("不归层", service.replyPointLevel(1L, new BigDecimal("120.1"), at));
        assertEquals("4", service.replyPointLevel(1L, new BigDecimal("66"), at));
    }

    @Test
    void missingPointRepliesUnclassifiedWithoutWriting() {
        when(pointMapper.selectById(anyLong())).thenReturn(null);
        assertEquals("不归层", service.replyPointLevel(9L, new BigDecimal("60"), d("2023-06-01 00:00:00")));
        verify(pointMapper, never()).update(isNull(), any(UpdateWrapper.class));
        assertEquals("不归层", service.replyPointLevel(null, new BigDecimal("60"), d("2023-06-01 00:00:00")));
    }

    @Test
    void orphanPointsListedOneByOne() {
        Date at = d("2023-06-01 00:00:00");
        TNslpPoint p1 = point(1L, "P01", "LA");
        TNslpPoint p2 = point(2L, "P02", "LZ");
        TNslpPoint p3 = point(3L, "P03", null);
        TNslpPoint p4 = point(4L, "P04", "");
        when(pointMapper.selectList(any())).thenReturn(new ArrayList<TNslpPoint>(Arrays.asList(p1, p2)));
        // LA 那一刻仍有在位版本；LZ 挂着代号却寻不到在位版本
        when(limitLineService.selectEffective("LA", at)).thenReturn(winnerLine());
        when(limitLineService.selectEffective("LZ", at)).thenReturn(null);

        List<TNslpPoint> orphans = service.listOrphanPoints(at);
        assertEquals(1, orphans.size());
        assertEquals("P02", orphans.get(0).getPointNo());
        assertTrue(service.listOrphanPoints(null).isEmpty());
    }
}
