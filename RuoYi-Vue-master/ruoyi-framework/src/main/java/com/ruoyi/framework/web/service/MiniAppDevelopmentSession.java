package com.ruoyi.framework.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

    @Autowired
    private Environment environment;

    public boolean isEnabled()
    {
        return mockEnabled && environment.acceptsProfiles(Profiles.of("local"));
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
