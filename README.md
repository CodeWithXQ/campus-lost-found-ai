# 校园失物招领智能匹配与 AI 图像识别系统

> 基于 **SpringBoot + Vue3 + MySQL + 多模态 AI（Chinese-CLIP + DINOv2 + BLIP）** 的校园失物招领系统，六维加权匹配 + 图像识别 + 认领闭环。

---

## 一、系统简介

面向校园师生提供失物 / 招领信息发布与智能匹配服务：

- **发布**：失物（我丢了）与招领（我捡到）信息，支持照片上传
- **智能匹配**：发布后自动匹配，六维加权打分（名称 + 类别 + 地点 + 时间 + 描述 + 语义），图像相似度 ≥ 0.5 时 6:4 融合
- **AI 图像能力**：Chinese-CLIP 提取 512 维特征向量（降级 DINOv2/MobileNetV3）；上传照片自动识别类别 + 生成中文描述（BLIP+OPUS-MT）
- **认领闭环**：失主申请认领 → 拾主确认 → 双方信息自动下架归档
- **实时通知**：匹配成功 / 认领申请 / 认领结果通过站内消息 + SSE 实时推送
- **数据看板**：ECharts 展示类别分布、高频地点 TOP10、近 6 个月趋势、匹配成功率
- **降级策略**：AI 服务不可用时自动切换纯文字匹配，系统核心功能零影响

## 二、技术架构

```
┌──────────────────────────────────────┐
│        前端 Vue3 + Element Plus       │
│   信息广场 │ 发布 │ 匹配 │ 认领 │ 看板  │
└──────────────────┬───────────────────┘
                   │ HTTP /api (Vite 代理)
┌──────────────────▼───────────────────┐
│       后端 SpringBoot 3.5 (8080)      │
│  用户 │ 帖子 │ 匹配算法 │ 认领 │ 通知SSE │
│  统计 │ JWT鉴权 │ 图片上传 │ AI客户端    │
└───────┬───────────────────┬──────────┘
        │                   │ HTTP (特征提取/分类)
┌───────▼────────┐  ┌───────▼──────────┐
│  MySQL (3306)   │  │ Python FastAPI   │
│  业务数据       │  │ AI服务 (8000)     │
└────────────────┘  │ MobileNetV3      │
                    └──────────────────┘
```

## 三、目录结构

```
lost-found-system/
├── backend/          # SpringBoot 后端（Maven 工程）
├── frontend/         # Vue3 前端（Vite 工程）
├── ai-service/       # Python FastAPI AI 图像服务
├── sql/init.sql      # 数据库初始化脚本（含种子数据）
├── maven-settings.xml# 本项目专用 Maven 配置（阿里云镜像）
└── README.md
```

## 四、环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+（本机 JDK21） |
| Maven | 3.6+ |
| Node.js | 16+ |
| MySQL | 8.0 |
| Python | 3.9+（需安装 PyTorch） |

## 五、启动步骤

> **完整启动顺序**：MySQL → AI 图像服务（可选）→ 后端 → 前端。
> 建议开多个终端分别运行；后端、AI 服务、前端各占一个终端。

### 1. 初始化数据库

确认 MySQL 已启动（默认端口 3306），执行初始化脚本。

> 重要：脚本与种子数据均为 UTF-8 编码。在中文 Windows 下必须加 `--default-character-set=utf8mb4`，否则中文会乱码并报 `Data too long for column ...` 错误。

```sql
-- 方式一：MySQL 命令行直接导入（推荐）
mysql -uroot -p --default-character-set=utf8mb4 < sql/init.sql

-- 方式二：进入 MySQL 客户端后执行
mysql -uroot -p --default-character-set=utf8mb4
SET NAMES utf8mb4;
source sql/init.sql;
exit;
```

> 脚本会 `DROP` 旧表并重建，同时写入 5 个演示账号与 10 条种子帖子。数据库账号密码见本地配置 `backend/src/main/resources/application-local.yml`（该文件不提交），如需修改请同步更新 `datasource.username/password`。

### 2. 启动 AI 图像服务（端口 8000，可选）

  AI 服务日志显示：PaddleOCR 不可用，OCR 功能降级（仅靠描述文本提取号码）: No module named 'paddleocr'

  这是预期行为——阶段四的 OCR 设计就是可降级的：
  - 图片 OCR（学号提取）当前不可用（需 pip install paddlepaddle paddleocr）
  - 但描述文本中的学号提取（KeyNoExtractor 零依赖）正常工作，证件卡精确匹配仍可用
  - 如需启用完整 OCR，运行 pip install paddlepaddle paddleocr 后重启 AI 服务即可
  
