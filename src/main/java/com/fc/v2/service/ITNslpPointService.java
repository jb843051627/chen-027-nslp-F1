package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TNslpPoint;

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
}
