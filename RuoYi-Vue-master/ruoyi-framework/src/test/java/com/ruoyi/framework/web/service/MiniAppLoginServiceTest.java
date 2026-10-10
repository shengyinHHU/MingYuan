package com.ruoyi.framework.web.service;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.ISysUserService;
import static org.junit.jupiter.api.Assertions.*;

class MiniAppLoginServiceTest
{
    private MiniAppLoginService service;
    private List<SysUser> users;
    private SysRole teacherRole;
    private LoginUser issuedToken;
    private boolean developmentEnabled;

    @BeforeEach
    void setup() throws Exception
    {
        service = new MiniAppLoginService();
        teacherRole = new SysRole(7L);
        teacherRole.setRoleKey("teacher");
        teacherRole.setStatus("0");
        teacherRole.setDelFlag("0");
        users = List.of(user(10L, "teacher_a", "0", List.of(teacherRole)));
        issuedToken = null;
        developmentEnabled = true;
        set("developmentSession", new MiniAppDevelopmentSession() {
            @Override public boolean isEnabled() { return developmentEnabled; }
            @Override public String getVersion() {
                if (!developmentEnabled) throw new ServiceException("Development login disabled");
                return "test-session";
            }
        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                proxy(HttpServletRequest.class, (method, args) -> switch (method) {
                    case "getHeader" -> null;
                    case "getRemoteAddr" -> "127.0.0.1";
                    default -> throw new AssertionError("Unexpected request call: " + method);
                })));
        set("mockEnabled", true);
        set("mockTeacherUsername", "");
        set("mockTeacherPhone", "");
        // Replace only database access, login metadata writes, permissions and Redis token storage.
        set("userService", proxy(ISysUserService.class, (method, args) -> switch (method) {
            case "selectUserByUserName" -> users.stream().filter(u -> u.getUserName().equals(args[0])).findFirst().orElse(null);
            case "selectUserByPhonenumber" -> users.stream().filter(u -> u.getPhonenumber().equals(args[0]))
                    .findFirst().map(u -> user(u.getUserId(), u.getUserName(), u.getStatus(),
                            u.getRoles().stream().limit(1).toList())).orElse(null);
            case "selectUserById" -> users.stream().filter(u -> u.getUserId().equals(args[0])).findFirst().orElse(null);
            case "updateLoginInfo" -> 1;
            default -> throw new AssertionError("Unexpected user service call: " + method);
        }));
        set("userMapper", proxy(SysUserMapper.class, (method, args) -> {
            if (!"selectAllocatedList".equals(method)) throw new AssertionError(method);
            SysUser filter = (SysUser) args[0];
            return users.stream().filter(u -> u.getRoles().stream()
                    .anyMatch(r -> r.getRoleId().equals(filter.getRoleId()))).toList();
        }));
        set("roleMapper", proxy(SysRoleMapper.class, (method, args) -> switch (method) {
            case "checkRoleKeyUnique" -> "teacher".equals(args[0]) ? teacherRole : null;
            case "selectRolePermissionByUserId" -> users.stream().filter(u -> u.getUserId().equals(args[0]))
                    .findFirst().orElseThrow().getRoles().stream().filter(r -> "0".equals(r.getDelFlag())).toList();
            default -> throw new AssertionError("Unexpected role mapper call: " + method);
        }));
        set("permissionService", new SysPermissionService() {
            @Override public Set<String> getMenuPermission(SysUser user) { return Set.of("teacher:test"); }
        });
        set("tokenService", new TokenService() {
            @Override public String createToken(LoginUser loginUser) {
                issuedToken = loginUser;
                return "test-token";
            }
            @Override public String createDevelopmentToken(LoginUser loginUser) {
                return createToken(loginUser);
            }
        });
    }

    @AfterEach
    void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void unspecifiedTeacherUsesLowestIdEnabledExistingTeacher()
    {
        users = List.of(user(20L, "teacher_b", "0", List.of(teacherRole)),
                user(2L, "disabled", "1", List.of(teacherRole)),
                user(10L, "teacher_a", "0", List.of(teacherRole)));
        assertEquals("test-token", login());
        assertEquals(10L, issuedToken.getUserId());
    }

    @Test
    void explicitUsernameOverridesAutomaticChoice() throws Exception
    {
        users = List.of(users.get(0), user(20L, "teacher_b", "0", List.of(teacherRole)));
        set("mockTeacherUsername", "teacher_b");
        assertEquals("test-token", login());
        assertEquals(20L, issuedToken.getUserId());
    }

