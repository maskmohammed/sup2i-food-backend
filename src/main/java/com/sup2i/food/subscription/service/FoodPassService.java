package com.sup2i.food.subscription.service;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.qr.domain.QrCredential;
import com.sup2i.food.qr.domain.QrCredentialType;
import com.sup2i.food.qr.repository.QrCredentialRepository;
import com.sup2i.food.qr.service.QrCredentialService;
import com.sup2i.food.subscription.api.dto.FoodPassActionRequest;
import com.sup2i.food.subscription.api.dto.FoodPassEventResponse;
import com.sup2i.food.subscription.api.dto.FoodPassResponse;
import com.sup2i.food.subscription.domain.FoodPass;
import com.sup2i.food.subscription.domain.FoodPassEvent;
import com.sup2i.food.subscription.domain.FoodPassEventType;
import com.sup2i.food.subscription.domain.FoodPassStatus;
import com.sup2i.food.subscription.exception.SubscriptionConflictException;
import com.sup2i.food.subscription.exception.SubscriptionNotFoundException;
import com.sup2i.food.subscription.exception.SubscriptionValidationException;
import com.sup2i.food.subscription.repository.FoodPassEventRepository;
import com.sup2i.food.subscription.repository.FoodPassRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FoodPassService {

    private final FoodPassRepository foodPassRepository;
    private final FoodPassEventRepository eventRepository;
    private final QrCredentialService qrCredentialService;
    private final QrCredentialRepository qrCredentialRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public FoodPassService(
        FoodPassRepository foodPassRepository,
        FoodPassEventRepository eventRepository,
        QrCredentialService qrCredentialService,
        QrCredentialRepository qrCredentialRepository,
        StudentRepository studentRepository,
        UserRepository userRepository
    ) {
        this.foodPassRepository =
            foodPassRepository;

        this.eventRepository =
            eventRepository;

        this.qrCredentialService =
            qrCredentialService;

        this.qrCredentialRepository =
            qrCredentialRepository;

        this.studentRepository =
            studentRepository;

        this.userRepository =
            userRepository;
    }

    @Transactional
    public FoodPassResponse issue(
        UUID operatorUserId,
        UUID studentUserId
    ) {

        User operator =
            operator(operatorUserId);

        Student student =
            studentRepository
                .findByUserId(studentUserId)
                .orElseThrow(() ->
                    new SubscriptionValidationException(
                        "Student does not exist."
                    )
                );

        if (
            !student.getUser()
                .getOrganization()
                .getId()
                .equals(
                    operator.getOrganization()
                        .getId()
                )
        ) {
            throw new SubscriptionNotFoundException(
                "Student does not exist."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        FoodPass previous =
            foodPassRepository
                .findByStudent_IdAndStatus(
                    student.getId(),
                    FoodPassStatus.ACTIVE
                )
                .orElse(null);

        if (
            previous != null
        ) {
            qrCredentialService.revoke(
                previous.getCredential()
                    .getId(),
                now
            );

            previous.markReplaced();

            foodPassRepository.saveAndFlush(
                previous
            );
        }

        QrCredentialService.IssuedCredential issued =
            qrCredentialService.issueDetailed(
                QrCredentialType.FOOD_PASS,
                student.getId(),
                null
            );

        QrCredential credential =
            qrCredentialRepository
                .findById(
                    issued.credentialId()
                )
                .orElseThrow(() ->
                    new SubscriptionNotFoundException(
                        "Credential does not exist."
                    )
                );

        FoodPass foodPass =
            new FoodPass(
                student,
                credential,
                cardNumber(),
                operator,
                null
            );

        foodPass.activate();

        if (
            previous != null
        ) {

            foodPass.setReplacedFrom(
                previous
            );

            eventRepository.save(
                new FoodPassEvent(
                    previous,
                    FoodPassEventType.REPLACED,
                    "Replaced by a new food pass.",
                    operator
                )
            );
        }

        foodPassRepository
            .saveAndFlush(foodPass);

        eventRepository.save(
            new FoodPassEvent(
                foodPass,
                FoodPassEventType.ISSUED,
                "Food pass issued.",
                operator
            )
        );

        return response(
            foodPass,
            issued.rawToken()
        );
    }

    @Transactional
    public FoodPassResponse block(
        UUID operatorUserId,
        UUID foodPassId,
        FoodPassActionRequest request
    ) {

        User operator =
            operator(operatorUserId);

        FoodPass foodPass =
            owned(operator, foodPassId);

        if (
            foodPass.getStatus()
                != FoodPassStatus.ACTIVE
        ) {
            throw new SubscriptionConflictException(
                "Only an active food pass can be blocked."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        qrCredentialService.revoke(
            foodPass.getCredential()
                .getId(),
            now
        );

        foodPass.block(
            now,
            request.reason()
        );

        foodPassRepository
            .saveAndFlush(foodPass);

        eventRepository.save(
            new FoodPassEvent(
                foodPass,
                FoodPassEventType.BLOCKED,
                request.reason(),
                operator
            )
        );

        return response(foodPass, null);
    }

    @Transactional
    public FoodPassResponse reportLost(
        UUID operatorUserId,
        UUID foodPassId,
        FoodPassActionRequest request
    ) {

        User operator =
            operator(operatorUserId);

        FoodPass foodPass =
            owned(operator, foodPassId);

        if (
            foodPass.getStatus()
                != FoodPassStatus.ACTIVE
        ) {
            throw new SubscriptionConflictException(
                "Only an active food pass can be reported lost."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        qrCredentialService.revoke(
            foodPass.getCredential()
                .getId(),
            now
        );

        foodPass.reportLost(
            now,
            request.reason()
        );

        foodPassRepository
            .saveAndFlush(foodPass);

        eventRepository.save(
            new FoodPassEvent(
                foodPass,
                FoodPassEventType.LOST,
                request.reason(),
                operator
            )
        );

        return response(foodPass, null);
    }

    @Transactional
    public FoodPassResponse reactivate(
        UUID operatorUserId,
        UUID foodPassId,
        FoodPassActionRequest request
    ) {

        User operator =
            operator(operatorUserId);

        FoodPass foodPass =
            owned(operator, foodPassId);

        if (
            foodPass.getStatus()
                != FoodPassStatus.BLOCKED
            && foodPass.getStatus()
                != FoodPassStatus.LOST
        ) {
            throw new SubscriptionConflictException(
                "Only a blocked or lost food pass can be reactivated."
            );
        }

        QrCredentialService.IssuedCredential issued =
            qrCredentialService.issueDetailed(
                QrCredentialType.FOOD_PASS,
                foodPass.getStudent()
                    .getId(),
                null
            );

        QrCredential credential =
            qrCredentialRepository
                .findById(
                    issued.credentialId()
                )
                .orElseThrow(() ->
                    new SubscriptionNotFoundException(
                        "Credential does not exist."
                    )
                );

        foodPass.reactivate(credential);

        foodPassRepository
            .saveAndFlush(foodPass);

        eventRepository.save(
            new FoodPassEvent(
                foodPass,
                FoodPassEventType.REACTIVATED,
                request.reason(),
                operator
            )
        );

        return response(
            foodPass,
            issued.rawToken()
        );
    }

    @Transactional
    public FoodPassResponse revoke(
        UUID operatorUserId,
        UUID foodPassId,
        FoodPassActionRequest request
    ) {

        User operator =
            operator(operatorUserId);

        FoodPass foodPass =
            owned(operator, foodPassId);

        if (
            foodPass.getStatus()
                != FoodPassStatus.ACTIVE
            && foodPass.getStatus()
                != FoodPassStatus.BLOCKED
            && foodPass.getStatus()
                != FoodPassStatus.LOST
        ) {
            throw new SubscriptionConflictException(
                "This food pass cannot be revoked."
            );
        }

        qrCredentialService.revoke(
            foodPass.getCredential()
                .getId(),
            OffsetDateTime.now()
        );

        foodPass.revoke();

        foodPassRepository
            .saveAndFlush(foodPass);

        eventRepository.save(
            new FoodPassEvent(
                foodPass,
                FoodPassEventType.REVOKED,
                request.reason(),
                operator
            )
        );

        return response(foodPass, null);
    }

    @Transactional(readOnly = true)
    public FoodPassResponse myFoodPass(
        UUID studentUserId
    ) {

        Student student =
            studentRepository
                .findByUserId(studentUserId)
                .orElseThrow(() ->
                    new SubscriptionValidationException(
                        "Authenticated user is not a registered student."
                    )
                );

        FoodPass foodPass =
            foodPassRepository
                .findByStudent_IdAndStatus(
                    student.getId(),
                    FoodPassStatus.ACTIVE
                )
                .orElseGet(() ->
                    foodPassRepository
                        .findAllByStudent_IdOrderByIssuedAtDesc(
                            student.getId()
                        )
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                            new SubscriptionNotFoundException(
                                "No food pass has been issued for this student."
                            )
                        )
                );

        return response(foodPass, null);
    }

    @Transactional(readOnly = true)
    public List<FoodPassResponse> listByOrganization(
        UUID operatorUserId
    ) {

        User operator =
            operator(operatorUserId);

        return foodPassRepository
            .findAllByStudent_User_Organization_IdOrderByIssuedAtDesc(
                operator.getOrganization()
                    .getId()
            )
            .stream()
            .map(foodPass ->
                response(foodPass, null)
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FoodPassEventResponse> events(
        UUID operatorUserId,
        UUID foodPassId
    ) {

        User operator =
            operator(operatorUserId);

        owned(operator, foodPassId);

        return eventRepository
            .findAllByFoodPass_IdOrderByCreatedAtAsc(
                foodPassId
            )
            .stream()
            .map(this::eventResponse)
            .toList();
    }

    // =========================================================
    // INTERNALS
    // =========================================================

    private FoodPass owned(
        User operator,
        UUID foodPassId
    ) {

        return foodPassRepository
            .findByIdAndStudent_User_Organization_Id(
                foodPassId,
                operator.getOrganization()
                    .getId()
            )
            .orElseThrow(() ->
                new SubscriptionNotFoundException(
                    "Food pass does not exist."
                )
            );
    }

    private User operator(
        UUID actorId
    ) {

        return userRepository
            .findById(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private String cardNumber() {

        return "FP-"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private FoodPassResponse response(
        FoodPass foodPass,
        String qrToken
    ) {

        return new FoodPassResponse(
            foodPass.getId(),
            foodPass.getStudent()
                .getId(),
            foodPass.getCredential()
                .getId(),
            foodPass.getCardNumber(),
            foodPass.getStatus(),
            foodPass.getIssuedAt(),
            foodPass.getExpiresAt(),
            foodPass.getBlockReason(),
            foodPass.getIssuedBy()
                == null
                    ? null
                    : foodPass.getIssuedBy()
                        .getId(),
            qrToken
        );
    }

    private FoodPassEventResponse eventResponse(
        FoodPassEvent event
    ) {

        return new FoodPassEventResponse(
            event.getId(),
            event.getEventType(),
            event.getReason(),
            event.getPerformedBy()
                == null
                    ? null
                    : event.getPerformedBy()
                        .getId(),
            event.getCreatedAt()
        );
    }
}