# Eco-Tech Kampanya Motoru

**Samsung x UNDP Sürdürülebilirlik Kampanya İçerik Üretim Sistemi**

---

## 1. Proje Özeti

### Ne Yapıyor?

Eco-Tech Kampanya Motoru, tek cümlelik bir kampanya fikrinden yola çıkarak üç farklı hedef kitleye uyarlanmış pazarlama içeriğini otomatik olarak üreten, insan onaylı bir AI içerik boru hattıdır.

### Hangi Problemi Çözüyor?

Samsung x UNDP sürdürülebilirlik kampanyaları, birbirinden oldukça farklı üç kitleye aynı anda ulaşmak zorundadır: değer odaklı Z Kuşağı, kurumsal sorumluluk peşindeki iş ortakları ve marka sadakatine dayanan mevcut müşteriler. Her kitle için farklı ton, format ve mesaj hiyerarşisi gerektiren bu çalışma, geleneksel süreçte metin yazarı zamanının büyük bölümünü tüketir. Bu sistem, Gemini API üzerinden tek istek → üç format üretimini milisaniyeler içinde gerçekleştirir; ancak üretilen içerik, insan onayı olmadan yayına giremez.

### Örnek Senaryo

> **Kullanıcı girer:** `"Eski telefonunu getir, geri dönüştürelim"`
>
> **Sistem üretir:**
> - Instagram postu (Z Kuşağı, emoji + hashtag, 80-150 kelime)
> - LinkedIn e-postası (Kurumsal partner, profesyonel ton, 120-200 kelime)
> - SMS (Mevcut müşteri, ≤160 karakter, tek eylem çağrısı)
>
> **Kampanya `DRAFT` olarak kaydedilir → Yetkili `APPROVED` veya `REJECTED` yapar → Onaylanan içerik kullanıma girer.**

---

## 2. Mimari Genel Bakış

### Katmanlar

```
┌─────────────────────────────────────────────────────────────┐
│                        İstemci                              │
│              (REST Client / Swagger UI)                     │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP
                            ▼
┌─────────────────────────────────────────────────────────────┐
│               Controller Katmanı                            │
│            CampaignController (/api/campaigns)              │
│  • İstek doğrulama (@Valid)                                 │
│  • HTTP durum kodları (201, 204 vb.)                        │
│  • GlobalExceptionHandler (tutarlı hata formatı)            │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│               Service Katmanı                               │
│            CampaignServiceImpl                              │
│                                                             │
│  generate():                                                │
│    1. PromptTemplateV1.build()  ─► Prompt inşa et          │
│    2. RetryTemplate.execute()   ─► Gemini API çağrısı      │
│    3. parseAndValidate()        ─► JSON parse + alan doğr.  │
│    4. scanForWarnings()         ─► İstatistik güvenlik tar. │
│    5. repository.save()         ─► Audit trail kayıt        │
│                                                             │
│  findAll() / findById() / updateStatus() / delete()        │
└──────────────┬────────────────────────────┬────────────────┘
               │                            │
               ▼                            ▼
┌──────────────────────────┐  ┌─────────────────────────────┐
│   Repository Katmanı     │  │     AI Sağlayıcı Katmanı    │
│  CampaignRepository      │  │   Spring AI + Gemini API    │
│  (Spring Data JPA)       │  │   (gemini-2.5-flash)        │
└──────────┬───────────────┘  └─────────────────────────────┘
           │
           ▼
┌──────────────────────────┐
│   PostgreSQL (Docker)    │
│   campaigns tablosu      │
│   (audit trail veritab.) │
└──────────────────────────┘
```

### Veri Akışı — POST /api/campaigns

```
İstemci
  │
  │ POST {"idea": "..."}
  ▼
CampaignController.create()
  │ @Valid ile girdi doğrulama (10-500 karakter)
  ▼
CampaignServiceImpl.generate()
  │
  ├─► PromptTemplateV1.build(verifiedFacts, idea)
  │     └─► 6 elemanlı prompt metni oluştur
  │
  ├─► RetryTemplate.execute()  [max 4 deneme, exponential backoff 2s→4s→8s]
  │     └─► ChatClient → Gemini API
  │           └─► Ham JSON yanıt
  │
  ├─► parseAndValidate(rawResponse)
  │     ├─► Code fence temizle (savunmacı regex)
  │     ├─► ObjectMapper.readValue() → GeneratedContent
  │     └─► instagramPost / linkedinEmail / smsText boş mu?
  │
  ├─► scanForWarnings(idea, content)
  │     ├─► STAT_PATTERN ile sayısal iddia tara
  │     ├─► [KAYNAK DOGRULANMALI] etiketi var mı?
  │     └─► smsText > 160 karakter mi?
  │
  └─► repository.save(campaign)  →  201 CampaignResponse
        (status=DRAFT, aiMetadata dolu, safety.warnings dolu)
```

