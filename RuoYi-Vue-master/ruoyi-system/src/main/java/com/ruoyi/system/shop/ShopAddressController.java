package com.ruoyi.system.shop;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
@RestController
@RequestMapping("/miniapp/parent/addresses")
@PreAuthorize("@ss.hasRole('parent')")
public class ShopAddressController {
    private final ShopService shop;
    public ShopAddressController(ShopService shop){this.shop=shop;}
    @GetMapping public AjaxResult list(){return AjaxResult.success(shop.addresses(SecurityUtils.getUserId()));}
    @PostMapping public AjaxResult add(@RequestBody Map<String,Object> body){return AjaxResult.success(shop.saveAddress(SecurityUtils.getUserId(),null,body));}
    @PutMapping("/{id}") public AjaxResult edit(@PathVariable long id,@RequestBody Map<String,Object> body){return AjaxResult.success(shop.saveAddress(SecurityUtils.getUserId(),id,body));}
    @DeleteMapping("/{id}") public AjaxResult delete(@PathVariable long id){shop.deleteAddress(SecurityUtils.getUserId(),id);return AjaxResult.success();}
    @PostMapping("/{id}/default") public AjaxResult defaultAddress(@PathVariable long id){return AjaxResult.success(shop.defaultAddress(SecurityUtils.getUserId(),id));}
}
