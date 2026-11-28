package com.ctts.common;

/**
 * 校验分组：不同接口对同一实体的必填要求不同，用分组隔开。
 * 注意：约束一旦标记 groups，就不再属于 Default 组，@Valid 触发不到，
 * 必须改用 @Validated(具体组) 。
 */
public interface ValidationGroups {

    /** 新增：全部必填字段要校验 */
    interface Create {
    }
}
