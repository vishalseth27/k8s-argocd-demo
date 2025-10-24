# Microservices Demo with ArgoCD - Complete Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture & Design](#architecture--design)
3. [Prerequisites](#prerequisites)
4. [Local Development Setup](#local-development-setup)
5. [Docker Setup](#docker-setup)
6. [Kubernetes Setup](#kubernetes-setup)
7. [ArgoCD GitOps Setup](#argocd-gitops-setup)
8. [Multi-Environment Setup](#multi-environment-setup)
9. [Operating the System](#operating-the-system)
10. [Troubleshooting](#troubleshooting)
11. [Best Practices](#best-practices)

---

## Overview

This project demonstrates a production-ready microservices architecture using:
- **Spring Boot 3.4.0** for microservices
- **Docker** for containerization
- **Kubernetes** for orchestration
- **ArgoCD** for GitOps continuous deployment
- **Kustomize** for environment-specific configurations

### Services

1. **user-service** (Port 8081)
   - User management CRUD operations
   - REST API endpoints for user data
   - In-memory data store (ConcurrentHashMap)

2. **order-service** (Port 8082)
   - Order management operations
   - Inter-service communication with user-service
   - REST API endpoints for orders

---

## Architecture & Design

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         GitHub Repository                    │
│                  (Source of Truth for GitOps)               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Monitors & Syncs
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                          ArgoCD                              │
│              (Continuous Deployment Engine)                  │
│  • Auto-sync enabled                                        │
│  • Self-healing                                             │
│  • Auto-prune                                               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ Deploys to
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Kubernetes Cluster (Minikube)             │
│                                                              │
│  ┌──────────────┐           ┌──────────────┐              │
│  │ Namespace:   │           │ Namespace:   │              │
│  │   argocd     │           │     dev      │              │
│  │              │           │              │              │
│  │ ArgoCD       │           │ ┌──────────┐ │              │
│  │ Components   │           │ │user-svc  │ │              │
│  │              │           │ │(Pod x1)  │ │              │
│  └──────────────┘           │ └────┬─────┘ │              │
│                             │      │       │              │
│                             │      │ HTTP  │              │
│                             │      │       │              │
│                             │ ┌────▼─────┐ │              │
│                             │ │order-svc │ │              │
│                             │ │(Pod x1)  │ │              │
│                             │ └──────────┘ │              │
│                             └──────────────┘              │
└─────────────────────────────────────────────────────────────┘
```

### Application Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       order-service                          │
│                                                              │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   REST      │───▶│    Service   │───▶│  Repository  │  │
│  │ Controller  │    │    Layer     │    │   (In-mem)   │  │
│  └─────────────┘    └──────────────┘    └──────────────┘  │
│         │                                                   │
│         │ RestTemplate                                      │
│         ▼                                                   │
│  ┌─────────────┐                                           │
│  │UserService  │                                           │
│  │   Client    │                                           │
│  └─────────────┘                                           │
└────────┬──────────────────────────────────────────────────┘
         │
         │ HTTP Request
         │ http://user-service:8081
         ▼
┌─────────────────────────────────────────────────────────────┐
│                       user-service                           │
│                                                              │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   REST      │───▶│    Service   │───▶│  Repository  │  │
│  │ Controller  │    │    Layer     │    │   (In-mem)   │  │
│  └─────────────┘    └──────────────┘    └──────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Design Principles

1. **Stateless Services**: Both services are completely stateless
   - No persistent storage dependencies
   - Can be scaled horizontally without data consistency issues
   - Session data is not stored (RESTful design)

2. **Service Discovery**: Uses Kubernetes DNS
   - Services communicate via service names (e.g., `http://user-service:8081`)
   - Kubernetes handles load balancing across pod replicas

3. **Health Monitoring**:
   - Liveness probes: Detects if container needs restart
   - Readiness probes: Determines if pod can accept traffic
   - Spring Boot Actuator provides `/actuator/health` endpoints

4. **GitOps Workflow**:
   - Git is single source of truth
   - All changes go through Git (infrastructure as code)
   - ArgoCD automatically syncs cluster state with Git

---

## Prerequisites

### Required Software

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17+ | Application runtime |
| Maven | 3.6+ | Build tool |
| Docker | 20+ | Containerization |
| kubectl | 1.28+ | Kubernetes CLI |
| Minikube | 1.30+ | Local Kubernetes |
| Git | 2.0+ | Version control |

### Installation Commands (macOS)

```bash
# Install Homebrew (if not installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install required tools
brew install openjdk@17
brew install maven
brew install docker
brew install kubectl
brew install minikube
brew install git

# Verify installations
java --version
mvn --version
docker --version
kubectl version --client
minikube version
git --version
```

### System Requirements

- **RAM**: Minimum 8GB (16GB recommended)
- **CPU**: 4 cores recommended
- **Disk**: 20GB free space
- **OS**: macOS, Linux, or Windows with WSL2

---

## Local Development Setup

### 1. Clone Repository

```bash
git clone https://github.com/vishalseth27/k8s-argocd-demo.git
cd k8s-argocd-demo
```

### 2. Build and Run user-service

```bash
cd services/user-service

# Build the application
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Or run the JAR directly
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

**Endpoints:**
- Health: http://localhost:8081/actuator/health
- List users: http://localhost:8081/api/users
- Get user: http://localhost:8081/api/users/{id}
- Create user: POST http://localhost:8081/api/users
- Update user: PUT http://localhost:8081/api/users/{id}
- Delete user: DELETE http://localhost:8081/api/users/{id}

### 3. Build and Run order-service

```bash
cd services/order-service

# Build the application
mvn clean package -DskipTests

# Run locally (with user-service URL)
USER_SERVICE_URL=http://localhost:8081 mvn spring-boot:run

# Or run the JAR directly
java -jar target/order-service-0.0.1-SNAPSHOT.jar
```

**Endpoints:**
- Health: http://localhost:8082/actuator/health
- List orders: http://localhost:8082/api/orders
- Get order: http://localhost:8082/api/orders/{id}
- Create order: POST http://localhost:8082/api/orders
- Update order: PUT http://localhost:8082/api/orders/{id}
- Delete order: DELETE http://localhost:8082/api/orders/{id}

### 4. Test Inter-Service Communication

```bash
# Create an order (will call user-service internally)
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productName": "Test Product",
    "quantity": 2,
    "totalPrice": 99.99
  }'
```

---

## Docker Setup

### Multi-Stage Dockerfile Design

Both services use identical multi-stage Dockerfiles with two stages:

**Stage 1: Builder**
- Base image: `maven:3.9.11-eclipse-temurin-17`
- Downloads dependencies (cached layer)
- Compiles source code
- Creates executable JAR

**Stage 2: Runtime**
- Base image: `eclipse-temurin:17-jre` (non-Alpine for ARM64 compatibility)
- Runs as non-root user (`spring:spring`)
- Contains only JRE and application JAR
- Configures health checks

### Build Docker Images

```bash
# Build user-service image
cd services/user-service
docker build -t user-service:v1.0.0 .

# Build order-service image
cd services/order-service
docker build -t order-service:v1.0.0 .

# Verify images
docker images | grep -E "(user-service|order-service)"
```

### Test Docker Images Locally

```bash
# Run user-service container
docker run -d \
  --name user-service-test \
  -p 8081:8081 \
  user-service:v1.0.0

# Run order-service container (linked to user-service)
docker run -d \
  --name order-service-test \
  -p 8082:8082 \
  -e USER_SERVICE_URL=http://host.docker.internal:8081 \
  order-service:v1.0.0

# Test health endpoints
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

# Cleanup
docker stop user-service-test order-service-test
docker rm user-service-test order-service-test
```

### Load Images into Minikube

```bash
# Load images into Minikube's Docker daemon
minikube image load user-service:v1.0.0
minikube image load order-service:v1.0.0

# Verify images are in Minikube
minikube image ls | grep -E "(user-service|order-service)"
```

**Why load images?** Minikube runs in its own Docker environment. Loading images makes them available without needing a remote registry.

---

## Kubernetes Setup

### Architecture Overview

```
k8s/
├── base/                    # Base configurations (environment-agnostic)
│   ├── user-service/
│   │   ├── deployment.yaml  # Deployment spec with 2 replicas
│   │   ├── service.yaml     # ClusterIP service on port 8081
│   │   └── kustomization.yaml
│   └── order-service/
│       ├── deployment.yaml  # Deployment spec with 2 replicas
│       ├── service.yaml     # ClusterIP service on port 8082
│       └── kustomization.yaml
└── overlays/                # Environment-specific overrides
    └── dev/
        └── kustomization.yaml  # Dev patches (1 replica, namespace)
```

### Kubernetes Manifests Explained

#### Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  labels:
    app: user-service
    version: v1.0.0
spec:
  replicas: 2  # Base configuration (overridden in overlays)
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
        version: v1.0.0
    spec:
      containers:
      - name: user-service
        image: user-service:v1.0.0
        imagePullPolicy: Never  # Use local image (Minikube)
        ports:
        - containerPort: 8081
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "default"

        # Liveness probe: Restart if unhealthy
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 40  # Wait for startup
          periodSeconds: 10         # Check every 10s
          timeoutSeconds: 3
          failureThreshold: 3       # Restart after 3 failures

        # Readiness probe: Remove from service if not ready
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3

        # Resource management
        resources:
          requests:
            memory: "512Mi"  # Guaranteed resources
            cpu: "500m"
          limits:
            memory: "1Gi"    # Maximum allowed
            cpu: "1000m"
```

**Key Configuration Points:**

1. **imagePullPolicy: Never**: Uses local images (required for Minikube)
2. **Health Probes**:
   - Liveness: Detects frozen/deadlocked containers
   - Readiness: Prevents routing traffic to unready pods
3. **Resource Limits**: Prevents resource exhaustion
4. **Labels**: Used for service selection and organization

#### Service Manifest

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  labels:
    app: user-service
spec:
  type: ClusterIP  # Internal-only service
  ports:
  - port: 8081           # Service port
    targetPort: 8081     # Container port
    protocol: TCP
    name: http
  selector:
    app: user-service    # Selects matching pods
```

**Service Types:**
- **ClusterIP** (used): Internal-only, no external access
- **NodePort**: Exposes on node IP:port
- **LoadBalancer**: Cloud provider load balancer
- **ExternalName**: DNS CNAME record

### Kustomize Configuration

**Base Kustomization:**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - deployment.yaml
  - service.yaml

commonLabels:
  app.kubernetes.io/name: user-service
  app.kubernetes.io/component: backend
  app.kubernetes.io/part-of: microservices-demo
```

**Dev Overlay:**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: dev  # Deploy to 'dev' namespace

resources:
  - ../../base/user-service
  - ../../base/order-service

commonLabels:
  environment: dev
  managed-by: argocd

# Environment-specific patches
patches:
  - target:
      kind: Deployment
      name: user-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 1  # Reduce replicas for dev
  - target:
      kind: Deployment
      name: order-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 1
```

### Start Minikube

```bash
# Start Minikube with adequate resources
minikube start --driver=docker --cpus=4 --memory=6144

# Verify cluster is running
minikube status
kubectl cluster-info
kubectl get nodes
```

### Create Namespaces

```bash
# Create namespaces
kubectl create namespace dev
kubectl create namespace argocd

# Verify
kubectl get namespaces
```

### Deploy Applications (Manual - Before ArgoCD)

```bash
# Preview what will be deployed
kubectl kustomize k8s/overlays/dev

# Apply manifests
kubectl apply -k k8s/overlays/dev

# Check deployment status
kubectl get pods -n dev
kubectl get svc -n dev

# Watch pods come up
kubectl get pods -n dev -w
```

### Access Services

```bash
# Port-forward user-service
kubectl port-forward -n dev svc/user-service 8081:8081 &

# Port-forward order-service
kubectl port-forward -n dev svc/order-service 8082:8082 &

# Test services
curl http://localhost:8081/api/users
curl http://localhost:8082/actuator/health
```

---

## ArgoCD GitOps Setup

### What is GitOps?

GitOps is a deployment methodology where:
1. **Git is the single source of truth** for infrastructure and applications
2. **Automated processes** sync cluster state with Git
3. **Declarative configuration** defines desired state
4. **Version control** provides audit trail and rollback capability

### ArgoCD Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      ArgoCD Components                       │
│                                                              │
│  ┌────────────────┐         ┌────────────────┐            │
│  │  API Server    │◀───────▶│   Web UI       │            │
│  │  (argocd-      │         │   (Dashboard)  │            │
│  │   server)      │         └────────────────┘            │
│  └────────┬───────┘                                        │
│           │                                                 │
│           ▼                                                 │
│  ┌────────────────┐         ┌────────────────┐            │
│  │  Application   │◀───────▶│  Repo Server   │            │
│  │  Controller    │         │  (Git sync)    │            │
│  │  (Reconcile)   │         └────────────────┘            │
│  └────────┬───────┘                                        │
│           │                                                 │
│           ▼                                                 │
│  ┌────────────────────────────────────┐                   │
│  │       Kubernetes API Server        │                   │
│  └────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

### Install ArgoCD

```bash
# Install ArgoCD
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for all pods to be ready
kubectl wait --for=condition=available --timeout=300s \
  -n argocd deployment/argocd-server

# Verify installation
kubectl get pods -n argocd
```

### Access ArgoCD UI

```bash
# Get admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo

# Port-forward ArgoCD server
kubectl port-forward svc/argocd-server -n argocd 8080:443 &

# Access UI at: https://localhost:8080
# Username: admin
# Password: (from command above)
```

### ArgoCD Application Manifest

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: microservices-demo
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io  # Cleanup on delete
spec:
  project: default

  # Source: Git repository
  source:
    repoURL: https://github.com/vishalseth27/k8s-argocd-demo.git
    targetRevision: HEAD  # Track main branch
    path: k8s/overlays/dev  # Path to Kustomize overlay

  # Destination: Kubernetes cluster
  destination:
    server: https://kubernetes.default.svc  # In-cluster
    namespace: dev

  # Sync policy: How to keep cluster in sync
  syncPolicy:
    automated:
      prune: true       # Delete resources removed from Git
      selfHeal: true    # Revert manual changes
      allowEmpty: false # Prevent empty sync

    syncOptions:
      - CreateNamespace=false  # Namespace must exist
      - PrunePropagationPolicy=foreground
      - PruneLast=true  # Prune after other operations

    retry:
      limit: 5          # Retry failed syncs
      backoff:
        duration: 5s
        factor: 2       # Exponential backoff
        maxDuration: 3m

  revisionHistoryLimit: 10  # Keep 10 versions for rollback
```

### Deploy Application to ArgoCD

```bash
# Apply ArgoCD Application manifest
kubectl apply -f argocd/application-microservices.yaml

# Check application status
kubectl get applications -n argocd

# Watch sync progress
kubectl get applications -n argocd -w

# Check deployed resources
kubectl get pods -n dev
```

### ArgoCD CLI (Optional)

```bash
# Install ArgoCD CLI
brew install argocd

# Login
argocd login localhost:8080

# List applications
argocd app list

# Get application details
argocd app get microservices-demo

# Sync manually
argocd app sync microservices-demo

# View sync history
argocd app history microservices-demo
```

### GitOps Workflow in Action

```bash
# 1. Make a change in Git
vim k8s/base/user-service/deployment.yaml
# Change replicas from 2 to 3

# 2. Commit and push
git add k8s/base/user-service/deployment.yaml
git commit -m "Scale user-service to 3 replicas"
git push

# 3. Watch ArgoCD detect and sync change (automatic)
kubectl get applications -n argocd -w

# 4. Verify new pods
kubectl get pods -n dev -l app=user-service

# 5. View change in ArgoCD UI
# https://localhost:8080 -> microservices-demo -> Details
```

**Auto-sync timing:**
- Default: 3 minutes polling interval
- Webhook: Instant (requires webhook configuration)

---

## Multi-Environment Setup

### Environment Strategy

Recommended environment structure:
- **dev**: Development environment (1 replica, fewer resources)
- **staging**: Pre-production testing (2 replicas, production-like)
- **prod**: Production environment (3+ replicas, full resources)

### Directory Structure for Multiple Environments

```
k8s/
├── base/                      # Shared base configurations
│   ├── user-service/
│   └── order-service/
└── overlays/
    ├── dev/                   # Development
    │   ├── kustomization.yaml
    │   ├── namespace.yaml
    │   └── patches/
    │       └── replicas.yaml
    ├── staging/               # Staging
    │   ├── kustomization.yaml
    │   ├── namespace.yaml
    │   ├── ingress.yaml       # External access
    │   └── patches/
    │       ├── replicas.yaml
    │       └── resources.yaml
    └── prod/                  # Production
        ├── kustomization.yaml
        ├── namespace.yaml
        ├── ingress.yaml
        ├── hpa.yaml           # Horizontal Pod Autoscaler
        ├── pdb.yaml           # Pod Disruption Budget
        └── patches/
            ├── replicas.yaml
            ├── resources.yaml
            └── security.yaml
```

### Create Staging Environment

```bash
# Create directory
mkdir -p k8s/overlays/staging

# Create namespace manifest
cat > k8s/overlays/staging/namespace.yaml <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: staging
  labels:
    environment: staging
    managed-by: argocd
EOF

# Create kustomization
cat > k8s/overlays/staging/kustomization.yaml <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: staging

resources:
  - namespace.yaml
  - ../../base/user-service
  - ../../base/order-service

commonLabels:
  environment: staging
  managed-by: argocd

patches:
  # Staging uses 2 replicas
  - target:
      kind: Deployment
      name: user-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 2
  - target:
      kind: Deployment
      name: order-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 2

  # Increase resources for staging
  - target:
      kind: Deployment
      name: user-service
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: "768Mi"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: "1.5Gi"

  - target:
      kind: Deployment
      name: order-service
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: "768Mi"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: "1.5Gi"

# Change image pull policy for staging (use registry)
images:
  - name: user-service
    newTag: v1.0.0
  - name: order-service
    newTag: v1.0.0
EOF
```

### Create Production Environment

```bash
# Create directory
mkdir -p k8s/overlays/prod

# Create namespace
cat > k8s/overlays/prod/namespace.yaml <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: prod
  labels:
    environment: production
    managed-by: argocd
EOF

# Create Horizontal Pod Autoscaler
cat > k8s/overlays/prod/hpa.yaml <<EOF
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: user-service-hpa
  namespace: prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
  namespace: prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
EOF

# Create Pod Disruption Budget
cat > k8s/overlays/prod/pdb.yaml <<EOF
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: user-service-pdb
  namespace: prod
spec:
  minAvailable: 2  # At least 2 pods must be available
  selector:
    matchLabels:
      app: user-service
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service-pdb
  namespace: prod
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: order-service
EOF

# Create kustomization
cat > k8s/overlays/prod/kustomization.yaml <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: prod

resources:
  - namespace.yaml
  - ../../base/user-service
  - ../../base/order-service
  - hpa.yaml
  - pdb.yaml

commonLabels:
  environment: production
  managed-by: argocd

patches:
  # Production uses minimum 3 replicas (HPA manages actual count)
  - target:
      kind: Deployment
      name: user-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 3
  - target:
      kind: Deployment
      name: order-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 3

  # Production resources
  - target:
      kind: Deployment
      name: user-service
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: "1Gi"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: "2Gi"
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/cpu
        value: "1000m"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/cpu
        value: "2000m"

  - target:
      kind: Deployment
      name: order-service
    patch: |-
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/memory
        value: "1Gi"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/memory
        value: "2Gi"
      - op: replace
        path: /spec/template/spec/containers/0/resources/requests/cpu
        value: "1000m"
      - op: replace
        path: /spec/template/spec/containers/0/resources/limits/cpu
        value: "2000m"

images:
  - name: user-service
    newName: ghcr.io/vishalseth27/user-service
    newTag: v1.0.0
  - name: order-service
    newName: ghcr.io/vishalseth27/order-service
    newTag: v1.0.0
EOF
```

### Create ArgoCD Applications for Each Environment

```bash
# Staging Application
cat > argocd/application-staging.yaml <<EOF
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: microservices-staging
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/vishalseth27/k8s-argocd-demo.git
    targetRevision: HEAD
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
  revisionHistoryLimit: 10
EOF

# Production Application (manual sync for safety)
cat > argocd/application-prod.yaml <<EOF
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: microservices-prod
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/vishalseth27/k8s-argocd-demo.git
    targetRevision: HEAD
    path: k8s/overlays/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: prod
  syncPolicy:
    # Manual sync for production (no automated)
    syncOptions:
      - CreateNamespace=true
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
  revisionHistoryLimit: 20  # More history for production
EOF

# Deploy applications
kubectl apply -f argocd/application-staging.yaml
kubectl apply -f argocd/application-prod.yaml

# Verify
kubectl get applications -n argocd
```

### Environment-Specific Configurations

| Configuration | Dev | Staging | Prod |
|---------------|-----|---------|------|
| **Replicas** | 1 per service | 2 per service | 3-10 (HPA) |
| **Memory Request** | 512Mi | 768Mi | 1Gi |
| **Memory Limit** | 1Gi | 1.5Gi | 2Gi |
| **CPU Request** | 500m | 500m | 1000m |
| **CPU Limit** | 1000m | 1000m | 2000m |
| **Auto-scaling** | No | No | Yes (HPA) |
| **Pod Disruption Budget** | No | Optional | Yes |
| **Image Source** | Local (Minikube) | Registry | Registry |
| **Sync Policy** | Automated | Automated | Manual |

### Promotion Strategy

**Typical flow:**
```
Commit → Dev (auto-deploy) → Staging (auto-deploy) → Prod (manual approval)
```

**Process:**
1. Developer pushes code to Git
2. ArgoCD auto-syncs to **dev** environment
3. After testing, merge to staging branch
4. ArgoCD auto-syncs to **staging**
5. After validation, create release tag
6. Manually sync to **prod** via ArgoCD UI

---

## Operating the System

### Daily Operations

#### Check Cluster Health

```bash
# Overall cluster status
kubectl get nodes
kubectl top nodes

# Check all pods
kubectl get pods -A

# Check specific namespace
kubectl get pods -n dev
kubectl get pods -n dev -o wide

# Check services
kubectl get svc -n dev
```

#### View Application Logs

```bash
# View logs for specific pod
kubectl logs -n dev <pod-name>

# Follow logs (tail -f)
kubectl logs -n dev <pod-name> -f

# View logs from all replicas
kubectl logs -n dev -l app=user-service --tail=100

# View previous container logs (after crash)
kubectl logs -n dev <pod-name> --previous
```

#### Exec into Pod

```bash
# Get shell in pod
kubectl exec -n dev -it <pod-name> -- /bin/sh

# Run command in pod
kubectl exec -n dev <pod-name> -- curl http://localhost:8081/actuator/health
```

#### Scale Services

```bash
# Manual scaling
kubectl scale deployment user-service -n dev --replicas=3

# Check replica status
kubectl get deployments -n dev

# Verify new pods
kubectl get pods -n dev -l app=user-service
```

#### Update Application

```bash
# Method 1: Update via Git (GitOps way)
# 1. Update manifest in k8s/overlays/dev/
# 2. Commit and push
git add k8s/
git commit -m "Update configuration"
git push
# 3. ArgoCD syncs automatically

# Method 2: Manual image update
kubectl set image deployment/user-service \
  user-service=user-service:v1.1.0 -n dev

# Method 3: Edit deployment directly (not recommended)
kubectl edit deployment user-service -n dev
```

#### Rollback Deployment

```bash
# View rollout history
kubectl rollout history deployment/user-service -n dev

# Rollback to previous version
kubectl rollout undo deployment/user-service -n dev

# Rollback to specific revision
kubectl rollout undo deployment/user-service -n dev --to-revision=2

# Check rollout status
kubectl rollout status deployment/user-service -n dev
```

### ArgoCD Operations

#### Sync Application

```bash
# Sync via CLI
argocd app sync microservices-demo

# Sync specific resource
argocd app sync microservices-demo --resource apps:Deployment:user-service

# Hard refresh (ignore cache)
argocd app sync microservices-demo --force
```

#### Diff and Preview

```bash
# See what would change
argocd app diff microservices-demo

# Preview sync (dry-run)
argocd app sync microservices-demo --dry-run
```

#### Rollback via ArgoCD

```bash
# View history
argocd app history microservices-demo

# Rollback to previous version
argocd app rollback microservices-demo <revision-id>
```

#### Manage Sync Policy

```bash
# Disable auto-sync
argocd app set microservices-demo --sync-policy none

# Enable auto-sync
argocd app set microservices-demo --sync-policy automated

# Enable self-heal
argocd app set microservices-demo --self-heal

# Enable prune
argocd app set microservices-demo --auto-prune
```

### Monitoring and Debugging

#### Check Resource Usage

```bash
# Node resources
kubectl top nodes

# Pod resources
kubectl top pods -n dev

# Specific pod
kubectl top pod <pod-name> -n dev
```

#### Describe Resources

```bash
# Get detailed pod information
kubectl describe pod <pod-name> -n dev

# Check events
kubectl get events -n dev --sort-by='.lastTimestamp'

# Describe deployment
kubectl describe deployment user-service -n dev
```

#### Debug Failed Pods

```bash
# Check pod status
kubectl get pod <pod-name> -n dev -o yaml

# Check events
kubectl describe pod <pod-name> -n dev

# Common issues and checks:

# 1. ImagePullBackOff
kubectl describe pod <pod-name> -n dev | grep -A 10 Events

# 2. CrashLoopBackOff
kubectl logs <pod-name> -n dev --previous

# 3. Pending (resource constraints)
kubectl describe pod <pod-name> -n dev | grep -A 10 Events

# 4. Health check failures
kubectl logs <pod-name> -n dev
kubectl describe pod <pod-name> -n dev | grep Liveness
```

### Backup and Disaster Recovery

#### Backup ArgoCD State

```bash
# Export all ArgoCD applications
kubectl get applications -n argocd -o yaml > argocd-apps-backup.yaml

# Export ArgoCD settings
kubectl get configmap argocd-cm -n argocd -o yaml > argocd-cm-backup.yaml
kubectl get secret argocd-secret -n argocd -o yaml > argocd-secret-backup.yaml
```

#### Backup Application Data

```bash
# Export all resources in namespace
kubectl get all -n dev -o yaml > dev-resources-backup.yaml

# Export specific resources
kubectl get deployments,services,configmaps -n dev -o yaml > dev-backup.yaml
```

#### Restore from Backup

```bash
# Restore ArgoCD applications
kubectl apply -f argocd-apps-backup.yaml

# Restore namespace resources
kubectl apply -f dev-resources-backup.yaml
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Pods Not Starting

**Symptoms:**
```bash
kubectl get pods -n dev
NAME                             READY   STATUS             RESTARTS   AGE
user-service-xxxx-yyyy           0/1     ImagePullBackOff   0          2m
```

**Diagnosis:**
```bash
kubectl describe pod user-service-xxxx-yyyy -n dev
```

**Common Causes:**

**a) Image not found in Minikube:**
```bash
# Solution: Load image into Minikube
minikube image load user-service:v1.0.0

# Verify
minikube image ls | grep user-service

# Restart deployment
kubectl rollout restart deployment user-service -n dev
```

**b) Wrong imagePullPolicy:**
```yaml
# For local images, ensure:
imagePullPolicy: Never
```

**c) Image tag mismatch:**
```bash
# Check deployment image
kubectl get deployment user-service -n dev -o yaml | grep image:

# Update if needed
kubectl set image deployment/user-service user-service=user-service:v1.0.0 -n dev
```

#### 2. Health Check Failures

**Symptoms:**
```bash
kubectl get pods -n dev
NAME                             READY   STATUS    RESTARTS   AGE
user-service-xxxx-yyyy           0/1     Running   3          5m
```

**Diagnosis:**
```bash
kubectl describe pod user-service-xxxx-yyyy -n dev
# Look for: "Readiness probe failed" or "Liveness probe failed"

kubectl logs user-service-xxxx-yyyy -n dev
```

**Solutions:**

**a) Slow startup - increase initialDelaySeconds:**
```yaml
readinessProbe:
  initialDelaySeconds: 60  # Increase from 30
```

**b) Health endpoint not responding:**
```bash
# Test health endpoint inside pod
kubectl exec -n dev user-service-xxxx-yyyy -- curl http://localhost:8081/actuator/health

# Check application logs for errors
kubectl logs -n dev user-service-xxxx-yyyy
```

**c) Incorrect health path:**
```yaml
# Ensure correct path
readinessProbe:
  httpGet:
    path: /actuator/health/readiness  # Not just /health
    port: 8081
```

#### 3. Service Communication Failures

**Symptoms:**
```bash
# order-service can't reach user-service
curl http://localhost:9082/api/orders
# Returns error or timeout
```

**Diagnosis:**
```bash
# Check if services exist
kubectl get svc -n dev

# Check service endpoints
kubectl get endpoints -n dev

# Test DNS resolution from order-service pod
kubectl exec -n dev order-service-xxxx-yyyy -- nslookup user-service

# Test connectivity
kubectl exec -n dev order-service-xxxx-yyyy -- curl http://user-service:8081/actuator/health
```

**Solutions:**

**a) Service selector mismatch:**
```bash
# Check service selector
kubectl get svc user-service -n dev -o yaml | grep -A 5 selector

# Check pod labels
kubectl get pods -n dev --show-labels | grep user-service

# Labels must match!
```

**b) Wrong service name in environment variable:**
```yaml
# order-service deployment should have:
env:
- name: USER_SERVICE_URL
  value: "http://user-service:8081"  # Service name + namespace's DNS
```

**c) Network policy blocking:**
```bash
# Check network policies
kubectl get networkpolicies -n dev

# Temporarily remove to test
kubectl delete networkpolicy <name> -n dev
```

#### 4. ArgoCD Sync Failures

**Symptoms:**
```bash
kubectl get applications -n argocd
NAME                 SYNC STATUS   HEALTH STATUS
microservices-demo   OutOfSync     Degraded
```

**Diagnosis:**
```bash
# Check application details
argocd app get microservices-demo

# View sync errors
kubectl describe application microservices-demo -n argocd
```

**Common Causes:**

**a) Invalid Kustomize syntax:**
```bash
# Test locally
kubectl kustomize k8s/overlays/dev

# Fix errors in kustomization.yaml
```

**b) Git repository access issues:**
```bash
# Check ArgoCD can access repo
argocd repo list

# Test repo connection
argocd repo get https://github.com/vishalseth27/k8s-argocd-demo.git
```

**c) Resource quotas exceeded:**
```bash
# Check quotas
kubectl get resourcequota -n dev

# Check resource usage
kubectl top nodes
kubectl top pods -n dev
```

**d) Manual sync needed:**
```bash
# If auto-sync disabled or failed
argocd app sync microservices-demo --force
```

#### 5. Resource Constraints

**Symptoms:**
```bash
kubectl get pods -n dev
NAME                             READY   STATUS    RESTARTS   AGE
user-service-xxxx-yyyy           0/1     Pending   0          10m
```

**Diagnosis:**
```bash
kubectl describe pod user-service-xxxx-yyyy -n dev
# Look for: "Insufficient memory" or "Insufficient cpu"
```

**Solutions:**

**a) Reduce resource requests:**
```yaml
resources:
  requests:
    memory: "256Mi"  # Reduce from 512Mi
    cpu: "250m"      # Reduce from 500m
```

**b) Increase Minikube resources:**
```bash
# Stop Minikube
minikube stop

# Start with more resources
minikube start --cpus=4 --memory=8192

# Verify
kubectl top nodes
```

**c) Delete unused resources:**
```bash
# Delete old pods
kubectl delete pod <old-pod> -n dev

# Clean up completed jobs
kubectl delete job <job-name> -n dev
```

#### 6. Minikube Issues

**Problem: Minikube won't start**
```bash
# Check status
minikube status

# Delete and recreate
minikube delete
minikube start --driver=docker --cpus=4 --memory=6144

# Check logs
minikube logs
```

**Problem: Docker daemon issues**
```bash
# Restart Docker Desktop
# Check Docker is running
docker ps

# Verify Minikube can access Docker
minikube docker-env
eval $(minikube docker-env)
```

**Problem: Images not loading**
```bash
# Use Minikube's Docker daemon
eval $(minikube docker-env)

# Build images again
docker build -t user-service:v1.0.0 services/user-service/

# Verify images
minikube image ls | grep service
```

### Debug Checklist

When troubleshooting, follow this checklist:

- [ ] Check pod status: `kubectl get pods -n dev`
- [ ] View pod logs: `kubectl logs <pod> -n dev`
- [ ] Describe pod: `kubectl describe pod <pod> -n dev`
- [ ] Check events: `kubectl get events -n dev --sort-by='.lastTimestamp'`
- [ ] Verify services: `kubectl get svc -n dev`
- [ ] Check endpoints: `kubectl get endpoints -n dev`
- [ ] Test connectivity: `kubectl exec -n dev <pod> -- curl <url>`
- [ ] Review resource usage: `kubectl top pods -n dev`
- [ ] Check ArgoCD status: `kubectl get applications -n argocd`
- [ ] Verify Minikube: `minikube status`

---

## Best Practices

### Development Best Practices

1. **Use Feature Branches**
   ```bash
   git checkout -b feature/new-endpoint
   # Make changes
   git commit -m "Add new endpoint"
   git push origin feature/new-endpoint
   # Create PR
   ```

2. **Test Locally Before Pushing**
   ```bash
   # Build and test
   mvn clean test

   # Build Docker image
   docker build -t user-service:test .

   # Test Kustomize
   kubectl kustomize k8s/overlays/dev
   ```

3. **Use Semantic Versioning**
   ```
   v1.0.0 - Initial release
   v1.0.1 - Patch (bug fix)
   v1.1.0 - Minor (new feature, backward compatible)
   v2.0.0 - Major (breaking changes)
   ```

### Kubernetes Best Practices

1. **Always Set Resource Limits**
   ```yaml
   resources:
     requests:  # Guaranteed
       memory: "512Mi"
       cpu: "500m"
     limits:    # Maximum
       memory: "1Gi"
       cpu: "1000m"
   ```

2. **Use Health Probes**
   - Liveness: Detects deadlocks, restarts container
   - Readiness: Prevents traffic to unready pods
   - Startup: Allows slow-starting containers

3. **Use Namespaces**
   - Separate environments (dev, staging, prod)
   - Apply resource quotas per namespace
   - Better organization and security

4. **Label Everything**
   ```yaml
   labels:
     app: user-service
     version: v1.0.0
     environment: dev
     component: backend
   ```

5. **Use ConfigMaps and Secrets**
   ```yaml
   # ConfigMap for non-sensitive data
   env:
   - name: LOG_LEVEL
     valueFrom:
       configMapKeyRef:
         name: app-config
         key: log.level

   # Secret for sensitive data
   - name: DB_PASSWORD
     valueFrom:
       secretKeyRef:
         name: db-secret
         key: password
   ```

### GitOps Best Practices

1. **Keep Git as Single Source of Truth**
   - Never make manual changes to cluster
   - All changes through Git commits
   - Use ArgoCD UI only for viewing

2. **Use Branch Strategy**
   ```
   main → prod
   staging → staging
   develop → dev
   ```

3. **Protect Production Branches**
   - Require PR reviews
   - Require passing CI/CD
   - Disable force push

4. **Use Manual Sync for Production**
   ```yaml
   # Production should be manual
   syncPolicy: {}  # No automated sync
   ```

5. **Monitor Sync Status**
   ```bash
   # Set up alerts
   argocd app get microservices-demo --output json | \
     jq -r '.status.sync.status'
   ```

### Security Best Practices

1. **Run as Non-Root User**
   ```dockerfile
   RUN groupadd -r spring && useradd -r -g spring spring
   USER spring:spring
   ```

2. **Use Read-Only Root Filesystem** (when possible)
   ```yaml
   securityContext:
     readOnlyRootFilesystem: true
   ```

3. **Don't Store Secrets in Git**
   - Use Sealed Secrets or External Secrets Operator
   - Or use cloud provider secret managers

4. **Scan Images for Vulnerabilities**
   ```bash
   # Use tools like Trivy
   trivy image user-service:v1.0.0
   ```

5. **Network Policies**
   ```yaml
   # Restrict traffic between pods
   apiVersion: networking.k8s.io/v1
   kind: NetworkPolicy
   metadata:
     name: allow-order-to-user
   spec:
     podSelector:
       matchLabels:
         app: user-service
     ingress:
     - from:
       - podSelector:
           matchLabels:
             app: order-service
   ```

### Monitoring Best Practices

1. **Implement Proper Logging**
   - Structured logging (JSON)
   - Log levels (DEBUG, INFO, WARN, ERROR)
   - Include correlation IDs

2. **Use Metrics**
   - Expose /metrics endpoint (Prometheus format)
   - Track request rate, latency, errors

3. **Set Up Alerts**
   - High error rate
   - Pod restarts
   - Resource exhaustion
   - Sync failures

4. **Distributed Tracing**
   - Use OpenTelemetry
   - Track requests across services

---

## Appendix

### Useful Commands Reference

#### kubectl Quick Reference

```bash
# Get resources
kubectl get pods -n dev
kubectl get all -n dev
kubectl get pods --all-namespaces

# Describe resources
kubectl describe pod <pod-name> -n dev
kubectl describe svc <service-name> -n dev

# Logs
kubectl logs <pod-name> -n dev
kubectl logs -f <pod-name> -n dev
kubectl logs <pod-name> -n dev --previous

# Execute commands
kubectl exec -it <pod-name> -n dev -- /bin/sh
kubectl exec <pod-name> -n dev -- curl localhost:8081/health

# Port forwarding
kubectl port-forward -n dev svc/<service-name> 8081:8081
kubectl port-forward -n dev <pod-name> 8081:8081

# Apply/Delete
kubectl apply -f manifest.yaml
kubectl apply -k k8s/overlays/dev
kubectl delete -f manifest.yaml
kubectl delete pod <pod-name> -n dev

# Scaling
kubectl scale deployment <name> --replicas=3 -n dev

# Rollout
kubectl rollout status deployment/<name> -n dev
kubectl rollout history deployment/<name> -n dev
kubectl rollout undo deployment/<name> -n dev

# Resource usage
kubectl top nodes
kubectl top pods -n dev

# Context and config
kubectl config get-contexts
kubectl config use-context minikube
kubectl config set-context --current --namespace=dev
```

#### ArgoCD CLI Reference

```bash
# Login
argocd login <server> --username admin --password <password>

# Applications
argocd app list
argocd app get <app-name>
argocd app sync <app-name>
argocd app history <app-name>
argocd app rollback <app-name> <revision>
argocd app delete <app-name>

# Repositories
argocd repo list
argocd repo add <repo-url>

# Projects
argocd proj list
argocd proj get default
```

#### Minikube Commands

```bash
# Cluster management
minikube start
minikube stop
minikube delete
minikube status
minikube pause
minikube unpause

# Resources
minikube dashboard
minikube service <service-name> -n dev
minikube tunnel

# Images
minikube image load <image>:<tag>
minikube image ls

# Addons
minikube addons list
minikube addons enable ingress
minikube addons enable metrics-server

# SSH
minikube ssh
```

### Configuration Templates

#### ConfigMap Template
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: dev
data:
  application.properties: |
    server.port=8081
    spring.application.name=user-service
    logging.level.root=INFO
```

#### Secret Template
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
  namespace: dev
type: Opaque
stringData:
  database-password: changeme
  api-key: your-api-key
```

#### Ingress Template
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: microservices-ingress
  namespace: dev
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: microservices.local
    http:
      paths:
      - path: /users
        pathType: Prefix
        backend:
          service:
            name: user-service
            port:
              number: 8081
      - path: /orders
        pathType: Prefix
        backend:
          service:
            name: order-service
            port:
              number: 8082
```

---

## Conclusion

This documentation covers the complete setup and operation of a production-ready microservices deployment using ArgoCD GitOps methodology. Key takeaways:

1. **GitOps Philosophy**: Git is the single source of truth
2. **Declarative Configuration**: All infrastructure as code
3. **Automated Deployment**: ArgoCD handles continuous deployment
4. **Environment Separation**: Use Kustomize overlays for different environments
5. **Observability**: Health checks, logs, and metrics are essential

For questions or issues, refer to:
- Repository: https://github.com/vishalseth27/k8s-argocd-demo
- ArgoCD Docs: https://argo-cd.readthedocs.io/
- Kubernetes Docs: https://kubernetes.io/docs/
- Spring Boot Docs: https://spring.io/projects/spring-boot
