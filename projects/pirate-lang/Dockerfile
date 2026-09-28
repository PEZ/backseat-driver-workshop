FROM clojure:temurin-21-tools-deps

WORKDIR /app

COPY deps.edn .
RUN clojure -P

EXPOSE 7888
