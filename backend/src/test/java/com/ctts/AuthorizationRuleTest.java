package com.ctts;

import com.ctts.common.RequireRole;
import com.ctts.controller.ProjectController;
import com.ctts.controller.ProjectMemberController;
import com.ctts.controller.StatsController;
import com.ctts.controller.TaskController;
import com.ctts.controller.TaskReviewController;
import com.ctts.controller.TaskSubmissionController;
import com.ctts.controller.UserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 授权注解的「制度锁定」测试——**不验证行为，验证规矩还在不在**。
 *
 * <p>为什么需要它：第㊴步把授权规则从 {@code JwtInterceptor.checkRole} 的 URL 前缀判断
 * 改为 {@code @RequireRole} 注解后，默认语义变成了「不贴注解 = 任意登录角色可调」。
 * 这正是方便之处，也正是风险所在——**将来加一个写接口忘了贴注解，它会静默对所有角色开放**，
 * 而且没有任何接口测试会失败（没人会为一个还不存在的接口写用例）。
 *
 * <p>所以这里用反射把规矩钉住：只要出现「写接口却没有声明角色」，测试直接红。
 * 这类测试的价值不在当前代码，而在**下一次提交**。
 */
class AuthorizationRuleTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            UserController.class,
            ProjectController.class,
            ProjectMemberController.class,
            TaskController.class,
            TaskSubmissionController.class,
            TaskReviewController.class,
            StatsController.class
    );

    /** 唯一允许免鉴权的写接口：登录本身不能要求先登录 */
    private static final String[] WHITELIST = {"UserController#login"};

    @Test
    @DisplayName("每个写接口都必须声明 @RequireRole——漏贴即对所有角色开放，比写错更危险")
    void everyWriteEndpointDeclaresRole() {
        List<String> missing = new ArrayList<>();

        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isWrite(method)) {
                    continue;
                }
                String key = controller.getSimpleName() + "#" + method.getName();
                if (Arrays.asList(WHITELIST).contains(key)) {
                    continue;
                }
                RequireRole rule = method.getAnnotation(RequireRole.class);
                if (rule == null) {
                    rule = controller.getAnnotation(RequireRole.class);
                }
                if (rule == null) {
                    missing.add(key);
                }
            }
        }

        assertTrue(missing.isEmpty(),
                "以下写接口没有声明允许的角色，会默认对任意登录用户开放：" + missing
                        + "。请在方法上加 @RequireRole({...})，或确认它确实应对所有人开放");
    }

    @Test
    @DisplayName("GET /users 仅管理员：这条规则被误放宽会让全体用户列表（含 role）对外可见")
    void userListIsAdminOnly() throws Exception {
        RequireRole rule = UserController.class.getMethod("list", Integer.class, Integer.class)
                .getAnnotation(RequireRole.class);

        assertNotNull(rule, "GET /users 必须显式声明角色，不能依赖默认的“任意登录”");
        assertArrayEquals(new int[]{3}, rule.value(),
                "GET /users 只应对管理员开放。若这里变成 {1,2,3}，任何学生都能枚举全部账号");
    }

    @Test
    @DisplayName("没有空角色数组：@RequireRole({}) 会让所有角色（含管理员）都吃 403")
    void noEmptyRoleArray() {
        List<String> empty = new ArrayList<>();

        for (Class<?> controller : CONTROLLERS) {
            RequireRole classRule = controller.getAnnotation(RequireRole.class);
            if (classRule != null && classRule.value().length == 0) {
                empty.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                RequireRole rule = method.getAnnotation(RequireRole.class);
                if (rule != null && rule.value().length == 0) {
                    empty.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertFalse(!empty.isEmpty(),
                "@RequireRole({}) 等于「谁都不许调」，通常是手误：" + empty);
    }

    private static boolean isWrite(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }
}
