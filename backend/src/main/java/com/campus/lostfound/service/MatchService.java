package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.entity.MatchRecord;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.MatchRecordMapper;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import com.campus.lostfound.util.CategorySynonym;
import com.campus.lostfound.util.DescriptionParser;
import com.campus.lostfound.util.StringSimilarity;
import com.campus.lostfound.vo.MatchVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能匹配服务
 * <p>
 * 匹配算法：
 * 1. 纯文字匹配分 = 名称相似度(编辑距离+包含加权, 权重0.30) + 类别匹配(0.25) + 地点相似度(0.25) + 时间相近度(0.20)
 * 2. 图像相似度：双方帖子都有 AI 特征向量时计算余弦相似度
 * 3. 综合分 = 0.6 * 图像相似度 + 0.4 * 文字匹配分（无图像时直接用文字匹配分，AI 降级）
 */
@Service
public class MatchService {

    @Resource
    private PostMapper postMapper;

    @Resource
    private MatchRecordMapper matchRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Value("${match.threshold:0.35}")
    private double threshold;

    @Value("${match.image-weight:0.6}")
    private double imageWeight;

    @Value("${match.text-weight:0.4}")
    private double textWeight;

    /** 图像相似度达到该阈值才参与融合；低于该值视为"无区分力"，退化为纯文字匹配 */
    @Value("${match.image-min-confidence:0.5}")
    private double imageMinConfidence;

    /** k-reciprocal 重排序的近邻数 K */
    @Value("${match.rerank-k:20}")
    private int rerankK;

    /** 匹配结果展示下限：低于该综合分的候选不进入详情页匹配列表（放宽自 threshold*0.6，防止漏掉低分但相关的真物品） */
    @Value("${match.floor:0.12}")
    private double floor;

    /** 详情页匹配列表返回的最大候选条数（前端分级展示：前 5 高亮，其余折叠） */
    @Value("${match.max-results:30}")
    private int maxResults;

    /** 名称 / 类别 / 地点 / 时间 / 描述 / 语义的文本维度权重 */
    private static final double W_NAME = 0.20;
    private static final double W_CATEGORY = 0.20;
    private static final double W_LOCATION = 0.20;
    private static final double W_TIME = 0.15;
    private static final double W_DESC = 0.15;
    private static final double W_SEMANTIC = 0.10;

    /**
     * 匹配分计算结果（含各维度拆解，用于前端展示匹配依据）
     */
    public static class ScoreResult {
        public double nameScore;
        public double categoryScore;
        public double locationScore;
        public double timeScore;
        public double descriptionScore;
        public double semanticScore;
        public double textScore;
        public Double imageScore;
        public double finalScore;
    }

