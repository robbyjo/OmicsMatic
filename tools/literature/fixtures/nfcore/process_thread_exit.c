/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
#define _POSIX_C_SOURCE 200809L
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

static void *worker(void *arg) {
    struct timespec brief = {0, 25000000};
    nanosleep(&brief, NULL);
    FILE *out = fopen((const char *)arg, "wx");
    if (!out) _exit(3);
    fprintf(out, "%ld\n", (long)getpid());
    fclose(out);
    struct timespec wait = {60, 0};
    nanosleep(&wait, NULL);
    return NULL;
}
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    pthread_t thread;
    if (pthread_create(&thread, NULL, worker, argv[1]) != 0) return 3;
    pthread_exit(NULL);
}
