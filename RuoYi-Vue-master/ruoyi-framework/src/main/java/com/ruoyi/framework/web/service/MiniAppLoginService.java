package com.ruoyi.framework.web.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.EduRoleConstants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.HttpUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysUserService;

/**
 * WeChat mini program login service.
 */
@Component
public class MiniAppLoginService
{
    @Value("${wechat.miniapp.appid:}")
    private String appid;

    @Value("${wechat.miniapp.secret:}")
    private String secret;

    @Value("${wechat.miniapp.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${wechat.miniapp.mock-phone:13800000000}")
    private String mockPhone;

    @Value("${wechat.miniapp.mock-admin-username:admin}")
    private String mockAdminUsername;

    @Value("${wechat.miniapp.mock-teacher-username:}")
    private String mockTeacherUsername;

    @Value("${wechat.miniapp.mock-teacher-phone:13910000002}")
    private String mockTeacherPhone;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    public String login(String loginCode, String phoneCode, String nickName, String devRole)
    {
        if (mockEnabled && StringUtils.isNotBlank(devRole))
        {
            return loginMockRole(devRole, nickName);
        }

        String openid;
        String phoneNumber;
        if (mockEnabled)
        {
            openid = "mock-openid-" + mockPhone;
            phoneNumber = mockPhone;
        }
        else
        {
            if (StringUtils.isBlank(appid) || StringUtils.isBlank(secret))
            {
                throw new ServiceException("Please configure wechat.miniapp.appid and secret");
            }
            if (StringUtils.isBlank(loginCode) || StringUtils.isBlank(phoneCode))
            {
                throw new ServiceException("WeChat login code and phone code are required");
            }
            openid = getOpenid(loginCode);
            phoneNumber = getPhoneNumber(phoneCode);
        }

        SysUser user = findOrCreateParentUser(phoneNumber, nickName, openid);
        if (UserConstants.USER_DISABLE.equals(user.getStatus()))
        {
            throw new ServiceException("Account is disabled");
        }

        return createLoginToken(user);
    }

    private String loginMockRole(String devRole, String nickName)
    {
        String role = StringUtils.defaultString(devRole).trim().toLowerCase();
        SysUser user;
        if (EduRoleConstants.ADMIN.equals(role))
        {
            // 支持配置为用户名或手机号
            user = userService.selectUserByUserName(mockAdminUsername);
            if (user == null)
            {
                user = userService.selectUserByPhonenumber(mockAdminUsername);
            }
            if (user == null)
            {
                throw new ServiceException("Mock admin user does not exist: " + mockAdminUsername);
            }
        }
        else if (EduRoleConstants.TEACHER.equals(role))
        {
            user = findMockTeacherUser();
            if (user == null)
            {
                throw new ServiceException("Mock teacher user does not exist. Please check mock teacher config");
            }
        }
        else if (EduRoleConstants.PARENT.equals(role))
        {
            user = findOrCreateParentUser(mockPhone, nickName, "mock-openid-" + mockPhone);
        }
        else
        {
            throw new ServiceException("Unsupported mock role: " + devRole);
        }

        if (UserConstants.USER_DISABLE.equals(user.getStatus()))
        {
            throw new ServiceException("Account is disabled");
        }

        return createLoginToken(user);
    }

    private SysUser findMockTeacherUser()
    {
        SysUser user = null;
        if (StringUtils.isNotBlank(mockTeacherUsername))
        {
            user = userService.selectUserByUserName(mockTeacherUsername);
        }
        if (user == null && StringUtils.isNotBlank(mockTeacherPhone))
        {
            user = userService.selectUserByPhonenumber(mockTeacherPhone);
        }
        return user;
    }

    private String createLoginToken(SysUser user)
    {
        userService.updateLoginInfo(user.getUserId(), IpUtils.getIpAddr(), DateUtils.getNowDate());
        Set<String> permissions = permissionService.getMenuPermission(user);
        LoginUser loginUser = new LoginUser(user.getUserId(), user.getDeptId(), user, permissions);
        return tokenService.createToken(loginUser);
    }

