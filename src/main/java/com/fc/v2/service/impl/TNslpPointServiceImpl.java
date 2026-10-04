package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.nslp.LimitLineCore;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.service.ITNslpLimitLineService;
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

    @Autowired
    private ITNslpLimitLineService nslpLimitLineService;

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

    @Override
    public String replyPointLevel(Long pointId, BigDecimal reading, Date at) {
        if (pointId == null) {
            return LimitLineCore.TEXT_UNCLASSIFIED;
        }
        TNslpPoint point = this.baseMapper.selectById(pointId);
        if (point == null || (point.getDelFlag() != null && point.getDelFlag() != 0)) {
            return LimitLineCore.TEXT_UNCLASSIFIED;
        }
        // 整表取优选开口人、贴层划档，都过 LimitLineCore 这一份算法，与按线代号取值、大屏折线同一次计算
        TNslpLimitLine winner = nslpLimitLineService.selectTop(at);
        int layer = LimitLineCore.layerOf(winner, reading);
        if (layer == LimitLineCore.LAYER_NONE) {
            // 一条线都贴不上：回话只写"不归层"，点位行上的线代号与起用那一一日清成空白，
            // 不拿邻线的数值凑一个像样答复
            this.baseMapper.update(null, new UpdateWrapper<TNslpPoint>()
                    .eq("id", pointId)
                    .eq("del_flag", 0)
                    .set("rule_code", null)
                    .set("eff_start", null));
            return LimitLineCore.TEXT_UNCLASSIFIED;
        }
        // 归到哪一层，就把开口那条线的线代号与起用那一一日钉进点位行，日后能追出当年照的是哪条线
        this.baseMapper.update(null, new UpdateWrapper<TNslpPoint>()
                .eq("id", pointId)
                .eq("del_flag", 0)
                .set("rule_code", winner.getRuleCode())
                .set("eff_start", winner.getEffStart()));
        return String.valueOf(layer);
    }

    @Override
    public List<TNslpPoint> listOrphanPoints(Date at) {
        List<TNslpPoint> orphans = new ArrayList<TNslpPoint>();
        if (at == null) {
            return orphans;
        }
        List<TNslpPoint> nailed = this.baseMapper.selectList(new QueryWrapper<TNslpPoint>()
                .eq("del_flag", 0)
                .isNotNull("rule_code")
                .ne("rule_code", ""));
        for (TNslpPoint p : nailed) {
            if (nslpLimitLineService.selectEffective(p.getRuleCode(), at) == null) {
                orphans.add(p);
            }
        }
        return orphans;
    }
}
