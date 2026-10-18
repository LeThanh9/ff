cmake_minimum_required(VERSION 3.22.1)
project("ffcheat")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    ffcheat
    SHARED
    ffcheat.cpp
    dumper.cpp
)

find_library(log-lib log)

target_link_libraries(
    ffcheat
    ${log-lib}
    android
)

# Compiler optimizations for ARM
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(ffcheat PRIVATE -O3 -march=armv8-a -fvisibility=hidden)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(ffcheat PRIVATE -O3 -march=armv7-a -mfpu=neon -fvisibility=hidden)
endif()
