package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.dto.ClaimDTO;
import com.campus.lostfound.entity.ClaimRecord;
import com.campus.lostfound.entity.MatchRecord;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.ClaimRecordMapper;
import com.campus.lostfound.mapper.MatchRecordMapper;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认领服务
 * <p>
 * 业务流程：失主发起认领申请 → 拾主确认 → 双方帖子自动下架（闭环）
 */
@Service
public class ClaimService {

    @Resource
    private ClaimRecordMapper claimRecordMapper;

    @Resource
    private MatchRecordMapper matchRecordMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private MatchService matchService;

    /**
     * 失主发起认领申请
     */
    public ClaimRecord apply(Long userId, ClaimDTO dto) {
        if (dto.getMatchId() == null) {
            throw new BusinessException("参数错误");
        }
        // 物品核验凭据必填：只有真物主才知道的细节（如物品特征、编号、包内物品），
        // 作为拾主确认时的比对凭据，降低冒领风险。
        if (!StringUtils.hasText(dto.getMessage())) {
            throw new BusinessException("请填写物品核验凭据（如物品特征、编号等），便于拾主确认");
        }
        MatchRecord m = matchRecordMapper.selectById(dto.getMatchId());
        if (m == null) {
            throw new BusinessException("匹配记录不存在");
        }
        Post lost = postMapper.selectById(m.getLostPostId());
        Post found = postMapper.selectById(m.getFoundPostId());
        if (lost == null || found == null) {
            throw new BusinessException("帖子不存在");
        }
        // 只有失主（失物帖发布者）可以发起认领
        if (!lost.getUserId().equals(userId)) {
            throw new BusinessException("只有失主可以发起认领申请");
        }
        Long cnt = claimRecordMapper.selectCount(new QueryWrapper<ClaimRecord>()
                .eq("match_id", m.getId())
                .eq("claimant_id", userId)
                .eq("status", ClaimRecord.STATUS_PENDING));
        if (cnt != null && cnt > 0) {
            throw new BusinessException("已发起过认领申请，请等待对方确认");
        }
        ClaimRecord c = new ClaimRecord();
        c.setMatchId(m.getId());
        c.setPostId(lost.getId());
        c.setClaimantId(userId);
        c.setMessage(dto.getMessage());
        c.setStatus(ClaimRecord.STATUS_PENDING);
        c.setCreateTime(LocalDateTime.now());
        claimRecordMapper.insert(c);

        m.setStatus("CONTACTED");
        matchRecordMapper.updateById(m);

        User claimant = userMapper.selectById(userId);
        String claimantName = claimant == null ? "用户" : claimant.getNickname();
        notificationService.notifyUser(found.getUserId(), "CLAIM",
                "收到认领申请",
                "失主「" + claimantName + "」申请认领您招领的《" + found.getTitle() + "》，请前往确认",
                c.getId());
        return c;
    }

    /**
     * 拾主确认认领 → 双方帖子自动下架
     */
    public void confirm(Long userId, Long claimId) {
        ClaimRecord c = claimRecordMapper.selectById(claimId);
        if (c == null) {
            throw new BusinessException("认领记录不存在");
        }
        if (!ClaimRecord.STATUS_PENDING.equals(c.getStatus())) {
            throw new BusinessException("该认领申请已处理");
        }
        MatchRecord m = matchRecordMapper.selectById(c.getMatchId());
        if (m == null) {
            throw new BusinessException("匹配记录不存在");
        }
        Post found = postMapper.selectById(m.getFoundPostId());
        if (found == null || !found.getUserId().equals(userId)) {
            throw new BusinessException("只有拾主可以确认认领");
        }
        c.setStatus(ClaimRecord.STATUS_CONFIRMED);
        claimRecordMapper.updateById(c);
        m.setStatus("CLAIMED");
        matchRecordMapper.updateById(m);

        // 双方帖子自动下架
        Post lost = postMapper.selectById(m.getLostPostId());
        LocalDateTime now = LocalDateTime.now();
        if (lost != null) {
            lost.setStatus(Post.STATUS_CLAIMED);
            lost.setUpdateTime(now);
            postMapper.updateById(lost);
        }
        found.setStatus(Post.STATUS_CLAIMED);
        found.setUpdateTime(now);
        postMapper.updateById(found);

        if (lost != null) {
            notificationService.notifyUser(lost.getUserId(), "CLAIM",
                    "认领成功",
                    "您认领的《" + lost.getTitle() + "》已由拾主确认，相关信息已自动下架，请尽快联系取回物品！",
                    c.getId());
        }
        notificationService.notifyUser(found.getUserId(), "CLAIM",
            "认领完成",
            "《" + found.getTitle() + "》认领流程已完成，该信息已自动下架。",
            c.getId());
    }

