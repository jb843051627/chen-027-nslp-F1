package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.nslp.LimitLineCore;
import com.fc.v2.mapper.auto.TNslpLimitLineMapper;
import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.service.ITNslpLimitLineService;

/**
 * 功能区昼夜间限值线 Service业务层处理（rule-eval 形状）
 *
 * <p>贴层、在位、撞线定序全部走 {@link LimitLineCore} 这一份算法；
 * 时段判定在内存里做（对照表是配置量级），不把 java.util.Date 直接绑给 JDBC，
 * 免得 serverTimezone 与本机时区不对称把边界判定带偏。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TNslpLimitLineServiceImpl implements ITNslpLimitLineService {

    @javax.annotation.Resource
    private TNslpLimitLineMapper nslpLimitLineMapper;

    @Override
    public TNslpLimitLine selectTNslpLimitLineById(Long id) {
        // 已撤下的线在库里仍是一行，照主键点得到
        return this.nslpLimitLineMapper.selectById(id);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return LimitLineCore.LAYER_NONE;
        }
        return LimitLineCore.layerOf(selectEffective(ruleCode, at), input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return LimitLineCore.LAYER_NONE;
        }
        return LimitLineCore.layerOf(selectTop(at), input);
    }

    @Override
    public List<TNslpLimitLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TNslpLimitLine>();
        }
        return LimitLineCore.available(loadAll(), at);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TNslpLimitLine r = this.nslpLimitLineMapper.selectById(id);
        return LimitLineCore.inEffect(r, at) && LimitLineCore.configValid(r);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    @Override
    public TNslpLimitLine selectEffective(String ruleCode, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        List<TNslpLimitLine> rows = this.nslpLimitLineMapper.selectList(new QueryWrapper<TNslpLimitLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        // 库层按代号收窄一遍，内存再按代号核一遍，定序只看本代号的版本
        List<TNslpLimitLine> mine = new ArrayList<TNslpLimitLine>();
        for (TNslpLimitLine r : rows) {
            if (ruleCode.equals(r.getRuleCode())) {
                mine.add(r);
            }
        }
        // 用 at 那一刻立着的那一版，不拿此刻最新的一版去套旧账
        return LimitLineCore.pickWinner(mine, at);
    }

    @Override
    public TNslpLimitLine selectTop(Date at) {
        if (at == null) {
            return null;
        }
        return LimitLineCore.pickWinner(loadAll(), at);
    }

    @Override
    public List<String> checkLineConfig(TNslpLimitLine line) {
        return LimitLineCore.checkConfig(line);
    }

    @Override
    public List<TNslpLimitLine> listAvailableInQuarter(int year, int quarter) {
        List<TNslpLimitLine> out = new ArrayList<TNslpLimitLine>();
        if (quarter < 1 || quarter > 4) {
            return out;
        }
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, (quarter - 1) * 3, 1);
        Date quarterStart = cal.getTime();
        cal.add(Calendar.MONTH, 3);
        Date quarterEnd = cal.getTime();
        for (TNslpLimitLine r : loadAll()) {
            if (LimitLineCore.windowOverlaps(r, quarterStart, quarterEnd) && LimitLineCore.configValid(r)) {
                out.add(r);
            }
        }
        out.sort(LimitLineCore.WINNER_FIRST);
        return out;
    }

    /** 未删除的全量行；在位、体检、定序都在 LimitLineCore 里过 */
    private List<TNslpLimitLine> loadAll() {
        return this.nslpLimitLineMapper.selectList(new QueryWrapper<TNslpLimitLine>().eq("del_flag", 0));
    }
}
