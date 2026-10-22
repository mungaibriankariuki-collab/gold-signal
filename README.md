# Gold Signal

Android app that reads an MT5 gold (XAUUSD) chart screenshot with the Gemini API and returns
BUY / SELL / NO TRADE with entry, stop loss, TP1, TP2, TP3, trend, reason and confidence.

- Pick a screenshot from the gallery, or share one to the app from MT5 or the gallery.
- Your Gemini API key is entered in Settings and stored encrypted with an Android Keystore key.
- Stop loss and three take profits are set in pips (defaults SL 120, TP1 240, TP2 370, TP3 500;
  1 pip = $0.10 price move). The app places them at exactly those distances from Gemini's entry.
- Lot size calculator: shows $ risk at the SL and $ reward at TP1-TP3. 1 lot = $10 per pip,
  so 0.05 lot = $0.50 per pip.
- Every signal is saved on the device with its date, time and a thumbnail.

Every push builds an APK in GitHub Actions (artifact `GoldSignal-apk`); pushes to `main` also
publish it as the latest release:
https://github.com/mungaibriankariuki-collab/gold-signal/releases/latest/download/GoldSignal.apk

AI chart reading can be wrong. Not financial advice.
