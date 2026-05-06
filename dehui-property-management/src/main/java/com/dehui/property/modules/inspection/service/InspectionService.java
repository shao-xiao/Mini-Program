package com.dehui.property.modules.inspection.service;

import com.dehui.property.modules.inspection.dto.InspectionCreateRequest;
import com.dehui.property.modules.inspection.entity.InspectionRecord;
import com.dehui.property.modules.inspection.repository.InspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRepository repository;

    public InspectionRecord create(InspectionCreateRequest req) {
        InspectionRecord r = new InspectionRecord();

        r.setInspectionDate(req.getInspectionDate());
        r.setInspector(req.getInspector());
        r.setInspectionType(req.getInspectionType());
        r.setArea(req.getArea());
        r.setTarget(req.getTarget());
        r.setResult(req.getResult());
        r.setProblemDescription(req.getProblemDescription());
        r.setActionTaken(req.getActionTaken());
        r.setRemark(req.getRemark());

        r.setStatus("OPEN");

        return repository.save(r);
    }

    public List<InspectionRecord> list() {
        return repository.findAll();
    }

    public InspectionRecord close(Long id) {
        InspectionRecord r = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("巡检记录不存在"));

        r.setStatus("CLOSED");
        return repository.save(r);
    }
}
