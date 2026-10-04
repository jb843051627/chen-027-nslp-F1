package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.service.ITNslpPointService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 声环境监测点位底册Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TNslpPointServiceImpl extends ServiceImpl<TNslpPointMapper, TNslpPoint> implements ITNslpPointService {

    @Autowired
    private TNslpZoneBookMapper nslpZoneBookMapper;

    @Override
    public TNslpPoint selectTNslpPointById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TNslpPoint>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TNslpPoint> selectTNslpPointList(Wrapper<TNslpPoint> queryWrapper) {
        QueryWrapper<TNslpPoint> wrapper = new QueryWrapper<TNslpPoint>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTNslpPoint(TNslpPoint record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getPointNo());
        TNslpZoneBook refArch = nslpZoneBookMapper.selectOne(new QueryWrapper<TNslpZoneBook>()
                .eq("id", record.getZoneId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setZoneNo(refArch.getZoneNo());
        if (StringUtils.isNotEmpty(record.getPointNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TNslpPoint>()
                    .eq("point_no", record.getPointNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        int remIn = record.getNeedNum() == null ? 0 : record.getNeedNum();
        int remOut = record.getGotNum() == null ? 0 : record.getGotNum();
        record.setLackNum(remIn + remOut);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTNslpPoint(TNslpPoint record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getPointNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TNslpPoint>()
                    .eq("point_no", record.getPointNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TNslpPoint>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTNslpPointByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTNslpPointById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
