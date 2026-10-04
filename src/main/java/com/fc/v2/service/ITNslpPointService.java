package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TNslpPoint;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 声环境监测点位底册 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITNslpPointService {

    /** 按主键查询 */
    TNslpPoint selectTNslpPointById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TNslpPoint> selectTNslpPointList(Wrapper<TNslpPoint> queryWrapper);

    /** 新增 */
    int insertTNslpPoint(TNslpPoint record);

    /** 修改 */
    int updateTNslpPoint(TNslpPoint record);

    /** 批量删除 */
    int deleteTNslpPointByIds(String ids);

    /** 按主键删除 */
    int deleteTNslpPointById(Long id);

    /**
     * 点位回话：按折算读数在 at 时刻整表取优贴层。
     * 贴上就把开口那条线的线代号与起用那一一日钉进点位行，回层号（"1".."4"）；
     * 一条线都贴不上就把点位行上这两格清成空白，回话只写"不归层"三个字。
     * 算法与按线代号取值、大屏折线同出 {@link com.fc.v2.common.nslp.LimitLineCore}。
     */
    String replyPointLevel(Long pointId, BigDecimal reading, Date at);

    /** 看板上挂着线代号、回到 at 那一刻却寻不到在位版本的点位，逐条列出 */
    List<TNslpPoint> listOrphanPoints(Date at);
}

