# Model benchmarks

**No benchmark has been run.** Benchmarks need the iQOO 7. Models offered: Qwen3 0.6B/4B, Qwen3.5 0.8B/2B/4B, Gemma 4 E2B/E4B (Apache-2.0, LiteRT-LM). Qwen3.5 9B (6 GB file) is not offered: the 40 %-of-RAM rule and ~9–10 GB expected peak exceed the 8 GB physical RAM.
Plan: on the phone, run the same 5 coding prompts (HTML calculator, Python CSV, bug fix, algorithm comparison, JSON-protocol compliance) on each installed model, recording tokens/s, peak RAM, temperature and whether the JSON parsed. Recommendation until measured: Qwen3.5 4B or Gemma 4 E4B for coding; expect small models to fail the multi-file JSON protocol sometimes.
