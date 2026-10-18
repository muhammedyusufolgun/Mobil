# AR Vision Studio Pro — Android prototip 0.2

## Çalışma modu
- Her açılışta **Canlı Kamera** gelir; küçük `admin` düğmesi dışındaki yönetim araçları kullanıcıya gösterilmez.
- Admin girişinden sonra görsel seçimi, eşleşme referansı ve yazı taslakları ile yayın paketi dışa aktarma açılır.
- Admin kendi Google Drive **klasör URL'sini** girebilir. Yalnızca eklediği adres ekranda görünür. Özel kaynak silinince başlangıç kaynağına dönülür ve başlangıç URL'si gösterilmez.
- Açılış klasör kimliği özel `local.properties` veya CI secret ile derlemeye aktarılır; kaynak deposuna konmaz. Derlenmiş APK içerisinde bulunur: **APK'dan tersine mühendislikle çıkarılabilir**, gizli erişim bilgisi değildir.
- Published ZIP Drive'da güncellendiğinde foreground tarama her 90 saniyede bir sürüm kontrolü yapar. Kamera önce açılır, Drive çağrıları arka plandadır. Son başarılı kayıt çevrimdışı kullanılır; farklı kaynakların önbellekleri ayrıdır.

## Geliştirilmiş özellikler
- Kotlin + Compose arayüzü, kullanıcı/admin geçişi, yerel hash tabanlı prototip parola kapısı
- Kamera izni ve ARCore destekli cihazlarda canlı ARCore kamera altlığı
- Görsel referansları için `AugmentedImageDatabase`, görsel algılanınca görsel merkezini izleyen **ekran üstü** yazı kartı
- ARCore desteklemeyen cihazlarda CameraX sadece-kamera modu
- Admin'de fotoğraf seçme veya kamera ile çekme, yerel taslak tanımlama/silme
- Admin özel Drive klasör adresi ve API anahtarı ayarı; başlangıç adresini arayüzden gizleme
- Drive API'den yayın ZIP sürümünü kontrol etme, boyut ve arşiv yolu doğrulama, yerel çevrimdışı paket deposu
- Admin'de `ARVISION_PUBLISHED.zip` oluşturma (Android belge kaydetme penceresi ile Google Drive klasörü seçilebilir)

## Henüz tamamlanmamış özellikler
- 3D nesnelerin gerçek konuma anchor ile sabitlenmesi; GLB modeller, yollar ve animasyon editörü
- OpenCV ile **kalibre edilmiş** %80 benzerlik skoru (ARCore'ın tanıma olayı yüzde değildir)
- Google OAuth ile Drive'a **otomatik** telefon içinden yayınlama. Şimdilik sistem dosya penceresiyle Drive klasörüne kaydedilir; Drive sağlayıcısı mevcut değilse ZIP elle yüklenir.
- APK ve fiziksel cihaz doğrulaması: yerel çalışma ortamında Android SDK/Gradle bulunmadığından build çalıştırılmadı.

## Drive kurulum
1. Google Cloud Console'da Drive API etkinleştirilir; yalnız **Drive API** erişimli, Android paket adı ve SHA-1 ile kısıtlanmış API key oluşturulur. Hedef klasör ve paket dosyası okuyuculara `bağlantıya sahip herkes görüntüleyebilir` yetkisiyle paylaşılmalıdır.
2. Anahtarınızı GitHub koduna yazmayın. `local.properties` içine `DRIVE_API_KEY=...` ve `BOOTSTRAP_FOLDER_ID=...` ekleyin veya GitHub Actions'ta **secret** olarak sağlanan `DRIVE_API_KEY` ve `BOOTSTRAP_FOLDER_ID` ortam değişkenlerini kullanın.
3. Admin taslakları kaydeder; `Drive'a Yayın Paketi` butonuyla sistemin kaydetme penceresi açılır. Drive uygulaması belge sağlayıcısı sunuyorsa ilgili klasörü seçerek `ARVISION_PUBLISHED.zip` olarak kaydedin. Aksi durumda kaydedilen dosyayı Drive klasörüne kendiniz yükleyin.
4. Yeni paket yayınlanınca uygulamalar sunucuyu kontrol eder ve güvenli şekilde eski çevrimdışı veriyi değiştirir.
5. Aynı klasörde **tek** `ARVISION_PUBLISHED.zip` tutun. Birden çok isimde/aynı adla dosya yüklemeyin.

### İlk adresin gösterilmemesi
Kullanıcı arayüzü ve Admin ayar kutusu hiçbir zaman uygulamayla gelen ilk bağlantıyı göstermeyecek. Bu **ekran gizliliğidir**, sunucu yetkilendirmesi değildir. Paylaşılan Drive bağlantısı ve API anahtarı APK incelemesiyle bulunabilir; özel/kurumsal verileri genel paylaşım klasörüne koymayın. Gerçek gizlilik için kimliği doğrulayan aracı servis ve OAuth gerekir.

## Derleme
Android Studio ile bu dizini açın; Android SDK 35 + JDK 17 kullanın. GitHub Actions workflow'u anahtarsız bile APK derleyebilir; Drive senkronizasyonunun çalışması için anahtar gerekir.

```sh
gradle :app:assembleDebug
```

**Önemli:** Bu bir başlangıç prototipidir. Tam AR sahne editörü değildir; derlemesi ve gerçek cihaz testi henüz doğrulanmadı.

## Önemli dağıtım sınırlamaları
- **Google Drive bağlantı paylaşımı, otomatik uygulama yazma izni vermez.** Yayın ZIP'i için Android belge kaydetme sağlayıcısı üzerinden Admin tarafından kaydetme gerekir. OAuth olmadan otomatik Drive yazma yoktur.
- **Drive API key ile genel klasör araması bazı paylaşım türlerinde boş/403 dönebilir.** Bu istemcide gerçek API anahtarı, herkese açık yayın dosyası ve cihaz üzerinde test gerekir; kurumsal gizli Drive verileri için Google OAuth ya da ayrı yetkilendirilmiş aktarım servisi gereklidir.
- API anahtarı güvenlik sınırı değildir. Sunucudaki hassas içerikleri herkesin görebildiği şekilde paylaşmayın.
- Kamera üstü yazı görsel merkezini takip eder, fiziksel bir 3D GLB hologram değildir. 3D editör, kalibre edilmiş benzerlik ve cihaz testleri tamamlanmadı.
