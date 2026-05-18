.PHONY: install-rumdl build test integration-test verify clean publish ci ci-release

# Single source of truth for the pinned rumdl version lives in
# gradle.properties so the build and the contract test never disagree.
RUMDL_VERSION := $(shell sed -n 's/^rumdlContractVersion=//p' gradle.properties)

# Install the exact pinned rumdl used by the LSP contract test. Idempotent:
# skips reinstalling when the pinned version is already resolvable, so the
# local inner loop stays fast and offline.
install-rumdl:
	@if command -v rumdl >/dev/null 2>&1 && \
	    [ "$$(rumdl --version | awk '{print $$2}')" = "$(RUMDL_VERSION)" ]; then \
	  echo "rumdl $(RUMDL_VERSION) already installed"; \
	else \
	  uv tool install --force "rumdl==$(RUMDL_VERSION)"; \
	fi

build:
	./gradlew buildPlugin

# Fast unit tests only: no rumdl, no network. Safe for the IDE inner loop.
test:
	./gradlew test

# Real-server LSP contract test against the pinned rumdl (issue #2 gate).
# Pass uv's tool-bin dir so the task resolves exactly the pinned binary even
# when another rumdl is already on PATH.
integration-test: install-rumdl
	./gradlew integrationTest -PrumdlBinDir="$$(uv tool dir --bin)"

verify:
	./gradlew verifyPlugin

clean:
	./gradlew clean

publish:
	./gradlew publishPlugin

# CI targets (mirror what GitHub Actions runs)
ci: clean build test integration-test

ci-release: clean build test integration-test verify
