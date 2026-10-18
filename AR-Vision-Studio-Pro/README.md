# AR Vision Studio Pro — v0.5 Hız ve Görünürlük Düzeltmesi

## Bu sürümde
- Fotoğraf çekimi artık küçük önizleme değil tam çözünürlüklü JPEG referans üretir (FileProvider + TakePicture).
- Görsel bitmap dekodlama AR çizim döngüsü dışında hazırlanır; veri tabanı değişimi kontrollüdür.
- Canlı kamerada tanıma/yüklenme durum göstergesi vardır.
- Admin Taslak Önizleme, görseli tanıma koşulu olmadan 3B efektin çizilip çizilmediğini test eder.
- Yeni tanımda varsayılan efekt 3B Küp seçilidir; Yazı modu yine kullanılabilir.
- Kullanıcıya yansıma için önce Bu Cihazda Yayınla gerekir.
- AR olmayan kamerada nesne tanıma/efekt beklenmemelidir.

# AR Vision Studio Pro — Android önizleme 0.4

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
- ARCore ile izlenen görselin gerçek koordinatında dönen **OpenGL ES 3B küp** ve animasyonlu **3B yön oku** (boyut/yükseklik/renk düzenlenebilir)
- ARCore desteklemeyen cihazlarda CameraX sadece-kamera modu
- Admin'de fotoğraf seçme veya kamera ile çekme, yerel taslak tanımlama/silme
- Admin özel Drive klasör adresi ve API anahtarı ayarı; başlangıç adresini arayüzden gizleme
- Drive API'den yayın ZIP sürümünü kontrol etme, boyut ve arşiv yolu doğrulama, yerel çevrimdışı paket deposu
- Admin'de `ARVISION_PUBLISHED.zip` oluşturma (Android belge kaydetme penceresi ile Google Drive klasörü seçilebilir)

## Henüz tamamlanmamış özellikler
- GLB/glTF dosyalarını içe alan tam sahne editörü, çok duraklı yollar ve gelişmiş animasyon editörü; v0.3 yalnızca dönen küp ve temel 3B ok sağlar
- OpenCV ile **kalibre edilmiş** %80 benzerlik skoru (ARCore'ın tanıma olayı yüzde değildir)
- Google OAuth ile Drive'a **otomatik** telefon içinden yayınlama. Şimdilik sistem dosya penceresiyle Drive klasörüne kaydedilir; Drive sağlayıcısı mevcut değilse ZIP elle yüklenir.
- Fiziksel cihaz, işlevsel AR ve Drive gerçek veri senkronizasyonu testleri (GitHub derlemesi bunların yerine geçmez).

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
- Kamera üstü bilgi yazısı hâlâ 2B ekran katmanıdır. Buna ek olarak gerçek dünyadaki izlenen resmin pozuna bağlanan OpenGL 3B küp ve ok desteklenir; GLB editörü, kalibre edilmiş benzerlik ve cihaz testleri tamamlanmadı.

## v0.4 yayın akışı
1. Admin girişinden sonra görsel yükleyin veya fotoğraf çekin.
2. Yazı, **3B Küp** ya da **3B Ok** seçin. Renk, boyut ve yükseklik ayarlayın.
3. Taslağı kaydedip kamera önizlemesinde deneyin.
4. **Bu Cihazda Yayınla** / **Drive ZIP Paketi** ile ZIP üretin; sistemin Google Drive belge kaydetme arayüzü kullanılabiliyorsa hedef klasöre kaydedin.
5. API anahtarı olmadan tek telefonda denemek için **Yayın ZIP’ini Cihaza İçe Aktar** seçin.
6. Okuyucu telefonlar otomatik senkron için ayrı ayrı yapılandırılmış Drive API erişimine ve okunabilir yayın dosyasına ihtiyaç duyar.

## v0.4 kararlılık düzeltmeleri
- AR kamera GL çiziminden önce hazır olma kontrolü, hataların sınırlandırılmış raporlanması ve başarısız AR durumunda standart kamera yedeği.
- Session/GLSurfaceView durdurma sırası ARCore resmi örneğindeki gibi düzenlendi; oturumun kapanmasıyla render işlemleri senkronize edildi.
- Büyük görseller, ARCore veritabanına eklenmeden önce uygun örnekleme ile küçültülür.
- Admin **Bu Cihazda Yayınla** ile taslağı tek dokunuşta okuyucu kamerasına geçirebilir.
- Drive API anahtarı yoksa bulut otomatik yenilenmez; paylaşımlı klasör izni bu anahtarın yerini tutmaz.
- **Gerçek cihazda test ve kök nedenin logcat ile doğrulanması gereklidir; tam AR sahne editörü yoktur.**
