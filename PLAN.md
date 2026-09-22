# Uygulama Kilidi — Proje Planı (tek doğruluk kaynağı, yeni oturumda önce bunu oku)

## Amaç
Harun'un telefonunda seçilen uygulamaları (Instagram, YouTube vb.) şifreyle kilitlemek: uygulama açılınca kilit ekranı çıkar, doğru şifre/biyometri girilmeden içeri girilemez. "Başka biri açmaya çalışırsa" senaryosu — yani telefona fiziksel erişimi olan biri.

## Karar: sıfırdan değil, açık kaynak temel üzerine
2026-09-22'de 4 aday incelendi (GitHub arama + F-Droid):
| Aday | Yıldız | Lisans | Durum |
|---|---|---|---|
| **aload0/AppLock** (eski adı PranavPurwar/AppLock) | **885** | **MIT** | F-Droid + IzzyOnDroid'de YAYINDA (gerçekten derlenip çalıştığı kanıtlı), son commit 9 hafta önce, temiz "features/" mimarisi, 63 Kotlin dosyası/12.323 satır |
| venkatschinthakindi/AppLock | 0 | belirtilmemiş | küçük/tek kişilik, yayında değil |
| abusalahmohammadnasim/OSAL | 0 | belirtilmemiş | küçük/tek kişilik, yayında değil |
| indrajithbandara/Privacy-Lock-App | 0 | Apache 2.0 | küçük/tek kişilik, yayında değil |

**Seçim: aload0/AppLock.** Hem "en iyisi" (yıldız/olgunluk/gerçek yayın kanıtı) hem "geliştirmeye en uygunu" (temiz feature-bazlı paket yapısı: core/data/features/services/ui; üç ayrı kilitleme mekanizması zaten var — AccessibilityService, Shizuku, UsageStats — bize esneklik sağlıyor).

## Kurulum yapıldı (2026-09-22)
- GitHub'da fork: https://github.com/cinarharunmat-jpg/uygulama-kilidi (origin=bizim fork, upstream=aload0/AppLock; ileride upstream düzeltmelerini `git pull upstream master` ile çekebiliriz)
- Yerel klasör: `C:\Users\Cnrma\uygulama-kilidi\`
- minSdk 26 (Android 8+), compileSdk/targetSdk 37 (güncel), Kotlin + Jetpack Compose + Material 3, Gradle (`./gradlew assembleDebug` ile derlenir).

## Teknik çalışma mantığı (upstream'den, henüz değiştirilmedi)
- `services/AppLockAccessibilityService.kt`: seçilen paketlerin ön plana geldiğini AccessibilityService ile yakalar, kilit ekranı overlay'i gösterir.
- `services/ShizukuAppLockService.kt`: Shizuku (adb yetkisi devreden bir araç) ile daha güçlü/erken müdahale — restricted-settings sorununu bypass etmenin bir yolu olabilir [H, doğrulanmadı].
- `services/UsageLockService.kt`: UsageStatsManager tabanlı yedek yöntem.
- `features/lockscreen`, `features/setpassword`, `features/antiuninstall`, `features/applist`, `features/settings`, `features/triggerexclusions`: PIN/şifre ekranı, uygulama seçici, kaldırmaya karşı koruma, hariç tutma kuralları — hepsi zaten var.

## ⚠️ Bilinen engel: Android 13+ "Kısıtlanmış Ayarlar"
adb ile (Play Store dışı) kurulan uygulamalarda Android, Erişilebilirlik Hizmeti iznini varsayılan olarak ENGELLER (kötü amaçlı yazılım koruması). Çözüm HER kurulumda elle yapılmalı, otomatikleştirilemez:
Ayarlar > Uygulamalar > (uygulama adı) > sağ üstte 3 nokta > "Kısıtlı ayarlara izin ver" > sonra Erişilebilirlik'ten servisi aç.
Bu adım atlanırsa kilit ekranı hiç tetiklenmez. İlk gerçek cihaz testinde mutlaka hatırlanacak.

## Varsayımlar (doğrulanmadı, cihaz bağlanınca kontrol edilecek)
- Hedef telefon muhtemelen S23 Ultra (önceki telefon temizliği projesinden); Android/One UI sürümü `adb shell getprop ro.build.version.release` ile ilk bağlantıda doğrulanacak.
- One UI 9.5'in kendi "App Lock" özelliği S27 serisiyle 2027 Q1'de geliyor [H, henüz S23 Ultra'ya ne zaman/gelip gelmeyeceği belirsiz] — bu yüzden kendi çözümümüz hâlâ gerekli.

## Sıradaki adımlar (Harun ile birlikte)
1. Android Studio / JDK / Gradle kurulu mu kontrol et, gerekirse kur.
2. `./gradlew assembleDebug` ile upstream halini DEĞİŞTİRMEDEN bir kez derle, telefona `adb install` ile kur, "kısıtlı ayarlar" adımını yaparak gerçekten çalıştığını doğrula (kanıt olmadan "çalışıyor" denmeyecek).
3. Ondan sonra birlikte özelleştirme: hangi uygulamalar varsayılan kilitli, marka/isim/ikon değişikliği, gereksiz özellik varsa çıkarma vb. — Harun'un tercihine göre.
4. Sonuçları/kararları bu dosyaya ve LOG.md'ye ekle.

## Log
- 2026-09-22: Araştırma + fork + yerel kurulum tamamlandı, henüz derleme/cihaz testi yapılmadı.
- 2026-09-22: İlk derleme + kurulum + gerçek cihaz testi (S23 Ultra, Android 16). `local.properties` içindeki ters eğik çizgi hatası düzeltildi. Kısıtlı-ayarlar adımı yapıldı, Erişilebilirlik izni açıldı, Instagram/YouTube kilitlendi ve DOĞRULANDI (Harun: "denedim, çalıştı").
- 2026-09-22: Pil ayarı doğrulandı/iyileştirildi — doze beyaz listesi + standby bucket EXEMPTED (adb ile).
- 2026-09-22: 3 algılama yöntemi karşılaştırıldı (kod okunarak) — Erişilebilirlik olay-tabanlı/en hızlı; UsageStats 250ms yoklama; Shizuku 500ms yoklama + ayrı kurulum. Erişilebilirlik (varsayılan seçim) doğru tercih, değiştirilmedi.
- 2026-09-22: **Tam Türkçeleştirme + yeniden markalama ("Kasa") + özel ikon.** `app_name` → Kasa; `strings.xml`'deki ~200 metin ve Compose ekranlarındaki ~25 sabit İngilizce metin (buton/etiket/contentDescription) Türkçeye çevrildi. Yeni adaptive icon üretildi (`scripts/ikon_uret.py`, Pillow ile programatik: koyu grafit kart + pirinç kasa kadranı + kol), tüm yoğunluklarda (mdpi-xxxhdpi) hem kare hem yuvarlak varyant değiştirildi. Derlendi, kuruldu, ekran görüntüsüyle doğrulandı (Ayarlar > Uygulama bilgileri: "Kasa" adı + yeni ikon görünüyor). GitHub'a push edildi (`44cf6d2`).
- Bilinmeyen/gelecek: Shizuku ve UsageStats servislerindeki (aktif olmayan) bazı log/hata mesajları hâlâ İngilizce (kullanıcıya görünmez, kasıtlı olarak atlandı). Başka dillerdeki (values-ar) mevcut çeviriye dokunulmadı.
