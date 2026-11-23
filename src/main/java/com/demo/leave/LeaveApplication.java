package com.demo.leave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 请假审批 Demo 启动类。
 *
 * 启动后：
 *  1) Activiti 自动建 ACT_* 表（H2 内存库）
 *  2) 自动部署 classpath:/processes/leave.bpmn20.xml
 *  3) 控制台会打印 "Deployment ... leave.bpmn20.xml" 和 "ProcessDefinition ... leaveProcess:1:xxx"
 */
@SpringBootApplication
public class LeaveApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveApplication.class, args);
        System.out.println("\n===== 启动成功 =====");
        System.out.println("接口文档见 README.md，H2 控制台：http://localhost:8080/h2-console");
    }
}