---

## 3. Prompt Mühendisliği

### 3.1 6 Elementli Yapı

Sistem, her üretim isteğinde `PromptTemplateV1.build()` metoduyla aşağıdaki altı elementli promptu inşa eder. Her element, modelin davranışını belirli bir boyutta kısıtlar veya yönlendirir.

---

**Element 1 — ROL (`### ROL ###`)**

Modele bir kimlik ve uzmanlık alanı atanır. Belirsiz "AI asistan" kimliği yerine dar kapsamlı bir otorite rolü tanımlanır; bu, çıktının odak tutarlılığını artırır.

```
Sen, Samsung x UNDP ortak surdurulebilirlik kampanyalari icin icerik ureten,
hedef kitle segmentasyonunda uzman bir kidemli pazarlama metin yazarisin.
```

---

**Element 2 — BAĞLAM (`### BAGLAM ###`)**

Modelin bilinmesi gereken arka plan bilgisi ve kısıtlı doğrulanmış veri seti burada sağlanır. Doğrulanmış veri alanı (`%s` parametresi), `app.verified-facts` konfigürasyon değerinden beslenir; boşsa model açıkça uyarılır: `"(bos - dogrulanmis veri yok)"`. Bu mekanizmanın önemi §3.4'te ayrıntılanmaktadır.

```
Samsung ve UNDP, elektronik atik geri donusumu ve dongusel ekonomi temali
ortak bir kampanya yurutuyor. [...] Kullanabilecegin DOGRULANMIS VERILER
(bu liste bossa hicbir sayisal veri yok demektir):
%s
```

---

**Element 3 — GÖREV (`### GOREV ###`)**

Modelden beklenen çıktı, net olarak numaralandırılmış üç format şeklinde tanımlanır. Kullanıcı girdisi (`%s`) bu elementte enjekte edilir; prompt yapısının geri kalanından ayrı tutularak prompt injection yüzeyini sınırlar.

```
Asagidaki kampanya fikrini uc farkli hedef kitleye uyarlanmis uc icerik
formatina donustur:
1. instagramPost: Z Kusagi icin Instagram postu
2. linkedinEmail: Kurumsal partnerler icin LinkedIn e-postasi
3. smsText: Mevcut Samsung musterileri icin SMS
Kampanya fikri: "%s"
```

---

**Element 4 — KISITLAR (`### KISITLAR ###`)**

Bu element, projenin en kritik güvenlik katmanıdır. İki önemli kısıt içerir:

