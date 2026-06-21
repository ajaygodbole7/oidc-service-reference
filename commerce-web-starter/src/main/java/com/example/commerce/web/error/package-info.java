/**
 * RFC 9457 problem-detail error handling: the shared exception hierarchy
 * ({@link com.example.commerce.web.error.ResourceNotFoundException} and friends) that service
 * domain exceptions extend, and the single auto-configured advice
 * ({@link com.example.commerce.web.error.GlobalExceptionHandler}) that maps every one of them to
 * a {@link org.springframework.http.ProblemDetail}.
 */
@org.jspecify.annotations.NullMarked
package com.example.commerce.web.error;
