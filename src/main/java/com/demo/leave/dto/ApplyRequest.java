package com.demo.leave.dto;

/**
 * 提交请假申请时的入参。
 *
 * 审批人先写死在请求里（deptLeader/director/hr），
 * 生产上应该按申请人所属部门去组织架构表里查——这就是"动态审批人"。
 */
public class ApplyRequest {

    private String applicant;
    private String dept;
    private Integer days;
    private String reason;
    private String deptLeader;
    private String director;
    private String hr;

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

    public String getDeptLeader() {
        return deptLeader;
    }

    public void setDeptLeader(String deptLeader) {
        this.deptLeader = deptLeader;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getHr() {
        return hr;
    }

    public void setHr(String hr) {
        this.hr = hr;
    }
}
