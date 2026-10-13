package com.ruoyi.web.controller.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.annotation.Anonymous;
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

    @Anonymous
    @GetMapping("/miniapp/dev/session")
    public AjaxResult developmentSession()
    {
        return AjaxResult.success(java.util.Map.of("version", miniAppLoginService.developmentSessionVersion()));
    }

    @Anonymous
    @GetMapping("/miniapp/dev/teachers")
    public AjaxResult teachers()
    {
        return AjaxResult.success(miniAppLoginService.developmentTeachers());
    }

    @PostMapping("/miniapp/login")
    public AjaxResult login(@RequestBody MiniAppLoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        String token = miniAppLoginService.login(loginBody.getLoginCode(), loginBody.getPhoneCode(),
                loginBody.getNickName(), loginBody.getDevRole(), loginBody.getDevTeacherId());
        ajax.put(Constants.TOKEN, token);
        String developmentVersion = miniAppLoginService.loginDevelopmentSessionVersion();
        if (developmentVersion != null)
        {
            ajax.put("devSessionVersion", developmentVersion);
        }
        return ajax;
    }
}
