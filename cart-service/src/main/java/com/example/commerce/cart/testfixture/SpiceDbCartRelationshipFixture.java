package com.example.commerce.cart.testfixture;

import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.Relationship;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.SubjectRef;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test-fixture")
final class SpiceDbCartRelationshipFixture implements CartRelationshipFixture {

  private static final ResourceRef ALICE_CART = new ResourceRef("cart", "alice-cart");
  private static final ResourceRef BOB_CART = new ResourceRef("cart", "bob-cart");
  private static final SubjectRef ALICE = SubjectRef.user("alice");
  private static final SubjectRef BOB = SubjectRef.user("bob");
  private static final String OWNER = "owner";

  private final AuthorizationClient authorizationClient;

  SpiceDbCartRelationshipFixture(AuthorizationClient authorizationClient) {
    this.authorizationClient = authorizationClient;
  }

  @Override
  public void restoreLocalSeed() {
    restoreAliceOwner();
    authorizationClient.writeRelationship(new Relationship(BOB_CART, OWNER, BOB));
  }

  @Override
  public void restoreAliceOwner() {
    authorizationClient.writeRelationship(new Relationship(ALICE_CART, OWNER, ALICE));
  }

  @Override
  public void removeAliceOwner() {
    authorizationClient.deleteRelationship(new Relationship(ALICE_CART, OWNER, ALICE));
  }
}
