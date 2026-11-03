# Build tasks for the pipeline-advanced-input-step plugin.
#
# This plugin's parent POM targets Maven 4 (its maven-hpi-plugin bundles a
# Maven-4-era Sisu that the Maven 3.9.x container cannot scan, so the `hpi`
# packaging fails to register under Maven 3). The toolchain (Maven 4 + JDK 21)
# is pinned in mise.toml and resolved via `mise exec`, so JAVA_HOME and the
# Maven version are set consistently here and in CI. A local settings.xml makes
# both Maven Central and the Jenkins community repository available.

set shell := ["bash", "-euo", "pipefail", "-c"]

settings := justfile_directory() / "maven-settings.xml"

# Run maven under the mise-pinned toolchain with the local settings. Tools are
# named explicitly so mise does not try to provision unrelated global tools.
_mvn := "mise exec java@21 maven@4.0.0-rc-7 -- mvn -s " + settings

# List available recipes (default)
default:
    @just --list

# Install the pinned toolchain (Maven 4 + JDK 21) from mise.toml
setup:
    mise install

# Compile main sources
compile:
    {{_mvn}} compile

# Run the test suite
test:
    {{_mvn}} test

# Build the .hpi without running tests
package:
    {{_mvn}} -DskipTests package

# Full verify: compile, test, package, and run plugin checks
verify:
    {{_mvn}} verify

# Remove build output
clean:
    {{_mvn}} clean

# Launch a local Jenkins with the plugin loaded (http://localhost:8080/jenkins)
run:
    {{_mvn}} hpi:run

# Build the hpi and emit a signed update-center.json (needs a key/cert pair).
# Example: just update-center 1.0 https://uc.example/advanced-input key.pem cert.pem
update-center version base_url key cert:
    {{_mvn}} -DskipTests -Dset.changelist -Dchangelist={{version}} clean package
    cp target/pipeline-advanced-input-step.hpi target/pipeline-advanced-input-step.hpi
    uv run --script scripts/make-update-center.py \
        --hpi target/pipeline-advanced-input-step.hpi \
        --base-url {{base_url}} --key {{key}} --cert {{cert}} \
        --out update-center.json

# Print the resolved toolchain for debugging
doctor:
    @echo "settings: {{settings}}"
    @mise ls
    @{{_mvn}} -version
