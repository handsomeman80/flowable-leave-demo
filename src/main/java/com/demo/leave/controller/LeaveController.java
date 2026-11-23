package com.demo.leave.controller;

import com.demo.leave.common.ApiResult;
import com.demo.leave.dto.ApplyRequest;
import com.demo.leave.service.LeaveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 请假审批接口。
 *
 * 典型调用顺序（curl 见 README）：
 *   1) POST /api/leave/apply            提交申请 → 返回 leaveId
 *   2) GET  /api/leave/todo?userId=zhangsan   主管看待办
 *   3) POST /api/leave/approve          审批通过
 *   4) POST /api/leave/reject           驳回（回到申请人）
 *   5) POST /api/leave/resubmit/{id}    申请人改完重新提交
 *   6) GET  /api/leave/{id}/history     看审批轨迹
 */
@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /** 提交请假申请 */
    @PostMapping("/apply")
    public ApiResult<Long> apply(@RequestBody ApplyRequest req) {
        return ApiResult.ok(leaveService.apply(req));
    }

    /** 我的待办 */
    @GetMapping("/todo")
    public ApiResult<List<Map<String, Object>>> todo(@RequestParam String userId) {
        return ApiResult.ok(leaveService.todo(userId));
    }

    /** 审批通过 */
    @PostMapping("/approve")
    public ApiResult<Void> approve(@RequestParam String taskId,
                                   @RequestParam(required = false) String comment) {
        leaveService.approve(taskId, comment);
        return ApiResult.ok();
    }

    /** 驳回（回到申请人节点） */
    @PostMapping("/reject")
    public ApiResult<Void> reject(@RequestParam String taskId,
                                  @RequestParam(required = false) String comment) {
        leaveService.reject(taskId, comment);
        return ApiResult.ok();
    }

    /** 驳回后重新提交 */
    @PostMapping("/resubmit/{leaveId}")
    public ApiResult<Void> resubmit(@PathVariable Long leaveId) {
        leaveService.resubmit(leaveId);
        return ApiResult.ok();
    }

    /** 单据详情（业务 + 当前节点） */
    @GetMapping("/{leaveId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable Long leaveId) {
        return ApiResult.ok(leaveService.detail(leaveId));
    }

    /** 审批轨迹（已办 + 意见） */
    @GetMapping("/{leaveId}/history")
    public ApiResult<Map<String, Object>> history(@PathVariable Long leaveId) {
        return ApiResult.ok(leaveService.history(leaveId));
    }

    /** 自检：流程定义是否部署成功 */
    @GetMapping("/process/definitions")
    public ApiResult<List<Map<String, Object>>> definitions() {
        return ApiResult.ok(leaveService.processDefinitions());
    }
}
