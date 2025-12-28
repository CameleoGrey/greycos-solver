#!/bin/bash

# Script to run core module tests one by one to identify hanging tests
# Usage: ./run-core-tests-individually.sh [timeout_in_seconds]

set -e

# Configuration
TIMEOUT=${1:-300}  # Default timeout: 5 minutes per test
LOG_DIR="./test-run-logs"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="$LOG_DIR/test_run_$TIMESTAMP.log"
HANGING_TESTS_FILE="$LOG_DIR/hanging_tests_$TIMESTAMP.txt"
PASSED_TESTS_FILE="$LOG_DIR/passed_tests_$TIMESTAMP.txt"
FAILED_TESTS_FILE="$LOG_DIR/failed_tests_$TIMESTAMP.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create log directory
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "Core Module Test Runner"
echo "=========================================="
echo "Timeout per test: ${TIMEOUT}s"
echo "Log directory: $LOG_DIR"
echo "=========================================="
echo ""

# Initialize counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
HANGING_TESTS=0

# Clear result files
> "$HANGING_TESTS_FILE"
> "$PASSED_TESTS_FILE"
> "$FAILED_TESTS_FILE"

# Log header
{
    echo "=========================================="
    echo "Core Module Test Run - $(date)"
    echo "=========================================="
    echo "Timeout per test: ${TIMEOUT}s"
    echo ""
} | tee -a "$LOG_FILE"

# Find all test files
echo "Finding all test files in core module..."
TEST_FILES=$(find core/src/test/java -name "*Test.java" | sort)
TOTAL_TEST_FILES=$(echo "$TEST_FILES" | wc -l)

echo "Found $TOTAL_TEST_FILES test files"
echo ""
echo "Starting test execution..."
echo ""

# Process each test file
TEST_NUM=0
while IFS= read -r test_file; do
    TEST_NUM=$((TEST_NUM + 1))
    
    # Convert file path to test class name
    test_class=$(echo "$test_file" | sed 's|^core/src/test/java/||' | sed 's|\.java$||' | tr '/' '.')
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    echo -n "[$TEST_NUM/$TOTAL_TEST_FILES] Running: $test_class ... "
    
    # Run the test with timeout
    {
        echo ""
        echo "----------------------------------------"
        echo "Test: $test_class"
        echo "Time: $(date)"
        echo "----------------------------------------"
    } >> "$LOG_FILE"
    
    # Use timeout command to run the test
    if timeout "$TIMEOUT" mvn test -pl core -Dtest="$test_class" -q >> "$LOG_FILE" 2>&1; then
        # Test passed
        echo -e "${GREEN}PASSED${NC}"
        echo "$test_class" >> "$PASSED_TESTS_FILE"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo "Result: PASSED" >> "$LOG_FILE"
    else
        exit_code=$?
        if [ $exit_code -eq 124 ]; then
            # Timeout occurred - test is hanging
            echo -e "${RED}HANGING (timeout after ${TIMEOUT}s)${NC}"
            echo "$test_class" >> "$HANGING_TESTS_FILE"
            HANGING_TESTS=$((HANGING_TESTS + 1))
            echo "Result: HANGING (timeout)" >> "$LOG_FILE"
            echo ""
            echo -e "${YELLOW}WARNING: Test $test_class appears to be hanging!${NC}"
            echo "This test has been logged to: $HANGING_TESTS_FILE"
        else
            # Test failed with error
            echo -e "${RED}FAILED (exit code: $exit_code)${NC}"
            echo "$test_class" >> "$FAILED_TESTS_FILE"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            echo "Result: FAILED (exit code: $exit_code)" >> "$LOG_FILE"
        fi
    fi
    
    # Small delay between tests
    sleep 0.5
    
done <<< "$TEST_FILES"

# Summary
echo ""
echo "=========================================="
echo "Test Execution Summary"
echo "=========================================="
echo "Total tests run: $TOTAL_TESTS"
echo -e "${GREEN}Passed: $PASSED_TESTS${NC}"
echo -e "${RED}Failed: $FAILED_TESTS${NC}"
echo -e "${YELLOW}Hanging: $HANGING_TESTS${NC}"
echo "=========================================="
echo ""

# Log summary to file
{
    echo ""
    echo "=========================================="
    echo "Summary"
    echo "=========================================="
    echo "Total tests run: $TOTAL_TESTS"
    echo "Passed: $PASSED_TESTS"
    echo "Failed: $FAILED_TESTS"
    echo "Hanging: $HANGING_TESTS"
    echo "=========================================="
} >> "$LOG_FILE"

# Show hanging tests if any
if [ $HANGING_TESTS -gt 0 ]; then
    echo -e "${YELLOW}Hanging tests detected:${NC}"
    cat "$HANGING_TESTS_FILE"
    echo ""
    echo "These tests appear to be hanging. Check the log file for details:"
    echo "$LOG_FILE"
fi

# Show failed tests if any
if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "${RED}Failed tests:${NC}"
    cat "$FAILED_TESTS_FILE"
    echo ""
fi

echo ""
echo "All logs saved to: $LOG_DIR"
echo "Main log file: $LOG_FILE"

# Exit with appropriate code
if [ $HANGING_TESTS -gt 0 ]; then
    exit 2
elif [ $FAILED_TESTS -gt 0 ]; then
    exit 1
else
    exit 0
fi
