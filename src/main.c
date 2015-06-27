#include <pebble.h>

// Function Prototypes
void welcome_init(void);
void welcome_deinit(void);

void handle_init(void) {
	// Load the welcome screen
	welcome_init();
}

void handle_deinit(void) {
	window_stack_pop_all(true);
	welcome_deinit();
}

int main(void) {
  handle_init();
  app_event_loop();
  handle_deinit();
}
