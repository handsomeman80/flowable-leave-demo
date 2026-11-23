package com.demo.leave.repo;

import com.demo.leave.model.LeaveRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/**
 * 请假单的持久层。用 JdbcTemplate 是为了让 demo 少一层配置，
 * 换成 MyBatis 只需把这里改成 Mapper 接口 + XML，SQL 完全一样。
 */
@Repository
public class LeaveRepository {

    private final JdbcTemplate jdbcTemplate;

    public LeaveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<LeaveRequest> ROW_MAPPER = (rs, i) -> {
        LeaveRequest r = new LeaveRequest();
        r.setId(rs.getLong("id"));
        r.setApplicant(rs.getString("applicant"));
        r.setDept(rs.getString("dept"));
        r.setDays(rs.getInt("days"));
        r.setReason(rs.getString("reason"));
        r.setStatus(rs.getString("status"));
        r.setProcessInstanceId(rs.getString("process_instance_id"));
        return r;
    };

    /** 插入并返回自增主键 */
    public Long insert(LeaveRequest r) {
        String sql = "insert into leave_request(applicant, dept, days, reason, status, create_time, update_time) "
                + "values (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, r.getApplicant());
            ps.setString(2, r.getDept());
            ps.setInt(3, r.getDays());
            ps.setString(4, r.getReason());
            ps.setString(5, r.getStatus());
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? null : key.longValue();
    }

    public void updateProcessInstanceId(Long id, String pid) {
        jdbcTemplate.update("update leave_request set process_instance_id = ?, update_time = ? where id = ?",
                pid, new Timestamp(System.currentTimeMillis()), id);
    }

    public void updateStatus(Long id, String status) {
        jdbcTemplate.update("update leave_request set status = ?, update_time = ? where id = ?",
                status, new Timestamp(System.currentTimeMillis()), id);
    }

    public void updateStatusByProcessInstanceId(String pid, String status) {
        jdbcTemplate.update("update leave_request set status = ?, update_time = ? where process_instance_id = ?",
                status, new Timestamp(System.currentTimeMillis()), pid);
    }

    public LeaveRequest findById(Long id) {
        List<LeaveRequest> list = jdbcTemplate.query(
                "select * from leave_request where id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public LeaveRequest findByProcessInstanceId(String pid) {
        List<LeaveRequest> list = jdbcTemplate.query(
                "select * from leave_request where process_instance_id = ?", ROW_MAPPER, pid);
        return list.isEmpty() ? null : list.get(0);
    }
}
