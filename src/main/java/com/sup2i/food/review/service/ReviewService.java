package com.sup2i.food.review.service;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.review.api.dto.CreateReviewRequest;
import com.sup2i.food.review.api.dto.ModerateReviewRequest;
import com.sup2i.food.review.api.dto.ReviewResponse;
import com.sup2i.food.review.domain.ModerationStatus;
import com.sup2i.food.review.domain.Review;
import com.sup2i.food.review.exception.ReviewConflictException;
import com.sup2i.food.review.exception.ReviewNotFoundException;
import com.sup2i.food.review.exception.ReviewValidationException;
import com.sup2i.food.review.repository.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private static final List<OrderStatus>
        DELIVERED_STATUSES =
            List.of(
                OrderStatus.COLLECTED,
                OrderStatus.COMPLETED
            );

    private static final int
        MAX_PHOTOS =
            5;

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(
        UserRepository userRepository,
        StudentRepository studentRepository,
        ProductRepository productRepository,
        OrderRepository orderRepository,
        ReviewRepository reviewRepository
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
    }

    // =========================================================
    // STUDENT OPERATIONS
    // =========================================================

    @Transactional
    public ReviewResponse create(
        UUID actorId,
        CreateReviewRequest request
    ) {
        Student student =
            requiredStudent(actorId);

        UUID organizationId =
            student.getCampus()
                .getOrganization()
                .getId();

        boolean targetsProduct =
            request.productId() != null;

        boolean targetsOrder =
            request.orderId() != null;

        if (
            targetsProduct == targetsOrder
        ) {
            throw new ReviewValidationException(
                "A review must target exactly one product or delivered order."
            );
        }

        List<String> photos =
            sanitizePhotos(request.photos());

        Review review;

        if (targetsProduct) {
            review =
                productReview(
                    student,
                    organizationId,
                    request.productId(),
                    request.rating(),
                    request.comment(),
                    photos
                );
        } else {
            review =
                orderReview(
                    student,
                    organizationId,
                    request.orderId(),
                    request.rating(),
                    request.comment(),
                    photos
                );
        }

        try {
            review = reviewRepository.save(review);
        } catch (DataIntegrityViolationException exception) {
            throw new ReviewConflictException(
                "Vous avez déjà laissé un avis pour cette cible."
            );
        }

        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listProductReviews(
        UUID productId,
        int page,
        int size
    ) {
        Product product =
            productRepository
                .findById(productId)
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Product does not exist."
                    )
                );

        if (!product.isActive()) {
            throw new CatalogNotFoundException(
                "Product does not exist."
            );
        }

        Page<Review> reviews =
            reviewRepository
                .findVisibleForProduct(
                    product.getId(),
                    ModerationStatus.APPROVED,
                    PageRequest.of(
                        page,
                        size
                    )
                );

        return PageResponse.from(
            reviews.map(ReviewResponse::from)
        );
    }

    // =========================================================
    // ADMIN OPERATIONS
    // =========================================================

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> pending(
        UUID actorId,
        int page,
        int size
    ) {
        requiredUser(actorId);

        Page<Review> reviews =
            reviewRepository
                .findAllByModerationStatusOrderByCreatedAtAsc(
                    ModerationStatus.PENDING,
                    PageRequest.of(
                        page,
                        size
                    )
                );

        return PageResponse.from(
            reviews.map(ReviewResponse::from)
        );
    }

    @Transactional
    public ReviewResponse moderate(
        UUID actorId,
        UUID reviewId,
        ModerateReviewRequest request
    ) {
        User moderator =
            requiredUser(actorId);

        Review review =
            reviewRepository
                .findOwnedByOrganization(
                    reviewId,
                    moderator.getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new ReviewNotFoundException(
                        "Review does not exist."
                    )
                );

        ModerationStatus target =
            ReviewModeration.moderate(
                review.getModerationStatus(),
                request.status()
            );

        review.moderate(
            target,
            moderator,
            OffsetDateTime.now()
        );

        reviewRepository.save(review);

        return ReviewResponse.from(review);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Review productReview(
        Student student,
        UUID organizationId,
        UUID productId,
        int rating,
        String comment,
        List<String> photos
    ) {
        Product product =
            productRepository
                .findCatalogProduct(
                    productId,
                    organizationId
                )
                .orElseThrow(() ->
                    new ReviewNotFoundException(
                        "Product does not exist in this organization."
                    )
                );

        if (
            reviewRepository
                .existsByStudent_IdAndProduct_Id(
                    student.getId(),
                    product.getId()
                )
        ) {
            throw new ReviewConflictException(
                "Vous avez déjà laissé un avis pour ce produit."
            );
        }

        return new Review(
            student,
            product,
            null,
            rating,
            comment,
            photos
        );
    }

    private Review orderReview(
        Student student,
        UUID organizationId,
        UUID orderId,
        int rating,
        String comment,
        List<String> photos
    ) {
        Order order =
            orderRepository
                .findByIdAndOrganization_IdAndStudent_Id(
                    orderId,
                    organizationId,
                    student.getId()
                )
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        if (
            !DELIVERED_STATUSES.contains(order.getStatus())
        ) {
            throw new ReviewConflictException(
                "Seules les commandes livrées peuvent être évaluées."
            );
        }

        if (
            reviewRepository
                .existsByStudent_IdAndOrder_Id(
                    student.getId(),
                    order.getId()
                )
        ) {
            throw new ReviewConflictException(
                "Vous avez déjà laissé un avis pour cette commande."
            );
        }

        return new Review(
            student,
            null,
            order,
            rating,
            comment,
            photos
        );
    }

    private List<String> sanitizePhotos(
        List<String> rawPhotos
    ) {
        if (
            rawPhotos == null
                || rawPhotos.isEmpty()
        ) {
            return List.of();
        }

        LinkedHashSet<String> unique =
            new LinkedHashSet<>();

        for (
            String photo
            : rawPhotos
        ) {
            if (
                photo != null
                    && !photo.isBlank()
                    && unique.size() < MAX_PHOTOS
            ) {
                unique.add(photo.trim());
            }
        }

        return List.copyOf(unique);
    }

    private User requiredUser(
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

    private Student requiredStudent(
        UUID actorId
    ) {
        return studentRepository
            .findByUserId(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated student does not exist."
                )
            );
    }
}