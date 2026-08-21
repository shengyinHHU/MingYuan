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
import com.ruoyi.system.domain.EduMaterial;
import com.ruoyi.system.service.IEduMaterialService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 资料商品Controller
 *
 * @author ruoyi
 * @date 2026-08-21
 */
@RestController
@RequestMapping("/system/material")
public class EduMaterialController extends BaseController
{
    @Autowired
    private IEduMaterialService eduMaterialService;

    /**
     * 查询资料商品列表
     */
    @PreAuthorize("@ss.hasPermi('system:material:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduMaterial eduMaterial)
    {
        startPage();
        List<EduMaterial> list = eduMaterialService.selectEduMaterialList(eduMaterial);
        return getDataTable(list);
    }

    /**
     * 导出资料商品列表
     */
    @PreAuthorize("@ss.hasPermi('system:material:export')")
    @Log(title = "资料商品", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduMaterial eduMaterial)
    {
        List<EduMaterial> list = eduMaterialService.selectEduMaterialList(eduMaterial);
        ExcelUtil<EduMaterial> util = new ExcelUtil<EduMaterial>(EduMaterial.class);
        util.exportExcel(response, list, "资料商品数据");
    }

    /**
     * 获取资料商品详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:material:query')")
    @GetMapping(value = "/{materialId}")
    public AjaxResult getInfo(@PathVariable("materialId") Long materialId)
    {
        return success(eduMaterialService.selectEduMaterialByMaterialId(materialId));
    }

    /**
     * 新增资料商品
     */
    @PreAuthorize("@ss.hasPermi('system:material:add')")
    @Log(title = "资料商品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody EduMaterial eduMaterial)
    {
        return toAjax(eduMaterialService.insertEduMaterial(eduMaterial));
    }

    /**
     * 修改资料商品
     */
    @PreAuthorize("@ss.hasPermi('system:material:edit')")
    @Log(title = "资料商品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody EduMaterial eduMaterial)
    {
        return toAjax(eduMaterialService.updateEduMaterial(eduMaterial));
    }

    /**
     * 删除资料商品
     */
    @PreAuthorize("@ss.hasPermi('system:material:remove')")
    @Log(title = "资料商品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{materialIds}")
    public AjaxResult remove(@PathVariable Long[] materialIds)
    {
        return toAjax(eduMaterialService.deleteEduMaterialByMaterialIds(materialIds));
    }
}
