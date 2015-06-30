#ifndef LOGGING_INCLUDE
#define LOGGING_INCLUDE

#include <pebble.h>

// Constants
static const AccelSamplingRate SAMPLE_RATE = ACCEL_SAMPLING_10HZ;
enum states {
  RECORDING,
  STOPPED
};

extern void logging_init();
extern void logging_deinit();

#endif