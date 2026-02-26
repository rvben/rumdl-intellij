.PHONY: build test verify clean publish

build:
	./gradlew buildPlugin

test:
	./gradlew test

verify:
	./gradlew verifyPlugin

clean:
	./gradlew clean

publish:
	./gradlew publishPlugin

# CI targets (mirror what GitHub Actions runs)
ci: clean build test

ci-release: clean build test verify