```bash
cd ai-service
pip install -r requirements.txt   # 依赖较大（torch + torchvision），首次安装需联网
python app.py
```

- 首次运行会自动下载 MobileNetV3 预训练权重（约 20MB），缓存到 `ai-service/.torch_cache/`，之后**离线可用**
- 全程本地 CPU 推理，无需 GPU、无需联网、无任何 API 费用

> **AI 服务未启动不影响核心功能**：后端会自动降级为纯文字匹配，发布 / 认领 / 看板均正常。

### 3. 启动后端（端口 8080）

> 数据库密码与 JWT 密钥已脱敏到 `backend/src/main/resources/application-local.yml`（该文件不进版本库）。启动**必须激活 `local` profile**，否则会因缺少密码/密钥而启动失败。

```bash
cd backend
# 方式一：Maven 直接运行（激活 local profile）
mvn -s ../maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local
# 方式二：打包运行
mvn -s ../maven-settings.xml package -DskipTests
java -jar target/lost-found-backend-1.0.0.jar --spring.profiles.active=local
```

> IDEA 运行：在 Run Configuration 的 `Active profiles` 填 `local`。

- `-s ../maven-settings.xml` 使用项目内置 Maven 配置（本地仓库 `.m2repo/` + 阿里云镜像，避免污染全局仓库）
- 启动成功后控制台显示 `Tomcat started on port(s): 8080`
- 若前端已构建，直接访问 http://localhost:8080 即可进入系统

### 4. 启动前端

**方式 A（开发模式，前后端分离，调试用）：**
cd lost-found-system
```bash
cd frontend
pnpm install     # 或 npm install
pnpm dev         # 或 npm run dev
```
 一个前端启动的注意事项

  这次重启发现：npm run dev 在后台运行时，vite 的启动输出会被 npm 缓冲、且后台任务会误报失败；改用直接 node_modules/.bin/vite --port  
  5173 --host 启动就正常（806ms 就绪）。

  以后你自己启动前端，建议用：
  cd frontend
  node_modules/.bin/vite --port 5173 --host
访问 **http://localhost:5173**（Vite 已配置 `/api`、`/uploads` 代理到后端 8080）。

**方式 B（生产模式，单端口访问，推荐使用）：**

```bash
cd frontend
pnpm install     # 或 npm install
pnpm build       # 或 npm run build，生成 dist 目录
```

构建完成后，后端会自动托管 `frontend/dist`，直接访问 **http://localhost:8080** 即可进入系统（无需再启动前端）。
若未构建前端就访问 8080，会看到友好的引导提示页（提示如何构建），而不是报错。

### 5. 验证启动
  一个需要你注意的点

  系统默认的 java 命令是 Java 8，而后端 jar 是 Java 17 编译的，直接 java -jar 会报 UnsupportedClassVersionError。所以启动后端要用 JDK 
  21 的完整路径：

  D:\AppGallery\Downloads\IDEA\jdk21\bin\java.exe -jar target/lost-found-backend-1.0.0.jar
```bash
# 后端自检（应返回 JSON 帖子列表）
curl http://localhost:8080/api/post/list

# 端到端冒烟测试（登录 → 发布 → 匹配 → 通知 → 认领 → 统计）
python smoke_test.py
```

### 6. 停止服务

- 后端 / 前端 / AI 服务：在各自终端按 `Ctrl+C`
- 或按端口查找并结束进程（Windows PowerShell）：
  ```bash
  netstat -ano | findstr :8080    # 找到最后一列的 PID
  taskkill /F /PID <PID>
  ```

## 六、演示账号（密码均为 `123456`）

| 账号 | 角色 | 说明 |
|------|------|------|
| admin | 管理员 | 可查看数据看板、帖子管理、用户管理 |
| student1 | 学生 | 张同学（发布过失物：蓝色书包、黑色钱包） |
| student2 | 学生 | 李同学（发布过招领：蓝色双肩包、黑色钱包） |
| student3 / student4 | 学生 | 王同学 / 赵同学 |

## 七、演示流程

**基础版（保底）：**
1. 登录 `student1` → 发布一条失物"蓝色书包，图书馆三楼"
2. 系统自动匹配到 `student2` 的招领"蓝色双肩包，图书馆"，匹配度 87%+
3. 帖子详情页展示**匹配依据**：名称相似度、类别、地点、时间分项
4. 双方收到站内通知（SSE 实时推送）
5. 失主申请认领 → 拾主在"认领管理"确认 → 两条帖子自动下架
6. 管理员登录查看数据看板（类别分布 / 地点 / 趋势 / 匹配成功率），并在「帖子管理」「用户管理」页查看/治理全站信息

