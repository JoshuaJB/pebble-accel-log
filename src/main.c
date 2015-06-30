#include <pebble.h>
#include <welcome.h>
  
// Flag variable to protect from double frees
static int finished = false;

void handle_init(void) {
  // Load the welcome screen
  welcome_init();
}

void handle_deinit(void) {
  if (finished)
    return;
  window_stack_pop_all(false);
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished pop");
  welcome_deinit();
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished main");
  finished = true;
}

int main(void) {
  handle_init();
  app_event_loop();
  handle_deinit();
}
