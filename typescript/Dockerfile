FROM node:22-slim AS build

WORKDIR /app

COPY package.json package-lock.json tsconfig.json ./

RUN npm ci

COPY src ./src

RUN npm run build

FROM node:22-slim AS runtime

WORKDIR /app

ENV NODE_ENV=production

RUN addgroup --system confident && adduser --system --ingroup confident confident

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=build /app/dist ./dist
RUN chown -R confident:confident /app

USER confident

CMD ["node", "dist/main.js"]