    @Test
    void missingExplicitUsernameDoesNotSilentlySwitchToPhoneOrAnotherTeacher() throws Exception
    {
        set("mockTeacherUsername", "missing_teacher");
        set("mockTeacherPhone", users.get(0).getPhonenumber());
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void explicitPhoneIsSupportedWhenUsernameIsBlank() throws Exception
    {
        set("mockTeacherPhone", users.get(0).getPhonenumber());
        assertEquals("test-token", login());
        assertEquals(10L, issuedToken.getUserId());
    }

    @Test
    void phoneLoginRetainsAllRolesDespiteLegacyJoinedLookupLimit() throws Exception
    {
        SysRole parent = new SysRole(8L);
        parent.setRoleKey("parent");
        parent.setStatus("0");
        parent.setDelFlag("0");
        users.get(0).setRoles(List.of(parent, teacherRole));
        set("mockTeacherPhone", users.get(0).getPhonenumber());
        assertEquals("test-token", login());
        assertEquals(List.of("parent", "teacher"), issuedToken.getUser().getRoles().stream()
                .map(SysRole::getRoleKey).toList());
    }

    @Test
    void sharedParentPhoneSelectsTheTeacherNotTheFirstUser() throws Exception
    {
        SysUser parent = user(3L, "parent_a", "0", List.of());
        parent.setPhonenumber(users.get(0).getPhonenumber());
        users = List.of(parent, users.get(0));
        set("mockTeacherPhone", parent.getPhonenumber());
        assertEquals("test-token", login());
        assertEquals(10L, issuedToken.getUserId());
    }

    @Test
    void phoneSharedByTwoEnabledTeachersRequiresExplicitUsername() throws Exception
    {
        SysUser other = user(20L, "teacher_b", "0", List.of(teacherRole));
        other.setPhonenumber(users.get(0).getPhonenumber());
        users = List.of(users.get(0), other);
        set("mockTeacherPhone", other.getPhonenumber());
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void explicitNonTeacherIsRejected() throws Exception
    {
        users = List.of(user(10L, "parent_a", "0", List.of()));
        set("mockTeacherUsername", "parent_a");
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void disabledOrDeletedExplicitTeacherIsRejected() throws Exception
    {
        set("mockTeacherUsername", "teacher_a");
        users.get(0).setStatus("1");
        assertThrows(ServiceException.class, this::login);
        users.get(0).setStatus("0");
        users.get(0).setDelFlag("2");
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void disabledOrDeletedTeacherRoleCannotIssueToken() throws Exception
    {
        set("mockTeacherUsername", "teacher_a");
        teacherRole.setStatus("1");
        assertThrows(ServiceException.class, this::login);
        teacherRole.setStatus("0");
        teacherRole.setDelFlag("2");
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void emptyTeacherDatabaseCannotIssueToken()
    {
        users = List.of();
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void disabledTeacherRoleCannotBeSelectedAutomatically()
    {
        teacherRole.setStatus("1");
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void devRoleCannotBypassRealWechatLoginWhenMockIsDisabled() throws Exception
    {
        set("mockEnabled", false);
        set("appid", "");
        set("secret", "");
        assertThrows(ServiceException.class, this::login);
        assertNull(issuedToken);
    }

    @Test
    void selectedTeacherOverridesConfiguredFallback() throws Exception
    {
        users = List.of(users.get(0), user(20L, "teacher_b", "0", List.of(teacherRole)));
        set("mockTeacherUsername", "teacher_a");
        assertEquals("test-token", service.login("mock", "mock", "Teacher", "teacher", 20L));
        assertEquals(20L, issuedToken.getUserId());
    }

    @Test
    void administratorCannotEnterThroughTeacherLogin() throws Exception
    {
        SysRole administrator = new SysRole(8L);
        administrator.setRoleKey("administrator");
        administrator.setStatus("0");
        administrator.setDelFlag("0");
        users.get(0).setRoles(List.of(teacherRole, administrator));
        assertThrows(ServiceException.class, this::login);
        assertThrows(ServiceException.class, () -> service.login("mock", "mock", "Teacher", "teacher", 10L));
        assertTrue(service.developmentTeachers().isEmpty());
        assertNull(issuedToken);
    }

    @Test
    void teacherLoginAndTeacherListRequireLocalDevelopmentSession()
    {
        developmentEnabled = false;
        assertThrows(ServiceException.class, this::login);
        assertThrows(ServiceException.class, () -> service.login("mock", "mock", "Teacher", "teacher", 10L));
        assertThrows(ServiceException.class, service::developmentTeachers);
        assertNull(issuedToken);
    }

    @Test
    void selectedTeacherIdCannotBeUsedForAnotherRole()
    {
        assertThrows(ServiceException.class, () -> service.login("mock", "mock", "Teacher", "parent", 10L));
        assertNull(issuedToken);
    }

    @Test
    void teacherListContainsExistingEligibleTeachers()
    {
        users = List.of(users.get(0), user(20L, "disabled_teacher", "1", List.of(teacherRole)));
        assertEquals(1, service.developmentTeachers().size());
        assertEquals("10", service.developmentTeachers().get(0).get("userId"));
    }

    private String login() { return service.login("mock", "mock", "Test Teacher", "teacher"); }

    private SysUser user(long id, String name, String status, List<SysRole> roles)
    {
        SysUser user = new SysUser(id);
        user.setUserName(name);
        user.setPhonenumber("138000000" + id);
        user.setStatus(status);
        user.setDelFlag("0");
        user.setRoles(roles);
        return user;
    }

    private void set(String name, Object value) throws Exception
    {
        var field = MiniAppLoginService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private interface Call { Object invoke(String method, Object[] args); }

    private <T> T proxy(Class<T> type, Call call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> call.invoke(method.getName(), args)));
    }
}
