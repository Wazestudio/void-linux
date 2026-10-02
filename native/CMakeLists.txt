cmake_minimum_required(VERSION 3.22.1)
project(voidlinux_native C)

add_library(voidlinux_jni SHARED
    src/main/cpp/terminal_jni.c
    src/main/cpp/pty_helper.c
)

target_compile_options(voidlinux_jni PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
    -fPIC
    -O2
)

target_link_libraries(voidlinux_jni
    android
    log
    dl
)
