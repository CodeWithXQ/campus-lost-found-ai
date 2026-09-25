package com.campus.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.lostfound.common.BusinessException;
import com.campus.lostfound.common.PageResult;
import com.campus.lostfound.dto.PostDTO;
import com.campus.lostfound.entity.MatchRecord;
import com.campus.lostfound.entity.Notification;
import com.campus.lostfound.entity.Post;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.mapper.MatchRecordMapper;
import com.campus.lostfound.mapper.PostMapper;
import com.campus.lostfound.mapper.UserMapper;
import com.campus.lostfound.util.ContentAuditUtil;
import com.campus.lostfound.util.DescriptionParser;
import com.campus.lostfound.util.KeyNoExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 失物 / 招领帖子服务
 */
@Service
public class PostService {

    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private PostMapper postMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MatchRecordMapper matchRecordMapper;

    @Resource
    private MatchService matchService;

    @Resource
    private AiClient aiClient;

    @Resource
    private SubscriptionService subscriptionService;

    @Resource
    private NotificationService notificationService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * 发布帖子：保存 → AI 图像特征提取（可降级） → AI 初审核 → 待人工二审。
     * <p>
     * 硬违禁内容被 AI 初审核直接拒绝（status=4）；否则：
     * 普通用户进入待审核（status=3），管理员免人工二审直接上线（status=0）。
     * 智能匹配与订阅提醒在帖子上线时触发（普通用户为审核通过后，管理员为发布即触发）。
     */
    public Post publish(Long userId, PostDTO dto, boolean isAdmin) {
        validate(dto);
        Post p = new Post();
        p.setUserId(userId);
        p.setType(dto.getType());
        p.setTitle(dto.getTitle().trim());
        p.setCategory(dto.getCategory());
        p.setLocation(dto.getLocation().trim());
        p.setLostTime(parseTime(dto.getLostTime()));
        p.setDescription(dto.getDescription());
        p.setImageUrls(dto.getImageUrls());
        p.setAiCategory(dto.getAiCategory());
        LocalDateTime now = LocalDateTime.now();
        p.setCreateTime(now);
        p.setUpdateTime(now);
        postMapper.insert(p);

        // AI 图像特征提取（AI 服务不可用时自动降级，不影响发布）
        if (StringUtils.hasText(dto.getImageUrls())) {
            String csv = embedImagesToCsv(dto.getImageUrls());
            if (csv != null) {
                p.setFeatureVector(csv);
                postMapper.updateById(p);
            }
        }

        // OCR 提取关键号码（学号/卡号），用于证件卡类精确匹配
        p.setOcrKey(extractOcrKey(p));

        // 文本语义向量（标题+描述编码），用于语义匹配
        p.setTextVector(embedTextOf(p));

        // 首图中文描述（BLIP + 英译中），用于匹配详情悬浮展示双方图片 AI 描述
        p.setImageCaption(captionFirstImage(p));

        // AI 初审核：硬违禁直接自动拒绝；否则普通用户进入待审核，管理员免人工二审直接上线
        aiAudit(p);
        boolean rejected = Post.AI_REJECT.equals(p.getAiAuditResult());
        p.setStatus(rejected ? Post.STATUS_REJECTED : (isAdmin ? Post.STATUS_OPEN : Post.STATUS_PENDING));
        postMapper.updateById(p);

        if (rejected) {
            notificationService.notifyUser(userId, Notification.TYPE_AUDIT,
                    "发布未通过自动审核",
                    "您发布的《" + p.getTitle() + "》未通过 AI 自动审核" + noteSuffix(p.getAiAuditNote()) + "，请修改后重新提交。",
                    p.getId());
        } else if (isAdmin) {
            // 管理员免人工二审：发布即上线，立即触发匹配与订阅提醒
            matchService.runMatchingForPost(p);
            subscriptionService.checkAndNotify(p);
            notificationService.notifyUser(userId, Notification.TYPE_AUDIT,
                    "发布成功",
                    "您发布的《" + p.getTitle() + "》已通过 AI 内容安全检测并上线信息广场，系统已为你匹配相关物品。",
                    p.getId());
        }
        // PASS/WARN：普通用户等待人工二审，此处不触发匹配与订阅提醒
        return p;
    }

