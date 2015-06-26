#include <pebble.h>

// Constants
static const uint8_t DATA_LOG_ID = 164;

// Function Prototypes
void welcome_init(void);
void welcome_deinit(void);

// Global Variables
DataLoggingSessionRef logging_session;


void handle_init(void) {
	// Load the welcome screen
	welcome_init();
  // Start the data logging service, we use only one for the application duration
  logging_session = data_logging_create(DATA_LOG_ID, DATA_LOGGING_BYTE_ARRAY, 6, false);
}

void handle_deinit(void) {
  // When we don't need to log anything else, we can close off the session.
  data_logging_finish(logging_session);
	welcome_deinit();
}

int main(void) {
  handle_init();
  app_event_loop();
  handle_deinit();
}
