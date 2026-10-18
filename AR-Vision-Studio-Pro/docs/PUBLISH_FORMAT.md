# Drive yayın protokolü (v1)

- Varsayılan Drive klasörünün içindeki `ARVISION_PUBLISHED.zip` dosyası yayın kaynağıdır.
- Uygulama `files.list` ile bu dosyanın Drive ID ve `md5Checksum` bilgisini sorgular (API key gerekir).
- MD5 değişmediyse hiçbir içerik tekrar indirilmez.
- Değişmişse ZIP indirilir, kontrol edilir, yerel önbelleğe alınır, daha sonra sahneler değiştirilir.
- ZIP içeriği: `manifest.json` ve `refs/<id>.jpg` (PNG de kabul edilir).
- manifest: `{ "schema":1, "scenes":[{"id":"...","title":"...","displayText":"...","widthMeters":0.2,"reference":"refs/<id>.jpg"}] }`
- Koruma: 25MB ZIP, 70MB açılmış veri, sahne sayısı en fazla 200, dosya yolları ve görsel formatı kontrol edilir.
- Arka plan çağrısı kamera UI açılışını engellemez, kullanıcıya yeni paket **indirilmeye başlandığında** `Yeni bilgi yükleniyor…` gösterilir.
- Önceden indirilmiş paket olmadan çevrimdışı ilk kurulumda algılanabilecek referans bulunmaz.
- Önceki başarılı yayın saklanır. Mevcut kaynak değişirse ilk kontrollü karşılaştırmada yeni yayın yüklenir.
- Bu ilk sürümde dosya seçimi Android sistem belge kaydetme penceresiyle yapılır; kullanıcı Google Drive hedef klasörünü seçer. Google OAuth yetkilendirilmiş doğrudan `files.create` ileri aşamadır.

### v0.3 isteğe bağlı AR görünüm alanları
Her `scenes[]` öğesinde `effect` (`CARD`, `CUBE`, `ARROW`), `color` (`#RRGGBB`), `sizeMeters` ve `heightMeters` bulunabilir. Eksik alanlar varsayılanlarla doldurulur. Formatın şema sürümü 1 olarak kalır.
