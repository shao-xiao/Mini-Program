package com.dehui.property.modules.attachment.repository;

import com.dehui.property.modules.attachment.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByBizTypeAndBizIdAndDeletedFalseOrderBySortOrderAscCreatedTimeAsc(String bizType, Long bizId);

    List<Attachment> findByBizTypeAndBizIdAndFileCategoryAndDeletedFalseOrderBySortOrderAscCreatedTimeAsc(
            String bizType,
            Long bizId,
            String fileCategory
    );

    long countByBizTypeAndBizIdAndFileCategoryAndDeletedFalse(String bizType, Long bizId, String fileCategory);
}
