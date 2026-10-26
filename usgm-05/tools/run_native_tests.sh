#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build-native
gcc -std=c11 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer \
 -Ifirmware/components/usgm_core/include firmware/components/usgm_core/usgm_core.c tests/test_core.c \
 -o build-native/test_core
ASAN_OPTIONS=detect_leaks=1 ./build-native/test_core
gcc -std=c11 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer \
 -Ifirmware/components/usgm_core/include firmware/components/usgm_core/usgm_core.c tests/test_stress.c \
 -o build-native/test_stress
ASAN_OPTIONS=detect_leaks=1 ./build-native/test_stress
node --test tests/test_report.mjs
python3 -m unittest discover -s tests -p 'test_*.py' -v
