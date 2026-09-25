package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.entity.ClaimRecord;
import com.campus.lostfound.entity.MatchRecord;
import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.ClaimRecordMapper;
import com.campus.lostfound.mapper.MatchRecordMapper;
import com.campus.lostfound.mapper.NotificationMapper;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员服务：帖子管理（查看全部/下架/删除）与用户管理（列表）
 */
@Service
public class AdminService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MatchRecordMapper matchRecordMapper;

    @Resource
    private ClaimRecordMapper claimRecordMapper;

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private MatchService matchService;

    @Resource
    private SubscriptionService subscriptionService;

    @Resource
    private NotificationService notificationService;

    /**
     * 分页查看在线帖子，支持类型/状态/关键词筛选。
     * 未指定状态时默认只展示「展示中」的帖子，已认领/已归档不进入在线帖子管理页。
     */
    public PageResult<Post> pagePosts(String type, Integer status, String keyword, int page, int size) {
        Integer s = status != null ? status : Post.STATUS_OPEN;
        QueryWrapper<Post> qw = new QueryWrapper<Post>()
                .eq(StringUtils.hasText(type), "type", type)
                .eq("status", s)
                .like(StringUtils.hasText(keyword), "title", keyword)
                .orderByDesc("create_time");
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), qw);
        fillOwner(p.getRecords());
        return PageResult.of(p);
    }

    /**
     * 下架任意帖子
     */
    public void archivePost(Long id) {
        Post p = getPost(id);
        p.setStatus(Post.STATUS_ARCHIVED);
        p.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(p);
    }

    /**
     * 重新上架已归档帖子：恢复为「展示中」并重新执行智能匹配。
     * AI 硬违禁拦截下架的帖子不允许直接重新上架，须先修改内容重新走审核（归档锁定）。
     */
    public void relistPost(Long id) {
        Post p = getPost(id);
        if (Post.AI_REJECT.equals(p.getAiAuditResult())) {
            throw new BusinessException("该帖子命中 AI 违禁拦截已归档，需先修改内容重新提交审核，不能直接上架");
        }
        p.setStatus(Post.STATUS_OPEN);
        p.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(p);
        matchService.runMatchingForPost(p);
    }

    /**
     * 待审核帖子队列（分页），供「AI 初审核 + 人工二审」的管理员审核界面使用。
     * 按提交时间升序，先提交的先审。
     */
    public PageResult<Post> pagePendingPosts(String type, String keyword, int page, int size) {
        QueryWrapper<Post> qw = new QueryWrapper<Post>()
                .eq("status", Post.STATUS_PENDING)
                .eq(StringUtils.hasText(type), "type", type)
                .like(StringUtils.hasText(keyword), "title", keyword)
                .orderByAsc("create_time");
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), qw);
        fillOwner(p.getRecords());
        return PageResult.of(p);
    }

    /**
     * 审核通过：待审核 → 展示中，并触发智能匹配 + 订阅提醒 + 通知作者。
     */
    public void approvePost(Long id) {
        Post p = getPost(id);
        if (p.getStatus() != Post.STATUS_PENDING) {
            throw new BusinessException("该帖子不在待审核状态");
        }
        p.setStatus(Post.STATUS_OPEN);
        p.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(p);
        postMapper.updateAuditReason(id, null);        // 清空历史拒绝原因
        matchService.runMatchingForPost(p);            // 上线即匹配
        subscriptionService.checkAndNotify(p);         // 上线即触达订阅用户
        notificationService.notifyUser(p.getUserId(), Notification.TYPE_AUDIT,
                "发布审核通过",
                "您发布的《" + p.getTitle() + "》已通过审核，现已上线信息广场，系统已为你匹配相关物品。",
                p.getId());
    }

    /**
     * 审核拒绝：待审核 → 审核未通过，写入拒绝原因并通知作者。
     */
    public void rejectPost(Long id, String reason) {
        Post p = getPost(id);
        if (p.getStatus() != Post.STATUS_PENDING) {
            throw new BusinessException("该帖子不在待审核状态");
        }
        p.setStatus(Post.STATUS_REJECTED);
        p.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(p);
        String trimmed = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : null;
        postMapper.updateAuditReason(id, trimmed);     // 拒绝原因落库
        String tip = trimmed != null ? "，原因：" + trimmed : "。";
        notificationService.notifyUser(p.getUserId(), Notification.TYPE_AUDIT,
                "发布审核未通过",
                "您发布的《" + p.getTitle() + "》未通过审核" + tip + " 请修改后重新提交。",
                p.getId());
    }

    /**
     * 待审核帖子数量（供管理员导航栏角标）
     */
    public long countPending() {
        Long c = postMapper.selectCount(new QueryWrapper<Post>().eq("status", Post.STATUS_PENDING));
        return c == null ? 0 : c;
    }

    /**
     * 删除任意帖子，并清理关联的匹配记录与认领记录，避免孤儿数据
     */
    public void deletePost(Long id) {
        getPost(id);
        List<MatchRecord> matches = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("lost_post_id", id).or().eq("found_post_id", id));
        if (!matches.isEmpty()) {
            List<Long> matchIds = new ArrayList<>();
            for (MatchRecord m : matches) {
                matchIds.add(m.getId());
            }
            claimRecordMapper.delete(new QueryWrapper<ClaimRecord>().in("match_id", matchIds));
        }
        claimRecordMapper.delete(new QueryWrapper<ClaimRecord>().eq("post_id", id));
        matchRecordMapper.delete(new QueryWrapper<MatchRecord>()
                .eq("lost_post_id", id).or().eq("found_post_id", id));
        postMapper.deleteById(id);
    }

    /**
     * 用户列表（含每人发布的帖子数），分页
     */
    public PageResult<Map<String, Object>> listUsers(int page, int size) {
        Page<User> p = userMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<User>().orderByDesc("create_time"));
        List<User> users = p.getRecords();

        // 仅统计当前页用户的发布数，避免全表聚合
        Map<Long, Long> postCount = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (User u : users) {
            ids.add(u.getId());
        }
        if (!ids.isEmpty()) {
            List<Map<String, Object>> cntRows = postMapper.selectMaps(new QueryWrapper<Post>()
                    .select("user_id", "count(*) as cnt")
                    .in("user_id", ids)
                    .groupBy("user_id"));
            for (Map<String, Object> row : cntRows) {
                Object uid = row.get("user_id");
                Object cnt = row.get("cnt");
                if (uid != null) {
                    postCount.put(Long.valueOf(uid.toString()), cnt == null ? 0L : Long.valueOf(cnt.toString()));
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("phone", u.getPhone());
            m.put("email", u.getEmail());
            m.put("status", u.getStatus() == null ? 0 : u.getStatus());
            m.put("createTime", u.getCreateTime());
            m.put("postCount", postCount.getOrDefault(u.getId(), 0L));
            result.add(m);
        }
        return PageResult.of(p.getTotal(), page, size, result);
    }

    /**
     * 注销用户（禁用登录，数据保留）
     */
    public void disableUser(Long id) {
        User u = getUser(id);
        if ("ADMIN".equals(u.getRole())) {
            throw new BusinessException("不能注销管理员账号");
        }
        u.setStatus(1);
        userMapper.updateById(u);
    }

    /**
     * 恢复已注销用户
     */
    public void enableUser(Long id) {
        User u = getUser(id);
        u.setStatus(0);
        userMapper.updateById(u);
    }

    /**
     * 删除用户，并级联清理其帖子、匹配、认领、通知，避免孤儿数据
     */
    public void deleteUser(Long id) {
        User u = getUser(id);
        if ("ADMIN".equals(u.getRole())) {
            throw new BusinessException("不能删除管理员账号");
        }

        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>().eq("user_id", id));
        List<Long> postIds = new ArrayList<>();
        for (Post p : posts) {
            postIds.add(p.getId());
        }

        if (!postIds.isEmpty()) {
            // 关联的匹配记录
            List<MatchRecord> matches = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                    .and(w -> w.in("lost_post_id", postIds).or().in("found_post_id", postIds)));
            List<Long> matchIds = new ArrayList<>();
            for (MatchRecord m : matches) {
                matchIds.add(m.getId());
            }
            // 认领记录：匹配关联 + 该用户发起 + 该用户帖子关联
            if (!matchIds.isEmpty()) {
                claimRecordMapper.delete(new QueryWrapper<ClaimRecord>().in("match_id", matchIds));
            }
            claimRecordMapper.delete(new QueryWrapper<ClaimRecord>()
                    .and(w -> w.eq("claimant_id", id).or().in("post_id", postIds)));
            // 匹配记录
            matchRecordMapper.delete(new QueryWrapper<MatchRecord>()
                    .and(w -> w.in("lost_post_id", postIds).or().in("found_post_id", postIds)));
            // 帖子
            postMapper.delete(new QueryWrapper<Post>().eq("user_id", id));
        } else {
            // 无帖子，仍可能有作为申请人发起的认领记录
            claimRecordMapper.delete(new QueryWrapper<ClaimRecord>().eq("claimant_id", id));
        }

        // 通知
        notificationMapper.delete(new QueryWrapper<Notification>().eq("user_id", id));
        // 用户
        userMapper.deleteById(id);
    }

    private User getUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BusinessException("用户不存在");
        }
        return u;
    }

    private Post getPost(Long id) {
        Post p = postMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("帖子不存在");
        }
        return p;
    }

    private void fillOwner(List<Post> posts) {
        for (Post p : posts) {
            User u = userMapper.selectById(p.getUserId());
            if (u != null) {
                p.setOwnerName(u.getNickname());
                p.setOwnerAvatar(u.getAvatar());
            }
        }
    }
}
