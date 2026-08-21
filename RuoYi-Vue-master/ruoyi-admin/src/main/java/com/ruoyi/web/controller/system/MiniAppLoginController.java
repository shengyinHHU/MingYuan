package com.ruoyi.web.controller.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.MiniAppLoginBody;
import com.ruoyi.framework.web.service.MiniAppLoginService;

/**
 * WeChat mini program login controller.
 */
@RestController
public class MiniAppLoginController
{
    @Autowired
    private MiniAppLoginService miniAppLoginService;

    @PostMapping("/miniapp/login")
    public AjaxResult login(@RequestBody MiniAppLoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        String token = miniAppLoginService.login(loginBody.getLoginCode(), loginBody.getPhoneCode(),
                loginBody.getNickName(), loginBody.getDevRole());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }
}
