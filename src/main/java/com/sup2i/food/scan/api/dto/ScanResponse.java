package com.sup2i.food.scan.api.dto;

public sealed interface ScanResponse
    permits ProductScanResult, OrderScanResult {
}