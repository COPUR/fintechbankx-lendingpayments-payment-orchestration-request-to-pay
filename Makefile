# Makefile for the Request-to-Pay Service

# Variables
GRADLE_CMD = ./gradlew --no-daemon

# Default target: run all checks
all: clean build test security

# Clean the project
clean:
	$(GRADLE_CMD) clean

# Build the project
build:
	$(GRADLE_CMD) assemble -x test

# Run tests and coverage checks
test:
	$(GRADLE_CMD) check

# Run security checks
security:
	@echo "Running Gitleaks..."
	@if ! command -v gitleaks &> /dev/null; then \
		echo "Gitleaks is not installed. Please install it from https://github.com/gitleaks/gitleaks"; \
		exit 1; \
	fi
	gitleaks detect --source . --no-banner --redact --no-git --config .gitleaks.toml

# A phony target to allow running 'all' even if a file named 'all' exists
.PHONY: all clean build test security
