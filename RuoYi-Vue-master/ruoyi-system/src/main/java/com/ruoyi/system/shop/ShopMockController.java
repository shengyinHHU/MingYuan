package com.ruoyi.system.shop;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
@RestController
@Profile("(local | test) & !prod & !production")
public class ShopMockController {
    private final ShopService shop;
    public ShopMockController(ShopService shop){this.shop=shop;}
    @PreAuthorize("@ss.hasRole('parent')")
    @PostMapping("/miniapp/parent/material/orders/{id}/mock-pay")
    public AjaxResult confirm(@PathVariable long id){return AjaxResult.success(shop.pay(SecurityUtils.getUserId(),id));}
}
