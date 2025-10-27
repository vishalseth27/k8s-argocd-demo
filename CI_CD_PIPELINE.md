# CI/CD Pipeline & Deployment Process

## Table of Contents
- [Current Manual Process](#current-manual-process)
- [What's Missing](#whats-missing)
- [Production CI/CD Pipeline](#production-cicd-pipeline)
- [Image Registry Strategy](#image-registry-strategy)
- [Complete Workflow](#complete-workflow)
- [Implementation Guide](#implementation-guide)
- [GitOps with ArgoCD](#gitops-with-argocd)

---

## Current Manual Process

### What You Have Now

```
Your Current Workflow (Manual):

1. Code Change
   └─> Edit Java files locally

2. Build JAR
   └─> mvn clean package

3. Build Docker Image
   └─> docker build -t user-service:v1.0.0 .

4. Load to Minikube
   └─> minikube image load user-service:v1.0.0

5. Update YAML
   └─> Edit k8s/base/user-service/rollout.yaml
   └─> Change image: user-service:v1.0.0 → v2.0.0

6. Apply to Cluster
   └─> kubectl apply -k k8s/overlays/dev

7. Promote (if using Argo Rollouts)
   └─> kubectl argo rollouts promote user-service -n dev
```

### Current Configuration

**In your rollout.yaml:**
```yaml
spec:
  template:
    spec:
      containers:
      - name: user-service
        image: user-service:v1.0.0
        imagePullPolicy: Never    # ← Key: Don't pull from registry!
```

**What `imagePullPolicy: Never` means:**
- Image MUST exist locally in Minikube
- No external registry needed
- Good for local development
- **NOT suitable for production**

---

## What's Missing

### Gap Analysis

```
Current State:              Production Needs:
┌──────────────────┐       ┌──────────────────┐
│ Manual builds    │  vs   │ Automated CI/CD  │
│ Local images     │  vs   │ Image registry   │
│ Manual deploy    │  vs   │ GitOps           │
│ No versioning    │  vs   │ Semantic versions│
│ No testing       │  vs   │ Automated tests  │
│ Single env       │  vs   │ Multi-env        │
└──────────────────┘       └──────────────────┘
```

### What Needs to Be Added

1. **CI/CD Pipeline** (GitHub Actions, Jenkins, GitLab CI)
2. **Container Registry** (Docker Hub, ECR, GCR, ACR)
3. **Automated Tests** (Unit, Integration, E2E)
4. **Version Tagging Strategy**
5. **ArgoCD Integration** (Already installed, needs configuration)
6. **Image Scanning** (Security vulnerabilities)
7. **Deployment Automation**

---

## Production CI/CD Pipeline

### Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Developer Workflow                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  1. Git Push     │
                    │  (dev branch)    │
                    └──────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CI Pipeline (GitHub Actions)                  │
├─────────────────────────────────────────────────────────────────┤
│  Step 1: Checkout Code                                          │
│  Step 2: Run Tests (Unit, Integration)                          │
│  Step 3: Build JAR (mvn package)                                │
│  Step 4: Build Docker Image                                     │
│  Step 5: Scan Image (Trivy, Snyk)                              │
│  Step 6: Tag Image (user-service:dev-abc123)                   │
│  Step 7: Push to Registry (Docker Hub, ECR)                     │
│  Step 8: Update K8s YAML with new tag                          │
│  Step 9: Commit & Push updated YAML                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ArgoCD (GitOps)                               │
├─────────────────────────────────────────────────────────────────┤
│  - Watches Git repository                                        │
│  - Detects YAML changes                                         │
│  - Pulls new image from registry                                │
│  - Applies to dev namespace                                     │
│  - Performs blue-green rollout                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  Dev Environment │
                    │  (Kubernetes)    │
                    └──────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  Tests Pass?      │
                    └─────────┬─────────┘
                              │ Yes
                              ▼
                    ┌──────────────────┐
                    │  2. Pull Request │
                    │  (dev → main)    │
                    └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  3. Code Review  │
                    │  & Approval      │
                    └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  4. Merge to     │
                    │     main         │
                    └──────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                CI Pipeline (main branch)                         │
├─────────────────────────────────────────────────────────────────┤
│  Same steps as before, but:                                     │
│  - Tag: user-service:staging-abc123                             │
│  - Update k8s/overlays/staging                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ Staging Env      │
                    │ (Auto-deployed)  │
                    └──────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │  QA Testing?      │
                    │  Perf Tests?      │
                    └─────────┬─────────┘
                              │ Pass
                              ▼
                    ┌──────────────────┐
                    │  5. Create       │
                    │     Release Tag  │
                    │     (v1.0.0)     │
                    └──────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                CI Pipeline (tag)                                 │
├─────────────────────────────────────────────────────────────────┤
│  - Tag: user-service:v1.0.0 (production tag)                    │
│  - Also tag: user-service:latest                                │
│  - Update k8s/overlays/prod                                     │
│  - Create GitHub Release                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  6. Manual       │
                    │     Approval     │
                    │     (for prod)   │
                    └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │  Production      │
                    │  Deployment      │
                    │  (Blue-Green)    │
                    └──────────────────┘
```

---

## Image Registry Strategy

### Registry Options

| Registry | Use Case | Cost | Features |
|----------|----------|------|----------|
| **Docker Hub** | Public images, small teams | Free (public), $7/mo (private) | Easy, popular, rate limits |
| **GitHub Container Registry (ghcr.io)** | GitHub users | Free (with GitHub) | Integrated with GitHub Actions |
| **AWS ECR** | AWS infrastructure | $0.10/GB stored | AWS native, IAM integration |
| **GCP GCR/Artifact Registry** | GCP infrastructure | $0.10/GB stored | GCP native |
| **Azure ACR** | Azure infrastructure | $5/mo + storage | Azure native |
| **Harbor** | Self-hosted | Infrastructure cost | Full control, vulnerability scanning |

### Recommended: GitHub Container Registry (ghcr.io)

**Why:**
- Free for public and private repos
- Integrated with GitHub Actions
- Good security
- Automatic cleanup policies
- Works with your existing GitHub repo

**Image naming convention:**
```
ghcr.io/vishalseth27/user-service:dev-abc123
ghcr.io/vishalseth27/user-service:staging-abc123
ghcr.io/vishalseth27/user-service:v1.0.0
ghcr.io/vishalseth27/user-service:latest
```

---

## Complete Workflow

### Environment-Specific Image Tags

```
Development:
├─ Branch: dev
├─ Tag: user-service:dev-{git-sha}
├─ Example: user-service:dev-a1b2c3d
└─ Purpose: Latest development changes

Staging:
├─ Branch: main
├─ Tag: user-service:staging-{git-sha}
├─ Example: user-service:staging-x7y8z9a
└─ Purpose: Pre-production testing

Production:
├─ Tag: v1.0.0
├─ Tag: user-service:v1.0.0
├─ Also: user-service:latest
└─ Purpose: Production releases
```

### Where Docker Builds Happen

**Current (Manual):**
```bash
# On your local machine
cd services/user-service
docker build -t user-service:v1.0.0 .
minikube image load user-service:v1.0.0
```

**Production (Automated):**
```yaml
# In GitHub Actions runner (cloud VM)
- name: Build Docker Image
  run: |
    docker build -t ghcr.io/${{ github.repository_owner }}/user-service:${{ github.sha }} \
      services/user-service

- name: Push to Registry
  run: |
    docker push ghcr.io/${{ github.repository_owner }}/user-service:${{ github.sha }}
```

### Where Images Are Stored

**Current:**
```
Your Machine:
├─ Docker Desktop cache
└─ Minikube VM cache
   └─ Images loaded with: minikube image load
```

**Production:**
```
Container Registry:
├─ ghcr.io/vishalseth27/user-service:dev-a1b2c3d
├─ ghcr.io/vishalseth27/user-service:staging-x7y8z9a
├─ ghcr.io/vishalseth27/user-service:v1.0.0
└─ ghcr.io/vishalseth27/user-service:latest

Kubernetes Cluster:
├─ Pulls from registry on deployment
├─ Caches in node's local storage
└─ Managed by kubelet
```

---

## Implementation Guide

### Step 1: Setup Container Registry (GitHub)

**Enable GitHub Container Registry:**

1. Go to GitHub repo settings
2. Enable "Package" permissions
3. Create a Personal Access Token (PAT) with `write:packages` permission

**Or use GitHub Actions automatic token:**
```yaml
# In GitHub Actions, this is automatic:
env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}
```

### Step 2: Create CI/CD Pipeline

**File: `.github/workflows/ci-cd.yaml`**

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [dev, main]
    tags:
      - 'v*'
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # Job 1: Build and Test
  build-and-test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [user-service, order-service]

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

      - name: Run tests
        run: |
          cd services/${{ matrix.service }}
          mvn clean test

      - name: Build JAR
        run: |
          cd services/${{ matrix.service }}
          mvn clean package -DskipTests

      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: ${{ matrix.service }}-jar
          path: services/${{ matrix.service }}/target/*.jar

  # Job 2: Build and Push Docker Images
  build-and-push-image:
    needs: build-and-test
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [user-service, order-service]

    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Log in to Container Registry
        uses: docker/login-action@v2
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v4
        with:
          images: ${{ env.REGISTRY }}/${{ github.repository_owner }}/${{ matrix.service }}
          tags: |
            # Branch-based tags
            type=ref,event=branch
            # PR tags
            type=ref,event=pr
            # Version tags (v1.0.0)
            type=semver,pattern={{version}}
            # SHA tags
            type=sha,prefix={{branch}}-

      - name: Build and push Docker image
        uses: docker/build-push-action@v4
        with:
          context: services/${{ matrix.service }}
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}

      - name: Run Trivy security scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.REGISTRY }}/${{ github.repository_owner }}/${{ matrix.service }}:${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'

  # Job 3: Update Kubernetes Manifests
  update-k8s-manifests:
    needs: build-and-push-image
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Determine environment
        id: env
        run: |
          if [[ "${{ github.ref }}" == "refs/heads/dev" ]]; then
            echo "environment=dev" >> $GITHUB_OUTPUT
          elif [[ "${{ github.ref }}" == "refs/heads/main" ]]; then
            echo "environment=staging" >> $GITHUB_OUTPUT
          elif [[ "${{ github.ref }}" =~ ^refs/tags/v ]]; then
            echo "environment=prod" >> $GITHUB_OUTPUT
          fi

      - name: Update image tags in Kustomize
        run: |
          cd k8s/overlays/${{ steps.env.outputs.environment }}

          # Update user-service image
          kustomize edit set image \
            user-service=${{ env.REGISTRY }}/${{ github.repository_owner }}/user-service:${{ github.sha }}

          # Update order-service image
          kustomize edit set image \
            order-service=${{ env.REGISTRY }}/${{ github.repository_owner }}/order-service:${{ github.sha }}

      - name: Commit and push changes
        run: |
          git config --global user.name "GitHub Actions"
          git config --global user.email "actions@github.com"
          git add k8s/overlays/${{ steps.env.outputs.environment }}
          git commit -m "Update ${{ steps.env.outputs.environment }} images to ${{ github.sha }}"
          git push

  # Job 4: Notify
  notify:
    needs: [build-and-test, build-and-push-image, update-k8s-manifests]
    runs-on: ubuntu-latest
    if: always()

    steps:
      - name: Send Slack notification
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          text: 'Deployment to ${{ steps.env.outputs.environment }} completed!'
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

### Step 3: Update Rollout Manifests

**Change from:**
```yaml
spec:
  template:
    spec:
      containers:
      - name: user-service
        image: user-service:v1.0.0
        imagePullPolicy: Never     # ← Remove this
```

**To:**
```yaml
spec:
  template:
    spec:
      containers:
      - name: user-service
        image: ghcr.io/vishalseth27/user-service:latest
        imagePullPolicy: Always    # ← Always pull from registry
```

### Step 4: Configure ArgoCD Applications

**File: `argocd/applications/user-service-dev.yaml`**

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: user-service-dev
  namespace: argocd
spec:
  project: default

  source:
    repoURL: https://github.com/vishalseth27/k8s-argocd-demo.git
    targetRevision: dev
    path: k8s/overlays/dev

  destination:
    server: https://kubernetes.default.svc
    namespace: dev

  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

**File: `argocd/applications/user-service-staging.yaml`**

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: user-service-staging
  namespace: argocd
spec:
  project: default

  source:
    repoURL: https://github.com/vishalseth27/k8s-argocd-demo.git
    targetRevision: main
    path: k8s/overlays/staging

  destination:
    server: https://kubernetes.default.svc
    namespace: staging

  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

---

## GitOps with ArgoCD

### How ArgoCD Works

```
┌─────────────────────────────────────────────────────────────┐
│                    Git Repository                            │
│  (Single Source of Truth)                                   │
│                                                              │
│  k8s/overlays/dev/kustomization.yaml                        │
│  └─> image: ghcr.io/user/user-service:dev-abc123           │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ ArgoCD polls every 3 minutes
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    ArgoCD Controller                         │
│                                                              │
│  1. Detects change in Git                                   │
│  2. Compares with cluster state                             │
│  3. Shows "Out of Sync"                                     │
│  4. Auto-sync (if enabled)                                  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│                                                              │
│  1. Pull new image from registry                            │
│  2. Create new pods (Green)                                 │
│  3. Wait for readiness                                      │
│  4. Switch traffic (Blue → Green)                           │
│  5. Terminate old pods (Blue)                               │
└─────────────────────────────────────────────────────────────┘
```

### ArgoCD UI Workflow

1. **View Applications:**
   ```
   https://localhost:8080
   Username: admin
   Password: <from kubectl get secret>
   ```

2. **Application Status:**
   ```
   user-service-dev
   ├─ Sync Status: Synced
   ├─ Health Status: Healthy
   ├─ Last Sync: 2 minutes ago
   └─ Resources:
      ├─ Rollout: Healthy
      ├─ Service: Healthy
      └─ Service (preview): Healthy
   ```

3. **Manual Sync:**
   ```
   Click "Sync" → "Synchronize"
   ArgoCD applies changes
   Monitor rollout progress
   ```

---

## Local Development vs Production

### Comparison

| Aspect | Local (Current) | Production |
|--------|----------------|------------|
| **Image Build** | Manual `docker build` | Automated CI pipeline |
| **Image Storage** | Minikube cache | Container Registry (ghcr.io) |
| **Image Pull** | Never (imagePullPolicy: Never) | Always from registry |
| **Deployment** | Manual `kubectl apply` | ArgoCD GitOps |
| **Testing** | Manual | Automated in pipeline |
| **Versioning** | Ad-hoc | Semantic versioning |
| **Rollback** | Manual kubectl | Argo Rollouts automated |
| **Monitoring** | kubectl commands | Prometheus, Grafana |

### Migration Path

**Phase 1: Add Container Registry**
```
Week 1:
- Setup GitHub Container Registry
- Configure authentication
- Test pushing images manually
```

**Phase 2: Add Basic CI**
```
Week 2:
- Create GitHub Actions workflow
- Automated build on push
- Push to registry
- No deployment yet (still manual)
```

**Phase 3: Add CD with ArgoCD**
```
Week 3:
- Create ArgoCD applications
- Configure auto-sync
- Test GitOps workflow
```

**Phase 4: Full Automation**
```
Week 4:
- Add automated tests
- Add security scanning
- Add multi-environment
- Add notifications
```

---

## Quick Start Guide

### For Local Development (Keep Current Workflow)

```bash
# 1. Code change
vi services/user-service/src/main/...

# 2. Build and test locally
cd services/user-service
mvn clean package
docker build -t user-service:dev-$(git rev-parse --short HEAD) .

# 3. Load to Minikube
minikube image load user-service:dev-$(git rev-parse --short HEAD)

# 4. Update and apply
vi k8s/base/user-service/rollout.yaml
kubectl apply -k k8s/overlays/dev
```

### For Production Deployment (Future)

```bash
# 1. Code change and commit
git add .
git commit -m "Add new feature"
git push origin dev

# 2. CI/CD pipeline automatically:
#    - Runs tests
#    - Builds Docker image
#    - Pushes to ghcr.io
#    - Updates k8s YAML
#    - Commits changes

# 3. ArgoCD automatically:
#    - Detects YAML change
#    - Pulls new image
#    - Deploys to dev

# 4. Verify in ArgoCD UI
open https://localhost:8080

# 5. Promote to staging (if tests pass)
git checkout main
git merge dev
git push origin main

# 6. Create production release
git tag v1.0.0
git push origin v1.0.0
```

---

## Summary

### Current State

✅ **What works:**
- Local development with Minikube
- Docker builds
- Manual deployments
- Argo Rollouts configured

❌ **What's missing:**
- Container registry
- CI/CD pipeline
- Automated testing
- GitOps automation
- Multi-environment deployment

### Next Steps

**Immediate (Keep developing):**
1. Use current manual workflow
2. Test features locally
3. Commit to Git

**Short-term (Add CI/CD):**
1. Setup GitHub Container Registry
2. Create GitHub Actions workflow
3. Automate image builds and pushes

**Medium-term (Add GitOps):**
1. Configure ArgoCD applications
2. Enable auto-sync
3. Test blue-green deployments

**Long-term (Full Production):**
1. Add automated tests
2. Add security scanning
3. Add monitoring/alerting
4. Add multi-region deployment

### Key Takeaway

> Your current manual process is **perfect for learning and development**.
>
> For production, you need:
> - **Container Registry** to store images
> - **CI/CD Pipeline** to automate builds
> - **GitOps (ArgoCD)** to automate deployments

---

**Generated for Microservices Demo Project**
Repository: https://github.com/vishalseth27/k8s-argocd-demo
