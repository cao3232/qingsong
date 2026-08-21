# ---------- 构建阶段 ----------
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json ./
RUN npm install

COPY . .
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
# 只做 vite 构建，跳过 vue-tsc 类型检查，保证 Docker 构建稳定
RUN npm run build-only

# ---------- 运行阶段 ----------
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
