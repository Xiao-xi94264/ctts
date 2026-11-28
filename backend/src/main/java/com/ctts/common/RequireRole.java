package com.ctts.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级授权声明：允许哪几种角色调用。
 *
 * <p><b>为什么要有它</b>：此前授权规则写死在 {@code JwtInterceptor.checkRole} 里，
 * 靠 {@code uri.startsWith("/projects")} 这种前缀匹配判断。两个后果——
 * ① 加一个模块就得改拦截器；② 前缀本身被当成了权限边界，
 * 于是"把成员接口挂到 /projects 下才算教师专属"这种畸形耦合出现了
 * （路径是给人看的，不该承担授权语义）。
 *
 * <p><b>不贴注解 = 任意登录角色可调</b>（仍要求 token，认证在授权之前）。
 * 这是刻意的：查询类接口大多对三种角色都开放，逐个写 {@code {1,2,3}} 反而噪音。
 * 但只要某个接口有角色限制，就必须显式声明，让"谁能调"这件事在方法签名旁一眼可见。
 *
 * <p><b>与 {@code @Tag} 的分工</b>：{@code @Tag} 是写给文档读者看的中文说明，
 * 本注解是机器实际执行的规则。改授权时必须改这里，文档描述跟着改。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许的角色：1 学生 / 2 教师 / 3 管理员 */
    int[] value();

    /** 拒绝时返回给调用方的提示；为空则用默认话术 */
    String message() default "";
}
