# Primary target device: iQOO 7

Official specification (not measured): Snapdragon 870, Adreno 650, 8 GB or 12 GB RAM, 128/256 GB storage, 4400 mAh, shipped with Android 11.
VISION does **not** assume which variant or Android/Funtouch version you own: the app's "This phone" screen reads the real RAM, storage, ABI and tier at runtime,
and model downloads are blocked when a model does not fit (a model may use at most 40% of RAM).

Model guidance for an 8 GB iQOO 7 (all unverified on the device until tested):
- Qwen3.5 4B mixed-int4 (2.75 GB, Apache-2.0, text only): published CPU benchmark on an 8 GB Pixel 8a is ~6 tokens/s, ~4.4 GB peak memory. Expect similar or slightly slower on a Snapdragon 870.
- Qwen3.5 2B int8 (2.1 GB) is the safer daily choice; 0.8B for speed.
- Qwen3.5 9B (6.0 GB) is blocked by the 40%-of-RAM safety rule even on 12 GB; not offered.
- Gemma 4 E4B (multimodal, ~3 GB) is published for LiteRT-LM but its Android GPU/CPU file variants and the image API are not yet verified here; not offered.
