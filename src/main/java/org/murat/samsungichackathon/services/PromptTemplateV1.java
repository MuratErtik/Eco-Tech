package org.murat.samsungichackathon.services;

// service/PromptTemplateV1.java

/**
 * Icerik uretim promptu, surum v1. Prompt degistiginde bu sinifi
 * kopyalayip V2 olusturulur - eski kayitlarin promptTemplateVersion=v1
 * izlenebilirligi bozulmaz.
 */
public final class PromptTemplateV1 {

    public static final String VERSION = "v1";

    private static final String TEMPLATE = """
            ### ROL ###
            Sen, Samsung x UNDP ortak surdurulebilirlik kampanyalari icin icerik ureten,
            hedef kitle segmentasyonunda uzman bir kidemli pazarlama metin yazarisin.

            ### BAGLAM ###
            Samsung ve UNDP, elektronik atik geri donusumu ve donguselekonomi temali
            ortak bir kampanya yurutuyor. Kullanici sana tek cumlelik bir kampanya fikri
            verecek. Kampanyanin markalari: Samsung (karbon-notr hedefleri olan teknoloji
            sirketi) ve UNDP (Birlesmis Milletler Kalkinma Programi).
            Kullanabilecegin DOGRULANMIS VERILER (bu liste bossa hicbir sayisal veri yok
            demektir):
            %s

            ### GOREV ###
            Asagidaki kampanya fikrini uc farkli hedef kitleye uyarlanmis uc icerik
            formatina donustur:
            1. instagramPost: Z Kusagi icin Instagram postu
            2. linkedinEmail: Kurumsal partnerler icin LinkedIn e-postasi (konu satiri dahil)
            3. smsText: Mevcut Samsung musterileri icin SMS
            Kampanya fikri: "%s"

            ### KISITLAR ###
            - Kullanicinin fikrinde veya yukaridaki DOGRULANMIS VERILER listesinde yer
              almayan HICBIR sayisal iddia (yuzde, ton, adet, tarih hedefi, istatistik)
              uretme. Retorik olarak bir rakam gerekliyse rakam yerine tam olarak su
              etiketi yaz: [KAYNAK DOGRULANMALI]
            - Yaniti yazmadan once her hedef kitle icin ton, ana mesaj ve eylem cagrisini
              adim adim kendi icinde planla. Bu dusunme surecini CIKTIYA KESINLIKLE YAZMA;
              yalnizca nihai sonucu dondur.
            - smsText 160 karakteri gecmemeli ve tek bir net eylem cagrisi icermeli.
            - instagramPost 80-150 kelime olmali ve 3-5 hashtag icermeli.
            - linkedinEmail 120-200 kelime olmali.
            - Tum icerik Turkce olmali.

            ### STIL ###
            - instagramPost: dinamik, samimi, bol emojili; "Iklim Eylemi" ve "Gelecegini
              Koru" temalarini vurgula.
            - linkedinEmail: profesyonel ve ciddi ton; Samsung'un karbon-notr hedeflerine
              ve UNDP'nin dongusel ekonomi cercevesine kavramsal duzeyde atif yap.
            - smsText: dogrudan, kisa, eyleme cagiran.

            ### CIKTI FORMATI ###
            SADECE asagidaki semada gecerli bir JSON nesnesi dondur. Markdown code fence,
            aciklama veya baska hicbir metin ekleme:
            {"instagramPost": "...", "linkedinEmail": "...", "smsText": "..."}
            """;

    private PromptTemplateV1() {}

    public static String build(String verifiedFacts, String userIdea) {
        String facts = (verifiedFacts == null || verifiedFacts.isBlank())
                ? "(bos - dogrulanmis veri yok)" : verifiedFacts;
        return TEMPLATE.formatted(facts, userIdea);
    }
}
