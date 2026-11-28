package com.ctts.controller;

import com.ctts.common.RequireRole;
import com.ctts.common.Result;
import com.ctts.entity.TaskReview;
import com.ctts.service.TaskReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评审模块 /reviews", description = "读：按角色收窄（学生→自己提交收到的 / 教师→自己写的 / 管理员→全部）；写：仅教师（role=2）且只能评自己项目下的提交")
@RestController
@RequestMapping("/reviews")
public class TaskReviewController {

    @Autowired
    private TaskReviewService taskReviewService;

    //查询评审列表：GET /reviews?submissionId=58&reviewerId=6，两个参数都可省略
    //可见范围由角色决定：管理员全部、学生只看自己提交收到的、教师只看自己写的
    @Operation(summary = "评审列表",
            description = "可选 ?submissionId= / ?reviewerId= 两个筛选参数，都可省略；省略等于不加该条件（不是查不到）。可见范围按角色收窄")
    @RequireRole({1, 2, 3})
    @GetMapping
    public Result<List<TaskReview>> list(
            @RequestParam(value = "submissionId", required = false) Long submissionId,
            @RequestParam(value = "reviewerId", required = false) Long reviewerId,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("role") Integer role) {
        return Result.ok(taskReviewService.list(submissionId, reviewerId, userId, role));
    }

    //根据id查询评审：同样按角色收窄（管理员全部 / 教师自己写的 / 学生自己提交收到的）
    @Operation(summary = "评审详情", description = "不在可见范围内 403；不存在 404")
    @RequireRole({1, 2, 3})
    @GetMapping("/{id}")
    public Result<TaskReview> getById(@PathVariable Long id,
                                      @RequestAttribute("userId") Long userId,
                                      @RequestAttribute("role") Integer role) {
        return Result.ok(taskReviewService.getById(id, userId, role));
    }

    //新增评审：评审人只认 token，请求体里的 reviewerId 一律忽略
    //并且只能评审自己项目下的提交（Service 里顺着 提交→任务→项目 校验归属）
    @Operation(summary = "新增评审",
            description = "reviewerId 只认 token；只能评自己项目下的提交（否则 403）；"
                    + "一个提交只能评一次，重复评审返回 400（改分请用 PUT /reviews/{id}）；"
                    + "评分后提交状态联动改为 2（已评审）")
    @RequireRole({2})
    @PostMapping
    public Result<TaskReview> add(@RequestBody TaskReview review,
                                  @RequestAttribute("userId") Long userId) {
        review.setReviewerId(userId);
        return Result.ok(taskReviewService.add(review));
    }

    //修改评审（只改分数和评语）：只能改自己写的
    @Operation(summary = "修改评审", description = "只改 score / comment；只能改自己写的，否则 403")
    @RequireRole({2})
    @PutMapping("/{id}")
    public Result<TaskReview> update(@PathVariable Long id, @RequestBody TaskReview review,
                                     @RequestAttribute("userId") Long userId) {
        return Result.ok(taskReviewService.update(id, review, userId));
    }

    //删除评审：只能删自己写的
    @Operation(summary = "删除评审", description = "只能删自己写的，否则 403；删除后提交状态回退为 1（待评审），两者同事务")
    @RequireRole({2})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        taskReviewService.delete(id, userId);
        return Result.ok(null);
    }
}
