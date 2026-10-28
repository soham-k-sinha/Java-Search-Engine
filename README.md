# UMass Off-Campus Housing Search Engine

Finding off-campus housing at UMass Amherst is a mess. After sophomore year, most students get pushed off campus into a market scattered across a dozen different websites, each with different formats, different update schedules, and no way to search across them. The [official UMass housing site](https://offcampushousing.umass.edu/) only lists landlords who opted in. The rest of the market lives on property management company websites like Kamins, individual landlord pages, and Facebook groups that the university [explicitly warns against](https://www.umass.edu/dean-students/off-campus-life/faqs) due to scams. Amherst Police have issued warnings after students lost money to rental fraud.

This project crawls those sources, normalizes the listings into a single schema, deduplicates across sites, and lets you search everything from one place. The crawler, inverted index, ranking, and HTTP server are all written from scratch in Java, with no search library and no web framework.

## How It Works

```
              ┌──────────── CrawlManager (5-thread ExecutorService) ────────────┐
              │                                                                  │
   UMass Off-Campus   Zumper (5 towns)   Craigslist   Kamins Real Estate   Valley PM
              │              │               │               │               │
              └──── per-source adapters: fetch (rate-limited) → parse → Document ┘
                                             │
                          normalize + deduplicate across sources
                                             │
                        InvertedIndex (TF-IDF) ── serialized to data/index.ser
                                             │
                   Server (raw ServerSocket, 8-thread pool) → JSON API + static UI
```

- **Concurrent multi-source crawling.** Each housing site gets its own adapter (a `Callable<List<Document>>`) because they all have different HTML structures. `CrawlManager` submits every adapter to a five-thread `ExecutorService` so sources are fetched at the same time, and each adapter sleeps between requests (500–1200 ms depending on the site) so we don't hammer anyone's server.
- **Schema normalization.** One site says "2BR/1BA", another says "bedrooms: 2", another buries it in a paragraph. Regex-based parsers pull everything into one `Document` model: price, bedrooms, bathrooms, address, lease terms, amenities, contact info, and source URL.
- **Cross-source deduplication.** The same apartment can show up on the UMass site *and* Kamins *and* Zumper. Duplicates are detected with a key built from the normalized address (street suffixes and directions abbreviated, unit numbers and ZIP codes stripped), the bedroom count, and a $100 price bucket. When two listings collide, the one with more descriptive text wins.
- **Ranked search.** A hand-written inverted index (term → postings with term frequency) with stop-word filtering and TF-IDF scoring. The index is built once per crawl and serialized to disk, so queries never wait on the network.
- **Raw-socket HTTP server.** `Server.java` binds a `ServerSocket`, hands each connection to a fixed thread pool, parses the request line and query string by hand, and returns JSON, including CORS headers.

## Sources

| Source | Adapter | Notes |
|---|---|---|
| UMass Off-Campus Housing | `UMassLocalAdapter` | Parses saved listing pages in `data/umass-html/`. A live crawler (`UMassAdapter`) exists but isn't wired in yet; it doesn't work reliably against the live site. |
| Zumper | `ZumperAdapter` | Amherst, Hadley, Sunderland, Northampton, Belchertown |
| Craigslist | `CraigslistAdapter` | Amherst-area apartments |
| Kamins Real Estate | `KaminsAdapter` | Paginated residential rentals |
| Valley Property Management | `ValleyPMAdapter` | List page + individual listing pages for details |

## Numbers

From the last committed crawl (commit `eb54060`):

- **~358 raw listings** across the 5 sources (280 from the saved UMass pages alone)
- **241 unique listings** after cross-source deduplication: UMass 163, Zumper 40, Craigslist 22, Kamins 10, Valley PM 6
- **1,274 unique terms** in the inverted index
- **Index lookups average ~0.03 ms** (worst ~1.2 ms over 1,600 warmed-up queries); search requests came back in **under 8 ms** in my end-to-end benchmarks

Listing counts change every time the sites update, so a fresh crawl will give different numbers.

## API

| Endpoint | Description |
|---|---|
| `GET /` | Search UI (`SearchEngine/static/index.html`) |
| `GET /search?q=<query>&limit=<n>` | Ranked results as JSON (default limit 10) |
| `GET /listing/<id>` | A single listing as JSON |

## Running It

Requires Java 21+. Jsoup is vendored in `SearchEngine/lib/`.

```bash
# compile
javac -cp SearchEngine/lib/jsoup-1.23.2.jar -d out SearchEngine/*.java

# crawl all sources, build the index, and start the server on :8080 (or $PORT)
java -cp out:SearchEngine/lib/jsoup-1.23.2.jar SearchEngine.CrawlManager
```

Then open http://localhost:8080 or try `curl "http://localhost:8080/search?q=2+bedroom+hadley"`.

### Docker

```bash
docker build -t housing-search .
docker run -p 8080:8080 housing-search
```

The multi-stage Dockerfile compiles on a JDK image and runs on a JRE image. This is how the project was deployed on Railway.

## Project Structure

```
SearchEngine/
  CrawlManager.java        # entry point: runs adapters concurrently, dedupes, indexes, starts server
  *Adapter.java            # one crawler/parser per source
  Document.java            # normalized listing model
  InvertedIndex.java       # tokenizer, postings, TF-IDF scoring, serialization
  Server.java              # raw-socket HTTP server + JSON API
  static/index.html        # search UI
  lib/jsoup-1.23.2.jar
Learning/                  # concurrency and socket experiments from before the real build
data/umass-html/           # saved UMass listing pages
Dockerfile
```

## Built With

- **Java 21**: core language, no frameworks for the crawling, indexing, or serving
- **Java HttpClient**: HTTP requests
- **Jsoup**: HTML parsing and text extraction
- **ExecutorService**: thread pools for crawling and request handling
- **java.net.ServerSocket**: the HTTP server
- **Docker**: containerized deployment

## Status

Working end to end: crawl → normalize → dedupe → index → search over HTTP.

- [x] Java fundamentals + concurrency (ExecutorService, thread safety, race conditions)
- [x] HTTP networking + HTML parsing
- [x] Search engine theory (inverted indexes, TF-IDF)
- [x] Core listing model and schema
- [x] Inverted index implementation
- [x] TF-IDF scoring and query engine
- [x] Per-source crawl adapters (5 sources)
- [x] Cross-source deduplication
- [x] Index persistence (serialized to disk)
- [x] HTTP server + JSON API, written on raw sockets
- [x] Docker deployment (Railway)
- [x] Benchmarks

Next up:

- [ ] Live UMass crawling (handle the session/cookie flow instead of saved pages)
- [ ] Structured filters (max price, bedrooms) alongside keyword search
- [ ] Scheduled re-crawls and change detection (new listings, price drops, removed listings)
- [ ] Unit tests (JUnit) for parsers, deduplication, and scoring

## Why I Built This

I'm a CS student who used Python and AI tools for everything. This project is me learning to build systems from scratch in a language I'm not comfortable in, understanding concurrency beyond async/await, and solving a problem I'm personally dealing with as a UMass student looking for housing.

Every line of code is written by me. AI tools are used for learning concepts and reviewing my code, not for generating implementations.
