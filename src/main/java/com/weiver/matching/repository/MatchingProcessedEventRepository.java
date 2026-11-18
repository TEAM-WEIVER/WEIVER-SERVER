package com.weiver.matching.repository;

import com.weiver.matching.domain.MatchingProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchingProcessedEventRepository extends JpaRepository<MatchingProcessedEvent, Long> {

    // 같은 ID의 동시 수신은 유일 제약에서 대기한다. 선행 트랜잭션이 롤백되면 다시 등록할 수 있다.
    @Modifying
    @Query(value = """
            INSERT INTO matching_processed_events (event_id, create_time, update_time)
            VALUES (:eventId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int registerIfAbsent(@Param("eventId") String eventId);
}
