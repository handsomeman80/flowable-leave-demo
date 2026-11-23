# Flowable 7 请假审批 Demo（已实测跑通）

一个最小但完整的工作流示例：**提交 → 主管审批 → 按天数分流（>3 天加总监）→ HR 备案 → 结束**，
包含**驳回（跳回申请人）**、**重新提交**、**审批轨迹**。

- 技术栈：**JDK 17+（实测 21）** + Spring Boot 3.2.5 + **Flowable 7.0.1** + H2 内存库
- 不需要装数据库、不需要建表：启动自动建 ACT_* 表、自动部署 BPMN
- 端口 **8081**

> 为什么用 Flowable 而不是 Activiti：Activiti 7 社区版停在 7.1.0.M6（2020 年），只支持 JDK 8 / Boot 2.x。
> 两者同源（Flowable 由 Activiti 6 核心团队另立），**API 名字几乎一样**

---

## 一、跑起来

```bash
cd flowable-leave-demo
mvn spring-boot:run
```

IDEA 里直接 Run `LeaveApplication` 也可以。

看到这两行就是成功：
```
Deployment ... leave.bpmn20.xml
ProcessDefinition ... leaveProcess:1:xxxx
```

自检：
```powershell
Invoke-RestMethod "http://localhost:8081/api/leave/process/definitions"
# → key=leaveProcess, version=1
```

---

## 二、实测通过的 3 个用例

### 用例 1：5 天请假 → 主管 → 总监 → HR → 通过
```powershell
$body = '{"applicant":"mabo","dept":"tech","days":5,"reason":"family","deptLeader":"zhangsan","director":"lisi","hr":"wangwu"}'
$id = (Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/leave/apply" -ContentType "application/json; charset=utf-8" -Body $body).data

# 主管待办 → 审批
$t1 = (Invoke-RestMethod "http://localhost:8081/api/leave/todo?userId=zhangsan").data
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/leave/approve?taskId=$($t1[0].taskId)&comment=ok"

# 总监待办（因为 5 > 3）→ 审批
$t2 = (Invoke-RestMethod "http://localhost:8081/api/leave/todo?userId=lisi").data
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/leave/approve?taskId=$($t2[0].taskId)&comment=ok"

# HR → 审批（流程结束）
$t3 = (Invoke-RestMethod "http://localhost:8081/api/leave/todo?userId=wangwu").data
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/leave/approve?taskId=$($t3[0].taskId)&comment=ok"

(Invoke-RestMethod "http://localhost:8081/api/leave/$id").data
# → status=APPROVED, running=False
```
**实测结果**：status=APPROVED，history 里 4 条任务、3 条审批意见。

### 用例 2：2 天请假 → 主管 → 直接 HR（跳过总监）
把 `days` 改成 2，主管审批后再查详情：
```
currentNodes = HR 备案(wangwu)
```
**实测结果**：排他网关按 `days <= 3` 正确分流，没有产生总监任务。

### 用例 3：驳回 → 申请人 → 重新提交
```powershell
$t = (Invoke-RestMethod "http://localhost:8081/api/leave/todo?userId=zhangsan").data
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/leave/reject?taskId=$($t[0].taskId)&comment=no"

(Invoke-RestMethod "http://localhost:8081/api/leave/$id").data
# → status=REJECTED, currentNodes=申请人提交(mabo)   ← 回到了申请人节点

Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/leave/resubmit/$id"
(Invoke-RestMethod "http://localhost:8081/api/leave/$id").data
# → status=APPROVING, currentNodes=主管审批(zhangsan)  ← 重新走审批
```
**实测结果**：驳回后状态 REJECTED、当前节点回到"申请人提交"；重新提交后回"主管审批"。

---

## 三、目录结构

```
flowable-leave-demo/
├── pom.xml
├── src/main/resources/
│   ├── application.yml                # 数据源 + H2 + Flowable 配置
│   ├── schema.sql                     # 业务表 leave_request
│   └── processes/leave.bpmn20.xml     # ★ 流程图
└── src/main/java/com/demo/leave/
    ├── LeaveApplication.java
    ├── controller/LeaveController.java
    ├── service/LeaveService.java      # ★ 核心：4 个 Service 的用法
    ├── repo/LeaveRepository.java
    ├── model/LeaveRequest.java
    ├── dto/ApplyRequest.java
    └── common/                        # 统一返回 + 异常处理
```

---

## 四、代码导读

### 1️⃣ 业务与流程怎么关联 —— businessKey
```java
ProcessInstance pi = runtimeService.startProcessInstanceByKey(
        "leaveProcess", String.valueOf(id), vars);   // 第 2 个参数是 businessKey
```
- 业务表存 `process_instance_id`，流程存 `business_key = 业务单 id`，两边互查
- 被问"业务表和流程怎么关联"，答这个就够

### 2️⃣ 为什么要 `complete` 两次（启动 + 提交）
流程启动后停在第一个 UserTask（`applyTask`）。demo 里"点提交" = 启动流程 + 完成提交节点：
```java
Task applyTask = taskService.createTaskQuery()
        .processInstanceId(pi.getId()).taskDefinitionKey("applyTask").singleResult();
taskService.complete(applyTask.getId());
```

