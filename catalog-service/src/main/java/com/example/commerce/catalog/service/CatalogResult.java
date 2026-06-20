package com.example.commerce.catalog.service;

import com.example.commerce.catalog.domain.Product;
import com.example.commerce.security.DecisionTrace;
import java.util.List;

public record CatalogResult(Product product, List<DecisionTrace> traces) {
}
