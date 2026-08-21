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
import com.ruoyi.system.domain.EduEnrollment;
import com.ruoyi.system.service.IEduEnrollmentService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 课程报名Controller
 * 
 * @author ruoyi
 * @date 2026-08-05
 */
@RestController
@RequestMapping("/system/enrollment")
public class EduEnrollmentController extends BaseController
{
    @Autowired
    private IEduEnrollmentService eduEnrollmentService;

    /**
     * 查询课程报名列表
     */
    @PreAuthorize("@ss.hasPermi('system:enrollment:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduEnrollment eduEnrollment)
    {
        startPage();
        List<EduEnrollment> list = eduEnrollmentService.selectEduEnrollmentList(eduEnrollment);
        return getDataTable(list);
    }

    /**
     * 导出课程报名列表
     */
    @PreAuthorize("@ss.hasPermi('system:enrollment:export')")
    @Log(title = "课程报名", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduEnrollment eduEnrollment)
    {
        List<EduEnrollment> list = eduEnrollmentService.selectEduEnrollmentList(eduEnrollment);
        ExcelUtil<EduEnrollment> util = new ExcelUtil<EduEnrollment>(EduEnrollment.class);
        util.exportExcel(response, list, "课程报名数据");
    }

    /**
     * 获取课程报名详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:enrollment:query')")
    @GetMapping(value = "/{enrollmentId}")
    public AjaxResult getInfo(@PathVariable("enrollmentId") Long enrollmentId)
    {
        return success(eduEnrollmentService.selectEduEnrollmentByEnrollmentId(enrollmentId));
    }

    /**
     * 新增课程报名
     */
    @PreAuthorize("@ss.hasPermi('system:enrollment:add')")
    @Log(title = "课程报名", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody EduEnrollment eduEnrollment)
    {
        return toAjax(eduEnrollmentService.insertEduEnrollment(eduEnrollment));
    }

    /**
     * 修改课程报名
     */
    @PreAuthorize("@ss.hasPermi('system:enrollment:edit')")
    @Log(title = "课程报名", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody EduEnrollment eduEnrollment)
    {
        return toAjax(eduEnrollmentService.updateEduEnrollment(eduEnrollment));
    }

    /**
     * 删除课程报名
     */
    @PreAuthorize("@ss.hasPermi('system:enrollment:remove')")
    @Log(title = "课程报名", businessType = BusinessType.DELETE)
	@DeleteMapping("/{enrollmentIds}")
    public AjaxResult remove(@PathVariable Long[] enrollmentIds)
    {
        return toAjax(eduEnrollmentService.deleteEduEnrollmentByEnrollmentIds(enrollmentIds));
    }
}
