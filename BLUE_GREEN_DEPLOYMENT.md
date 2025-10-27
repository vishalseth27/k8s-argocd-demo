# Blue-Green Deployment with Argo Rollouts

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [How Blue-Green Deployment Works](#how-blue-green-deployment-works)
- [Network-Level Details](#network-level-details)
- [Step-by-Step Implementation](#step-by-step-implementation)
- [Deployment Workflow](#deployment-workflow)
- [Testing and Verification](#testing-and-verification)
- [Rollback Procedures](#rollback-procedures)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)

---

## Overview

Blue-Green deployment is a release strategy that reduces downtime and risk by running two identical production environments called **Blue** (current version) and **Green** (new version). At any time, only one environment is live and serving production traffic.

### Key Benefits

- ✅ **Zero Downtime**: Instant traffic switch between versions
- ✅ **Easy Rollback**: Revert to previous version with a single command
- ✅ **Safe Testing**: Test new version in production-like environment before promotion
- ✅ **Risk Mitigation**: New version fully tested before receiving traffic
- ✅ **Simplified Rollback**: Old version remains running during initial rollout

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Argo Rollouts Controller                  │
│  (Manages Blue-Green deployments and traffic switching)      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │         Kubernetes Cluster           │
        │                                      │
        │  ┌────────────────────────────────┐ │
        │  │   Active Service (Production)  │ │
        │  │   - Routes production traffic  │ │
        │  │   - Stable endpoint            │ │
        │  └────────┬───────────────────────┘ │
        │           │                          │
        │  ┌────────────────────────────────┐ │
        │  │   Preview Service (Testing)    │ │
        │  │   - Routes test traffic        │ │
        │  │   - Pre-production validation  │ │
        │  └────────┬───────────────────────┘ │
        │           │                          │
        │  ┌────────┴────────┬────────────┐   │
        │  │                 │            │   │
        │  ▼                 ▼            ▼   │
        │ ┌────┐         ┌────┐      ┌────┐  │
        │ │Pod │         │Pod │      │Pod │  │
        │ │Blue│         │Blue│      │Grn │  │
        │ └────┘         └────┘      └────┘  │
        │                                      │
        └──────────────────────────────────────┘
```

### Service Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                           Rollout Resource                        │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Strategy: Blue-Green                                       │ │
│  │  - activeService: user-service                             │ │
│  │  - previewService: user-service-preview                    │ │
│  │  - autoPromotionEnabled: false                             │ │
│  │  - scaleDownDelaySeconds: 30                               │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                                │
                ┌───────────────┴───────────────┐
                ▼                               ▼
    ┌─────────────────────┐         ┌─────────────────────┐
    │  Active Service     │         │  Preview Service    │
    │  (Production)       │         │  (Testing)          │
    │                     │         │                     │
    │  Port: 8081         │         │  Port: 8081         │
    │  Type: ClusterIP    │         │  Type: ClusterIP    │
    └─────────────────────┘         └─────────────────────┘
                │                               │
                │                               │
      Selector: app=user-service       Selector: app=user-service
                rollouts-pod-template-hash       rollouts-pod-template-hash
```

---

## How Blue-Green Deployment Works

### Phase 1: Initial State (Blue is Active)

```
Production Traffic Flow:
┌──────────┐
│  Users   │
└────┬─────┘
     │
     ▼
┌─────────────────────┐
│  Active Service     │──────────┐
│  user-service       │          │
└─────────────────────┘          │
                                 │
┌─────────────────────┐          │
│  Preview Service    │          │
│  (no endpoints)     │          │
└─────────────────────┘          │
                                 │
                                 ▼
                    ┌──────────────────────┐
                    │   Blue Pods (v1.0)   │
                    │   ✓ Ready            │
                    │   ✓ Receiving Traffic│
                    └──────────────────────┘
```

**State:**
- Blue pods running v1.0.0
- Active service points to Blue
- Preview service has no endpoints
- All production traffic goes to Blue

### Phase 2: Green Deployment Started

```
Deploy v2.0.0:
┌──────────┐
│  Users   │ ◄───── Still on v1.0.0
└────┬─────┘
     │
     ▼
┌─────────────────────┐
│  Active Service     │──────────┐
│  user-service       │          │
└─────────────────────┘          │
                                 │
┌─────────────────────┐          │
│  Preview Service    │──────┐   │
│  user-service-prev  │      │   │
└─────────────────────┘      │   │
                             │   │
        ┌────────────────────┘   │
        │                        │
        ▼                        ▼
┌──────────────────┐   ┌──────────────────────┐
│ Green Pods (v2.0)│   │   Blue Pods (v1.0)   │
│ ⏳ Starting      │   │   ✓ Ready            │
│ ⏳ Health Checks │   │   ✓ Receiving Traffic│
└──────────────────┘   └──────────────────────┘
```

**State:**
- Green pods starting with v2.0.0
- Blue pods still serving production traffic
- Preview service waiting for Green to be ready
- Zero impact on production

### Phase 3: Green Ready, Awaiting Promotion

```
Both Versions Running:
┌──────────┐
│  Users   │ ◄───── Still on v1.0.0
└────┬─────┘
     │
     ▼
┌─────────────────────┐
│  Active Service     │──────────┐
│  user-service       │          │
└─────────────────────┘          │
                                 │
┌─────────────────────┐          │
│  Preview Service    │──────┐   │
│  user-service-prev  │      │   │
└─────────────────────┘      │   │
                             │   │
        ┌────────────────────┘   │
        │                        │
        ▼                        ▼
┌──────────────────┐   ┌──────────────────────┐
│ Green Pods (v2.0)│   │   Blue Pods (v1.0)   │
│ ✓ Ready          │   │   ✓ Ready            │
│ ✓ Healthy        │   │   ✓ Receiving Traffic│
│ ⏳ Preview Only  │   │   ✓ Production       │
└──────────────────┘   └──────────────────────┘
      ▲
      │
   Testing via
   Preview Port
```

**State:**
- Green pods ready and healthy
- Preview service routes to Green
- Active service still routes to Blue
- Can test v2.0.0 via preview service
- Production unaffected

**Testing Command:**
```bash
# Test Green (preview) - should return v2.0.0
curl http://localhost:9091/api/users/version

# Test Blue (production) - returns v1.0.0
curl http://localhost:9081/api/users/version
```

### Phase 4: Promotion (Traffic Switch)

```
Promote Command Issued:
┌──────────┐
│  Users   │ ◄───── Switching to v2.0.0
└────┬─────┘
     │
     ▼
┌─────────────────────┐
│  Active Service     │────────────┐
│  user-service       │            │
│  (Selector Updated) │◄───────────┼─── Atomic Switch!
└─────────────────────┘            │
                                   │
┌─────────────────────┐            │
│  Preview Service    │────────┐   │
│  user-service-prev  │        │   │
└─────────────────────┘        │   │
                               │   │
        ┌──────────────────────┴───┴──┐
        │                              │
        ▼                              ▼
┌──────────────────┐         ┌──────────────────┐
│ Green Pods (v2.0)│         │  Blue Pods (v1.0)│
│ ✓ Ready          │◄────────│  ✓ Ready         │
│ ✓ Receiving      │  Instant│  ⏳ Draining     │
│   Traffic NOW    │  Switch │    Connections   │
└──────────────────┘         └──────────────────┘
```

**State:**
- Active service selector instantly updated to Green
- Green pods now receive production traffic
- Blue pods enter termination grace period
- Preview service also points to Green
- **Zero downtime** - instant switch

**What Happens:**
1. Argo Rollouts updates Active Service selector
2. kube-proxy updates iptables rules
3. New connections route to Green
4. Existing connections on Blue complete gracefully
5. Blue pods marked for termination after delay

### Phase 5: Cleanup (Blue Termination)

```
After scaleDownDelaySeconds (30s):
┌──────────┐
│  Users   │ ◄───── On v2.0.0
└────┬─────┘
     │
     ▼
┌─────────────────────┐
│  Active Service     │──────────┐
│  user-service       │          │
└─────────────────────┘          │
                                 │
┌─────────────────────┐          │
│  Preview Service    │──────┐   │
│  user-service-prev  │      │   │
└─────────────────────┘      │   │
                             │   │
        ┌────────────────────┴───┘
        │
        ▼
┌──────────────────┐         ┌──────────────────┐
│ Green Pods (v2.0)│         │  Blue Pods (v1.0)│
│ ✓ Ready          │         │  ✗ Terminating   │
│ ✓ Receiving      │         │  ✗ Scaling Down  │
│   Traffic        │         │                  │
└──────────────────┘         └──────────────────┘
```

**State:**
- Green is sole production version
- Blue pods gracefully terminated
- Rollout marked as "Healthy"
- Ready for next deployment

---

## Network-Level Details

### How Traffic Switching Works

#### 1. Kubernetes Service Selector Update

Before Promotion:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  selector:
    app: user-service
    rollouts-pod-template-hash: 7b89c8745f  # Points to Blue
  ports:
  - port: 8081
    targetPort: 8081
```

After Promotion:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  selector:
    app: user-service
    rollouts-pod-template-hash: d9dbb495b  # Points to Green ✓
  ports:
  - port: 8081
    targetPort: 8081
```

#### 2. Endpoints Object Changes

Before:
```bash
$ kubectl get endpoints user-service -n dev
NAME           ENDPOINTS          AGE
user-service   10.244.0.18:8081   2d  # Blue pod IP
```

After:
```bash
$ kubectl get endpoints user-service -n dev
NAME           ENDPOINTS          AGE
user-service   10.244.0.19:8081   2d  # Green pod IP ✓
```

#### 3. iptables Rules Update

kube-proxy updates iptables rules on all nodes:

Before (routing to Blue):
```
-A KUBE-SERVICES -d 10.101.163.217/32 -p tcp -m tcp --dport 8081 \
   -j KUBE-SVC-USER-SERVICE

-A KUBE-SVC-USER-SERVICE -j KUBE-SEP-BLUE-POD
-A KUBE-SEP-BLUE-POD -p tcp -m tcp -j DNAT \
   --to-destination 10.244.0.18:8081
```

After (routing to Green):
```
-A KUBE-SERVICES -d 10.101.163.217/32 -p tcp -m tcp --dport 8081 \
   -j KUBE-SVC-USER-SERVICE

-A KUBE-SVC-USER-SERVICE -j KUBE-SEP-GREEN-POD
-A KUBE-SEP-GREEN-POD -p tcp -m tcp -j DNAT \
   --to-destination 10.244.0.19:8081
```

#### 4. Connection Tracking (conntrack)

Existing connections are preserved:
```
$ conntrack -L | grep 10.244.0.18
tcp  ESTABLISHED src=10.244.0.5 dst=10.244.0.18 sport=45678 dport=8081
```

New connections route to Green:
```
$ conntrack -L | grep 10.244.0.19
tcp  ESTABLISHED src=10.244.0.5 dst=10.244.0.19 sport=45679 dport=8081
```

### Pod Lifecycle During Rollout

```
Timeline of Events:

T+0s    ┌─────────────────────────────────────────┐
        │ Argo Rollouts: Create Green ReplicaSet │
        └─────────────────────────────────────────┘
                         │
T+1s    ┌────────────────▼────────────────────────┐
        │ Kubernetes: Schedule Green Pods         │
        └─────────────────────────────────────────┘
                         │
T+2s    ┌────────────────▼────────────────────────┐
        │ kubelet: Start Green Containers          │
        └─────────────────────────────────────────┘
                         │
T+32s   ┌────────────────▼────────────────────────┐
        │ Readiness Probe: Success (after 30s)    │
        │ Green Pods marked READY                  │
        └─────────────────────────────────────────┘
                         │
T+33s   ┌────────────────▼────────────────────────┐
        │ Argo Rollouts: Update Preview Service   │
        │ Preview → Green Pods                     │
        └─────────────────────────────────────────┘
                         │
        ┌────────────────▼────────────────────────┐
        │ Manual Testing Phase                     │
        │ (Test via Preview Service)               │
        └─────────────────────────────────────────┘
                         │
T+300s  ┌────────────────▼────────────────────────┐
        │ kubectl argo rollouts promote            │
        │ (Manual Promotion Command)               │
        └─────────────────────────────────────────┘
                         │
T+301s  ┌────────────────▼────────────────────────┐
        │ Argo Rollouts: Update Active Service    │
        │ Active → Green Pods (INSTANT SWITCH)     │
        └─────────────────────────────────────────┘
                         │
T+301s  ┌────────────────▼────────────────────────┐
        │ kube-proxy: Update iptables on all nodes│
        └─────────────────────────────────────────┘
                         │
T+302s  ┌────────────────▼────────────────────────┐
        │ New connections → Green                  │
        │ Existing connections → Blue (draining)   │
        └─────────────────────────────────────────┘
                         │
T+331s  ┌────────────────▼────────────────────────┐
        │ scaleDownDelaySeconds (30s) elapsed     │
        │ Send SIGTERM to Blue Pods                │
        └─────────────────────────────────────────┘
                         │
T+332s  ┌────────────────▼────────────────────────┐
        │ Blue Pods: Execute preStop hook          │
        │ (sleep 15s - drain connections)          │
        └─────────────────────────────────────────┘
                         │
T+347s  ┌────────────────▼────────────────────────┐
        │ Blue Pods: Send SIGTERM to main process │
        └─────────────────────────────────────────┘
                         │
T+377s  ┌────────────────▼────────────────────────┐
        │ terminationGracePeriodSeconds (30s)     │
        │ elapsed - Force SIGKILL if still running│
        └─────────────────────────────────────────┘
                         │
T+378s  ┌────────────────▼────────────────────────┐
        │ Blue Pods: Terminated                    │
        │ Rollout Status: Healthy                  │
        └─────────────────────────────────────────┘
```

---

## Step-by-Step Implementation

### Prerequisites

1. **Kubernetes Cluster** (Minikube, EKS, GKE, AKS)
2. **kubectl** configured
3. **Docker** installed
4. **Git** installed

### Step 1: Install Argo Rollouts Controller

```bash
# Create namespace
kubectl create namespace argo-rollouts

# Install Argo Rollouts
kubectl apply -n argo-rollouts -f \
  https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# Verify installation
kubectl get pods -n argo-rollouts
```

Expected output:
```
NAME                              READY   STATUS    RESTARTS   AGE
argo-rollouts-5c7c95d5f4-xxxxx    1/1     Running   0          30s
```

### Step 2: Install Argo Rollouts kubectl Plugin

**macOS:**
```bash
brew install argoproj/tap/kubectl-argo-rollouts
```

**Linux:**
```bash
curl -LO https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-amd64
chmod +x kubectl-argo-rollouts-linux-amd64
sudo mv kubectl-argo-rollouts-linux-amd64 /usr/local/bin/kubectl-argo-rollouts
```

**Verify:**
```bash
kubectl argo rollouts version
```

### Step 3: Create Rollout Manifest

Create `k8s/base/user-service/rollout.yaml`:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: user-service
  labels:
    app: user-service
spec:
  replicas: 2
  revisionHistoryLimit: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
        app.kubernetes.io/name: user-service
        app.kubernetes.io/component: backend
        app.kubernetes.io/part-of: microservices-demo
    spec:
      containers:
      - name: user-service
        image: user-service:v1.0.0
        imagePullPolicy: Never
        ports:
        - containerPort: 8081
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "default"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 40
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 15"]
      terminationGracePeriodSeconds: 30

  strategy:
    blueGreen:
      activeService: user-service              # Production service
      previewService: user-service-preview     # Testing service
      autoPromotionEnabled: false              # Manual promotion
      scaleDownDelaySeconds: 30                # Keep old version 30s
```

**Key Configuration:**
- `activeService`: Production service name
- `previewService`: Preview service name
- `autoPromotionEnabled: false`: Require manual promotion
- `scaleDownDelaySeconds: 30`: Wait 30s before terminating old pods

### Step 4: Create Preview Service

Create `k8s/base/user-service/service-preview.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service-preview
  labels:
    app: user-service
    role: preview
spec:
  type: ClusterIP
  ports:
  - port: 8081
    targetPort: 8081
    protocol: TCP
    name: http
  selector:
    app: user-service
    # Selector will be managed by Argo Rollouts
    # It will automatically point to preview (green) pods
```

### Step 5: Update Kustomization

Update `k8s/base/user-service/kustomization.yaml`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - rollout.yaml              # Changed from deployment.yaml
  - service.yaml
  - service-preview.yaml      # Added preview service

commonLabels:
  app.kubernetes.io/name: user-service
  app.kubernetes.io/component: backend
  app.kubernetes.io/part-of: microservices-demo
```

### Step 6: Configure Environment Overlays

Update `k8s/overlays/dev/kustomization.yaml`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: dev

resources:
  - ../../base/user-service
  - ../../base/order-service

commonLabels:
  environment: dev
  managed-by: argocd

patches:
  - target:
      kind: Rollout                    # Changed from Deployment
      name: user-service
    patch: |-
      - op: replace
        path: /spec/replicas
        value: 1
      - op: add
        path: /spec/template/metadata/labels/environment
        value: dev
      - op: add
        path: /spec/template/metadata/labels/managed-by
        value: argocd
```

### Step 7: Deploy Initial Version

```bash
# Build and load v1.0.0 image
cd services/user-service
docker build -t user-service:v1.0.0 .
minikube image load user-service:v1.0.0

# Apply Rollout
cd /path/to/project
kubectl apply -k k8s/overlays/dev

# Watch Rollout status
kubectl argo rollouts get rollout user-service -n dev --watch
```

Expected output:
```
Name:            user-service
Namespace:       dev
Status:          ✔ Healthy
Strategy:        BlueGreen
Images:          user-service:v1.0.0 (stable, active)
Replicas:
  Desired:       1
  Current:       1
  Updated:       1
  Ready:         1
  Available:     1
```

---

## Deployment Workflow

### Deploying a New Version

#### Step 1: Build New Version

```bash
# Make code changes
vi services/user-service/src/main/java/.../UserController.java

# Add version endpoint
@GetMapping("/version")
public ResponseEntity<String> getVersion() {
    return ResponseEntity.ok("v2.0.0 - Blue-Green Deployment");
}

# Build new image
cd services/user-service
docker build -t user-service:v2.0.0 .
minikube image load user-service:v2.0.0
```

#### Step 2: Update Rollout Image

```bash
# Edit rollout.yaml
vi k8s/base/user-service/rollout.yaml

# Change image version
# FROM: image: user-service:v1.0.0
# TO:   image: user-service:v2.0.0

# Apply changes
kubectl apply -k k8s/overlays/dev
```

#### Step 3: Monitor Deployment

```bash
# Watch in real-time
kubectl argo rollouts get rollout user-service -n dev --watch
```

You'll see:
```
Status:          ◌ Progressing
Message:         active service cutover pending
Images:          user-service:v1.0.0 (stable, active)
                 user-service:v2.0.0 (preview)
Replicas:
  Desired:       1
  Current:       2      # Both versions running
  Updated:       1
  Ready:         1
  Available:     1
```

#### Step 4: Verify Both Versions

```bash
# Setup port forwards
kubectl port-forward -n dev svc/user-service 9081:8081 &
kubectl port-forward -n dev svc/user-service-preview 9091:8081 &

# Test Blue (v1.0.0 - Production)
curl http://localhost:9081/api/users/version
# 404 - version endpoint doesn't exist in v1.0.0

# Test Green (v2.0.0 - Preview)
curl http://localhost:9091/api/users/version
# v2.0.0 - Blue-Green Deployment ✓
```

#### Step 5: Check Endpoints

```bash
kubectl get endpoints -n dev
```

Output:
```
NAME                    ENDPOINTS          AGE
user-service            10.244.0.18:8081   5m   # Blue (v1.0.0)
user-service-preview    10.244.0.19:8081   2m   # Green (v2.0.0)
```

This confirms traffic separation!

#### Step 6: Promote to Production

```bash
# Promote green to production
kubectl argo rollouts promote user-service -n dev
```

Watch the instant switch:
```
Status:          ◌ Progressing
Message:         updating active service
Images:          user-service:v2.0.0 (stable, active)  # ✓ Promoted!
```

#### Step 7: Verify Traffic Switch

```bash
# Check endpoints after promotion
kubectl get endpoints -n dev
```

Output:
```
NAME                    ENDPOINTS          AGE
user-service            10.244.0.19:8081   6m   # Now Green! ✓
user-service-preview    10.244.0.19:8081   3m   # Also Green
```

```bash
# Test production (should now be v2.0.0)
curl http://localhost:9081/api/users/version
# v2.0.0 - Blue-Green Deployment ✓
```

#### Step 8: Monitor Cleanup

```bash
# Watch pod status
kubectl get pods -n dev | grep user-service
```

Output:
```
user-service-7b89c8745f-xdg5h    1/1     Terminating   0    5m  # Blue
user-service-d9dbb495b-xw5mf     1/1     Running       0    3m  # Green ✓
```

After 30 seconds (scaleDownDelaySeconds):
```
user-service-d9dbb495b-xw5mf     1/1     Running       0    3m  # Green ✓
# Blue pod terminated
```

#### Step 9: Verify Final State

```bash
kubectl argo rollouts status user-service -n dev
```

Output:
```
Healthy
```

Deployment complete! ✓

---

## Testing and Verification

### Testing Checklist

#### Before Promotion

- [ ] Green pods are in Ready state
- [ ] Preview service routes to Green
- [ ] Active service still routes to Blue
- [ ] Can access Green via preview port
- [ ] Can access Blue via production port
- [ ] Health checks passing on Green
- [ ] Application logs look good on Green
- [ ] Resource usage acceptable on Green

#### During Promotion

- [ ] Monitor pod count (should be Blue + Green)
- [ ] Watch endpoints change
- [ ] Monitor error rates
- [ ] Check response times
- [ ] Verify no dropped connections

#### After Promotion

- [ ] Active service routes to Green
- [ ] Production traffic serves new version
- [ ] Blue pods terminating gracefully
- [ ] No error spikes in logs
- [ ] Metrics look healthy
- [ ] Old pods cleaned up after delay

### Verification Commands

#### Check Rollout Status

```bash
# Detailed status
kubectl argo rollouts get rollout user-service -n dev

# Simple health check
kubectl argo rollouts status user-service -n dev

# List all rollouts
kubectl argo rollouts list -n dev
```

#### Monitor Pods

```bash
# Watch pods
kubectl get pods -n dev -w

# Describe specific pod
kubectl describe pod <pod-name> -n dev

# Check logs
kubectl logs -f <pod-name> -n dev

# Check previous version logs
kubectl logs <blue-pod-name> -n dev --previous
```

#### Check Services and Endpoints

```bash
# View services
kubectl get svc -n dev

# View endpoints
kubectl get endpoints -n dev

# Describe service
kubectl describe svc user-service -n dev

# Check service selector
kubectl get svc user-service -n dev -o yaml | grep -A 10 selector
```

#### Network Testing

```bash
# From another pod
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -n dev -- \
  curl http://user-service:8081/api/users/version

# Port forward and test locally
kubectl port-forward -n dev svc/user-service 9081:8081
curl http://localhost:9081/api/users/version

# Test preview
kubectl port-forward -n dev svc/user-service-preview 9091:8081
curl http://localhost:9091/api/users/version
```

#### Check Resource Usage

```bash
# Pod resources
kubectl top pods -n dev

# Node resources
kubectl top nodes

# Detailed pod metrics
kubectl get pods -n dev -o custom-columns=\
NAME:.metadata.name,\
CPU:.spec.containers[0].resources.requests.cpu,\
MEMORY:.spec.containers[0].resources.requests.memory
```

---

## Rollback Procedures

### Instant Rollback (Before Old Version Terminated)

If you promoted to Green but want to rollback immediately:

```bash
# Rollback to Blue (if still running)
kubectl argo rollouts abort user-service -n dev

# Then promote back to previous version
kubectl argo rollouts promote user-service -n dev
```

### Rollback After Cleanup

If old version is terminated:

#### Method 1: Undo Rollout

```bash
# Undo to previous revision
kubectl argo rollouts undo user-service -n dev

# Undo to specific revision
kubectl argo rollouts undo user-service -n dev --to-revision=3
```

#### Method 2: Update Image

```bash
# Update rollout.yaml back to v1.0.0
vi k8s/base/user-service/rollout.yaml
# Change: image: user-service:v1.0.0

# Apply
kubectl apply -k k8s/overlays/dev

# Promote
kubectl argo rollouts promote user-service -n dev
```

### Emergency Rollback

If Green is failing badly:

```bash
# Abort current rollout
kubectl argo rollouts abort user-service -n dev

# Scale down Green immediately
kubectl scale rollout user-service --replicas=0 -n dev

# Redeploy v1.0.0
# Update image in rollout.yaml to v1.0.0
kubectl apply -k k8s/overlays/dev

# Promote
kubectl argo rollouts promote user-service -n dev
```

---

## Troubleshooting

### Issue 1: Rollout Stuck in "Progressing"

**Symptoms:**
```
Status:          ◌ Progressing
Message:         updated replicas are still becoming available
```

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n dev | grep user-service

# Check pod events
kubectl describe pod <pod-name> -n dev

# Check logs
kubectl logs <pod-name> -n dev
```

**Common Causes:**
1. **Readiness probe failing**
   - Check application health endpoint
   - Verify initialDelaySeconds is sufficient

2. **Image pull errors**
   - Verify image exists: `minikube image ls | grep user-service`
   - Check imagePullPolicy

3. **Resource constraints**
   - Check node resources: `kubectl top nodes`
   - Check pod resources: `kubectl top pods -n dev`

**Solutions:**
```bash
# Increase readiness probe delay
kubectl patch rollout user-service -n dev --type='json' -p='[
  {"op": "replace", "path": "/spec/template/spec/containers/0/readinessProbe/initialDelaySeconds", "value": 60}
]'

# Check image
minikube image load user-service:v2.0.0

# Add more resources if needed
kubectl patch rollout user-service -n dev --type='json' -p='[
  {"op": "replace", "path": "/spec/template/spec/containers/0/resources/requests/memory", "value": "1Gi"}
]'
```

### Issue 2: Service Selector Mismatch

**Symptoms:**
```
Status:          ✖ Degraded
Message:         Service 'user-service' has unmatch label 'app.kubernetes.io/component' in rollout
```

**Diagnosis:**
```bash
# Check service selector
kubectl get svc user-service -n dev -o yaml | grep -A 10 selector

# Check rollout labels
kubectl get rollout user-service -n dev -o yaml | grep -A 10 "template:" | grep -A 5 "labels:"
```

**Solution:**
Ensure Rollout pod labels match Service selector:

```yaml
# In rollout.yaml
spec:
  template:
    metadata:
      labels:
        app: user-service
        app.kubernetes.io/name: user-service          # Match service
        app.kubernetes.io/component: backend           # Match service
        app.kubernetes.io/part-of: microservices-demo  # Match service
```

### Issue 3: Preview Service Not Working

**Symptoms:**
- Can't access preview service
- 404 or connection refused

**Diagnosis:**
```bash
# Check preview service
kubectl get svc user-service-preview -n dev

# Check endpoints
kubectl get endpoints user-service-preview -n dev

# Test from within cluster
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -n dev -- \
  curl http://user-service-preview:8081/actuator/health
```

**Solutions:**
```bash
# Recreate preview service
kubectl delete svc user-service-preview -n dev
kubectl apply -f k8s/base/user-service/service-preview.yaml -n dev

# Check port forward
kubectl port-forward -n dev svc/user-service-preview 9091:8081

# Verify pods are ready
kubectl get pods -n dev -l app=user-service
```

### Issue 4: Old Pods Not Terminating

**Symptoms:**
- Blue pods remain running after scaleDownDelaySeconds

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n dev

# Check rollout status
kubectl argo rollouts get rollout user-service -n dev

# Check replicaset
kubectl get rs -n dev
```

**Solutions:**
```bash
# Manually delete old replicaset
kubectl delete rs <old-replicaset-name> -n dev

# Or force delete pods
kubectl delete pod <pod-name> -n dev --force --grace-period=0
```

### Issue 5: Traffic Not Switching

**Symptoms:**
- Promotion successful but still seeing old version

**Diagnosis:**
```bash
# Check service endpoints
kubectl get endpoints user-service -n dev

# Check service selector
kubectl describe svc user-service -n dev

# Check pod labels
kubectl get pods -n dev --show-labels | grep user-service
```

**Solutions:**
```bash
# Force service to update
kubectl delete endpoints user-service -n dev
# Endpoints will auto-recreate

# Restart kube-proxy (updates iptables)
kubectl delete pod -l k8s-app=kube-proxy -n kube-system

# Check from inside cluster
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -n dev -- \
  curl http://user-service:8081/api/users/version
```

### Debug Commands

```bash
# Full rollout details
kubectl argo rollouts get rollout user-service -n dev

# Rollout history
kubectl argo rollouts history user-service -n dev

# Watch logs from all pods
kubectl logs -f -l app=user-service -n dev --all-containers=true

# Describe rollout
kubectl describe rollout user-service -n dev

# Get rollout YAML
kubectl get rollout user-service -n dev -o yaml

# Check events
kubectl get events -n dev --sort-by='.lastTimestamp' | grep user-service
```

---

## Best Practices

### 1. Readiness Probes

**Always configure readiness probes:**
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness  # Use dedicated endpoint
    port: 8081
  initialDelaySeconds: 30  # Allow app to start
  periodSeconds: 5         # Check frequently
  timeoutSeconds: 3
  failureThreshold: 3      # Allow some failures
```

**Why:** Prevents traffic to unhealthy pods

### 2. Liveness Probes

**Use liveness probes to restart unhealthy containers:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 40  # Higher than readiness
  periodSeconds: 10        # Less frequent
  timeoutSeconds: 3
  failureThreshold: 3
```

**Why:** Restarts stuck containers automatically

### 3. Resource Limits

**Always set resource requests and limits:**
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

**Why:** Prevents resource starvation and ensures QoS

### 4. Graceful Shutdown

**Configure preStop hook:**
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 15"]
```

**And terminationGracePeriodSeconds:**
```yaml
terminationGracePeriodSeconds: 30
```

**Why:** Allows in-flight requests to complete

### 5. Scale Down Delay

**Set appropriate scaleDownDelaySeconds:**
```yaml
strategy:
  blueGreen:
    scaleDownDelaySeconds: 30  # Keep old version for rollback
```

**Why:** Quick rollback if issues discovered immediately

### 6. Manual Promotion

**Disable auto-promotion for production:**
```yaml
strategy:
  blueGreen:
    autoPromotionEnabled: false  # Require manual approval
```

**Why:** Allows testing before promotion

### 7. Revision History

**Keep sufficient revision history:**
```yaml
revisionHistoryLimit: 2  # Keep last 2 versions
```

**Why:** Enables easy rollback

### 8. Testing Strategy

**Test in preview environment:**
1. Smoke tests
2. Integration tests
3. Load tests (if possible)
4. Manual verification

### 9. Monitoring

**Monitor these metrics:**
- Pod count (should be old + new during rollout)
- Error rates
- Response times
- Resource usage
- Endpoint changes

### 10. Rollback Plan

**Always have a rollback plan:**
1. Document rollback commands
2. Test rollback procedures
3. Monitor for issues after promotion
4. Keep old version running briefly

---

## Summary

Blue-Green deployment with Argo Rollouts provides:

✅ **Zero Downtime** - Instant traffic switching
✅ **Safe Deployments** - Test before production promotion
✅ **Easy Rollback** - Revert with single command
✅ **Production Testing** - Preview environment identical to production
✅ **Automated Management** - Argo Rollouts handles complexity

### Key Takeaways

1. **Two Services**: Active (production) and Preview (testing)
2. **Two Versions**: Blue (current) and Green (new) run simultaneously
3. **Instant Switch**: Service selector update routes traffic atomically
4. **Manual Control**: Promotion requires explicit approval
5. **Graceful Cleanup**: Old version terminates after delay

### Next Steps

- Integrate with ArgoCD for GitOps
- Add automated analysis (metrics-based promotion)
- Implement canary deployments for gradual rollouts
- Set up Prometheus metrics for monitoring
- Create automated rollback based on error rates

---

**Generated with Argo Rollouts**
For more information: https://argo-rollouts.readthedocs.io/
