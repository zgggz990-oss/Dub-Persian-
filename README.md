# PersianDub (نمونه اولیه)

ویدیوی انگلیسی ← تشخیص گفتار (Vosk، آفلاین) ← ترجمه (ML Kit، آفلاین) ← دوبله زنده فارسی (TTS اندروید).

## راه‌اندازی
1. پوشه را در Android Studio باز کنید و Gradle Sync بزنید.
2. مدل انگلیسی Vosk (مثلاً `vosk-model-small-en-us-0.15`) را از https://alphacephei.com/vosk/models دانلود کنید،
   از حالت زیپ دربیاورید و محتویاتش را در `app/src/main/assets/model-en-us/` بگذارید
   (باید داخل آن فایل `uuid` هم باشد: یک فایل متنی با هر محتوای دلخواه، مثلاً `en-us-1`).
3. روی گوشی صدای فارسی TTS نصب باشد (Google Speech Services ← زبان فارسی).
4. اجرا کنید.
