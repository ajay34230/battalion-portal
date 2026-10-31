# Model benchmarks

**No benchmark has been run.** Benchmarks need the iQOO 7. Models offered: Qwen3 0.6B/4B, Qwen3.5 0.8B/2B/4B, Gemma 4 E2B/E4B (Apache-2.0, LiteRT-LM). Qwen3.5 9B (6 GB file) is offered as **experimental** only (you confirm a warning): the 40 %-of-RAM rule and ~9–10 GB expected peak exceed 8 GB physical RAM, so it may be slow or be killed. If it fails to load, VISION marks it bad and falls back to the next smaller model automatically. Not measured on a phone.
Plan: on the phone, run the same 5 coding prompts (HTML calculator, Python CSV, bug fix, algorithm comparison, JSON-protocol compliance) on each installed model, recording tokens/s, peak RAM, temperature and whether the JSON parsed. Recommendation until measured: Qwen3.5 4B or Gemma 4 E4B for coding; expect small models to fail the multi-file JSON protocol sometimes.

## Added after live check (31 Oct 2026)
Verified against Hugging Face (open licence, anonymous download, size and SHA-256 read from the LFS record): Qwen3 1.7B (Apache-2.0, 1.75 GB), IBM Granite 4.0 350M (Apache-2.0, 0.47 GB), Gemma 4 12B (Apache-2.0, 6.9 GB, experimental). Not added: Gemma 3 / 3n (need a Hugging Face login and licence acceptance, so an in-app download would fail), Phi-4 mini (3.9 GB exceeds the RAM rule on 8 GB), Qwen3 14B (8.7 GB), Nanbeige 4.2 3B (too new/untested). Nothing here has been benchmarked on a phone.
