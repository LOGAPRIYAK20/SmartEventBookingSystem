FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN mkdir -p out && javac -d out $(find src -name "*.java")

EXPOSE 10000

CMD ["sh", "-c", "java -cp out WebMain ${PORT:-10000}"]