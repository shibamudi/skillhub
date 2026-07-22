# Quick Start

## Install the CLI Tool

SkillHub provides multiple CLI tools. Choose any one to manage skill packages:

### Option 1: ClawHub CLI (OpenClaw-compatible, run via npx)

```bash
# Configure the SkillHub registry URL
export CLAWHUB_REGISTRY=https://xplt.sdu.edu.cn/skillhub

# Search for skill packages
npx clawhub search email

# Install a skill package
npx clawhub install my-skill

# Publish a skill package
npx clawhub publish ./my-skill
```

### Option 2: SkillHub CLI (npm global install)

```bash
# Install the CLI
npm install -g @astron-team/skillhub

# Search for skill packages (--registry specifies the registry URL)
skillhub search email --registry https://xplt.sdu.edu.cn/skillhub

# Install a skill package
skillhub install my-skill --registry https://xplt.sdu.edu.cn/skillhub
```

### Option 3: SkillHub CLI (run via npx, no installation required)

```bash
# Search for skill packages (--registry specifies the registry URL)
npx @astron-team/skillhub@latest search email --registry https://xplt.sdu.edu.cn/skillhub

# Install a skill package
npx @astron-team/skillhub@latest install my-skill --registry https://xplt.sdu.edu.cn/skillhub
```

## Publish Your First Skill Package

### Publish via CLI (Recommended)

1. **Prepare the skill package**

Create a simple skill package directory:

```
my-skill/
├── skill.md          # Skill description
├── package.json      # Metadata
└── scripts/          # Script files
    └── main.py
```

2. **Publish using the CLI**

```bash
# Configure the registry
export CLAWHUB_REGISTRY=https://xplt.sdu.edu.cn/skillhub

# Publish to the default namespace
npx clawhub publish ./my-skill

# Publish to a specific namespace
npx clawhub publish ./my-skill --namespace my-team
```

3. **Wait for security scanning**

After publishing, the Skill Scanner will automatically scan the skill package for potential security issues:
- Malicious code detection
- Sensitive information leakage
- Dependency vulnerability scanning
- Behavioral analysis

Scan results are displayed on the skill package detail page.

4. **Wait for review** (if the namespace has review enabled)

Administrators will receive a notification and the skill package will be officially published once approved.

### Publish via Web UI

1. Visit https://xplt.sdu.edu.cn/skillhub/dashboard/publish
2. Select a namespace (create one first if needed)
3. Upload a zip file
4. Choose visibility (PUBLIC / PRIVATE / INTERNAL)
5. Click "Publish"

## Search and Download Skill Packages

### Using the CLI

```bash
# Search for skill packages
npx clawhub search pdf

# Install a skill package
npx clawhub install pdf-parser

# Install a skill package from a specific namespace
npx clawhub install my-team--pdf-parser
```

### Using the Web UI

1. Visit https://xplt.sdu.edu.cn/skillhub/search
2. Enter keywords to search
3. Click a skill package to view details
4. Click "Download" or copy the install command