    /**
     * 分页查询
     */
    public PageResult<Post> list(String type, String category, String keyword, String location,
                                 Integer status, int page, int size) {
        QueryWrapper<Post> qw = new QueryWrapper<Post>()
                .eq("status", status == null ? Post.STATUS_OPEN : status)
                .eq(StringUtils.hasText(type), "type", type)
                .eq(StringUtils.hasText(category), "category", category)
                .like(StringUtils.hasText(keyword), "title", keyword)
                .like(StringUtils.hasText(location), "location", location)
                .orderByDesc("create_time");
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), qw);
        fillOwner(p.getRecords());
        return PageResult.of(p);
    }

    public Post detail(Long id) {
        Post p = postMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("帖子不存在或已下架");
        }
        fillOwner(java.util.Collections.singletonList(p));
        return p;
    }

    public PageResult<Post> myPosts(Long userId, int page, int size) {
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), new QueryWrapper<Post>()
                .eq("user_id", userId)
                .orderByDesc("create_time"));
        fillOwner(p.getRecords());
        fillMatchStat(p.getRecords());
        return PageResult.of(p);
    }

    public Post update(Long id, Long userId, PostDTO dto, boolean isAdmin) {
        Post p = postMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!p.getUserId().equals(userId)) {
            throw new BusinessException("无权操作他人的帖子");
        }
        if (p.getStatus() == Post.STATUS_CLAIMED || p.getStatus() == Post.STATUS_ARCHIVED) {
            throw new BusinessException("帖子已下架，无法编辑");
        }
        validate(dto);
        int beforeStatus = p.getStatus();
        p.setType(dto.getType());
        p.setTitle(dto.getTitle().trim());
        p.setCategory(dto.getCategory());
        p.setLocation(dto.getLocation().trim());
        p.setLostTime(parseTime(dto.getLostTime()));
        p.setDescription(dto.getDescription());
        p.setImageUrls(dto.getImageUrls());
        p.setAiCategory(dto.getAiCategory());
        p.setUpdateTime(LocalDateTime.now());

        // 更新后重新提取特征。
        // 无图 / 提取失败时须将特征向量显式置空：updateById 会忽略 null 字段，
        // 否则删除图片后残留旧向量，智能匹配列表仍会显示图像相似度。
        String csv = StringUtils.hasText(dto.getImageUrls()) ? embedImagesToCsv(dto.getImageUrls()) : null;
        p.setFeatureVector(csv);

        // OCR 提取关键号码（编辑后可能变化，需显式更新以支持置空）
        p.setOcrKey(extractOcrKey(p));
        p.setTextVector(embedTextOf(p));
        p.setImageCaption(captionFirstImage(p));

        // 编辑重审：任何编辑都重新走 AI 初审核
        aiAudit(p);
        if (Post.AI_REJECT.equals(p.getAiAuditResult())) {
            // 命中硬违禁：展示中的自动下架，待审核/审核未通过的转为审核未通过
            p.setStatus(beforeStatus == Post.STATUS_OPEN ? Post.STATUS_ARCHIVED : Post.STATUS_REJECTED);
        } else if (beforeStatus == Post.STATUS_REJECTED) {
            // 原为审核未通过，编辑后重新进入待审核（重提）；管理员免二审，直接恢复展示
            p.setStatus(isAdmin ? Post.STATUS_OPEN : Post.STATUS_PENDING);
        } else if (beforeStatus == Post.STATUS_OPEN && !isAdmin && Post.AI_WARN.equals(p.getAiAuditResult())) {
            // 编辑「展示中」的帖子且命中软风险（WARN）：普通用户重新进入待审核，人工复审后上线；
            // PASS 保持展示（避免改错别字也要重审），管理员免二审
            p.setStatus(Post.STATUS_PENDING);
        }
        // 展示中（PASS）/ 待审核：保持原状态

        postMapper.updateById(p);
        // AI 结论单独更新，避免 WARN→PASS 时 aiAuditNote 由非空转 null 残留旧值
        postMapper.updateAiAudit(p.getId(), p.getAiAuditResult(), p.getAiAuditNote());
        // 编辑重提（原为审核未通过）时清空历史拒绝原因
        if (beforeStatus == Post.STATUS_REJECTED) {
            postMapper.updateAuditReason(p.getId(), null);
        }
        postMapper.updateFeatureVector(p.getId(), p.getFeatureVector());
        postMapper.updateOcrKey(p.getId(), p.getOcrKey());
        postMapper.updateTextVector(p.getId(), p.getTextVector());
        postMapper.updateImageCaption(p.getId(), p.getImageCaption());

        if (Post.STATUS_OPEN == p.getStatus()) {
            matchService.runMatchingForPost(p);
        }

        // 违规通知：展示中编辑违规被自动下架 / 审核未通过编辑后仍未通过
        if (Post.STATUS_REJECTED == p.getStatus()
                || (beforeStatus == Post.STATUS_OPEN && Post.STATUS_ARCHIVED == p.getStatus())) {
            boolean offShelf = beforeStatus == Post.STATUS_OPEN;
            notificationService.notifyUser(userId, Notification.TYPE_AUDIT,
                    offShelf ? "编辑内容违规，已自动下架" : "编辑后仍未通过自动审核",
                    offShelf
                            ? "您编辑的《" + p.getTitle() + "》命中违禁内容，已自动下架" + noteSuffix(p.getAiAuditNote()) + "。"
                            : "您编辑的《" + p.getTitle() + "》仍未通过 AI 自动审核" + noteSuffix(p.getAiAuditNote()) + "，请修改后重新提交。",
                    p.getId());
        }
        return p;
    }

    public void delete(Long id, Long userId) {
        Post p = postMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!p.getUserId().equals(userId)) {
            throw new BusinessException("无权操作他人的帖子");
        }
        // 删除相关匹配记录
        matchRecordMapper.delete(new QueryWrapper<MatchRecord>()
                .eq("lost_post_id", id).or().eq("found_post_id", id));
        postMapper.deleteById(id);
    }

    public void archive(Long id, Long userId) {
        Post p = postMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!p.getUserId().equals(userId)) {
            throw new BusinessException("无权操作他人的帖子");
        }
        p.setStatus(Post.STATUS_ARCHIVED);
        p.setUpdateTime(LocalDateTime.now());
        postMapper.updateById(p);
    }

    /**
     * AI 初审核：文本敏感词 + 图片清晰度 + 类别一致性三路判定。
     * 结果写入 aiAuditResult（PASS/WARN/REJECT）与 aiAuditNote（风险说明）。
     */
    private void aiAudit(Post p) {
        List<String> risks = new ArrayList<>();

        // 1. 文本敏感词（标题 + 描述）
        String textFlag = ContentAuditUtil.audit(
                (p.getTitle() == null ? "" : p.getTitle()) + " " + (p.getDescription() == null ? "" : p.getDescription()));
        if ("HARD".equals(textFlag)) {
            p.setAiAuditResult(Post.AI_REJECT);
            p.setAiAuditNote("文本命中违禁词");
            return;
        }
        if ("SOFT".equals(textFlag)) {
            risks.add("文本含广告或联系方式倾向");
        }

        // 2. 图片清晰度（取第一张图评估）
        double q = imageQualityOf(p.getImageUrls());
        if (q >= 0 && q < 0.3) {
            risks.add("图片清晰度较低");
        }

        // 3. AI 类别一致性（AI 识别类别 vs 用户填写类别）
        if (StringUtils.hasText(p.getAiCategory()) && StringUtils.hasText(p.getCategory())
                && !p.getAiCategory().equals(p.getCategory())) {
            risks.add("AI 识别类别与填写类别不符");
        }

        if (risks.isEmpty()) {
            p.setAiAuditResult(Post.AI_PASS);
            p.setAiAuditNote(null);
        } else {
            p.setAiAuditResult(Post.AI_WARN);
            p.setAiAuditNote(String.join("；", risks));
        }
    }

    /** 取第一张本地图片做清晰度评估，失败返回 -1 */
    private double imageQualityOf(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return -1;
        }
        String path = imageUrls.split(",")[0].trim();
        if (path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
            return -1;
        }
        File f = new File(new File(uploadDir).getAbsoluteFile(), path);
        if (!f.exists() || !f.isFile()) {
            return -1;
        }
        try {
            return aiClient.imageQuality(Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            return -1;
        }
    }

    /** 通知文案中的原因后缀 */
    private String noteSuffix(String note) {
        return (note == null || note.trim().isEmpty()) ? "" : "（" + note + "）";
    }

    public void fillOwner(List<Post> posts) {
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
        // 批量查询用户再回填，避免逐个 selectById 的 N+1
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

    /**
     * 填充每个帖子的匹配统计：匹配记录数 + 最高匹配度 + 最高匹配的对方帖子。
     * 仅统计已落库的匹配记录（t_match_record），供"我的发布"列表提示与指向展示使用。
     */
    private void fillMatchStat(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Post p : posts) {
            ids.add(p.getId());
        }
        List<MatchRecord> records = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .and(w -> w.in("lost_post_id", ids).or().in("found_post_id", ids)));
        if (records.isEmpty()) {
            for (Post p : posts) {
                p.setMatchCount(0);
                p.setMaxMatchScore(null);
                p.setTopMatchPost(null);
            }
            return;
        }

        // 收集所有匹配涉及的帖子 id，批量查询对方帖子
        Set<Long> otherIds = new HashSet<>();
        for (MatchRecord m : records) {
            otherIds.add(m.getLostPostId());
            otherIds.add(m.getFoundPostId());
        }
        Map<Long, Post> postMap = new HashMap<>();
        if (!otherIds.isEmpty()) {
            List<Post> others = postMapper.selectList(new QueryWrapper<Post>().in("id", otherIds));
            for (Post o : others) {
                postMap.put(o.getId(), o);
            }
        }

        for (Post p : posts) {
            int count = 0;
            double max = -1;
            MatchRecord best = null;
            for (MatchRecord m : records) {
                if (p.getId().equals(m.getLostPostId()) || p.getId().equals(m.getFoundPostId())) {
                    count++;
                    if (m.getScore() != null && m.getScore() > max) {
                        max = m.getScore();
                        best = m;
                    }
                }
            }
            p.setMatchCount(count);
            p.setMaxMatchScore(count > 0 ? max : null);
            Post top = null;
            if (best != null) {
                Long otherId = p.getId().equals(best.getLostPostId()) ? best.getFoundPostId() : best.getLostPostId();
                top = postMap.get(otherId);
            }
            p.setTopMatchPost(top);
        }
    }

    private void validate(PostDTO dto) {
        if (dto.getType() == null || (!"LOST".equals(dto.getType()) && !"FOUND".equals(dto.getType()))) {
            throw new BusinessException("帖子类型错误");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException("物品名称不能为空");
        }
        if (!StringUtils.hasText(dto.getCategory())) {
            throw new BusinessException("请选择物品类别");
        }
        if (!StringUtils.hasText(dto.getLocation())) {
            throw new BusinessException("地点不能为空");
        }
    }

    private LocalDateTime parseTime(String s) {
        if (!StringUtils.hasText(s)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(s, FMT_DATETIME);
        } catch (Exception e) {
            try {
                return LocalDate.parse(s, FMT_DATE).atStartOfDay();
            } catch (Exception e2) {
                return LocalDateTime.now();
            }
        }
    }

    /**
     * 提取帖子关键号码（学号/卡号）：优先从描述文本提取，其次对图片做 OCR。
     * 用于证件卡类物品的精确匹配。全部未命中返回 null。
     */
    private String extractOcrKey(Post p) {
        // 1. 描述文本中的学号/卡号（零依赖，最可靠）
        String keyNo = KeyNoExtractor.extract(p.getDescription());
        if (keyNo != null) {
            return keyNo;
        }
        // 2. 图片 OCR（AI 服务可用时；未安装 OCR 则自动跳过）
        if (StringUtils.hasText(p.getImageUrls())) {
            StringBuilder ocrText = new StringBuilder();
            for (String part : p.getImageUrls().split(",")) {
                String path = part.trim();
                if (path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
                    continue;
                }
                File f = new File(new File(uploadDir).getAbsoluteFile(), path);
                if (!f.exists() || !f.isFile()) {
                    continue;
                }
                try {
                    String t = aiClient.ocr(Files.readAllBytes(f.toPath()));
                    if (t != null) {
                        ocrText.append(t).append('\n');
                    }
                } catch (Exception e) {
                    // 单图失败跳过
                }
            }
            keyNo = KeyNoExtractor.extract(ocrText.toString());
        }
        return keyNo;
    }

    /**
     * 生成首图的中文描述（BLIP + 英译中），用于匹配详情悬浮展示双方图片 AI 描述。
     * 无图 / 网络图片 / AI 服务不可用时返回 null（自动降级，不影响主流程）。
     */
    private String captionFirstImage(Post p) {
        if (!StringUtils.hasText(p.getImageUrls())) {
            return null;
        }
        String path = p.getImageUrls().split(",")[0].trim();
        if (path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
            return null;
        }
        File f = new File(new File(uploadDir).getAbsoluteFile(), path);
        if (!f.exists() || !f.isFile()) {
            return null;
        }
        try {
            return aiClient.caption(Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编码帖子标题 + 描述的语义向量（Chinese-CLIP 文本塔，与图像同空间）。
     * AI 服务不可用时返回 null（语义维度自动降级）。
     */
    private String embedTextOf(Post p) {
        String text = (p.getTitle() == null ? "" : p.getTitle())
                + " " + (p.getDescription() == null ? "" : p.getDescription());
        text = text.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            double[] v = aiClient.embedText(text);
            return v == null ? null : toCsv(v);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取帖子全部图片的特征向量，每张图一个向量，用 "|" 分隔。
     * 匹配时逐图取最大相似度，避免 mean-pooling 稀释主物品特征。
     * 任意一张图提取失败则跳过，全部失败返回 null（匹配时自动降级为纯文字匹配）。
     */
    private String embedImagesToCsv(String imageUrls) {
        List<double[]> vecs = new ArrayList<>();
        for (String part : imageUrls.split(",")) {
            String path = part.trim();
            if (path.isEmpty() || path.startsWith("http://") || path.startsWith("https://")) {
                continue; // 网络图片无本地文件，跳过
            }
            File f = new File(new File(uploadDir).getAbsoluteFile(), path);
            if (!f.exists() || !f.isFile()) {
                continue;
            }
            try {
                double[] v = aiClient.embedImage(Files.readAllBytes(f.toPath()));
                if (v != null && v.length > 0) {
                    vecs.add(v);
                }
            } catch (Exception e) {
                // 单图失败跳过，不影响其他图片
            }
        }
        if (vecs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vecs.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(toCsv(vecs.get(i)));
        }
        return sb.toString();
    }

    private String toCsv(double[] vec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format("%.6f", vec[i]));
        }
        return sb.toString();
    }

    /**
     * 文搜图：输入文字描述，编码为语义向量后与所有展示中帖子的图像/文本向量算余弦，
     * 返回相似度最高的帖子。AI 服务不可用时返回空列表（降级）。
     */
    public List<Post> searchByText(String text, int size) {
        double[] query = aiClient.embedText(text);
        if (query == null) {
            return java.util.Collections.emptyList();
        }
        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>().eq("status", Post.STATUS_OPEN));
        if (posts.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 查询文本的属性（颜色/品牌/型号），用于属性精确匹配加成
        DescriptionParser.Attrs queryAttrs = DescriptionParser.extract(text);
        Map<Long, Double> simMap = new HashMap<>();
        for (Post p : posts) {
            double semanticSim = maxCosine(query, p.getFeatureVector(), p.getTextVector());
            // 语义 + 属性双路重排：属性命中加分、冲突减分、缺失中性，让"蓝色书包"真正排到"红色书包"前面
            double boost = attrBoost(queryAttrs, DescriptionParser.extract(p.getTitle(), p.getDescription()));
            simMap.put(p.getId(), semanticSim + boost);
        }
        List<Post> scored = new ArrayList<>(posts);
        scored.sort((a, b) -> Double.compare(
                simMap.getOrDefault(b.getId(), 0.0),
                simMap.getOrDefault(a.getId(), 0.0)));
        int n = Math.max(0, Math.min(size, scored.size()));
        List<Post> result = new ArrayList<>(scored.subList(0, n));
        fillOwner(result);
        return result;
    }

    /** 查询属性与帖子属性的匹配加成：颜色/品牌/型号各自命中 +0.25、冲突 -0.25、查询有属性但帖子缺失 -0.1 */
    private double attrBoost(DescriptionParser.Attrs q, DescriptionParser.Attrs p) {
        return setBoost(q.colors, p.colors)
                + setBoost(q.brands, p.brands)
                + setBoost(q.models, p.models);
    }

    private double setBoost(Set<String> q, Set<String> p) {
        if (q.isEmpty()) {
            return 0.0; // 查询无此属性，不参与排序（如搜"书包"纯物品词）
        }
        if (p.isEmpty()) {
            return -0.1; // 查询有此属性但帖子缺失 → 轻微惩罚（搜"黑色"时无颜色物品不是目标）
        }
        Set<String> inter = new HashSet<>(q);
        inter.retainAll(p);
        return inter.isEmpty() ? -0.25 : 0.25; // 冲突 -0.25，命中 +0.25
    }

    /**
     * 回填历史帖子的文本语义向量（textVector 为空的帖子），用于语义匹配/文搜图。
     * 历史帖子 featureVector 为 384 维（DINOv2）与 query 512 维（CLIP 文本塔）不匹配，
     * 导致语义检索对它们失效，需统一为 512 维文本向量。返回回填成功的帖子数。
     */
    public int fillMissingTextVectors() {
        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>()
                .and(w -> w.isNull("text_vector").or().eq("text_vector", "")));
        int count = 0;
        for (Post p : posts) {
            String tv = embedTextOf(p);
            if (tv != null) {
                postMapper.updateTextVector(p.getId(), tv);
                count++;
            }
        }
        return count;
    }

    /** 查询向量与多个候选向量（图像/文本，图像可能为 "|" 分隔的多向量）的最大余弦 */
    private double maxCosine(double[] query, String... vectorCsvs) {
        double max = 0;
        for (String csv : vectorCsvs) {
            if (csv == null || csv.isEmpty()) {
                continue;
            }
            for (String part : csv.split("\\|")) {
                double[] v = parseVector(part);
                if (v != null && v.length == query.length) {
                    max = Math.max(max, cosine(query, v));
                }
            }
        }
        return max;
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
}
