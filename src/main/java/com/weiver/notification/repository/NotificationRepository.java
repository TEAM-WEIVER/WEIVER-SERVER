package com.weiver.notification.repository;

import com.weiver.notification.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("SELECT n.matchResult.jobPosting.jdId, COUNT(DISTINCT n.matchResult) " +
            "FROM Notification n " +
            "WHERE n.matchResult.jobPosting.jdId IN :jdIds AND n.isRead = false " +
            "GROUP BY n.matchResult.jobPosting.jdId")
    List<Object[]> countNewApplicantsByJdIds(@Param("jdIds") List<Long> jdIds);

    @EntityGraph(attributePaths = {
            "matchResult",
            "matchResult.jobPosting"
    })
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

    List<Notification> findAllByCompany_PublicIdAndIsReadFalse(String companyPublicId);
}
