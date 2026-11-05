cmake_minimum_required(VERSION 3.22.1)
project("bytewave_disasm_jni" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    bytewave_disasm_jni
    SHARED
    bytewave_disasm_jni.cpp
)

target_compile_options(
    bytewave_disasm_jni
    PRIVATE
    -O3
    -ffast-math
    -Wall
    -Wextra
)

find_library(
    log-lib
    log
)

target_link_libraries(
    bytewave_disasm_jni
    ${log-lib}
)
