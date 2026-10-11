package com.ruoyi.framework.web.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Value("${wechat.miniapp.mock-teacher-phone:}")
    private String mockTeacherPhone;

    @Autowired
    private MiniAppDevelopmentSession developmentSession;

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
        return login(loginCode, phoneCode, nickName, devRole, null);
    }

    public String login(String loginCode, String phoneCode, String nickName, String devRole, Long devTeacherId)
    {
        if (devTeacherId != null && !EduRoleConstants.TEACHER.equalsIgnoreCase(StringUtils.trim(devRole)))
        {
            throw new ServiceException("教师账号参数仅适用于教师开发登录");
        }
        if (devTeacherId != null || EduRoleConstants.TEACHER.equalsIgnoreCase(StringUtils.trim(devRole)))
        {
            assertDevelopmentTeacherLogin();
        }
        if (mockEnabled && StringUtils.isNotBlank(devRole))
        {
            return loginMockRole(devRole, nickName, devTeacherId);
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

    private String loginMockRole(String devRole, String nickName, Long devTeacherId)
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
            user = devTeacherId == null ? findMockTeacherUser() : userService.selectUserById(devTeacherId);
            if (!isEligibleTeacher(user))
            {
                throw new ServiceException("该教师不存在、已停用或不具备独立教师身份，请刷新教师列表");
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

    private void assertDevelopmentTeacherLogin()
    {
        developmentSession.getVersion();
    }

    private SysUser findMockTeacherUser()
    {
        // Explicit configuration must not silently log in as a different teacher.
        boolean byUsername = StringUtils.isNotBlank(mockTeacherUsername);
        if (byUsername || StringUtils.isNotBlank(mockTeacherPhone))
        {
            SysUser user = byUsername
                    ? userService.selectUserByUserName(mockTeacherUsername.trim())
                    : findExistingTeacher(mockTeacherPhone.trim());
            if (!isEligibleTeacher(user))
            {
                throw new ServiceException("配置的模拟教师不可用，请检查账号、启用状态和教师角色（WECHAT_MOCK_TEACHER_USERNAME / WECHAT_MOCK_TEACHER_PHONE）");
            }
            return user;
        }
        return findExistingTeacher(null);
    }

    private SysUser findExistingTeacher(String phone)
    {
        // Reuse existing users and role assignments; never create a demo teacher.
        SysRole teacherRole = roleMapper.checkRoleKeyUnique(EduRoleConstants.TEACHER);
        if (teacherRole == null || !UserConstants.ROLE_NORMAL.equals(teacherRole.getStatus())
                || !UserConstants.NORMAL.equals(teacherRole.getDelFlag()))
        {
            return null;
        }
        SysUser filter = new SysUser();
        filter.setRoleId(teacherRole.getRoleId());
        List<SysUser> candidates = userMapper.selectAllocatedList(filter).stream()
                .filter(user -> UserConstants.NORMAL.equals(user.getStatus()))
                .filter(user -> phone == null || phone.equals(user.getPhonenumber()))
                .sorted(Comparator.comparing(SysUser::getUserId))
                .map(user -> userService.selectUserById(user.getUserId()))
                .filter(this::isEligibleTeacher)
                .limit(phone == null ? 1 : 2).toList();
        if (candidates.size() > 1)
        {
            throw new ServiceException("该手机号对应多个可用教师，请用 WECHAT_MOCK_TEACHER_USERNAME 指定用户名");
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public String developmentSessionVersion()
    {
        return developmentSession.getVersion();
    }

    public String loginDevelopmentSessionVersion()
    {
        return developmentSession.isEnabled() ? developmentSession.getVersion() : null;
    }

    public List<Map<String, Object>> developmentTeachers()
    {
        assertDevelopmentTeacherLogin();
        List<Map<String, Object>> options = new ArrayList<>();
        SysRole teacherRole = roleMapper.checkRoleKeyUnique(EduRoleConstants.TEACHER);
        if (teacherRole == null || !"0".equals(teacherRole.getStatus()) || !"0".equals(teacherRole.getDelFlag()))
        {
            return options;
        }
        SysUser query = new SysUser();
        query.setRoleId(teacherRole.getRoleId());
        // Reuse the existing teacher-role query used by timetable management.
        List<SysUser> users = new ArrayList<>(userMapper.selectAllocatedList(query));
        users.sort(Comparator.comparing(SysUser::getNickName, Comparator.nullsLast(String::compareTo))
                .thenComparing(SysUser::getUserId));
        for (SysUser candidate : users)
        {
            SysUser user = userService.selectUserById(candidate.getUserId());
            if (!isEligibleTeacher(user))
            {
                continue;
            }
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("userId", String.valueOf(user.getUserId()));
            option.put("name", StringUtils.defaultIfBlank(user.getNickName(), user.getUserName()));
            option.put("userName", user.getUserName());
            options.add(option);
        }
        return options;
    }

    private boolean isEligibleTeacher(SysUser user)
    {
        if (user == null || user.isAdmin() || !"0".equals(user.getStatus())
                || !"0".equals(user.getDelFlag()) || user.getRoles() == null)
        {
            return false;
        }
        for (SysRole role : user.getRoles())
        {
            // An administrator account must not enter through the teacher shortcut.
            if (role.isAdmin() || EduRoleConstants.ADMIN.equals(role.getRoleKey())
                    || "administrator".equals(role.getRoleKey()))
            {
                return false;
            }
        }
        // Check active role assignments directly so a deleted teacher role cannot grant login.
        return roleMapper.selectRolePermissionByUserId(user.getUserId()).stream()
                .anyMatch(role -> EduRoleConstants.TEACHER.equals(role.getRoleKey())
                        && UserConstants.ROLE_NORMAL.equals(role.getStatus())
                        && UserConstants.NORMAL.equals(role.getDelFlag()));
    }

    private String createLoginToken(SysUser user)
    {
        userService.updateLoginInfo(user.getUserId(), IpUtils.getIpAddr(), DateUtils.getNowDate());
        Set<String> permissions = permissionService.getMenuPermission(user);
        LoginUser loginUser = new LoginUser(user.getUserId(), user.getDeptId(), user, permissions);
        if (developmentSession.isEnabled())
        {
            return tokenService.createDevelopmentToken(loginUser);
        }
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
