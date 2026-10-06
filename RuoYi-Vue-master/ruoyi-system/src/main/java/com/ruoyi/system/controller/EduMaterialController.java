package com.ruoyi.system.controller;

import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.shop.*;

@RestController
@RequestMapping("/system/material")
public class EduMaterialController {
    private final ShopService shop;
    private final PrivateStorage storage;
    public EduMaterialController(ShopService shop,PrivateStorage storage){this.shop=shop;this.storage=storage;}
    @PreAuthorize("@ss.hasPermi('system:material:list')")
    @GetMapping("/list") public AjaxResult list(@RequestParam Map<String,String> q){
        var data=shop.products(q,SecurityUtils.getUserId(),true,false);
        var result=AjaxResult.success();result.putAll(data);return result;
    }
    @PreAuthorize("@ss.hasPermi('system:material:query')")
    @GetMapping("/{id}") public AjaxResult detail(@PathVariable long id){return AjaxResult.success(shop.product(id,SecurityUtils.getUserId(),true));}
    @PreAuthorize("@ss.hasPermi('system:material:add')")
    @Log(title="资料商品",isSaveResponseData=false,businessType=BusinessType.INSERT)
    @PostMapping public AjaxResult add(@RequestBody Map<String,Object> body){body.remove("materialId");return AjaxResult.success(shop.saveProduct(SecurityUtils.getUserId(),body,false));}
    @PreAuthorize("@ss.hasPermi('system:material:edit')")
    @Log(title="资料商品",isSaveResponseData=false,businessType=BusinessType.UPDATE)
    @PutMapping public AjaxResult edit(@RequestBody Map<String,Object> body){ShopService.require(body.get("materialId")!=null,"资料ID不能为空");return AjaxResult.success(shop.saveProduct(SecurityUtils.getUserId(),body,false));}
    @PreAuthorize("@ss.hasPermi('system:material:edit')")
    @Log(title="商品上下架",isSaveResponseData=false,businessType=BusinessType.UPDATE)
    @PutMapping("/{id}/shelf") public AjaxResult shelf(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(shop.shelf(SecurityUtils.getUserId(),id,ShopService.text(body.get("shelfStatus"))));}
    @PreAuthorize("@ss.hasPermi('system:material:edit')")
    @Log(title="商品库存调整",isSaveResponseData=false,businessType=BusinessType.UPDATE)
    @PostMapping("/{id}/stock-adjustments") public AjaxResult stock(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(shop.stock(SecurityUtils.getUserId(),id,body));}
    @PreAuthorize("@ss.hasPermi('system:material:remove')")
    @Log(title="资料商品",isSaveResponseData=false,businessType=BusinessType.DELETE)
    @DeleteMapping("/{ids}") public AjaxResult delete(@PathVariable long[] ids){for(long id:ids)shop.deleteProduct(SecurityUtils.getUserId(),id,false);return AjaxResult.success();}
    @PreAuthorize("@ss.hasAnyPermi('system:material:add,system:material:edit')")
    @PostMapping("/file") public AjaxResult file(@RequestParam("file") MultipartFile file)throws Exception{return AjaxResult.success(storage.upload(SecurityUtils.getUserId(),file));}
    @PreAuthorize("@ss.hasPermi('system:material:export')")
    @Log(title="资料商品",isSaveResponseData=false,businessType=BusinessType.EXPORT)
    @PostMapping("/export") public void export(@RequestParam Map<String,String> q,HttpServletResponse response)throws Exception{
        ShopExport.write(response,"资料商品",ShopExport.collect(q,p->shop.products(p,SecurityUtils.getUserId(),true,false)),List.of("materialId","title","subjectName","gradeName","price","deliveryType","availableStock","shelfStatus","saleCount"));
    }
}
