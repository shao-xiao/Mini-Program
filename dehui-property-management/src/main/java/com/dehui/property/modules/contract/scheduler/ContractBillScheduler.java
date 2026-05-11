package com.dehui.property.modules.contract.scheduler;

import com.dehui.property.modules.contract.service.ContractBillGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractBillScheduler {
    private final ContractBillGenerationService contractBillGenerationService;

    @Scheduled(cron = "0 15 2 * * ?")
    public void generateContractBills() {
        int createdCount = contractBillGenerationService.generateDueBills();
        log.info("合同自动生成账单任务完成，新增账单数量={}", createdCount);
    }
}