    /**
     * 计算两个帖子的匹配分
     */
    public ScoreResult computeScore(Post a, Post b) {
        ScoreResult r = new ScoreResult();
        // 1. 名称相似度（编辑距离 + 包含关系）
        r.nameScore = StringSimilarity.similarity(a.getTitle(), b.getTitle());
        // 2. 类别匹配
        r.categoryScore = categoryScore(a.getCategory(), b.getCategory());
        // 3. 地点匹配
        r.locationScore = StringSimilarity.similarity(a.getLocation(), b.getLocation());
        // 4. 时间相近度
        r.timeScore = timeScore(a.getLostTime(), b.getLostTime());
        // 5. 描述相似度（颜色/品牌/型号属性抽取）
        r.descriptionScore = DescriptionParser.similarity(
                a.getTitle(), a.getDescription(), b.getTitle(), b.getDescription());
        // 6. 文本语义相似度（Chinese-CLIP 文本塔，捕获字面不同但语义相关的表达）
        // 语义分与类别分联动：Chinese-CLIP 文本塔对物品名词区分度弱（跨类别语义分虚高），
        // 乘以类别匹配度做调制——类别不同时语义分被压制，仅同类时才保留语义增益（捕捉"双肩包 vs 书包"）
        r.semanticScore = semanticScore(a.getTextVector(), b.getTextVector()) * r.categoryScore;

        r.textScore = r.nameScore * W_NAME
                + r.categoryScore * W_CATEGORY
                + r.locationScore * W_LOCATION
                + r.timeScore * W_TIME
                + r.descriptionScore * W_DESC
                + r.semanticScore * W_SEMANTIC;

        // 5. 图像相似度（双方都有 AI 特征向量时，逐图取最大相似度）
        r.imageScore = null;
        List<double[]> va = parseVectors(a.getFeatureVector());
        List<double[]> vb = parseVectors(b.getFeatureVector());
        if (va != null && vb != null) {
            r.imageScore = imageSimilarity(va, vb);
        }

        // 综合分：图像相似度达到"有把握"阈值时才 6:4 融合；
        // 图像相似度过低或缺失时，图像不可信，退化为纯文字匹配——
        // 图像只作为加分项、不作为减分项，避免低相似图拉低本应匹配的文字高分。
        if (r.imageScore != null && r.imageScore >= imageMinConfidence) {
            r.finalScore = imageWeight * r.imageScore + textWeight * r.textScore;
        } else {
            r.finalScore = r.textScore;
        }

        // OCR 精确匹配：双方关键号码（学号/卡号）相同，视为强证据，直接拉高综合分
        if (StringUtils.hasText(a.getOcrKey()) && a.getOcrKey().equals(b.getOcrKey())) {
            r.finalScore = Math.max(r.finalScore, 0.95);
        }
        return r;
    }

    /**
     * 帖子发布 / 更新后执行匹配，达到阈值的生成匹配记录并通知双方
     */
    public void runMatchingForPost(Post post) {
        if (post == null || post.getStatus() != Post.STATUS_OPEN) {
            return;
        }
        String opposite = "LOST".equals(post.getType()) ? "FOUND" : "LOST";
        List<Post> candidates = postMapper.selectList(new QueryWrapper<Post>()
                .eq("type", opposite)
                .eq("status", Post.STATUS_OPEN)
                .ne("id", post.getId()));
        for (Post cand : candidates) {
            ScoreResult r = computeScore(post, cand);
            if (r.finalScore < threshold) {
                continue;
            }
            Long lostId = "LOST".equals(post.getType()) ? post.getId() : cand.getId();
            Long foundId = "LOST".equals(post.getType()) ? cand.getId() : post.getId();
            upsertMatch(lostId, foundId, r);
        }
    }

    /**
     * 重新执行匹配（前端"重新匹配"按钮）
     */
    public void rerun(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        runMatchingForPost(post);
    }

    /**
     * 忽略某条匹配（负反馈）：标记为 IGNORED，后续匹配不再推荐该配对。
     * 只有匹配双方的帖子发布者可以操作。
     */
    public void ignore(Long matchId, Long userId) {
        MatchRecord m = matchRecordMapper.selectById(matchId);
        if (m == null) {
            throw new BusinessException("匹配记录不存在");
        }
        Post lost = postMapper.selectById(m.getLostPostId());
        Post found = postMapper.selectById(m.getFoundPostId());
        boolean isLostOwner = lost != null && lost.getUserId().equals(userId);
        boolean isFoundOwner = found != null && found.getUserId().equals(userId);
        if (!isLostOwner && !isFoundOwner) {
            throw new BusinessException("无权操作该匹配");
        }
        if ("CLAIMED".equals(m.getStatus())) {
            throw new BusinessException("该匹配已认领完成，无法忽略");
        }
        m.setStatus("IGNORED");
        matchRecordMapper.updateById(m);
    }

