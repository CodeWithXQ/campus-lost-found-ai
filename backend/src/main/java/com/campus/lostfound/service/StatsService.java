package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.lostfound.entity.MatchRecord;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.mapper.ClaimRecordMapper;
import com.campus.lostfound.mapper.MatchRecordMapper;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计服务（看板）
 */
@Service
public class StatsService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MatchRecordMapper matchRecordMapper;

    @Resource
    private ClaimRecordMapper claimRecordMapper;

    public Map<String, Object> overview() {
        Map<String, Object> map = new LinkedHashMap<>();
        // 「信息总数/失物/招领」只统计「展示中(status=0)」的帖子，与信息广场展示口径一致
        map.put("totalPosts", postMapper.selectCount(new QueryWrapper<Post>().eq("status", Post.STATUS_OPEN)));
        map.put("lostPosts", countByTypeAndStatus("LOST", Post.STATUS_OPEN));
        map.put("foundPosts", countByTypeAndStatus("FOUND", Post.STATUS_OPEN));
        // 已认领（认领完成后自动下架）
        map.put("claimedPosts", postMapper.selectCount(new QueryWrapper<Post>().eq("status", Post.STATUS_CLAIMED)));
        // 已归档（手动下架）
        map.put("archivedPosts", postMapper.selectCount(new QueryWrapper<Post>().eq("status", Post.STATUS_ARCHIVED)));
        map.put("totalUsers", userMapper.selectCount(null));
        map.put("totalMatches", matchRecordMapper.selectCount(null));
        map.put("totalClaims", claimRecordMapper.selectCount(null));
        return map;
    }

    /**
     * 按类别统计（失物/招领分别统计，仅统计展示中的帖子）
     */
    public List<Map<String, Object>> categoryStats() {
        return postMapper.selectMaps(new QueryWrapper<Post>()
                .select("type", "category", "count(*) as cnt")
                .eq("status", Post.STATUS_OPEN)
                .groupBy("type", "category")
                .orderByDesc("cnt"));
    }

    /**
     * 高频丢失 / 拾获地点 TOP10（仅统计展示中的帖子，与信息广场展示口径一致）
     */
    public List<Map<String, Object>> locationStats() {
        return postMapper.selectMaps(new QueryWrapper<Post>()
                .select("location", "count(*) as cnt")
                .eq("status", Post.STATUS_OPEN)
                .groupBy("location")
                .orderByDesc("cnt")
                .last("limit 10"));
    }

    /**
     * 近 6 个月发布趋势
     */
    public List<Map<String, Object>> trend() {
        return postMapper.selectMaps(new QueryWrapper<Post>()
                .select("DATE_FORMAT(create_time, '%Y-%m') as month", "type", "count(*) as cnt")
                .ge("create_time", LocalDate.now().minusMonths(6).withDayOfMonth(1).atStartOfDay())
                .groupBy("month", "type")
                .orderByAsc("month"));
    }

    /**
     * 匹配成功率 = 已认领匹配 / 总匹配
     */
    public Map<String, Object> successRate() {
        Long total = matchRecordMapper.selectCount(null);
        Long claimed = matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>().eq("status", "CLAIMED"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", total);
        map.put("claimed", claimed);
        map.put("rate", (total == null || total == 0) ? 0.0 : (double) claimed / total * 100);
        return map;
    }

    private long countByTypeAndStatus(String type, int status) {
        Long c = postMapper.selectCount(new QueryWrapper<Post>()
                .eq("type", type)
                .eq("status", status));
        return c == null ? 0 : c;
    }
}
