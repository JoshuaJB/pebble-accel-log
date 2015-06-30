#include <pebble.h>
#include "logging.h"
#include "prerun.h"

// Global Variables
static Window *window;
static TextLayer  *text_layer;
static int menu_index;

// Called whenever a button is pushed to start logging
void start_logging(ClickRecognizerRef recognizer, void *context){
  logging_init(menu_index);
}

// Setup button handling
void click_config_provider2(Window *window) {
  window_single_click_subscribe(BUTTON_ID_SELECT, start_logging);	
  window_single_click_subscribe(BUTTON_ID_UP, start_logging);	
  window_single_click_subscribe(BUTTON_ID_DOWN, start_logging);	
}

void prerun_init(int index){
  menu_index = index;
  window = window_create();
  text_layer = text_layer_create(layer_get_bounds(window_get_root_layer(window)));
  text_layer_set_text(text_layer, "Ready to go!\n\nThis sensor will monitor your motion. Press the back button to choose a different part of the body or press any button on the right to begin logging.");
  layer_add_child(window_get_root_layer(window), text_layer_get_layer(text_layer));
	
  window_set_click_config_provider(window, (ClickConfigProvider) click_config_provider2);
	
  window_stack_push(window, true);
}

void prerun_deinit(){
  logging_deinit();
  layer_remove_from_parent(text_layer_get_layer(text_layer));
  text_layer_destroy(text_layer);
  window_destroy(window);
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished prerun");
}
