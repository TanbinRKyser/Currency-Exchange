# Spring Boot Microservices on Kubernetes

A hands‑on reference implementation of two Spring Boot microservices containerized with Docker and deployed to Kubernetes.  

## Table of Contents

- [Project Overview](#project-overview)  
- [Architecture](#architecture)  
- [Prerequisites](#prerequisites)  
- [Getting Started](#getting-started)  
  - [Clone the Repository](#clone-the-repository)  
  - [Run Locally with Maven](#run-locally-with-maven)  
- [Dockerizing the Services](#dockerizing-the-services)  
- [Deploying to Kubernetes](#deploying-to-kubernetes)  
- [Project Structure](#project-structure)  
- [Technologies](#technologies)  
- [Contributing](#contributing)  
- [License](#license)  
- [Author](#author)  

---

## Project Overview

This repository contains two independent Spring Boot microservices demonstrating:

- **Currency Exchange Service**: exposes exchange rates between currency pairs  
- **Currency Conversion Service**: calls the exchange service to calculate converted amounts  

Both services are packaged as Docker containers and designed to be deployed on a Kubernetes cluster. :contentReference[oaicite:0]{index=0}  

---

## Architecture

┌──────────────────────────┐ ┌────────────────────────────┐
│ Currency Conversion Svc │ ──▶ │ Currency Exchange Svc (Eureka) │
└──────────────────────────┘ └────────────────────────────┘
| ▲
│ │
└─────────► External Clients ───────┘

- **Currency Conversion Service** retrieves exchange rates from  
  **Currency Exchange Service** and computes final amounts.  
- Both services can be independently scaled and updated. 

---

## Prerequisites

- **Java 11+**  
- **Apache Maven 3.6+**  
- **Docker CLI**  
- **kubectl** (to interact with your Kubernetes cluster)  

---

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/TanbinRKyser/Spring-boot-microservices-k8s.git
cd Spring-boot-microservices-k8s
# In one terminal:
cd currency-exchange-service
mvn clean spring-boot:run

# In another terminal:
cd ../currency-conversion-microservice
mvn clean spring-boot:run


Exchange service listens on localhost:8000 by default.

Conversion service listens on localhost:8100 by default.

# Build exchange service image
cd currency-exchange-service
docker build -t <your-dockerhub-username>/currency-exchange:latest .

# Build conversion service image
cd ../currency-conversion-microservice
docker build -t <your-dockerhub-username>/currency-conversion:latest .


docker push <your-dockerhub-username>/currency-exchange:latest
docker push <your-dockerhub-username>/currency-conversion:latest

1. Create a namespace (optional):
kubectl create namespace spring-microservices

2. Apply Deployment & Service manifests
kubectl apply -f k8s/currency-exchange-deployment.yaml -n spring-microservices
kubectl apply -f k8s/currency-exchange-service.yaml    -n spring-microservices

kubectl apply -f k8s/currency-conversion-deployment.yaml -n spring-microservices
kubectl apply -f k8s/currency-conversion-service.yaml    -n spring-microservices

3. Verify pods and services:
kubectl get pods,svc -n spring-microservices

4. Test the setup via port-forwarding or an Ingress:
kubectl port-forward svc/currency-conversion 8100:8100 -n spring-microservices
# then in another terminal:
curl http://localhost:8100/convert?from=USD&to=INR&quantity=100


