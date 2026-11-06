package com.weiver.matching.domain;

import com.weiver.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "matching_processed_events")
public class MatchingProcessedEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "matching_processed_event_id")
    private Long matchingProcessedEventId;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId;
}
