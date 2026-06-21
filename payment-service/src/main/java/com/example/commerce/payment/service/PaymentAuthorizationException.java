package com.example.commerce.payment.service;

import com.example.commerce.web.error.BusinessRuleException;

/**
 * The S2S caller is not an authorized payment caller (wrong client or missing
 * {@code payments:authorize} scope). This is a defense-in-depth guard at the service layer; the
 * {@code ServicePrincipalFilter} rejects unauthorized tokens with 401 before the service runs.
 *
 * <p>The frozen starter contract exposes only four domain status families (404/409/422/503); 403 is
 * reserved for the security {@code AuthorizationDeniedException}. This domain-invariant violation
 * therefore maps to HTTP 422, not the previous 403. See the contract-gap note in the change report.
 */
public final class PaymentAuthorizationException extends BusinessRuleException {

  public PaymentAuthorizationException(String message) {
    super("payment-caller-not-authorized", message);
  }
}
