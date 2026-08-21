FROM node:24-bookworm-slim
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev
COPY src ./src
RUN mkdir -p /app/data
EXPOSE 8080
ENV DB_PATH=/app/data/parking.sqlite
CMD ["node","src/server.js"]
