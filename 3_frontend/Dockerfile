# Frontend de EspacioYa (React + Vite) servido por nginx. Contexto de build: 3_frontend/
#   docker build -t espacioya-frontend 3_frontend
# VITE_API_URL vacía: el navegador llama a /api/... en el mismo origen y el ALB lo manda al backend.

# --- Compilación -------------------------------------------------------------------------------
FROM node:24-alpine AS compilacion
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
ENV VITE_API_URL=""
RUN npm run build

# --- Servidor ----------------------------------------------------------------------------------
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacion /build/dist /usr/share/nginx/html
EXPOSE 80
