#include <pebble.h>
#include "logging.h"

// Constants
static const AccelSamplingRate PAL_SAMPLE_RATE = ACCEL_SAMPLING_25HZ;
enum states {
  RECORDING,
  STOPPED
};

// Global Variables
static Window *window;
static TextLayer  *text_layer;
static DataLoggingSessionRef logging_session;
static enum states state;
static int data_log_id;
static int sample_num = 0;
// Debug, uncomment #define to enable
// #define DEBUG_TIMING
#ifdef DEBUG_TIMING
static uint16_t lastMSReading = 0;
static time_t lastSReading = 0;
#endif

// Function declarations
extern void main_deinit();

/* Can encode up to 16 bytes
 * @param length number of bytes to encode
 */
static void encode_bytes(unsigned char destination[], uint8_t startidx, int64_t source, uint8_t length) {
  // Big endian encoding
  for (int i = 0; i < length; i++) {
    destination[startidx + i] = source >> 8 * (length - i - 1);
  }
}

static void display_log_res(DataLoggingResult res, TextLayer * tl) {
  switch (res) {
    case DATA_LOGGING_SUCCESS:
      break; // Successful operation
    case DATA_LOGGING_BUSY:
      text_layer_set_text(tl, "Error: Someone else is writing to this logging session");
      break;
    case DATA_LOGGING_FULL:
      text_layer_set_text(tl, "Error: No more space to save data");
      break;
    case DATA_LOGGING_NOT_FOUND:
      text_layer_set_text(tl, "Error: The logging session does not exist");
      break;
    case DATA_LOGGING_CLOSED:
      text_layer_set_text(tl, "Error: The logging session was made inactive");
      break;
    case DATA_LOGGING_INVALID_PARAMS:
      text_layer_set_text(tl, "Error: An invalid parameter was passed to one of the data logging functions");
      break;
    default: //DATA_LOGGING_INTERNAL_ERR:
      text_layer_set_text(tl, "Error: The data logging session has experienced an internal error");
      break;
  }
}

static void log_timestamp(int64_t timestamp) {
  unsigned char packed_mess[6];
  encode_bytes(packed_mess, 0, timestamp, 6);
  // Timestamp messages are prefixed with a 1
  packed_mess[0] |= 0x80;
  data_logging_log(logging_session, &packed_mess, 1);
}

// Stop and start the data logging session. This /should/ flush the buffer.
static void flush_data_buffer(ClickRecognizerRef recognizer, void * context) {
  data_logging_finish(logging_session);
  logging_session = data_logging_create(data_log_id, DATA_LOGGING_BYTE_ARRAY, 6, true);
}

// Push data from the accelerometer data service to the data logging service
static void cache_accel(AccelData * data, uint32_t num_samples) {
  /* On our first message and every 1000th reading we sync the
   * timestamp to cope with limited data logging storage on the
   * Pebble.
   */
  if (sample_num % 1000 == 0)
    log_timestamp(data[0].timestamp);
#ifdef DEBUG_TIMING
  if (sample_num == 0)
    time_ms(&lastSReading, &lastMSReading);
  else {
    static char text[96];
    snprintf(text, 96, "Time diff: %lu\nShould be: 1000\n", (time(NULL) - lastSReading) * 1000 + (time_ms(NULL, NULL) - lastMSReading));
    text_layer_set_text(text_layer, text);
    time_ms(&lastSReading, &lastMSReading);
  }
#endif
  // Array of 6 byte arrays
  unsigned char packed_data[num_samples][6];
  // Store the XYZ magnitudes in the data log
  for (uint32_t i = 0; i < num_samples; i++) {
    encode_bytes(packed_data[i], 0, data[i].x, 2);
    encode_bytes(packed_data[i], 2, data[i].y, 2);
    encode_bytes(packed_data[i], 4, data[i].z, 2);
    // Reading messages are prefixed with a 0
    packed_data[i][0] &= 0x7f;
  }
  display_log_res(data_logging_log(logging_session, &packed_data, num_samples), text_layer);
  sample_num += num_samples;
}

// Start data recording
static void start(ClickRecognizerRef recognizer, void *context) {
  state = RECORDING;
  // Register acceleration event handler with a 25 sample buffer
  accel_data_service_subscribe(25, cache_accel);
  // Display the pre-run message
  text_layer_set_text(text_layer, "Logging...\n\n(press the top or middle buttons to stop, or the bottom button to flush the data buffer)");
}

// Analyse and dislay data
static void stop(ClickRecognizerRef recognizer, void *context) {
  state = STOPPED;
  // De-register acceleration event handler
  accel_data_service_unsubscribe();
  // Display appropriate message
  text_layer_set_text(text_layer, "Stopped.\n\nPress any button to exit.");
}

// Toggle state
static void switch_state(ClickRecognizerRef recognizer, void * context) {
  if (state == RECORDING)
    stop(NULL, NULL);
  else if (state == STOPPED)
    main_deinit();
}

// Setup button handling
void click_config_provider3(Window *window) {
  window_single_click_subscribe(BUTTON_ID_SELECT, switch_state);
  window_single_click_subscribe(BUTTON_ID_UP, switch_state);	
  window_single_click_subscribe(BUTTON_ID_DOWN, flush_data_buffer);
}

void logging_init(int index){
  data_log_id = index;
  // Create window and a text layer
  window = window_create();
  text_layer = text_layer_create(layer_get_bounds(window_get_root_layer(window)));
  layer_add_child(window_get_root_layer(window), text_layer_get_layer(text_layer));
  // Setup button handling
  window_set_click_config_provider(window, (ClickConfigProvider) click_config_provider3);
  // Set Accelerometer to sample rate
  // NOT WORKING: Sample rate is always 25HZ regardless
  accel_service_set_sampling_rate(PAL_SAMPLE_RATE);
  // Start the data logging service, we use only one for the application duration
  logging_session = data_logging_create(data_log_id, DATA_LOGGING_BYTE_ARRAY, 6, false);
  // Start logging
  start(NULL, NULL);
  // Display window
  window_stack_push(window, true);
}

void logging_deinit(){
  // This could cause inaccuracies <= 1s as the phone app assumes that every
  //  timestamp corelates to a reading set.
  log_timestamp((int64_t)time(NULL) * 1000 + (int64_t)time_ms(NULL, NULL));
  // When we don't need to log anything else, we can close off the session.
  data_logging_finish(logging_session);
  // De-register acceleration event handler (needed when using back to exit screen)
  accel_data_service_unsubscribe();
	
  layer_remove_from_parent(text_layer_get_layer(text_layer));
  text_layer_destroy(text_layer);
  window_destroy(window);
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished logging");
}
