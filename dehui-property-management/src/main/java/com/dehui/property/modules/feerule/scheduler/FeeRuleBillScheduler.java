package com.dehui.property.modules.feerule.scheduler;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.dto.BillResponse;
import com.dehui.property.modules.feerule.entity.FeeRule;
import com.dehui.property.modules.feerule.repository.FeeRuleRepository;
import com.dehui.property.modules.feerule.service.FeeRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeeRuleBillScheduler {

    private final FeeRuleRepository feeRuleRepository;
    private final FeeRuleService feeRuleService;

    /**
     * 每天凌晨 2 点自动检查收费规则。
     *
     * 逻辑：
     * 1. 查询 ACTIVE 收费规则
     * 2. 判断今天是否为该规则的 generateDay
     * 3. 调用 FeeRuleService.generateBill(ruleId)
     * 4. 账单防重复逻辑复用 BillRepository.existsByContractIdAndPeriodStart
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateBillsByActiveRules() {
        LocalDate today = LocalDate.now();

        List<FeeRule> activeRules = feeRuleRepository.findByStatus("ACTIVE");

        log.info("开始执行收费规则自动生成账单任务，日期={}，ACTIVE规则数量={}", today, activeRules.size());

        for (FeeRule rule : activeRules) {
            try {
                if (!shouldGenerateToday(rule, today)) {
                    continue;
                }

                Result<BillResponse> result = feeRuleService.generateBill(rule.getId());

                if (result != null && result.getCode() == 200) {
                    BillResponse bill = result.getData();
                    log.info(
                            "收费规则自动生成账单成功，ruleId={}，ruleName={}，billNumber={}",
                            rule.getId(),
                            rule.getRuleName(),
                            bill == null ? null : bill.getBillNumber()
                    );
                } else {
                    log.warn(
                            "收费规则自动生成账单未成功，ruleId={}，ruleName={}，message={}",
                            rule.getId(),
                            rule.getRuleName(),
                            result == null ? "无返回结果" : result.getMessage()
                    );
                }
            } catch (Exception e) {
                log.error(
                        "收费规则自动生成账单异常，ruleId={}，ruleName={}",
                        rule.getId(),
                        rule.getRuleName(),
                        e
                );
            }
        }

        log.info("收费规则自动生成账单任务执行结束");
    }

    private boolean shouldGenerateToday(FeeRule rule, LocalDate today) {
        if (rule == null) {
            return false;
        }

        if (!"ACTIVE".equals(rule.getStatus())) {
            return false;
        }

        if (rule.getStartDate() != null && today.isBefore(rule.getStartDate())) {
            return false;
        }

        if (rule.getEndDate() != null && today.isAfter(rule.getEndDate())) {
            return false;
        }

        int configuredDay = rule.getGenerateDay() == null ? 10 : rule.getGenerateDay();
        YearMonth currentMonth = YearMonth.from(today);
        int effectiveGenerateDay = Math.min(configuredDay, currentMonth.lengthOfMonth());

        return today.getDayOfMonth() == effectiveGenerateDay;
    }
}
