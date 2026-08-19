package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.AuthLoginEvent;
import com.sup2i.food.identity.domain.AuthLoginResult;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.AuthLoginEventRepository;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.util.UUID;

@Service
public class AuthLoginAuditService {

    private final AuthLoginEventRepository repository;
    private final UserRepository userRepository;
    private final TokenHashService tokenHashService;

    public AuthLoginAuditService(
        AuthLoginEventRepository repository,
        UserRepository userRepository,
        TokenHashService tokenHashService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tokenHashService = tokenHashService;
    }

    /*
     * SUCCESS reste dans la transaction normale du login :
     * token + lastLogin + audit SUCCESS sont atomiques.
     */
    @Transactional
    public void recordSuccess(
        UUID userId,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        record(
            AuthLoginResult.SUCCESS,
            userId,
            identifier,
            null,
            ipAddress,
            userAgent
        );
    }

    /*
     * FAILED doit survivre au rollback du login.
     */
    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void recordFailure(
        UUID userId,
        String identifier,
        String reason,
        InetAddress ipAddress,
        String userAgent
    ) {
        record(
            AuthLoginResult.FAILED,
            userId,
            identifier,
            reason,
            ipAddress,
            userAgent
        );
    }

    /*
     * BLOCKED / SUSPENDED / RATE_LIMITED doivent également
     * survivre au rollback du login.
     */
    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void recordBlocked(
        UUID userId,
        String identifier,
        String reason,
        InetAddress ipAddress,
        String userAgent
    ) {
        record(
            AuthLoginResult.BLOCKED,
            userId,
            identifier,
            reason,
            ipAddress,
            userAgent
        );
    }

    private void record(
        AuthLoginResult result,
        UUID userId,
        String identifier,
        String reason,
        InetAddress ipAddress,
        String userAgent
    ) {
        AuthLoginEvent event =
            new AuthLoginEvent(result);

        if (userId != null) {
            User user =
                userRepository.getReferenceById(
                    userId
                );

            event.setUser(user);
        }

        event.setIdentifierHash(
            tokenHashService.hash(identifier)
        );

        event.setFailureReason(reason);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);

        repository.save(event);
    }
}