    /**
     * 拾主拒绝认领
     */
    public void reject(Long userId, Long claimId) {
        ClaimRecord c = claimRecordMapper.selectById(claimId);
        if (c == null) {
            throw new BusinessException("认领记录不存在");
        }
        if (!ClaimRecord.STATUS_PENDING.equals(c.getStatus())) {
            throw new BusinessException("该认领申请已处理");
        }
        MatchRecord m = matchRecordMapper.selectById(c.getMatchId());
        if (m == null) {
            throw new BusinessException("匹配记录不存在");
        }
        Post found = postMapper.selectById(m.getFoundPostId());
        if (found == null || !found.getUserId().equals(userId)) {
            throw new BusinessException("只有拾主可以处理认领申请");
        }
        c.setStatus(ClaimRecord.STATUS_REJECTED);
        claimRecordMapper.updateById(c);
        // 认领被拒 = 匹配错误的负反馈，标记为 IGNORED，后续不再推荐该配对
        m.setStatus("IGNORED");
        matchRecordMapper.updateById(m);
        notificationService.notifyUser(c.getClaimantId(), "CLAIM",
                "认领申请被拒绝",
                "您对《" + found.getTitle() + "》的认领申请未通过，可尝试联系对方或继续寻找其他匹配。",
                c.getId());
    }

