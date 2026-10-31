# Internet programming research

Sources actually queried: GitHub repository search API (licence, stars, last push, archived flag), Stack Exchange API (Stack Overflow), MDN search API; plus the general web search in the assistant (SearXNG/Brave/DuckDuckGo/Wikipedia). All keyless, official APIs, user-agent set, few results per call to respect rate limits (GitHub unauthenticated: ~10 searches/min).
Results are **untrusted text**: instruction-like phrases are neutralised by `Untrusted.sanitize`, nothing found online can trigger an action, and each result carries warnings (archived, stale >2 years, unclear licence → "do not copy code").
Not built: reading full documentation pages for code questions, issue-tracker search, academic paper search for code, caching docs for offline use. VISION does not search "the whole Internet".
