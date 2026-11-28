package com.ctts.config;

import com.ctts.common.RequireRole;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Knife4j / springdoc 的文档增强。
 *
 * <p>两个 Bean：{@code cttsOpenAPI} 给文档一个标题和背景说明；{@code commonResponses}
 * 给全部操作统一挂 400 / 401 / 403 / 404。
 *
 * <p><b>为什么用 {@link OperationCustomizer} 而不是在 32 个方法上各写一遍 {@code @ApiResponse}</b>：
 * 错误码是**横切语义**——400 来自 {@code @Validated} 与 {@code GlobalExceptionHandler}，
 * 401 来自 {@code JwtInterceptor}，403 来自拦截器与各 Service 的归属校验，404 来自"先查后写"。
 * 它们不属于某个方法，逐方法手写必然漂移；更糟的是**新增接口时忘了写，文档会悄悄退化成只有 200**。
 *
 * <p><b>哪些码是推断出来的、依据是什么</b>：
 * <ul>
 *   <li>400：**有客户端输入**的接口（@PathVariable / @RequestParam / @RequestBody）。
 *       没有输入的 {@code GET /projects}、{@code GET /stats/projects} 不可能"输入不合法"，故不出 400</li>
 *   <li>401：除 {@code /users/login} 外都有（验签在拦截器，白名单只有 login 与文档路径）</li>
 *   <li>403：{@code @RequireRole} 未放开全部角色、写操作（还要过归属校验）、{@code /users} 下的读、提交/评审详情。
 *       **角色部分不再是推测**——第㊴步把授权规则抽成 {@code @RequireRole} 注解后，文档与拦截器读同一处注解。
 *       仍属推测的只剩"记录是不是你的"这一类：它写在各 Service 里，反射看不见，只能按"该模块有没有可能 403"粗判</li>
 *   <li>404：方法参数里有 {@code @PathVariable} 的（带 id 的查/改/删）</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cttsOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("CTTS 校园实训任务管理系统 API")
                .version("v1.1")
                .description("""
                        32 个接口，统一返回 `{code, message, data}`。

                        角色：1 学生 / 2 教师 / 3 管理员。除 `POST /users/login` 外，所有接口都需要
                        `Authorization: Bearer <token>`，身份只认 token——请求体里传 `userId`/`teacherId`/`reviewerId` 一律无效。

                        **本页只画得出路径、参数、字段。** 角色矩阵、默认值与边界（`totalScore` 缺省 100、
                        `pageSize` 上限 100、`deadline` 只收 `yyyy-MM-dd HH:mm:ss`）以 `docs/03-接口文档.md` 为准。
                        """));
    }

    @Bean
    public OperationCustomizer commonResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) {
                return operation;
            }

            Method method = handlerMethod.getMethod();
            boolean isLogin = "login".equals(method.getName());
            boolean isWrite = handlerMethod.getMethodAnnotation(GetMapping.class) == null;
            boolean hasPathVar = hasPathVariable(method);
            boolean hasInput = hasClientInput(method);
            boolean underUsers = isUnder(handlerMethod, "/users");
            // 角色受限 = @RequireRole 没有放开全部三种角色。这里直接读注解而不是按路径猜——
            // 授权规则与文档说明同源，改注解即改文档，不会出现"代码收紧了、文档还写着能调"
            boolean roleRestricted = isRoleRestricted(handlerMethod);

            // 没有客户端输入就不可能有"输入不合法"：GET /projects、GET /stats/projects 无参数，不出 400
            if (hasInput) {
                putIfAbsent(responses, "400", "参数不合法",
                        "字段校验失败（必填项缺失）、参数类型转换失败、请求体 JSON 或日期格式错误");
            }

            if (isLogin) {
                putIfAbsent(responses, "401", "登录失败", "用户名或密码错误，或账号已被禁用（status=0）");
            } else {
                putIfAbsent(responses, "401", "未认证",
                        "未携带 token、token 过期、账号被禁用、或角色已变更（需重新登录）");
            }

            // 403 的四条来源：① @RequireRole 未放开全部角色（角色不够）；
            // ② 写操作（还要过"是不是你的记录"这一关）；③ /users 下的读操作（仅管理员、或查他人）；
            // ④ 提交/评审详情（不在可见范围内——这是列表"收窄"之外唯一会拒绝的读）
            boolean scopedDetail = !isWrite && hasPathVar
                    && (isUnder(handlerMethod, "/submissions") || isUnder(handlerMethod, "/reviews"));
            if (!isLogin && (roleRestricted || isWrite || underUsers || scopedDetail)) {
                putIfAbsent(responses, "403", "无权限",
                        "当前角色不能执行该操作，或数据不属于当前用户");
            }

            if (hasPathVar) {
                putIfAbsent(responses, "404", "资源不存在", "按 id 查询/更新/删除时目标记录不存在");
            }
            return operation;
        };
    }

    /** 只补空缺，不覆盖：方法上若手写了某个码的说明，以手写为准 */
    private static void putIfAbsent(ApiResponses responses, String code, String title, String detail) {
        if (responses.containsKey(code)) {
            return;
        }
        responses.addApiResponse(code, new ApiResponse().description(title + " —— " + detail));
    }

    private static boolean hasPathVariable(Method method) {
        return Arrays.stream(method.getParameters())
                .anyMatch(p -> p.isAnnotationPresent(PathVariable.class));
    }

    /** 客户端能传的东西：路径段、查询串、请求体。@RequestAttribute 不算——它由拦截器写入，客户端控制不了 */
    private static boolean hasClientInput(Method method) {
        return Arrays.stream(method.getParameters()).anyMatch(p ->
                p.isAnnotationPresent(PathVariable.class)
                        || p.isAnnotationPresent(RequestParam.class)
                        || p.isAnnotationPresent(RequestBody.class));
    }

    /**
     * 该接口是否"只对部分角色开放"。规则来自 {@code @RequireRole}，方法上的优先于类上的，
     * 与 {@code JwtInterceptor.checkRole} 读的是同一处——**授权只有一份真相**，
     * 文档里显示的 403 因此不会和实际拦截行为脱节。
     */
    private static boolean isRoleRestricted(HandlerMethod handlerMethod) {
        RequireRole rule = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (rule == null) {
            rule = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (rule == null) {
            return false;
        }
        return Arrays.stream(rule.value()).distinct().count() < 3;
    }

    /**
     * 按类上的 @RequestMapping 前缀判断模块，避免 config 反向依赖具体 Controller 类。
     *
     * <p>用 {@code startsWith} 而不是 {@code equals}：子资源也属于父模块的权限模型。
     * {@code /projects/{projectId}/members} 的写操作同样只有项目负责人能做（403），
     * 用 equals 判断会把它当成"未知模块"，这个 403 就从文档里消失了。
     */
    private static boolean isUnder(HandlerMethod handlerMethod, String prefix) {
        RequestMapping mapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return false;
        }
        return mapping.value()[0].startsWith(prefix);
    }
}
