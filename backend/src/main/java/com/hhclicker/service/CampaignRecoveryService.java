package com.hhclicker.service;

import com.hhclicker.enumeration.CampaignStatus;
import com.hhclicker.repository.ApplicationCampaignRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampaignRecoveryService implements ApplicationRunner {
    private final ApplicationCampaignRepository campaigns;

    public CampaignRecoveryService(ApplicationCampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        campaigns.findAllByStatusIn(List.of(CampaignStatus.APPLYING, CampaignStatus.LETTERS_GENERATING)).forEach(campaign -> {
            campaign.setStatus(CampaignStatus.INTERRUPTED);
            campaigns.save(campaign);
        });
    }
}
