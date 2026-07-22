# 快速开始

## 安装 CLI 工具

SkillHub 提供多种 CLI 工具，任选其一即可管理技能包：

### 方式一：ClawHub CLI（兼容 OpenClaw，npx 直接运行）

```bash
# 配置 SkillHub 注册中心地址
export CLAWHUB_REGISTRY=https://skill.xfyun.cn

# 搜索技能包
npx clawhub search email

# 安装技能包
npx clawhub install my-skill

# 发布技能包
npx clawhub publish ./my-skill
```

### 方式二：SkillHub CLI（npm 全局安装）

```bash
# 安装 CLI
npm install -g @astron-team/skillhub

# 搜索技能包（--registry 指定注册中心地址）
skillhub search email --registry https://skill.xfyun.cn

# 安装技能包
skillhub install my-skill --registry https://skill.xfyun.cn
```

### 方式三：SkillHub CLI（npx 直接运行，无需安装）

```bash
# 搜索技能包（--registry 指定注册中心地址）
npx @astron-team/skillhub@latest search email --registry https://skill.xfyun.cn

# 安装技能包
npx @astron-team/skillhub@latest install my-skill --registry https://skill.xfyun.cn
```

## 发布第一个技能包

### 使用 CLI 工具发布（推荐）

1. **准备技能包**

创建一个简单的技能包目录：

```
my-skill/
├── skill.md          # 技能描述
├── package.json      # 元数据
└── scripts/          # 脚本文件
    └── main.py
```

2. **使用 CLI 发布**

```bash
# 配置注册中心
export CLAWHUB_REGISTRY=https://skill.xfyun.cn

# 发布到默认命名空间
npx clawhub publish ./my-skill

# 发布到指定命名空间
npx clawhub publish ./my-skill --namespace my-team
```

3. **等待安全扫描**

发布后，Skill Scanner 会自动扫描技能包，检测潜在的安全问题：
- 恶意代码检测
- 敏感信息泄露
- 依赖漏洞扫描
- 行为分析

扫描结果会显示在技能包详情页。

4. **等待审核**（如果命名空间开启了审核）

管理员会收到通知，审核通过后技能包正式发布。

### 使用 Web UI 发布

1. 访问 https://skill.xfyun.cn/dashboard/publish
2. 选择命名空间（如果没有，先创建一个）
3. 上传 zip 文件
4. 选择可见性（PUBLIC / PRIVATE / INTERNAL）
5. 点击「发布」

## 搜索和下载技能包

### 使用 CLI 工具

```bash
# 搜索技能包
npx clawhub search pdf

# 安装技能包
npx clawhub install pdf-parser

# 安装指定命名空间的技能包
npx clawhub install my-team--pdf-parser
```

### 使用 Web UI

1. 访问 https://skill.xfyun.cn/search
2. 输入关键词搜索
3. 点击技能包查看详情
4. 点击「下载」或复制安装命令
