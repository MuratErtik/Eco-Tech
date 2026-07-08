package org.murat.samsungichackathon.dto;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yeni kampanya uretim istegi.
 * idea siniri gerekce: min 10 -> "test" gibi anlamsiz girdileri eler;
 * max 500 -> girdi "tek cumlelik fikir" olarak tanimli, 500 karakter bunun
 * ust siniri. Ayrica asiri uzun girdi hem token maliyetini sisirir hem de
 * prompt injection yuzeyini buyutur (Safety gerekcesi - rapora yazilabilir).
 */
public record CreateCampaignRequest(
        @NotBlank(message = "Kampanya fikri bos olamaz")
        @Size(min = 10, max = 500,
                message = "Kampanya fikri 10-500 karakter arasinda olmali")
        String idea
) {}
