package com.ruoyi.system.controller;

import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.shop.*;

@RestController
@RequestMapping("/system/materialOrder")
public class EduMaterialOrderController {
    private final ShopService shop;
    public EduMaterialOrderController(ShopService shop){this.shop=shop;}
    @PreAuthorize("@ss.hasPermi('system:materialOrder:list')")
    @GetMapping("/list") public AjaxResult list(@RequestParam Map<String,String> q){
        var data=shop.orders(q,SecurityUtils.getUserId(),true);var result=AjaxResult.success();result.putAll(data);return result;
    }
    @PreAuthorize("@ss.hasPermi('system:materialOrder:query')")
    @GetMapping("/{id}") public AjaxResult detail(@PathVariable long id){return AjaxResult.success(shop.order(id,SecurityUtils.getUserId(),true));}
    @PreAuthorize("@ss.hasPermi('system:materialOrder:edit')")
    @Log(title="资料订单发货",isSaveResponseData=false,businessType=BusinessType.UPDATE)
    @PostMapping("/{id}/ship") public AjaxResult ship(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(shop.ship(SecurityUtils.getUserId(),id,body));}
    @PreAuthorize("@ss.hasPermi('system:materialOrder:edit')")
    @Log(title="资料订单模拟退款",isSaveResponseData=false,businessType=BusinessType.UPDATE)
    @PostMapping("/{id}/refund") public AjaxResult refund(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(shop.refund(SecurityUtils.getUserId(),id,ShopService.text(body.get("reason"))));}
    @PreAuthorize("@ss.hasPermi('system:materialOrder:edit')")
    @PutMapping public AjaxResult disabledEdit(){return AjaxResult.error("订单不允许通用编辑，请使用发货或退款操作");}
    @PreAuthorize("@ss.hasPermi('system:materialOrder:remove')")
    @DeleteMapping("/{ids}") public AjaxResult disabledDelete(){return AjaxResult.error("交易订单不允许删除");}
    @PreAuthorize("@ss.hasPermi('system:materialOrder:export')")
    @Log(title="资料订单",isSaveResponseData=false,businessType=BusinessType.EXPORT)
    @PostMapping("/export") public void export(@RequestParam Map<String,String> q,HttpServletResponse response)throws Exception{
        ShopExport.write(response,"资料订单",ShopExport.collect(q,p->shop.orders(p,SecurityUtils.getUserId(),true)),List.of("orderId","orderCode","parentName","orderStatus","payStatus","totalQuantity","goodsAmount","shippingAmount","payableAmount","amount","refundedAmount","receiverName","receiverPhone","createTime","payTime"));
    }
}
