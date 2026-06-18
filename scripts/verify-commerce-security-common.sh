#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
. "$script_dir/lib/common.sh"
root="$(repo_root "$script_dir")"
cd "$root"

info "commerce-security-common verifier"
info "evidence is bounded: Maven goal and test result only; no tokens or secrets are printed"

[ -f commerce-security-common/pom.xml ] \
  || die "commerce-security-common/pom.xml missing; shared security primitives are not scaffolded"

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

java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')"
case "$java_version" in
  26*|27*|28*|29*) ;;
  *) die "JDK 26+ required; JAVA_HOME reports $java_version" ;;
esac

if [ -x auth-service/mvnw ]; then
  auth-service/mvnw -B -f commerce-security-common/pom.xml clean test
else
  require_cmd mvn "Install Maven or restore auth-service/mvnw."
  mvn -B -f commerce-security-common/pom.xml clean test
fi

success "HARNESS-COMMERCE-SECURITY-COMMON passed"
