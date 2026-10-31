# Gmail sign-in setup (one time, about 10 minutes)

VISION reads Gmail **read-only** (`gmail.readonly`) to find payment mails. Google requires an OAuth client that only you can create in your own Google Cloud project; nothing in this repo can do it for you.

1. Open https://console.cloud.google.com, create a project (any name).
2. APIs & Services → Library → enable **Gmail API**.
3. APIs & Services → OAuth consent screen → External → add yourself (ajay34230@gmail.com) as a **Test user**. Add scope `.../auth/gmail.readonly`. Keep the app in *Testing* (no Google review needed for your own account; Google expires test-mode consent after 7 days, so you re-approve weekly).
4. Credentials → Create credentials → OAuth client ID → **Android**.
   - Package name: `com.vision.assistant`
   - SHA-1: `6D:65:C2:37:26:E8:CE:B7:5F:11:61:9C:B8:41:AD:03:40:96:F8:20` (the fixed personal-sideload key used by every release APK)
5. In VISION: More → Money → *Sign in & sync Gmail*. No client ID is typed into the app; Google matches package + signing key.

If you see "Google rejected this app", step 4 is missing, mistyped, or not yet active (can take several minutes).

Limits: only the Subject and Google's snippet are read, never full bodies or attachments. Mail text can be forged by senders, so totals are a helper, not accounting. Not tested on a phone.
