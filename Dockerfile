# Stage 1: Build Java 專案
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# 先複製 pom.xml 下載 dependency（利用 Docker 快取機制加快後續 build 速度）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 複製程式碼並打包 JAR
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: 運行 JRE 輕量環境
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 複製打包好的 jar 檔
COPY --from=builder /app/target/*.jar app.jar

# 暴露 8080 Port
EXPOSE 8080

# 啟動命令
ENTRYPOINT ["java", "-jar", "app.jar"]