1. **İstatistik yasağı + zorunlu etiket:** Modelin kendi üreteceği sayısal iddiaları engeller. Sayı gerekiyorsa `[KAYNAK DOGRULANMALI]` etiketini zorunlu kılar.
2. **Chain-of-thought gizleme:** Modelin düşünme sürecini gizli tutması emredilir (§3.3'te detay).

```
- Kullanicinin fikrinde veya yukaridaki DOGRULANMIS VERILER listesinde yer
  almayan HICBIR sayisal iddia [...] uretme. Retorik olarak bir rakam
  gerekliyse rakam yerine tam olarak su etiketi yaz: [KAYNAK DOGRULANMALI]
- Yaniti yazmadan once her hedef kitle icin ton, ana mesaj ve eylem cagrisini
  adim adim kendi icinde planla. Bu dusunme surecini CIKTIYA KESINLIKLE
  YAZMA; yalnizca nihai sonucu dondur.
- smsText 160 karakteri gecmemeli [...]
```

---

**Element 5 — STİL (`### STIL ###`)**

Her kitle için ton rehberi ayrı ayrı belirtilir. Tek bir "uygun ton kullan" komutu yerine kitle başına somut talimatlar verilir; bu yaklaşım, modelin "ortalama" bir ton seçmesini önler.

```
- instagramPost: dinamik, samimi, bol emojili; "Iklim Eylemi" ve "Gelecegini
  Koru" temalarini vurgula.
- linkedinEmail: profesyonel ve ciddi ton; Samsung'un karbon-notr hedeflerine
  ve UNDP'nin dongusel ekonomi cercevesine kavramsal duzeyde atif yap.
- smsText: dogrudan, kisa, eyleme cagiran.
```

---

**Element 6 — ÇIKTI FORMATI (`### CIKTI FORMATI ###`)**

Model çıktısının makineyle işlenebilir olması için kesin format şeması tanımlanır. Markdown, açıklama veya başka metin eklenmesi açıkça yasaklanır.

```
SADECE asagidaki semada gecerli bir JSON nesnesi dondur. Markdown code fence,
aciklama veya baska hicbir metin ekleme:
{"instagramPost": "...", "linkedinEmail": "...", "smsText": "..."}
```

Bu kısıta rağmen Gemini bazen ` ```json ``` ` bloğu ekleyebilmektedir. Buna karşı `parseAndValidate()` metodunda savunmacı regex temizliği uygulanmaktadır (`replaceAll("^```(json)?\\s*", "")`).

---

### 3.2 Zero-Shot ve Few-Shot Karşılaştırması

Projede `PromptTemplateV1`, **zero-shot** yaklaşımıyla çalışır — yanıt örneği içermez. Few-shot alternatifi değerlendirildi ancak aşağıdaki gerekçelerle zero-shot tercih edildi:

| Boyut | Zero-Shot (Kullanılan) | Few-Shot (Değerlendirilen) |
|---|---|---|
| **Token maliyeti** | Düşük (~600-700 token/istek) | Yüksek (örnek başına +300-500 token) |
| **Örnek güncelliği** | Sorun yok | Örnekler bayatlarsa çıktı kalitesi düşer |
| **Format uyumu** | Yeterli — JSON şema + kısıtlar format kalitesini sağlıyor | Gereğinden fazla — format element 6 ile zaten sabit |
| **Kitle çeşitliliği** | Stil elementi 3 kitleyi kapsamlı tanımlıyor | Örnek eklenmesi tutarsızlık riski yaratır |

**Few-shot'ın avantajlı olacağı durumlar:** Sistemin çok daha spesifik bir marka sesi (örn. belirli bir şirketin geçmiş içeriklerine dayanan ton) tutturması gerekseydi, veya JSON formatına uyum düşük olsaydı few-shot örnekleri eklenirdi. Mevcut konfigürasyonda bu ihtiyaç doğmamıştır.

---

### 3.3 Chain-of-Thought Mekanizması

Kısıtlar elementinde yer alan şu talimat, gizli bir chain-of-thought adımı tetikler:

```
Yaniti yazmadan once her hedef kitle icin ton, ana mesaj ve eylem cagrisini
adim adim kendi icinde planla. Bu dusunme surecini CIKTIYA KESINLIKLE YAZMA.
```

**Neden gizli?**

1. **Format bütünlüğü:** API katmanı yalnızca `{"instagramPost": ..., "linkedinEmail": ..., "smsText": ...}` formatını bekler. İç muhakeme metni JSON parse'ı kırar.
2. **Çıktı kalitesi:** Modelin önce planlamasına ve sonra yazmaya geçmesi, doğrudan yazmaya başlamasına kıyasla daha tutarlı ve hedef kitleye uygun içerik üretir. Bu düşünme sürecinin kullanıcıya görünmesi gerekmez.
3. **Token verimliliği:** Kullanıcıya dönen yanıt, gereksiz muhakeme metni içermez.

Bu yaklaşım, modelin "refleksif" yanıt yerine "planlı" yanıt vermesini sağlarken API arayüzünü temiz tutar.

---

### 3.4 Doğrulanmış Veri Kullanımı ve İstatistik Riski

Prompt BAĞLAM elementinde `app.verified-facts` konfigürasyon parametresi üzerinden yalnızca onaylanmış veriler modele sunulur. Bu alan boş olduğunda prompt açıkça şöyle der: `"(bos - dogrulanmis veri yok)"`.

**Neden rastgele istatistik üretilmiyor?**

LLM'ler sayısal iddia üretme konusunda yüksek hallüsinasyon riski taşır: "%30 daha az karbon", "500 ton geri dönüşüm" gibi gerçekte doğrulanamayan rakamlar hem yasal risk hem de marka itibar riski oluşturur. Bu nedenle iki katmanlı bir savunma uygulanmıştır:

- **Prompt seviyesinde kısıt:** Model, doğrulanmış veri listesi dışında kalan her türlü sayısal iddiayı üretmek yerine `[KAYNAK DOGRULANMALI]` etiketi yazmakla yükümlüdür.
- **Backend seviyesinde bağımsız doğrulama:** `scanForWarnings()` metodu, `STAT_PATTERN` regex'iyle tüm çıktıyı tarar. Kullanıcı girdisinde yer almayan bir sayısal ifade tespit edildiğinde `CONTAINS_UNVERIFIED_STATISTIC` uyarısı, yanıtın `safety.warnings` alanına eklenir.

İki katmanlı bu yaklaşım; prompt kısıtını modelin bypass etmesi durumunu da kapsar — backend doğrulaması prompt direktiflerinden bağımsızdır.

---

## 4. Etik Değerlendirme

### 4.1 Tespit Edilen Riskler ve Alınan Önlemler

#### Risk 1: Hallüsinasyon / Uydurma İstatistik

| Alan | Detay |
|---|---|
| **Risk** | Model, gerçekte var olmayan istatistiksel iddialar üretebilir. Kampanya materyalinde yanlış bir oran veya rakam, hukuki sorumluluk ve marka zararı yaratır. |
| **Prompt önlemi** | KISITLAR elementinde sayısal iddia yasağı ve `[KAYNAK DOGRULANMALI]` etiketi zorunluluğu. |
| **Backend önlemi** | `scanForWarnings()` → `CONTAINS_UNVERIFIED_STATISTIC` uyarısı `safety.warnings` alanında görünür. `CONTAINS_VERIFICATION_PLACEHOLDER` uyarısı, modelin etiketi doğru kullandığı durumları işaretler. |
| **Kalan risk** | Model, regex'in yakalamayacağı biçimde (örn. "birkaç yüz ton") sayısal iddiayı gizleyebilir. Bu durum için tek güvence insan onayı aşamasıdır. |

#### Risk 2: Hedef Kitle Stereotipleştirmesi

| Alan | Detay |
|---|---|
| **Risk** | "Z Kuşağı" veya "Kurumsal Partner" etiketleri, modelin klişe ve aşırı genellemeci içerik üretmesine yol açabilir. |
| **Önlem** | STİL elementi, her kitle için ton rehberini genel etiket yerine içerik odaklı kısıtlarla tanımlar: "dinamik, samimi, bol emojili" ve "karbon-nötr hedeflerine kavramsal atıf yap" gibi ifadeler, modeli klişe kalıplardan uzaklaştırır. |
| **Kalan risk** | Stil kararlarında kültürel ve demografik önyargılar hâlâ bulunabilir. Sistematik değerlendirme yapılmamıştır. |

#### Risk 3: AI Kökenli İçeriğin Şeffaf Olmaması

| Alan | Detay |
|---|---|
| **Risk** | AI tarafından üretildiği belirsiz içerik yanıltıcı olarak algılanabilir. |
| **Önlem** | Her `CampaignResponse`, `aiMetadata` alanı üzerinden içeriğin `"AI"` tarafından `"Google Gemini"` modeli ile hangi `promptTemplateVersion` kullanılarak üretildiğini açıkça gösterir. Bu alan veritabanında kalıcı olarak saklanır; değiştirilemez. |

#### Risk 4: Denetimsiz Yayın

| Alan | Detay |
|---|---|
| **Risk** | AI çıktısının doğrudan yayına girmesi, hatalı veya uygunsuz içeriğin kontrolsüz yayılmasına yol açar. |
| **Önlem** | Tüm kampanyalar `DRAFT` statüsüyle başlar. `PATCH /api/campaigns/{id}/status` uç noktası üzerinden yalnızca yetkili bir insan `APPROVED` veya `REJECTED` geçişini yapabilir. `APPROVED` olmayan içerik sistemin üretim akışına dahil edilemez. |

---

### 4.2 FATS İlkesinin API Tasarımında Somutlaşması

**FATS (Fairness, Accountability, Transparency, Safety)** ilkeleri, bu projede soyut etik beyan olarak değil, API uç noktalarına ve veri modellerine gömülü somut mekanizmalar olarak uygulanmıştır.

| İlke | Uç Nokta / Alan | Somutlaşma |
|---|---|---|
| **Transparency** | `GET /api/campaigns/{id}` → `aiMetadata` | Her kampanya, içeriği kimin (AI), hangi sistemin (Google Gemini), hangi modelin (gemini-2.5-flash), hangi sıcaklıkla (temperature: 0.7) ve hangi prompt sürümüyle (promptTemplateVersion: v1) ürettiğini açıkça taşır. |
| **Transparency** | `GET /api/campaigns/{id}` → `safety.warnings` | İçerik uyarıları (`CONTAINS_UNVERIFIED_STATISTIC`, `SMS_LENGTH_EXCEEDED` vb.) her zaman yanıtta görünür; gizlenmez. |
| **Accountability** | `POST /api/campaigns` — başarısız üretimler dahil | Gemini API'ye ulaşılamadığında veya JSON parse başarısız olduğunda, başarısız deneme bile `REJECTED` statüsüyle veritabanına kaydedilir. Hiçbir üretim denemesi audit trail dışında kalamaz. |
| **Accountability** | `Campaign.fullPromptText` (DB kolonu) | Gemini'ye gönderilen tam prompt metni veritabanında saklanır. API yanıtında gösterilmez, ancak denetim amacıyla sorgulanabilir. |
| **Accountability** | `Campaign.promptTemplateVersion` | Prompt değiştiğinde eski kayıtlar `v1` etiketini korur; hangi promptun hangi içeriği ürettiği her zaman izlenebilir kalır. |
| **Fairness** | `PromptTemplateV1` → STİL elementi | Kitleler, klişe demografi etiketleri yerine içerik odaklı ton rehberiyle tanımlanır. |
| **Safety** | `POST /api/campaigns` → KISITLAR + `scanForWarnings()` | İstatistik kısıtı hem prompt seviyesinde hem bağımsız backend taramasıyla çift katmanlı uygulanır. |
| **Safety** | `CreateCampaignRequest` → `@Size(min=10, max=500)` | Maksimum 500 karakter kısıtı prompt injection yüzeyini sınırlar; minimum 10 karakter anlamsız girdileri eler. |

---

### 4.3 Human-in-the-Loop Mekanizması

```
POST /api/campaigns
        │
        ▼
   status: DRAFT  ◄─── Tüm yeni üretimler buraya başlar
        │
        │  PATCH /api/campaigns/{id}/status
        │
        ├──── {"status": "APPROVED", "reviewNote": "..."} ───► status: APPROVED
        │                                                        reviewedAt doldurulur
        │
        └──── {"status": "REJECTED", "reviewNote": "..."} ───► status: REJECTED
                                                                reviewedAt doldurulur
```

**İş kuralları:**
- Yalnızca `DRAFT` statüsündeki kampanyaların durumu değiştirilebilir.
- `APPROVED → DRAFT` veya `REJECTED → APPROVED` gibi geri dönüşler mümkün değildir (`InvalidStatusTransitionException` → 409 Conflict).
- Her geçişte `reviewNote` (açıklama) ve `reviewedAt` (zaman damgası) kaydedilir.

---

### 4.4 Bilinen Sınırlılıklar

Bu bölüm, sistemin kasıtlı olarak kapsam dışı bıraktığı veya hackathon zaman kısıtı nedeniyle eksik kalan unsurları içerir.

| Sınırlılık | Açıklama |
|---|---|
| **Kimlik doğrulama yok** | Sistem herhangi bir auth mekanizması içermez. `PATCH /api/campaigns/{id}/status` uç noktası herkese açıktır; dolayısıyla "kim onayladı" bilgisi teknik olarak doğrulanamaz. `reviewNote` alanı insan yorumu içerse de bu bilgi sistemce doğrulanmaz. |
| **Tek kullanıcı modeli** | Rol tabanlı erişim kontrolü (RBAC) yoktur. Üretici ve onaylayıcı rolleri ayrılmamıştır. |
| **Prompt injection koruması sınırlı** | `idea` alanında 500 karakter sınırı uygulanmaktadır; ancak bu, sofistike prompt injection girişimlerine karşı yeterli değildir. Girdi sanitizasyonu yapılmamıştır. |
| **Doğrulanmış veri dinamik değil** | `app.verified-facts` statik bir konfigürasyon değeridir; uygulama yeniden başlatılmadan güncellenemez. |
| **Kitle çeşitliliği değerlendirmesi yok** | Üretilen içeriğin stereotipleştirme veya dışlayıcı dil içerip içermediğini sistematik olarak ölçen bir mekanizma yoktur. |
| **Tek dil** | Sistem yalnızca Türkçe içerik üretir; çok dilli senaryo desteklenmez. |

---

## 5. API Endpoint Referansı

| Metot | Path | Açıklama |
|---|---|---|
| `POST` | `/api/campaigns` | Yeni kampanya üretir (Gemini API çağrısı); `DRAFT` statüsüyle kaydeder |
| `GET` | `/api/campaigns` | Tüm kampanyaları listeler; opsiyonel `?status=DRAFT\|APPROVED\|REJECTED` filtresi |
| `GET` | `/api/campaigns/{id}` | Tek kampanyanın tüm detaylarını getirir (`aiMetadata`, `safety` dahil) |
| `PATCH` | `/api/campaigns/{id}/status` | İnsan onayı: `DRAFT → APPROVED` veya `DRAFT → REJECTED` geçişi |
| `DELETE` | `/api/campaigns/{id}` | Kampanyayı kalıcı olarak siler |

**Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

### İstek / Yanıt Örnekleri

**POST /api/campaigns**
```json
// İstek
{ "idea": "Eski telefonunu getir, geri dönüştürelim" }

// Yanıt — 201 Created
{
  "id": 1,
  "userInput": "Eski telefonunu getir, geri dönüştürelim",
  "content": {
    "instagramPost": "...",
    "linkedinEmail": "...",
    "smsText": "..."
  },
  "status": "DRAFT",
  "reviewNote": null,
  "reviewedAt": null,
  "aiMetadata": {
    "generatedBy": "AI",
    "provider": "Google Gemini",
    "modelName": "gemini-2.5-flash",
    "temperature": 0.7,
    "promptTemplateVersion": "v1"
  },
  "safety": {
    "validationPassed": true,
    "warnings": []
  },
  "createdAt": "2026-07-08T10:47:44.550Z"
}
```

**PATCH /api/campaigns/{id}/status**
```json
// İstek
{ "status": "APPROVED", "reviewNote": "İçerik uygun, yayına alınabilir." }

// Yanıt — 200 OK (reviewedAt doldurulmuş CampaignResponse)
```

---

## 6. Teknoloji Yığını

| Teknoloji | Versiyon | Tercih Gerekçesi |
|---|---|---|
| **Java** | 21 | LTS; record, sealed interface, pattern matching desteği |
| **Spring Boot** | 3.5.x | Olgun ekosistem; Spring AI entegrasyonu; JPA/Validation/OpenAPI otomatik konfigürasyonu |
| **Spring AI** | 1.1.x | Google Gemini'yi `ChatClient` soyutlamasıyla sarmalayarak provider değişikliğini tek satır konfigürasyona indirgeyecek şekilde tasarlanmıştır |
| **Google Gemini** | gemini-2.5-flash | Hız/kalite dengesi; uzun prompt + çok formatlı JSON çıktı üretiminde test edilmiş başarı |
| **PostgreSQL 16** | Docker | Kalıcı audit trail için ilişkisel DB; `TEXT` kolonu sınırsız AI çıktısını karşılar |
| **Spring Retry** | — | Gemini 5xx hatalarında exponential backoff (2s→4s→8s, maks. 4 deneme) |
| **Jackson** | — | JSON parse; `GeneratedContent` record'una doğrudan deserializasyon |
| **SpringDoc OpenAPI** | — | Swagger UI; endpoint dokümantasyonu otomatik üretimi |
| **spring-dotenv** | 4.x | `.env` dosyasından `GEMINI_API_KEY` ve veritabanı kimlik bilgileri Spring Environment'a enjekte edilir |

---

## 7. Kurulum

### Ön Koşullar

- Docker Desktop
- Java 21
- Maven 3.9+

### Başlatma

```bash
# 1. Depoyu klonla
git clone <repo-url>
cd SamsungIC-Hackathon

# 2. .env dosyasını oluştur (GEMINI_API_KEY zorunlu)
# POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB, GEMINI_API_KEY alanlarını doldur

# 3. PostgreSQL container'ını başlat
docker compose up -d

# 4. Uygulamayı çalıştır
./mvnw spring-boot:run

# 5. Çalıştığını doğrula
curl -X POST http://localhost:8080/api/campaigns \
  -H "Content-Type: application/json" \
  -d '{"idea":"Eski telefonunu getir, geri donusturelim"}'
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`