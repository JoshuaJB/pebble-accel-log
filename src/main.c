#include <pebble.h>

// Constants
static const uint8_t DATA_LOG_ID = 164;

// Function Prototypes
void welcome_init(void);
void welcome_deinit(void);

void handle_init(void) {
	// Load the welcome screen
	welcome_init();
}

void handle_deinit(void) {
	welcome_deinit();
}

int main(void) {
  handle_init();
  app_event_loop();
  handle_deinit();
}
