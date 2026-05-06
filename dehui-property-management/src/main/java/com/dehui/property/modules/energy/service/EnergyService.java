package com.dehui.property.modules.energy.service;

import com.dehui.property.modules.energy.entity.EnergyRecord;
import com.dehui.property.modules.energy.repository.EnergyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnergyService {
    private final EnergyRecordRepository energyRecordRepository;

    public List<EnergyRecord> findAll() {
        return energyRecordRepository.findAll();
    }

    public EnergyRecord save(EnergyRecord energyRecord) {
        return energyRecordRepository.save(energyRecord);
    }
}
