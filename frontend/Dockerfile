# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies (cached unless package files change)
COPY package.json package-lock.json* ./
RUN npm install

# Build the production bundle
COPY . .
RUN npm run build

# ---- Runtime stage (nginx) ----
FROM nginx:1.27-alpine AS runtime

# SPA + API-proxy config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static build output
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]