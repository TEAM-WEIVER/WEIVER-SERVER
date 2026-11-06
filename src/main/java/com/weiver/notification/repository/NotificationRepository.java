package com.weiver.notification.repository;

import com.weiver.notification.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("SELECT n.jdId, COUNT(DISTINCT n.matchId) " +
            "FROM Notification n " +
            "WHERE n.jdId IN :jdIds AND n.isRead = false " +
            "GROUP BY n.jdId")
    List<Object[]> countNewApplicantsByJdIds(@Param("jdIds") List<Long> jdIds);

    @Query("""
        SELECT n
        FROM Notification n
        WHERE n.company.companyId = (
            SELECT c.companyId
            FROM Company c
            WHERE c.publicId = :companyPublicId
        )
        ORDER BY n.createTime DESC, n.notificationId DESC
        """)
    Slice<Notification> findSliceByCompanyPublicId(
            @Param("companyPublicId") String companyPublicId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.isRead = true
            WHERE n.company.companyId = (
                SELECT c.companyId FROM Company c WHERE c.publicId = :companyPublicId
            ) AND n.isRead = false
            """)
    int markAllAsReadByCompanyPublicId(@Param("companyPublicId") String companyPublicId);
}
