# ============================================================
# vue-frontend 운영 이미지 : 빌드(Node) → 정적 서빙(nginx, 8080, 비루트)
#   docker build -t flowlog/vue-frontend .
#   docker run -p 127.0.0.1:8080:8080 flowlog/vue-frontend
#   Gateway : WEB_SERVICE_URI=http://{이 컨테이너}:8080
# ============================================================
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
