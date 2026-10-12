package com.ruoyi.framework.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.uuid.IdUtils;

/** Local mini-program sessions last only for this backend process. */
@Component
public class MiniAppDevelopmentSession
{
    private final String version = IdUtils.fastUUID();

    @Value("${wechat.miniapp.mock-enabled:false}")
    private boolean mockEnabled;

    /** 模拟环境（mock-enabled=true）即开放角色免密切换，不再要求 local profile，方便任意方式启动后端调试。 */
    public boolean isEnabled()
    {
        return mockEnabled;
    }

    public String getVersion()
    {
        if (!isEnabled())
        {
            throw new ServiceException("角色免密切换仅在本地开发环境开放");
        }
        return version;
    }

    public boolean matches(String candidate)
    {
        return isEnabled() && version.equals(candidate);
    }
}
