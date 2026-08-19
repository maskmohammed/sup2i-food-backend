package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository
    extends JpaRepository<Student, UUID> {

    Optional<Student> findByUserId(UUID userId);
}