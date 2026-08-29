package com.sup2i.food.scan.api.dto;

public record ProductScanResult(
    String type,
    ProductScanProductResponse product
) implements ScanResponse {

    public ProductScanResult(
        ProductScanProductResponse product
    ) {
        this(
            "PRODUCT",
            product
        );
    }
}