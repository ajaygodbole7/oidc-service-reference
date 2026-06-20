#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

info "catalog-service verifier"
info "evidence is bounded: Maven goal and test result only; no tokens or secrets are printed"

[ -f pom.xml ] || die "root pom.xml missing; catalog-service must build with commerce-security-common in the same reactor"
[ -f catalog-service/pom.xml ] || die "catalog-service/pom.xml missing"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')"
else
  java_version=""
fi

case "$java_version" in
  26*|27*|28*|29*) ;;
  *)
    if [ -x "$HOME/.sdkman/candidates/java/26-amzn/bin/java" ]; then
      warn "JAVA_HOME is not JDK 26+; using $HOME/.sdkman/candidates/java/26-amzn for this verifier"
      JAVA_HOME="$HOME/.sdkman/candidates/java/26-amzn"
      export JAVA_HOME
    fi
    ;;
esac

[ -n "${JAVA_HOME:-}" ] || die "JAVA_HOME must point at JDK 26 for this slice"
[ -x "$JAVA_HOME/bin/java" ] || die "JAVA_HOME does not contain an executable java: $JAVA_HOME"

auth-service/mvnw -B -f pom.xml -pl catalog-service -am clean test

success "HARNESS-CATALOG-SERVICE passed"
