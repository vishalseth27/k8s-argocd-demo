# Microservices Demo with ArgoCD

This project demonstrates a GitOps workflow using two stateless Spring Boot microservices deployed to Kubernetes with ArgoCD.

## Project Structure

```
microservices-demo/
├── services/           # Spring Boot microservices
│   ├── user-service/   # User management service (port 8081)
│   └── order-service/  # Order processing service (port 8082)
├── k8s/                # Kubernetes manifests
│   ├── base/           # Base Kustomize configurations
│   │   ├── user-service/
│   │   └── order-service/
│   └── overlays/       # Environment-specific overlays
│       └── dev/        # Development environment
├── argocd/             # ArgoCD Application manifests
└── README.md           # This file
```

## Services

### user-service
- Port: 8081
- Endpoints:
  - `GET /api/users` - List all users
  - `GET /api/users/{id}` - Get user by ID
  - `GET /actuator/health` - Health check

### order-service
- Port: 8082
- Endpoints:
  - `GET /api/orders` - List all orders
  - `GET /api/orders/{id}` - Get order by ID
  - `GET /actuator/health` - Health check
- Communicates with user-service for user validation

## Tech Stack

- **Language**: Java 25
- **Framework**: Spring Boot 3.2.x
- **Build Tool**: Maven 3.9.11
- **Container**: Docker 28.5.1
- **Orchestration**: Kubernetes (Minikube 1.37.0)
- **GitOps**: ArgoCD
- **Deployment**: Kustomize

## Prerequisites

- Java 17+
- Maven 3.x
- Docker
- kubectl
- Minikube
- Git

## Getting Started

### 1. Build Services Locally
```bash
cd services/user-service
mvn spring-boot:run

cd ../order-service
mvn spring-boot:run
```

### 2. Build Docker Images
```bash
cd services/user-service
docker build -t user-service:v1.0.0 .

cd ../order-service
docker build -t order-service:v1.0.0 .
```

### 3. Start Minikube
```bash
minikube start
minikube addons enable ingress
```

### 4. Deploy with ArgoCD
```bash
# Install ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Deploy applications
kubectl apply -f argocd/user-service-dev.yaml
kubectl apply -f argocd/order-service-dev.yaml
```

## Development Workflow

1. Make code changes in `services/`
2. Build and push Docker image
3. Update image tag in `k8s/` manifests
4. Commit and push to Git
5. ArgoCD automatically syncs changes to Kubernetes

## License

MIT
