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
import com.ruoyi.system.domain.EduMaterialOrder;
import com.ruoyi.system.service.IEduMaterialOrderService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * 资料订单Controller
 *
 * @author ruoyi
 * @date 2026-08-21
 */
@RestController
@RequestMapping("/system/materialOrder")
public class EduMaterialOrderController extends BaseController
{
    @Autowired
    private IEduMaterialOrderService eduMaterialOrderService;

    /**
     * 查询资料订单列表
     */
    @PreAuthorize("@ss.hasPermi('system:materialOrder:list')")
    @GetMapping("/list")
    public TableDataInfo list(EduMaterialOrder eduMaterialOrder)
    {
        startPage();
        List<EduMaterialOrder> list = eduMaterialOrderService.selectEduMaterialOrderList(eduMaterialOrder);
        return getDataTable(list);
    }

    /**
     * 导出资料订单列表
     */
    @PreAuthorize("@ss.hasPermi('system:materialOrder:export')")
    @Log(title = "资料订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, EduMaterialOrder eduMaterialOrder)
    {
        List<EduMaterialOrder> list = eduMaterialOrderService.selectEduMaterialOrderList(eduMaterialOrder);
        ExcelUtil<EduMaterialOrder> util = new ExcelUtil<EduMaterialOrder>(EduMaterialOrder.class);
        util.exportExcel(response, list, "资料订单数据");
    }

    /**
     * 获取资料订单详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:materialOrder:query')")
    @GetMapping(value = "/{orderId}")
    public AjaxResult getInfo(@PathVariable("orderId") Long orderId)
    {
        return success(eduMaterialOrderService.selectEduMaterialOrderByOrderId(orderId));
    }

    /**
     * 修改资料订单（例如退款）
     */
    @PreAuthorize("@ss.hasPermi('system:materialOrder:edit')")
    @Log(title = "资料订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody EduMaterialOrder eduMaterialOrder)
    {
        return toAjax(eduMaterialOrderService.updateEduMaterialOrder(eduMaterialOrder));
    }

    /**
     * 删除资料订单
     */
    @PreAuthorize("@ss.hasPermi('system:materialOrder:remove')")
    @Log(title = "资料订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public AjaxResult remove(@PathVariable Long[] orderIds)
    {
        return toAjax(eduMaterialOrderService.deleteEduMaterialOrderByOrderIds(orderIds));
    }
}
