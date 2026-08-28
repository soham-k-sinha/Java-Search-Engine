# UMass Off-Campus Housing Search Engine

Finding off-campus housing at UMass Amherst is a mess. After sophomore year, most students get pushed off campus into a market scattered across a dozen different websites, each with different formats, different update schedules, and no way to search across them. The [official UMass housing site](https://offcampushousing.umass.edu/) only lists landlords who opted in. The rest of the market lives on property management company websites like Kamins, individual landlord pages, and Facebook groups that the university [explicitly warns against](https://www.umass.edu/dean-students/off-campus-life/faqs) due to scams. Amherst Police have issued warnings after students lost money to rental fraud.

This project crawls all of those sources, normalizes the listings into a single schema, deduplicates across sites, and lets you search everything from one place.

## What This Actually Does

- **Concurrent multi-source crawling** - each housing site gets its own adapter because they all have different HTML structures. A thread pool fetches from all sources simultaneously with per-domain rate limiting so we don't get blocked.
- **Schema normalization** - one site says "2BR/1BA", another says "bedrooms: 2", another buries it in a paragraph. Everything gets parsed into structured fields: price, bedrooms, bathrooms, address, lease term, proximity to campus, bus route access.
- **Cross-source deduplication** - the same apartment on North Pleasant Street shows up on the UMass site AND Kamins AND another aggregator. We detect duplicates through address normalization, price matching, and fuzzy field comparison.
- **Ranked search** - an inverted index with TF-IDF scoring lets you search "2 bedroom under 1500 near engineering" and get ranked results from every source.
- **Change detection** - continuous monitoring detects new listings, price drops, and removed listings by diffing against previous crawl state.

## Built With

- **Java** - core language, no frameworks for the crawling/indexing engine
- **Jsoup** - HTML parsing and text extraction
- **Java HttpClient** - HTTP requests with connection management
- **ExecutorService** - thread pool management for concurrent crawling
- **ConcurrentHashMap / ConcurrentLinkedQueue** - thread-safe data structures
- **JUnit** - test suite
- **Docker** - containerized deployment
- **Javalin** - lightweight REST API for search queries


## Status

🚧 **In progress.** Building this from scratch to learn Java, concurrency, and systems design. Committing daily.

- [x] Java fundamentals + concurrency (ExecutorService, thread safety, race conditions)
- [x] HTTP networking + HTML parsing
- [x] Search engine theory (inverted indexes, TF-IDF)
- [x] Core listing model and schema
- [ ] Inverted index implementation
- [ ] TF-IDF scoring and query engine
- [ ] Per-source crawl adapters
- [ ] Cross-source deduplication
- [ ] Persistence layer
- [ ] REST API
- [ ] Docker deployment
- [ ] Benchmarks and optimization

## Why I Built This

I'm a CS student who used Python and AI tools for everything. This project is me learning to build systems from scratch in a language I'm not comfortable in, understanding concurrency beyond async/await, and solving a problem I'm personally dealing with as a UMass student looking for housing.

Every line of code is written by me. AI tools are used for learning concepts and reviewing my code, not for generating implementations.