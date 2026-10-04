package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.service.ITNslpPointService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 声环境监测点位底册 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "声环境监测点位底册")
@Controller
@RequestMapping("/NslpPointController")
public class NslpPointController extends BaseController {

    private final String prefix = "admin/nslpPoint";

    @Autowired
    private ITNslpPointService nslpPointService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("nslp:nslpPoint:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "声环境监测点位底册集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("nslp:nslpPoint:list")
    @ResponseBody
    public ResultTable list(TNslpPoint record) {
        QueryWrapper<TNslpPoint> queryWrapper = new QueryWrapper<TNslpPoint>();
        startPage();
        com.github.pagehelper.PageInfo<TNslpPoint> page =
                new com.github.pagehelper.PageInfo<TNslpPoint>(nslpPointService.selectTNslpPointList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "声环境监测点位底册新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("nslp:nslpPoint:add")
    @ResponseBody
    public AjaxResult add(TNslpPoint record) {
        return toAjax(nslpPointService.insertTNslpPoint(record));
    }

    @Log(title = "声环境监测点位底册修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("nslp:nslpPoint:edit")
    @ResponseBody
    public AjaxResult editSave(TNslpPoint record) {
        return toAjax(nslpPointService.updateTNslpPoint(record));
    }

    @Log(title = "声环境监测点位底册删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("nslp:nslpPoint:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(nslpPointService.deleteTNslpPointByIds(ids));
    }
}
