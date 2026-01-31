FROM node:20-alpine AS builder
WORKDIR /fe
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
ENV VITE_API_BASE=http://localhost:7000
ENV VITE_C06_URL=http://localhost:3000
RUN npm run build

FROM node:20-alpine
WORKDIR /app
RUN npm install -g serve
COPY --from=builder /fe/dist ./dist
EXPOSE 5173
CMD ["serve", "-s", "dist", "-l", "5173"]
