FROM eclipse-temurin:21-jdk AS build

WORKDIR /app
COPY SearchEngine/ SearchEngine/

RUN javac -cp "SearchEngine/lib/jsoup-1.23.2.jar" SearchEngine/*.java

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/SearchEngine/*.class SearchEngine/
COPY SearchEngine/lib/ SearchEngine/lib/
COPY SearchEngine/static/ SearchEngine/static/
COPY data/umass-html/ data/umass-html/

RUN mkdir -p data/listings

EXPOSE 8080

CMD ["java", "-cp", ".:SearchEngine/lib/jsoup-1.23.2.jar", "SearchEngine.CrawlManager"]
