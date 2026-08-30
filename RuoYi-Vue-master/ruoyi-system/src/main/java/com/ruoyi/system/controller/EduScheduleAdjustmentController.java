package com.ruoyi.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.EduScheduleAdjustment;
import com.ruoyi.system.domain.EduScheduleAdjustmentBatch;
import com.ruoyi.system.service.IEduScheduleAdjustmentService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 排课调课记录Controller
 *
 * @author ruoyi
 * @date 2026-08-26
 */
@RestController
@RequestMapping("/system/scheduleAdjustment")
public class EduScheduleAdjustmentController extends BaseController
{
    @Autowired
    private IEduScheduleAdjustmentService eduScheduleAdjustmentService;

    /**
     * 查询排课调课记录列表
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduScheduleAdjustment eduScheduleAdjustment)
    {
        startPage();
        List<EduScheduleAdjustment> list = eduScheduleAdjustmentService.selectEduScheduleAdjustmentList(eduScheduleAdjustment);
        return getDataTable(list);
    }

    /**
     * 导出排课调课记录列表
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:export')")
    @Log(title = "排课调课记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduScheduleAdjustment eduScheduleAdjustment)
    {
        List<EduScheduleAdjustment> list = eduScheduleAdjustmentService.selectEduScheduleAdjustmentList(eduScheduleAdjustment);
        ExcelUtil<EduScheduleAdjustment> util = new ExcelUtil<EduScheduleAdjustment>(EduScheduleAdjustment.class);
        util.exportExcel(response, list, "排课调课记录数据");
    }

    /**
     * 获取排课调课记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:query')")
    @GetMapping(value = "/{adjustmentId}")
    public AjaxResult getInfo(@PathVariable("adjustmentId") Long adjustmentId)
    {
        return success(eduScheduleAdjustmentService.selectEduScheduleAdjustmentByAdjustmentId(adjustmentId));
    }

    /**
     * 新增排课调课记录
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:add')")
    @Log(title = "排课调课记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody EduScheduleAdjustment eduScheduleAdjustment)
    {
        return toAjax(eduScheduleAdjustmentService.insertEduScheduleAdjustment(eduScheduleAdjustment));
    }

    /**
     * 批量调课（按学期+期次将日期范围内的上课日统一调整/停课）
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:add')")
    @Log(title = "排课调课记录", businessType = BusinessType.INSERT)
    @PostMapping("/batch")
    public AjaxResult batchAdjust(@RequestBody EduScheduleAdjustmentBatch batch)
    {
        int count = eduScheduleAdjustmentService.batchAdjust(batch);
        return AjaxResult.success("批量调课成功，共生成 " + count + " 条调课记录", count);
    }

    /**
     * 修改排课调课记录
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:edit')")
    @Log(title = "排课调课记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody EduScheduleAdjustment eduScheduleAdjustment)
    {
        return toAjax(eduScheduleAdjustmentService.updateEduScheduleAdjustment(eduScheduleAdjustment));
    }

    /**
     * 删除排课调课记录
     */
    @PreAuthorize("@ss.hasPermi('system:scheduleAdjustment:remove')")
    @Log(title = "排课调课记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{adjustmentIds}")
    public AjaxResult remove(@PathVariable Long[] adjustmentIds)
    {
        return toAjax(eduScheduleAdjustmentService.deleteEduScheduleAdjustmentByIds(adjustmentIds));
    }
}