    private SysUser findOrCreateParentUser(String phoneNumber, String nickName, String openid)
    {
        SysRole parentRole = getParentRole();
        // 家长账号的user_name为学生姓名(导入数据)，登录统一按手机号字段匹配；
        // 同一手机号可能同时绑定教师/管理员账号，此处仅匹配含家长角色的账号
        SysUser user = userMapper.selectParentUserByPhone(phoneNumber, parentRole.getRoleId());
        if (user == null)
        {
            user = new SysUser();
            user.setUserName(phoneNumber);
            user.setNickName(StringUtils.defaultIfBlank(nickName, "MiniApp Parent"));
            user.setPhonenumber(phoneNumber);
            user.setPassword(SecurityUtils.encryptPassword(IdUtils.fastSimpleUUID()));
            user.setStatus(UserConstants.NORMAL);
            user.setCreateBy("miniapp");
            user.setRemark("Mini program register, openid=" + openid);
            user.setRoleIds(new Long[] { parentRole.getRoleId() });
            if (userService.insertUser(user) <= 0)
            {
                throw new ServiceException("Failed to create parent account");
            }
        }
        else
        {
            ensureParentRole(user, parentRole);
        }
        return userService.selectUserById(user.getUserId());
    }

    private void ensureParentRole(SysUser user, SysRole parentRole)
    {
        List<SysRole> roles = roleMapper.selectRolePermissionByUserId(user.getUserId());
        boolean hasParentRole = roles.stream().anyMatch(role -> EduRoleConstants.PARENT.equals(role.getRoleKey()));
        if (!hasParentRole && !user.isAdmin())
        {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(user.getUserId());
            userRole.setRoleId(parentRole.getRoleId());
            userRoleMapper.batchUserRole(Collections.singletonList(userRole));
        }
    }

    private SysRole getParentRole()
    {
        SysRole parentRole = roleMapper.checkRoleKeyUnique(EduRoleConstants.PARENT);
        if (parentRole == null)
        {
            throw new ServiceException("Parent role does not exist. Please run edu_roles.sql first");
        }
        return parentRole;
    }

    private String getOpenid(String loginCode)
    {
        String params = "appid=" + encode(appid)
                + "&secret=" + encode(secret)
                + "&js_code=" + encode(loginCode)
                + "&grant_type=authorization_code";
        JSONObject json = requestJson("https://api.weixin.qq.com/sns/jscode2session", params);
        String openid = json.getString("openid");
        if (StringUtils.isBlank(openid))
        {
            throw new ServiceException("WeChat login failed: " + json.getString("errmsg"));
        }
        return openid;
    }

    private String getPhoneNumber(String phoneCode)
    {
        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + encode(accessToken);
        JSONObject body = new JSONObject();
        body.put("code", phoneCode);
        String response = HttpUtils.sendPost(url, body.toJSONString(), MediaType.APPLICATION_JSON_VALUE);
        JSONObject json = parseWechatResponse(response);
        JSONObject phoneInfo = json.getJSONObject("phone_info");
        String phoneNumber = phoneInfo == null ? null : phoneInfo.getString("phoneNumber");
        if (StringUtils.isBlank(phoneNumber))
        {
            throw new ServiceException("Failed to get WeChat phone number: " + json.getString("errmsg"));
        }
        return phoneNumber;
    }

    private String getAccessToken()
    {
        String params = "grant_type=client_credential"
                + "&appid=" + encode(appid)
                + "&secret=" + encode(secret);
        JSONObject json = requestJson("https://api.weixin.qq.com/cgi-bin/token", params);
        String accessToken = json.getString("access_token");
        if (StringUtils.isBlank(accessToken))
        {
            throw new ServiceException("Failed to get WeChat access_token: " + json.getString("errmsg"));
        }
        return accessToken;
    }

    private JSONObject requestJson(String url, String params)
    {
        String response = HttpUtils.sendGet(url, params);
        return parseWechatResponse(response);
    }

    private JSONObject parseWechatResponse(String response)
    {
        if (StringUtils.isBlank(response))
        {
            throw new ServiceException("WeChat API has no response");
        }
        JSONObject json = JSONObject.parseObject(response);
        Integer errcode = json.getInteger("errcode");
        if (errcode != null && errcode != 0)
        {
            throw new ServiceException("WeChat API error: " + json.getString("errmsg"));
        }
        return json;
    }

    private String encode(String value)
    {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }
}