    /**
     * 查看某个帖子的实时匹配结果（含匹配依据，按综合分倒序，最多 maxResults 条）。
     * 前端分级展示：前 5 条高亮，其余折叠为"可能相关"，防止低分但相关的真物品被硬截断漏掉。
     */
    public List<MatchVO> matchForPost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return Collections.emptyList();
        }
        fillOwners(Collections.singletonList(post));
        String opposite = "LOST".equals(post.getType()) ? "FOUND" : "LOST";
        List<Post> candidates = postMapper.selectList(new QueryWrapper<Post>()
                .eq("type", opposite)
                .eq("status", Post.STATUS_OPEN)
                .ne("id", post.getId()));
        // 批量填充候选发布者，避免逐个查库的 N+1
        fillOwners(candidates);
        List<MatchVO> list = new ArrayList<>();
        for (Post cand : candidates) {
            ScoreResult r = computeScore(post, cand);
            if (r.finalScore < floor) {
                continue;
            }
            MatchVO vo = new MatchVO();
            vo.setMyPost(post);
            vo.setOtherPost(cand);
            vo.setFinalScore(r.finalScore);
            vo.setTextScore(r.textScore);
            vo.setImageScore(r.imageScore);
            vo.setNameScore(r.nameScore);
            vo.setCategoryScore(r.categoryScore);
            vo.setLocationScore(r.locationScore);
            vo.setTimeScore(r.timeScore);
            vo.setDescriptionScore(r.descriptionScore);
            vo.setSemanticScore(r.semanticScore);
            vo.setMyImageCaption(post.getImageCaption());
            vo.setOtherImageCaption(cand.getImageCaption());
            Long lostId = "LOST".equals(post.getType()) ? post.getId() : cand.getId();
            Long foundId = "LOST".equals(post.getType()) ? cand.getId() : post.getId();
            MatchRecord exist = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                    .eq("lost_post_id", lostId)
                    .eq("found_post_id", foundId));
            // 已忽略的匹配不再展示（负反馈）
            if (exist != null && "IGNORED".equals(exist.getStatus())) {
                continue;
            }
            if (exist != null) {
                vo.setMatchId(exist.getId());
                vo.setMatchStatus(exist.getStatus());
                vo.setCreateTime(exist.getCreateTime());
            }
            list.add(vo);
        }
        list.sort(this::compareByEvidence);
        list = rerankByReciprocal(post, list);
        return list.size() > maxResults ? list.subList(0, maxResults) : list;
    }

    /**
     * 我相关的全部匹配记录
     */
    public List<MatchVO> myMatches(Long userId) {
        List<Post> myPosts = postMapper.selectList(new QueryWrapper<Post>().eq("user_id", userId));
        if (myPosts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> myLost = new ArrayList<>();
        List<Long> myFound = new ArrayList<>();
        for (Post p : myPosts) {
            if ("LOST".equals(p.getType())) {
                myLost.add(p.getId());
            } else {
                myFound.add(p.getId());
            }
        }
        // 收集匹配记录（去重），再批量加载关联帖子与发布者，避免 N+1
        Map<Long, MatchRecord> matchMap = new LinkedHashMap<>();
        if (!myLost.isEmpty()) {
            List<MatchRecord> ms = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                    .in("lost_post_id", myLost)
                    .orderByDesc("score"));
            for (MatchRecord m : ms) {
                matchMap.putIfAbsent(m.getId(), m);
            }
        }
        if (!myFound.isEmpty()) {
            List<MatchRecord> ms = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                    .in("found_post_id", myFound)
                    .orderByDesc("score"));
            for (MatchRecord m : ms) {
                matchMap.putIfAbsent(m.getId(), m);
            }
        }
        List<MatchRecord> matches = new ArrayList<>(matchMap.values());

        // 批量加载所有涉及的帖子，并一次性填充发布者昵称
        Set<Long> postIds = new HashSet<>();
        for (MatchRecord m : matches) {
            postIds.add(m.getLostPostId());
            postIds.add(m.getFoundPostId());
        }
        Map<Long, Post> postMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<Post> posts = postMapper.selectBatchIds(postIds);
            fillOwners(posts);
            for (Post p : posts) {
                postMap.put(p.getId(), p);
            }
        }

        Set<Long> lostSet = new HashSet<>(myLost);
        List<MatchVO> result = new ArrayList<>();
        for (MatchRecord m : matches) {
            boolean mySideIsLost = lostSet.contains(m.getLostPostId());
            MatchVO vo = toVO(m, mySideIsLost, postMap);
            if (vo != null) {
                result.add(vo);
            }
        }
        result.sort(this::compareByEvidence);
        return result;
    }

    private MatchVO toVO(MatchRecord m, boolean mySideIsLost, Map<Long, Post> postMap) {
        Post lost = postMap.get(m.getLostPostId());
        Post found = postMap.get(m.getFoundPostId());
        if (lost == null || found == null) {
            return null;
        }
        MatchVO vo = new MatchVO();
        vo.setMatchId(m.getId());
        vo.setMatchStatus(m.getStatus());
        vo.setMyPost(mySideIsLost ? lost : found);
        vo.setOtherPost(mySideIsLost ? found : lost);
        vo.setFinalScore(m.getScore());
        vo.setTextScore(m.getTextScore());
        vo.setImageScore(m.getImageScore());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }

    private void upsertMatch(Long lostId, Long foundId, ScoreResult r) {
        MatchRecord exist = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("lost_post_id", lostId)
                .eq("found_post_id", foundId));
        if (exist != null) {
            // 已忽略（负反馈）或已认领（已闭环）的配对不再更新推荐
            if ("IGNORED".equals(exist.getStatus()) || "CLAIMED".equals(exist.getStatus())) {
                return;
            }
            exist.setTextScore(r.textScore);
            exist.setImageScore(r.imageScore);
            exist.setScore(r.finalScore);
            matchRecordMapper.updateById(exist);
            return;
        }
        MatchRecord m = new MatchRecord();
        m.setLostPostId(lostId);
        m.setFoundPostId(foundId);
        m.setTextScore(r.textScore);
        m.setImageScore(r.imageScore);
        m.setScore(r.finalScore);
        m.setStatus("NEW");
        m.setCreateTime(LocalDateTime.now());
        matchRecordMapper.insert(m);
    }

    private double categoryScore(String ca, String cb) {
        return CategorySynonym.categorySimilarity(ca, cb);
    }

    private double timeScore(LocalDateTime ta, LocalDateTime tb) {
        if (ta == null || tb == null) {
            return 0.5;
        }
        long hours = Math.abs(Duration.between(ta, tb).toHours());
        if (hours <= 2) {
            return 1.0;
        }
        if (hours <= 24) {
            return 0.85;
        }
        if (hours <= 72) {
            return 0.65;
        }
        if (hours <= 168) {
            return 0.40;
        }
        return 0.15;
    }

    private double[] parseVector(String csv) {
        if (csv == null || csv.isEmpty()) {
            return null;
        }
        String[] parts = csv.split(",");
        double[] v = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                v[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return v;
    }

    /** 解析多个特征向量（"|" 分隔，每段为逗号分隔的 CSV） */
    private List<double[]> parseVectors(String csv) {
        if (csv == null || csv.isEmpty()) {
            return null;
        }
        List<double[]> result = new ArrayList<>();
        for (String part : csv.split("\\|")) {
            double[] v = parseVector(part);
            if (v != null && v.length > 0) {
                result.add(v);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /** 两个多向量集合的相似度：逐对余弦取最大（拍到同一物品任一角度即命中） */
    private double imageSimilarity(List<double[]> va, List<double[]> vb) {
        double max = 0;
        for (double[] a : va) {
            for (double[] b : vb) {
                if (a.length != b.length) {
                    continue;
                }
                max = Math.max(max, cosine(a, b));
            }
        }
        return max;
    }

    /** 文本语义相似度：双方都有语义向量时算余弦，缺失返回中性分 0.5 */
    private double semanticScore(String vaCsv, String vbCsv) {
        double[] va = parseVector(vaCsv);
        double[] vb = parseVector(vbCsv);
        if (va == null || vb == null || va.length == 0 || va.length != vb.length) {
            return 0.5;
        }
        return cosine(va, vb);
    }

    /**
     * 排序规则：按综合匹配分降序。
     * 图像相似度已通过融合反映在 finalScore 中，无需再单独对"同图"做特殊排序。
     */
    private int compareByEvidence(MatchVO a, MatchVO b) {
        return Double.compare(b.getFinalScore(), a.getFinalScore());
    }

    /**
     * k-reciprocal 重排序（适配"失物↔招领"双向异质匹配）。
     * <p>
     * 核心思想：候选 B 若与查询 A 互为 top-K 近邻（B 在 A 的 top-K 里，且 A 也在 B 的 top-K 里），
     * 说明是"双向强匹配"，可靠性高于仅单向的高相似度，应优先排序。
     * <p>
     * 注：标准 ReID 的 Jaccard 重排序要求 query/gallery 同质；此处失物与招领分属两个异质集合，
     * 邻居集元素类型不同无法直接求交集，故以"互近邻"布尔判定替代 Jaccard，保留 k-reciprocal 本质。
     */
    private List<MatchVO> rerankByReciprocal(Post post, List<MatchVO> list) {
        if (list == null || list.size() <= 1) {
            return list;
        }
        int k = Math.min(rerankK, list.size());
        if (k <= 1) {
            return list;
        }
        // 与查询同类型的帖子池（用于计算候选的反向近邻），不含查询自身
        List<Post> sameTypePool = postMapper.selectList(new QueryWrapper<Post>()
                .eq("type", post.getType())
                .eq("status", Post.STATUS_OPEN)
                .ne("id", post.getId()));
        if (sameTypePool.isEmpty()) {
            return list;
        }

        List<MatchVO> topCandidates = new ArrayList<>(list.subList(0, k));
        Set<Long> reciprocalIds = new HashSet<>();
        for (MatchVO vo : topCandidates) {
            Post cand = vo.getOtherPost();
            if (cand == null) {
                continue;
            }
            // 预计算候选与同类型池各帖子的匹配分，避免排序比较时重复计算
            Map<Long, Double> scores = new HashMap<>();
            for (Post p : sameTypePool) {
                scores.put(p.getId(), computeScore(cand, p).finalScore);
            }
            List<Post> ranked = new ArrayList<>(sameTypePool);
            ranked.sort((p1, p2) -> Double.compare(
                    scores.getOrDefault(p2.getId(), 0.0),
                    scores.getOrDefault(p1.getId(), 0.0)));
            // 查询是否落在候选的反向 top-K 近邻里
            for (int i = 0; i < Math.min(k, ranked.size()); i++) {
                if (ranked.get(i).getId().equals(post.getId())) {
                    reciprocalIds.add(cand.getId());
                    break;
                }
            }
        }

        // 互近邻优先，其次按综合分降序
        List<MatchVO> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> {
            boolean ra = a.getOtherPost() != null && reciprocalIds.contains(a.getOtherPost().getId());
            boolean rb = b.getOtherPost() != null && reciprocalIds.contains(b.getOtherPost().getId());
            if (ra != rb) {
                return ra ? -1 : 1;
            }
            return Double.compare(b.getFinalScore(), a.getFinalScore());
        });
        return sorted;
    }

    private double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 批量填充帖子的发布者昵称 / 头像（非数据库字段，前端展示用），避免逐个查库的 N+1 */
    private void fillOwners(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Post p : posts) {
            if (p.getUserId() != null) {
                ids.add(p.getUserId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        List<User> users = userMapper.selectBatchIds(ids);
        Map<Long, User> userMap = new HashMap<>();
        for (User u : users) {
            userMap.put(u.getId(), u);
        }
        for (Post p : posts) {
            User u = userMap.get(p.getUserId());
            if (u != null) {
                p.setOwnerName(u.getNickname());
                p.setOwnerAvatar(u.getAvatar());
            }
        }
    }
}
