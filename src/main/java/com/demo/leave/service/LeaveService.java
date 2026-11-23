package com.demo.leave.service;

import com.demo.leave.dto.ApplyRequest;
import com.demo.leave.model.LeaveRequest;
import com.demo.leave.repo.LeaveRepository;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假审批的核心业务。
 *
 * 四个 Service 的分工（面试必答）：
 *   RepositoryService —— 部署流程、查流程定义（管"图纸"）
 *   RuntimeService    —— 启动实例、推进、设变量、改状态（管"在跑的"）
 *   TaskService       —— 查待办、签收、完成任务、加审批意见（管"人的操作"）
 *   HistoryService    —— 查已办、审批轨迹（管"历史"）
 */
@Service
public class LeaveService {

    private static final Logger log = LoggerFactory.getLogger(LeaveService.class);

    private static final String PROCESS_KEY = "leaveProcess";
    private static final String APPLY_TASK_KEY = "applyTask";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final LeaveRepository leaveRepository;

    public LeaveService(RuntimeService runtimeService,
                        TaskService taskService,
                        HistoryService historyService,
                        RepositoryService repositoryService,
                        LeaveRepository leaveRepository) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.repositoryService = repositoryService;
        this.leaveRepository = leaveRepository;
    }

    // ============================================================
    // 1. 提交请假（启动流程）
    // ============================================================
    @Transactional(rollbackFor = Exception.class)
    public Long apply(ApplyRequest req) {
        // ① 先落业务单
        LeaveRequest lr = new LeaveRequest();
        lr.setApplicant(req.getApplicant());
        lr.setDept(req.getDept());
        lr.setDays(req.getDays());
        lr.setReason(req.getReason());
        lr.setStatus("APPROVING");
        Long id = leaveRepository.insert(lr);

        // ② 流程变量：审批人 + 业务字段（网关要用 days）
        Map<String, Object> vars = new HashMap<String, Object>();
        vars.put("applicant", req.getApplicant());
        vars.put("deptLeader", req.getDeptLeader());
        vars.put("director", req.getDirector());
        vars.put("hr", req.getHr());
        vars.put("days", req.getDays());
        vars.put("reason", req.getReason());

        // ③ 启动流程。businessKey 传业务单 id —— 这是业务与流程的关联键，
        //    后面查历史、查详情都靠它；也可以理解成"流程的身份证"
        ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY, String.valueOf(id), vars);
        log.info("流程已启动: pid={}, businessKey={}", pi.getId(), pi.getBusinessKey());

        // ④ 流程启动后停在 applyTask（等申请人确认提交）。
        //    demo 里直接帮他点一次"提交"，推进到主管审批。
        Task applyTask = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey(APPLY_TASK_KEY)
                .singleResult();
        if (applyTask != null) {
            taskService.complete(applyTask.getId());
        }

        // ⑤ 回写流程实例 id
        leaveRepository.updateProcessInstanceId(id, pi.getId());
        return id;
    }

    // ============================================================
    // 2. 我的待办
    // ============================================================
    public List<Map<String, Object>> todo(String userId) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(userId)
                .orderByTaskCreateTime().desc()
                .list();

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Task task : tasks) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("taskId", task.getId());
            m.put("taskName", task.getName());
            m.put("taskDefinitionKey", task.getTaskDefinitionKey());
            m.put("processInstanceId", task.getProcessInstanceId());
            m.put("createTime", task.getCreateTime());

            // 通过流程实例拿 businessKey → 关联业务单
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId()).singleResult();
            if (pi != null) {
                m.put("businessKey", pi.getBusinessKey());
                LeaveRequest lr = leaveRepository.findByProcessInstanceId(pi.getId());
                if (lr != null) {
                    m.put("leaveId", lr.getId());
                    m.put("applicant", lr.getApplicant());
                    m.put("days", lr.getDays());
                    m.put("reason", lr.getReason());
                }
            }
            result.add(m);
        }
        return result;
    }

    // ============================================================
    // 3. 审批通过
    // ============================================================
    @Transactional(rollbackFor = Exception.class)
    public void approve(String taskId, String comment) {
        Task task = requireTask(taskId);

        // 审批意见落 ACT_HI_COMMENT，历史轨迹里能查到
        taskService.addComment(taskId, task.getProcessInstanceId(),
                (comment == null || comment.isEmpty()) ? "同意" : comment);

        // 完成当前任务 → 引擎按 BPMN 自动走下一个节点
        taskService.complete(taskId);

        // 流程走完了吗？走完就把业务状态改成已通过
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).singleResult();
        if (pi == null) {
            leaveRepository.updateStatusByProcessInstanceId(task.getProcessInstanceId(), "APPROVED");
            log.info("流程结束（通过）: pid={}", task.getProcessInstanceId());
        }
    }

    // ============================================================
    // 4. 驳回（面试必问：Flowable 原生没有"自由驳回"）
    // ============================================================
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, String comment) {
        Task task = requireTask(taskId);
        String currentKey = task.getTaskDefinitionKey();

        if (APPLY_TASK_KEY.equals(currentKey)) {
            throw new IllegalStateException("当前就在申请人节点，无需驳回，可直接撤回单据");
        }

        taskService.addComment(taskId, task.getProcessInstanceId(),
                (comment == null || comment.isEmpty()) ? "驳回" : ("驳回：" + comment));

        // 关键 API：把流程从"当前节点"跳回"申请人节点"
        // 效果：当前任务被删除，applyTask 重新生成（assignee = ${applicant}）
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(currentKey, APPLY_TASK_KEY)
                .changeState();

        leaveRepository.updateStatusByProcessInstanceId(task.getProcessInstanceId(), "REJECTED");
        log.info("已驳回: pid={}, from={} to={}", task.getProcessInstanceId(), currentKey, APPLY_TASK_KEY);
    }

    // ============================================================
    // 5. 修改后重新提交（驳回后申请人再提交）
    // ============================================================
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long leaveId) {
        LeaveRequest lr = leaveRepository.findById(leaveId);
        if (lr == null) {
            throw new IllegalArgumentException("请假单不存在: " + leaveId);
        }
        Task applyTask = taskService.createTaskQuery()
                .processInstanceId(lr.getProcessInstanceId())
                .taskDefinitionKey(APPLY_TASK_KEY)
                .singleResult();
        if (applyTask == null) {
            throw new IllegalStateException("当前不在待提交状态");
        }
        leaveRepository.updateStatus(leaveId, "APPROVING");
        taskService.complete(applyTask.getId());
    }

    // ============================================================
    // 6. 单据详情（业务 + 当前节点）
    // ============================================================
    public Map<String, Object> detail(Long leaveId) {
        LeaveRequest lr = leaveRepository.findById(leaveId);
        if (lr == null) {
            throw new IllegalArgumentException("请假单不存在: " + leaveId);
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("leave", lr);

        List<Task> current = taskService.createTaskQuery()
                .processInstanceId(lr.getProcessInstanceId()).list();
        List<String> currentNode = new ArrayList<String>();
        for (Task t : current) {
            currentNode.add(t.getName() + "(" + t.getAssignee() + ")");
        }
        m.put("running", !current.isEmpty());
        m.put("currentNodes", currentNode);
        return m;
    }

    // ============================================================
    // 7. 审批轨迹（已办 + 意见）
    // ============================================================
    public Map<String, Object> history(Long leaveId) {
        LeaveRequest lr = leaveRepository.findById(leaveId);
        if (lr == null) {
            throw new IllegalArgumentException("请假单不存在: " + leaveId);
        }
        String pid = lr.getProcessInstanceId();

        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(pid)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list();

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (HistoricTaskInstance t : tasks) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("taskName", t.getName());
            m.put("assignee", t.getAssignee());
            m.put("startTime", t.getStartTime());
            m.put("endTime", t.getEndTime());
            m.put("durationMs", t.getDurationInMillis());
            result.add(m);
        }

        List<Comment> comments = taskService.getProcessInstanceComments(pid);
        List<Map<String, Object>> cs = new ArrayList<Map<String, Object>>();
        for (Comment c : comments) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("userId", c.getUserId());
            m.put("message", c.getFullMessage());
            m.put("time", c.getTime());
            cs.add(m);
        }

        Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put("leaveId", leaveId);
        wrapper.put("status", lr.getStatus());
        wrapper.put("tasks", result);
        wrapper.put("comments", cs);
        return wrapper;
    }

    // ============================================================
    // 工具方法
    // ============================================================
    private Task requireTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已处理: " + taskId);
        }
        return task;
    }

    /** 部署情况自检：能查到流程定义说明 BPMN 加载成功 */
    public List<Map<String, Object>> processDefinitions() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        repositoryService.createProcessDefinitionQuery()
                .orderByProcessDefinitionVersion().desc().list()
                .forEach(pd -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("id", pd.getId());
                    m.put("key", pd.getKey());
                    m.put("name", pd.getName());
                    m.put("version", pd.getVersion());
                    m.put("deploymentId", pd.getDeploymentId());
                    list.add(m);
                });
        return list;
    }
}
