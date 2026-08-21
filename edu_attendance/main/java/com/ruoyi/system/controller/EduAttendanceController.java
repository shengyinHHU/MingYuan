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
import com.ruoyi.system.domain.EduAttendance;
import com.ruoyi.system.service.IEduAttendanceService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 课程上课记录Controller
 * 
 * @author ruoyi
 * @date 2026-08-06
 */
@RestController
@RequestMapping("/system/attendance")
public class EduAttendanceController extends BaseController
{
    @Autowired
    private IEduAttendanceService eduAttendanceService;

    /**
     * 查询课程上课记录列表
     */
    @PreAuthorize("@ss.hasPermi('system:attendance:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduAttendance eduAttendance)
    {
        startPage();
        List<EduAttendance> list = eduAttendanceService.selectEduAttendanceList(eduAttendance);
        return getDataTable(list);
    }

    /**
     * 导出课程上课记录列表
     */
    @PreAuthorize("@ss.hasPermi('system:attendance:export')")
    @Log(title = "课程上课记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduAttendance eduAttendance)
    {
        List<EduAttendance> list = eduAttendanceService.selectEduAttendanceList(eduAttendance);
        ExcelUtil<EduAttendance> util = new ExcelUtil<EduAttendance>(EduAttendance.class);
        util.exportExcel(response, list, "课程上课记录数据");
    }

    /**
     * 获取课程上课记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:attendance:query')")
    @GetMapping(value = "/{attendanceId}")
    public AjaxResult getInfo(@PathVariable("attendanceId") Long attendanceId)
    {
        return success(eduAttendanceService.selectEduAttendanceByAttendanceId(attendanceId));
    }

    /**
     * 新增课程上课记录
     */
    @PreAuthorize("@ss.hasPermi('system:attendance:add')")
    @Log(title = "课程上课记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody EduAttendance eduAttendance)
    {
        return toAjax(eduAttendanceService.insertEduAttendance(eduAttendance));
    }

    /**
     * 修改课程上课记录
     */
    @PreAuthorize("@ss.hasPermi('system:attendance:edit')")
    @Log(title = "课程上课记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody EduAttendance eduAttendance)
    {
        return toAjax(eduAttendanceService.updateEduAttendance(eduAttendance));
    }

    /**
     * 删除课程上课记录
     */
    @PreAuthorize("@ss.hasPermi('system:attendance:remove')")
    @Log(title = "课程上课记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{attendanceIds}")
    public AjaxResult remove(@PathVariable Long[] attendanceIds)
    {
        return toAjax(eduAttendanceService.deleteEduAttendanceByAttendanceIds(attendanceIds));
    }
}
