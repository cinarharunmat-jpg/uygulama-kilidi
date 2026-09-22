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
- 2026-09-22 (devam): Kalan İngilizce metinler (Gelişmiş/Algılama Yöntemi/Bağlantılar) düzeltildi; Kaynak Kod/Sorun Bildir bağlantıları kendi fork'umuza güncellendi. PIN otomatik açılış eklendi (uzunluk `PreferencesRepository.setPinLength`'te ayrıca saklanıyor, hash'ten geri okunamıyor; ilk manuel onaydan sonra kendiliğinden öğreniliyor).

## ⚠️ TEKRARLAYABİLİR SORUN: kilitleme "adb install -r" sonrası duruyor
2026-09-22: Harun kilitli uygulamayı kilitsiz açabildiğini bildirdi. Kök neden bulundu: art arda `adb install -r` güncellemelerinden sonra Android/One UI Erişilebilirlik Hizmeti iznini OTOMATİK KAPATIYOR (`secure enabled_accessibility_services` = null, `accessibility_enabled` = 0) — bu Android'in bilinçli güvenlik davranışı (özellikle sideload/ADB kurulumlarında), kodda düzeltilecek bir hata değil. Canlı düzeltme: `adb shell settings put secure enabled_accessibility_services dev.pranav.applock/dev.pranav.applock.services.AppLockAccessibilityService` + `adb shell settings put secure accessibility_enabled 1`, `dumpsys accessibility` ile bound+enabled doğrulandı. **KURAL: bundan sonra her `adb install -r`'dan hemen sonra bu iki komutu da çalıştır**, yoksa kilitleme sessizce durur ve fark edilmez.