    /**
     * 我相关的认领记录（我申请的 + 需要我确认处理的），合并后按时间倒序分页
     */
    public PageResult<ClaimRecord> myClaims(Long userId, int page, int size, String scope) {
        if ("applied".equals(scope)) {
            return myAppliedClaims(userId, page, size);
        }
        if ("handle".equals(scope)) {
            return myHandleClaims(userId, page, size);
        }
        // 默认：合并（我申请的 + 需我处理的），保持向后兼容
        // 1. 我发起的认领申请
        List<ClaimRecord> mine = claimRecordMapper.selectList(new QueryWrapper<ClaimRecord>()
                .eq("claimant_id", userId)
                .orderByDesc("create_time"));

        // 2. 需要我确认处理的：我发布的招领帖所对应的匹配上、由别人发起的认领
        List<Post> myFound = postMapper.selectList(new QueryWrapper<Post>()
                .eq("user_id", userId)
                .eq("type", "FOUND"));
        List<Long> foundIds = new ArrayList<>();
        for (Post p : myFound) {
            foundIds.add(p.getId());
        }
        List<ClaimRecord> needHandle = new ArrayList<>();
        if (!foundIds.isEmpty()) {
            List<MatchRecord> myMatches = matchRecordMapper.selectList(
                    new QueryWrapper<MatchRecord>().in("found_post_id", foundIds));
            List<Long> matchIds = new ArrayList<>();
            for (MatchRecord m : myMatches) {
                matchIds.add(m.getId());
            }
            if (!matchIds.isEmpty()) {
                needHandle = claimRecordMapper.selectList(new QueryWrapper<ClaimRecord>()
                        .in("match_id", matchIds)
                        .ne("claimant_id", userId)
                        .orderByDesc("create_time"));
            }
        }

        // 合并去重，按时间倒序
        Map<Long, ClaimRecord> merged = new LinkedHashMap<>();
        for (ClaimRecord c : mine) {
            merged.putIfAbsent(c.getId(), c);
        }
        for (ClaimRecord c : needHandle) {
            merged.putIfAbsent(c.getId(), c);
        }
        List<ClaimRecord> all = new ArrayList<>(merged.values());
        all.sort(Comparator.comparing(ClaimRecord::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 内存分页
        long total = all.size();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<ClaimRecord> pageList = all.subList(from, to);

        // 仅对当前页填充关联信息
        for (ClaimRecord c : pageList) {
            fillDetail(c);
        }
        return PageResult.of(total, page, size, pageList);
    }

    /**
     * 我发起的认领申请（分页）
     */
    public PageResult<ClaimRecord> myAppliedClaims(Long userId, int page, int size) {
        Page<ClaimRecord> p = claimRecordMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ClaimRecord>()
                        .eq("claimant_id", userId)
                        .orderByDesc("create_time"));
        for (ClaimRecord c : p.getRecords()) {
            fillDetail(c);
        }
        return PageResult.of(p);
    }

    /**
     * 需我处理的认领（我发布的招领帖被他人申请），分页
     */
    public PageResult<ClaimRecord> myHandleClaims(Long userId, int page, int size) {
        List<Post> myFound = postMapper.selectList(new QueryWrapper<Post>()
                .eq("user_id", userId)
                .eq("type", "FOUND"));
        List<Long> foundIds = new ArrayList<>();
        for (Post p : myFound) {
            foundIds.add(p.getId());
        }
        if (foundIds.isEmpty()) {
            return PageResult.of(0L, page, size, new ArrayList<>());
        }
        List<MatchRecord> myMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().in("found_post_id", foundIds));
        List<Long> matchIds = new ArrayList<>();
        for (MatchRecord m : myMatches) {
            matchIds.add(m.getId());
        }
        if (matchIds.isEmpty()) {
            return PageResult.of(0L, page, size, new ArrayList<>());
        }
        Page<ClaimRecord> p = claimRecordMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ClaimRecord>()
                        .in("match_id", matchIds)
                        .ne("claimant_id", userId)
                        .orderByDesc("create_time"));
        for (ClaimRecord c : p.getRecords()) {
            fillDetail(c);
        }
        return PageResult.of(p);
    }

    /**
     * 全系统认领记录（管理员），分页 + 可选状态筛选
     */
    public PageResult<ClaimRecord> allClaims(int page, int size, String status) {
        QueryWrapper<ClaimRecord> qw = new QueryWrapper<ClaimRecord>()
                .eq(status != null && !status.isEmpty(), "status", status)
                .orderByDesc("create_time");
        Page<ClaimRecord> p = claimRecordMapper.selectPage(new Page<>(page, size), qw);
        for (ClaimRecord c : p.getRecords()) {
            fillDetail(c);
        }
        return PageResult.of(p);
    }

    /**
     * 查看单条认领详情（申请人本人、拾主或管理员）
     */
    public ClaimRecord detail(Long userId, String role, Long claimId) {
        ClaimRecord c = claimRecordMapper.selectById(claimId);
        if (c == null) {
            throw new BusinessException("认领记录不存在");
        }
        if (!"ADMIN".equals(role)) {
            MatchRecord m = matchRecordMapper.selectById(c.getMatchId());
            boolean isClaimant = c.getClaimantId().equals(userId);
            boolean isFinder = false;
            if (m != null) {
                Post found = postMapper.selectById(m.getFoundPostId());
                isFinder = found != null && found.getUserId().equals(userId);
            }
            if (!isClaimant && !isFinder) {
                throw new BusinessException("无权查看该认领记录");
            }
        }
        fillDetail(c);
        return c;
    }

    /**
     * 填充认领记录的关联信息（匹配、帖子、申请人昵称）
     */
    private void fillDetail(ClaimRecord c) {
        MatchRecord m = matchRecordMapper.selectById(c.getMatchId());
        if (m != null) {
            c.setMatch(m);
            Post lost = postMapper.selectById(m.getLostPostId());
            if (lost != null) {
                c.setPostTitle(lost.getTitle());
                m.setLostPost(lost);
            }
            Post found = postMapper.selectById(m.getFoundPostId());
            if (found != null) {
                m.setFoundPost(found);
                User finder = userMapper.selectById(found.getUserId());
                if (finder != null) {
                    c.setFinderName(finder.getNickname());
                    found.setOwnerName(finder.getNickname());
                }
            }
            // 实时重算匹配六维依据，供认领详情展示（与帖子详情一致的可解释匹配）
            if (lost != null && found != null) {
                MatchService.ScoreResult r = matchService.computeScore(lost, found);
                m.setNameScore(r.nameScore);
                m.setCategoryScore(r.categoryScore);
                m.setLocationScore(r.locationScore);
                m.setTimeScore(r.timeScore);
                m.setDescriptionScore(r.descriptionScore);
                m.setSemanticScore(r.semanticScore);
            }
        }
        User u = userMapper.selectById(c.getClaimantId());
        if (u != null) {
            c.setClaimantName(u.getNickname());
        }
    }
}