**AI 版（加分）：**
1. 发布页上传一张钱包照片 → 点击"🤖 AI 识别物品类别" → 自动识别为"钱包/包"并回填类别
2. 发布后系统提取照片特征向量，与已有带图帖子做余弦相似度匹配
3. 匹配结果中展示"图像相似 XX%"分项

## 八、核心设计说明

### 匹配算法（`MatchService`）

```
文字匹配分 = 名称×0.20 + 类别×0.20 + 地点×0.20 + 时间×0.15 + 描述×0.15 + 语义×0.10
综合匹配分 = 图像相似度 ≥ 0.5 时 0.6×图像 + 0.4×文字；否则 = 文字分（图像只加分不减分）
```

- 名称 / 地点采用**编辑距离 + 包含关系加权**相似度，能处理"蓝色书包"vs"蓝色双肩包"
- 时间相近度：2 小时内满分，7 天后衰减
- 综合分 ≥ 0.35 自动生成匹配记录并通知双方

### AI 图像服务（`ai-service/`）

- 特征：Chinese-CLIP（512 维，图文同空间）优先，降级 DINOv2（384 维）/ MobileNetV3
- 相似度：余弦相似度（0~1）
- 自动分类：Chinese-CLIP 零样本分类（12 个校园类别），置信度 < 0.30 判「其他」
- 图像描述：BLIP 生成英文 + OPUS-MT 英译中，输出中文描述
- 全部本地 CPU 推理，无需 GPU、无需网络（权重下载一次后离线）

### 降级策略（`AiClient`）

AI 服务请求失败 → 标记不可用并进入 30 秒冷却 → 期间纯文字匹配；
冷却结束后自动重试，恢复后重新启用图像匹配。

### 踩坑记录

**AI 服务稳定性问题**：AI 图像识别服务依赖 HuggingFace 下载模型权重，国内网络不稳时识别经常超时或失败。如果让匹配流程强依赖 AI，AI 一挂整个系统就不可用。于是主动加了熔断降级——AI 请求失败即进入 30 秒冷却、期间退化为纯文字匹配，冷却后自动重试；并写了端到端冒烟测试兜底，保证 AI 不可用时核心功能（发布/匹配/认领）零影响。

## 九、主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/login | 登录 |
| POST | /api/auth/register | 注册 |
| GET | /api/post/list | 信息列表（公共） |
| POST | /api/post | 发布 |
| GET | /api/match/post/{id} | 帖子匹配结果（含依据） |
| POST | /api/claim/apply | 发起认领 |
| POST | /api/claim/{id}/confirm | 确认认领（自动下架） |
| GET | /api/notification/stream | SSE 实时推送 |
| POST | /api/ai/classify | AI 物品识别 |
| GET | /api/stats/overview | 看板总览（管理员） |
| POST | /api/file/upload | 图片上传 |
| GET | /api/admin/posts | 帖子管理（管理员） |
| GET | /api/admin/users | 用户管理（管理员） |

## 十、常见问题

- **localhost 拒绝连接 / 前端打不开**：确认后端已启动（端口 8080）；开发模式还需确认前端 `pnpm dev` 已启动（端口 5173）。可用 `netstat -ano | findstr :8080` 检查端口是否在监听
- **`pnpm dev` 报"找不到 vite"**：`node_modules/.bin` 软链接缺失，重新执行 `pnpm install`（或 `npm install`）重建即可
- **前端图片不显示**：确认后端已启动，图片存放在后端 `uploads/` 目录
- **AI 识别无结果**：确认 `ai-service` 已启动（`python app.py`）；首次运行需联网下载权重
- **登录失败（提示账号密码错误）**：确认已执行 `sql/init.sql`，且 `application-local.yml` 中数据库账号密码正确；可在 MySQL 中执行 `SELECT COUNT(*) FROM t_user;` 验证数据是否导入成功（应为 5）
- **导入 init.sql 报 `Data too long for column ...`**：中文 Windows 下 MySQL 客户端默认按 GBK 解读 UTF-8 文件导致。加 `--default-character-set=utf8mb4` 重新导入即可
- **SSE 收不到实时消息**：浏览器自动降级为 15 秒轮询，功能不受影响