## 🆕 GİZLİ DOSYALAR (Vault) — tasarlandı + yazıldı, CİHAZDA HENÜZ TEST EDİLMEDİ
Harun'un isteği: Kasa'ya dosya/fotoğraf gizleme özelliği. Mantık:
1. Kullanıcı sistem dosya seçiciyle (SAF, çoklu seçim) dosya seçer.
2. Baytlar AES256-GCM ile (Android Keystore'da tutulan anahtar, muhtemelen bu cihazda donanım destekli) şifrelenip `filesDir/vault/<uuid>.enc` olarak yazılır — bu klasör diğer uygulamalardan, Galeri'den, Dosyalarım'dan, USB/PC bağlantısından GÖRÜNMEZ (uygulamanın özel iç deposu).
3. Küçük bir `index.json` (yalnız ad/tarih/boyut, İÇERİK DEĞİL) düz metin tutuluyor.
4. Orijinal dosyayı genel depodan silmek için Android'in kendi "silinsin mi?" onay penceresi tetiklenir (`MediaStore.createDeleteRequest`, API 30+); kullanıcı iptal ederse orijinal olduğu yerde kalır (gizli kopya yine de oluşmuş olur) — bu Harun'a açıkça söylenecek, sanki otomatik/sessiz siliniyormuş gibi sunulmayacak.
5. Kasa içinde "Gizli Dosyalar" ekranı (MainScreen üst çubuğunda yeni bir simge): 3 sütunlu galeri, resimler için küçük resim + tam ekran önizleme, resim olmayanlar (video/belge) için "başka uygulamayla aç" (geçici şifresiz kopya FileProvider ile paylaşılır, `cacheDir/vault_tmp`'ye yazılır).
6. Geri yükle: şifreyi çözüp MediaStore'a (Pictures/Kasa, Movies/Kasa veya Downloads/Kasa) geri yazar, Kasa'daki kopyayı siler.
7. Kalıcı sil: onay penceresiyle Kasa'dan tamamen kaldırır (geri alınamaz).
Bu ekran ayrıca bir PIN istemiyor — zaten MainScreen'e ulaşmak için uygulamanın kendi PIN'i gerekiyor (Ayarlar/Tetikleme İstisnaları/Kaldırmaya Karşı Koruma ile aynı mantık).
Kod: `data/vault/VaultRepository.kt`, `features/vault/VaultViewModel.kt`, `features/vault/ui/VaultScreen.kt`. Bağımlılık: `androidx.security:security-crypto:1.1.0-alpha06`. Derlendi (BUILD SUCCESSFUL), commit `8f44796`, push edildi. **Cihaza kurulmadı/denenmedi — Harun telefonu tekrar bağlayınca ilk iş bu olacak.**
Bilinen sınırlar (Harun'a dürüstçe söylenecek): index.json şifresiz (yalnız dosya adı/tarih sızabilir, içerik değil); silme onayını iptal ederse dosya iki yerde de kalır; API 26-29 (bu cihazda geçerli değil, Android 16) için silme yolu test edilmedi; video için uygulama içi oynatma yok (harici uygulamayla açılıyor).

## ✅ KİLİTLEME BUG'I — KÖK NEDEN BULUNDU + KALICI OTOMATİK ONARIM (2026-09-22)
Harun WhatsApp Business'ı kilitledi ama kilitsiz açılabiliyordu. Kök neden logcat + `dumpsys window`/`dumpsys accessibility` ile kanıtlandı: Android her APK güncellemesinden (ve muhtemelen OEM arka plan öldürmelerinden) sonra Erişilebilirlik Hizmeti iznini otomatik kapatıyor; bu normal bir Service değil, kod içinden `startService()` ile geri açılamıyor (BootReceiver'ın eski denemesi bu yüzden hep sessizce başarısız oluyordu).
**Kalıcı çözüm:** `WRITE_SECURE_SETTINGS` izni bir kez `adb shell pm grant dev.pranav.applock android.permission.WRITE_SECURE_SETTINGS` ile verildi (uygulama silinip yeniden kurulursa -r değil de temiz kurulursa tekrar verilmeli). Yeni `AccessibilitySettingsHelper.repairAccessibilityServiceIfNeeded()` bu izinle doğrudan `Settings.Secure`'a yazıyor; `BootReceiver` içinden APK güncellemesi + açılış + **her ekran kilidi açma (USER_PRESENT)** anında tetikleniyor.
**Doğrulandı (gerçek senaryo, adb ile elle düzeltme yapmadan):** izin elle kapatıldı → aynı APK `-r` ile yeniden kuruldu (gerçek MY_PACKAGE_REPLACED yayını) → kendiliğinden onarıldığı `dumpsys accessibility` ile görüldü → WhatsApp Business açıldı → `dumpsys window` `mCurrentFocus=dev.pranav.applock` (kilit ekranı gerçekten odakta) + log `isLockScreenShown=true`. **Ekran görüntüleri siyah çıkıyor çünkü kilit ekranı `FLAG_SECURE` kullanıyor (ekran kaydını engelleyen kasıtlı güvenlik özelliği) — bu normal, hata değil, adb screencap'in doğal sınırı.**
Commit: `4c2b7ab`. Ders: bundan sonra artık HER install-r'dan sonra elle adb düzeltmesi yapmama gerek yok — sistem kendini onarıyor.

## 🆕 "UYGULAMAYI GÖRÜNMEZ YAP" — tasarım (Harun'un isteği, 2026-09-22)
İstek: Instagram (veya başka bir uygulama) telefon ekranında hiç görünmesin, yalnızca Kasa üzerinden bulunup açılabilsin, açılırken yine şifre istesin.

**Teknik gerçek (dürüstçe):** Kasa (kök/sistem izni olmayan sıradan bir uygulama), BAŞKA bir uygulamanın (Instagram'ın) ana ekran/uygulama çekmecesi simgesini KENDİ KODUYLA gizleyemez — bu, `CHANGE_COMPONENT_ENABLED_STATE` gibi yalnızca sistem imzalı uygulamalara verilen bir izin gerektiriyor, `adb pm grant` ile bile verilemiyor (imza seviyesi izin). Kilitleme çalışıyor çünkü KENDİ uygulamamızın Erişilebilirlik Hizmeti izniyle "hangi uygulama açıldı" diye izliyoruz — ama simgeyi gizlemek bambaşka bir izin sınıfı, kök olmadan imkânsız.

**Bu yüzden en mantıklı ve gerçekten çalışan tasarım İKİ PARÇALI:**
1. **Simgeyi gizleme — Samsung'un KENDİ özelliği (bizim kodumuzla değil, elle bir kerelik ayar):** One UI'da zaten var — Ana ekranda boş bir yere uzun bas > Ana ekran ayarları > **Uygulamaları gizle** > Instagram'ı seç. Bu, simgeyi hem ana ekrandan hem uygulama çekmecesinden tamamen kaldırıyor (rastgele bakan biri göremez). Bunu otomatikleştiremedim (Samsung'un bu ekrana giden gizli bir kısayolu/API'si yok, denedim), Harun'un bir kerelik elle yapması gerekiyor — 10 saniyelik iş.
2. **Bulma ve açma — Kasa (bizim kodumuz, bugün eklendi):** Kilitli uygulamalar listesindeki bir satıra dokunmak artık o uygulamayı DOĞRUDAN AÇIYOR (`packageManager.getLaunchIntentForPackage`). Simgesi ana ekrandan kaybolsa bile, Kasa'yı açıp (PIN) listeden Instagram'a dokunursan açılır. Kilit ekranı yine de normal şekilde devreye girer (nasıl açıldığına bakmaksızın, Erişilebilirlik izleme her zaman çalışıyor) — yani "açılırken yine şifre istesin" isteği zaten otomatik sağlanıyor, ekstra kod gerekmedi.

Sonuç: Harun bir kez Samsung'un ayarından Instagram'ı gizler → o andan sonra Instagram'ı yalnızca Kasa'dan bulup PIN'le açabilir; simgesi hiçbir yerde görünmez. Commit `07eb0a3`.
**Kısıtlar (dürüstçe):** Sistem Ayarları > Uygulamalar (tüm uygulamalar listesi) içinden hâlâ görülüp açılabilir — bu Samsung'un sınırı, bizim değil; "hiç kimse hiçbir şekilde bulamaz" garantisi verilemez, sadece "rastgele bakan biri ana ekranda/çekmecede görmez" garantisi verilebilir.
