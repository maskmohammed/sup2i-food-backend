package com.sup2i.food.audit.service;

import com.sup2i.food.audit.domain.AuditLog;
import com.sup2i.food.audit.repository.AuditLogRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final String SOURCE_API =
        "API";

    private static final String RESULT_SUCCESS =
        "SUCCESS";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(
        AuditLogRepository auditLogRepository,
        UserRepository userRepository
    ) {
        this.auditLogRepository =
            auditLogRepository;

        this.userRepository =
            userRepository;
    }

    /**
     * Enregistre une entrée d'audit dans sa propre transaction
     * (REQUIRES_NEW) pour survivre au rollback de l'opération métier.
     */
    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void record(
        UUID organizationId,
        UUID userId,
        String action,
        String resourceType,
        UUID resourceId,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String reason
    ) {
        User user = null;

        if (userId != null) {
            user =
                userRepository.findById(userId)
                    .orElse(null);
        }

        AuditLog entry =
            new AuditLog();

        entry.record(
            organizationId,
            user,
            null,
            action,
            resourceType,
            resourceId,
            beforeData,
            afterData,
            reasonableReason(reason),
            SOURCE_API,
            resolveIp(),
            RESULT_SUCCESS
        );

        auditLogRepository.save(entry);
    }

    private String reasonableReason(
        String reason
    ) {
        if (reason == null) {
            return null;
        }

        return reason.length() <= 1000
            ? reason
            : reason.substring(0, 1000);
    }

    private InetAddress resolveIp() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                    RequestContextHolder
                        .getRequestAttributes();

            if (attributes == null) {
                return null;
            }

            HttpServletRequest request =
                attributes.getRequest();

            if (request == null) {
                return null;
            }

            return InetAddress.getByName(
                request.getRemoteAddr()
            );
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}