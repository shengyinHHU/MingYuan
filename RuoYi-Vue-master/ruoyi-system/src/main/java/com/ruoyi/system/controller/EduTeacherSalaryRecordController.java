package com.ruoyi.system.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.EduTeacherSalaryRecord;
import com.ruoyi.system.service.IEduTeacherSalaryRecordService;

/**
 * 教师薪资结算Controller
 *
 * @author ruoyi
 * @date 2026-09-04
 */
@RestController
@RequestMapping("/system/salaryRecord")
public class EduTeacherSalaryRecordController extends BaseController
{
    @Autowired
    private IEduTeacherSalaryRecordService recordService;

    /**
     * 结算单列表（已入库快照）
     */
    @PreAuthorize("@ss.hasPermi('system:salaryRecord:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduTeacherSalaryRecord query)
    {
        startPage();
        List<EduTeacherSalaryRecord> list = recordService.selectRecordList(query);
        return getDataTable(list);
    }

    /**
     * 按月预览（实时统计，不入库）
     */
    @PreAuthorize("@ss.hasPermi('system:salaryRecord:list')")
    @GetMapping("/preview")
    public AjaxResult preview(@RequestParam("salaryMonth") String salaryMonth)
    {
        return success(recordService.previewMonth(salaryMonth));
    }

    /**
     * 生成/重算月度结算
     */
    @PreAuthorize("@ss.hasPermi('system:salaryRecord:add')")
    @Log(title = "教师薪资结算", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestParam("salaryMonth") String salaryMonth)
    {
        int count = recordService.generateMonth(salaryMonth);
        return success("已生成 " + count + " 位教师的结算单").put("count", count);
    }

    /**
     * 确认结算单
     */
    @PreAuthorize("@ss.hasPermi('system:salaryRecord:edit')")
    @Log(title = "教师薪资结算确认", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{recordId}")
    public AjaxResult confirm(@PathVariable("recordId") Long recordId)
    {
        return toAjax(recordService.confirmRecord(recordId));
    }

    /**
     * 删除结算单（仅待确认）
     */
    @PreAuthorize("@ss.hasPermi('system:salaryRecord:remove')")
    @Log(title = "教师薪资结算", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(recordService.deleteRecordByIds(recordIds));
    }
}
