package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductBarcodeRepository
    extends JpaRepository<ProductBarcode, UUID> {

    boolean existsByBarcode(
        String barcode
    );
}