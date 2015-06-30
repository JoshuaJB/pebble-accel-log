#include <pebble.h>
#include "welcome.h"
  
// Flag variable to protect from double frees
static int finished = false;

void main_deinit() {
  if (finished)
    return;
  window_stack_pop_all(false);
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished pop");
  welcome_deinit();
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished main");
  finished = true;
}

int main() {
  welcome_init();
  app_event_loop();
  main_deinit();
}