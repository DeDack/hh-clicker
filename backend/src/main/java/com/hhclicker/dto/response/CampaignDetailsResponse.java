package com.hhclicker.dto.response;

import java.util.List;

public record CampaignDetailsResponse(CampaignResponse campaign, List<CampaignVacancyResponse> vacancies) {
}