### 3️⃣ 审批通过后必须判断流程是否结束
```java
ProcessInstance pi = runtimeService.createProcessInstanceQuery()
        .processInstanceId(pid).singleResult();
if (pi == null) { /* 流程已结束 → 业务状态改 APPROVED */ }
```
> **漏了这步，单据会永远卡在"审批中"。**

### 4️⃣ 驳回：原生没有"自由驳回"
```java
runtimeService.createChangeActivityStateBuilder()
        .processInstanceId(pid)
        .moveActivityIdTo(currentKey, "applyTask")   // 当前节点 → 申请人节点
        .changeState();
```
效果：当前任务删除、`applyTask` 重新生成，历史保留，申请人改完可再提交。

---

## 五、两个坑

### 坑 1：H2 加 `MODE=MySQL` 导致引擎建表失败
```
JdbcSQLSyntaxErrorException: Syntax error in SQL statement ... create table ACT_GE_PROPERTY ...
```
**原因**：MySQL 兼容模式会改变 H2 的解析规则，Flowable 自带的 H2 建表脚本解析不了。
**解决**：URL 保持纯 H2 —— `jdbc:h2:mem:leave;DB_CLOSE_DELAY=-1`。
（同理不要在 URL 上随便加 `DATABASE_TO_LOWER=TRUE`，会让引擎判断"表已存在"时大小写不匹配，重复建表报 `already exists`。）

### 坑 2：`GeneratedKeyHolder.getKey()` 报 "contains multiple keys"
```
The getKey method should only be used when a single key is returned. 
The current key entry contains multiple keys: [{ID=1, CREATE_TIME=..., UPDATE_TIME=...}]
```
**原因**：H2 会把带默认值的列（create_time/update_time）也当成生成列返回。
**解决**：`con.prepareStatement(sql, new String[]{"id"})`，**显式指定只取 id 列**。


---

## 六、和 Activiti 的差异（就 4 处）

| # | 改什么 | Activiti | Flowable |
|---|---|---|---|
| 1 | 依赖 | `org.activiti:activiti-spring-boot-starter:7.1.0.M6` | `org.flowable:flowable-spring-boot-starter-process:7.0.1` |
| 2 | import | `org.activiti.engine.*`、`org.activiti.engine.task.Task` | `org.flowable.engine.*`、`org.flowable.task.api.Task` |
| 3 | BPMN | `xmlns:activiti="http://activiti.org/bpmn"`、`activiti:assignee` | `xmlns:flowable="http://flowable.org/bpmn"`、`flowable:assignee` |
| 4 | 配置前缀 | `spring.activiti.*` | `flowable.*` |

另外 Flowable 里 `HistoricTaskInstance` 的包是 `org.flowable.task.api.history`（不在 engine.history 下）。

---

## 七、总结

> "之前用 Activiti 7 做请假审批的工作流，Activiti 7 不再适配spring boot 3.x ，所以用 Flowable 跑同一套流程。
> 流程是：申请人提交 → 主管审批 → 按天数走排他网关，超过 3 天加一级总监 → HR 备案 → 结束。
> 业务表存 process_instance_id，流程用 businessKey 存业务单 id，两边能互查。
> 通过就是 taskService.complete，但要额外判断流程实例是否还在——不在就说明结束了，要把单据状态改成已通过，这一步很多人会漏。
> 驳回是重点：原生没有自由驳回，我用 createChangeActivityStateBuilder().moveActivityIdTo(当前, 申请节点).changeState() 把流程跳回去，历史保留，申请人改完能重新提交。
> 审批意见存在 ACT_HI_COMMENT，查轨迹时和任务列表一起返回。"

### 快问快答

| 问 | 答 |
|---|---|
| 会签怎么做 | UserTask 设多实例（collection + elementVariable），completionCondition 控制全票/一票 |
| 动态审批人 | 启动传变量，或 TaskListener 的 create 事件里查组织架构 setAssignee；也可用 candidateGroups |
| 流程改了在途单据怎么办 | 已跑的按旧版本走完，新实例用新版本；改流程要评估在途数据 |
| 流程和业务事务 | 同库同事务，`complete` 与业务更新在一个 `@Transactional` 里 |
| 待办怎么查 | `taskQuery().taskAssignee(userId)`；候选人用 `taskCandidateUser` |
| 引擎表多少张 | 约 25 张，分 ACT_RE_/ACT_RU_/ACT_HI_/ACT_ID_ 四类，实际只关心 RU 和 HI |

---

## 八、改成你自己的项目（10 分钟）

1. **换业务**：`leave_request` → `settlement_approval`（结算单审批），字段换成单据号、金额、客户；
2. **加节点**：BPMN 里加 `userTask id="financeTask"`，网关条件改成 `${amount > 50000}`；
3. **换审批人来源**：`deptLeader` 从请求参数改成查组织架构表；
4. **加待办通知**：在 `TaskListener` 的 create 事件里发消息（正好能讲"工作流 + 消息"）。
