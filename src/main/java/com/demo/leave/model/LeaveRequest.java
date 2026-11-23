package com.demo.leave.model;

/**
 * 请假单（业务表 leave_request 的映射）。
 *
 * 注意：工作流引擎只负责"流程走到哪"，业务数据仍然在自己的表里。
 * 两者的关联靠：业务表.process_instance_id  ←→  流程实例.businessKey
 */
public class LeaveRequest {

    private Long id;
    private String applicant;
    private String dept;
    private Integer days;
    private String reason;
    /** DRAFT / APPROVING / APPROVED / REJECTED */
    private String status;
    private String processInstanceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public String getDept() {
        return dept;
    }

    public void setDept(String dept) {
        this.dept = dept;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }
}
