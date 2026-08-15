package com.gojeom.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * {@code created_at}만 있는 테이블용 베이스.
 *
 * <p>스키마상 생성 시각만 두는 테이블이 있어 {@link BaseTimeEntity}와 분리했다.
 * 시간은 UTC로 저장하고 표시할 때 KST로 변환한다. (ERD.md D-3)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
