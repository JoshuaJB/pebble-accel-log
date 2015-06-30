#include <pebble.h>
#include <menu.h>

// Global Variables
static Window *window;
static TextLayer  *text_layer;

// Called whenever a button is pushed on the welcome screen and brings up menu
void begin(ClickRecognizerRef recognizer, void *context){
  menu_init();
}

// Setup button handling
void click_config_provider(Window *window) {
  window_single_click_subscribe(BUTTON_ID_SELECT, begin);	
  window_single_click_subscribe(BUTTON_ID_UP, begin);	
  window_single_click_subscribe(BUTTON_ID_DOWN, begin);	
}

// Setting up window and placing it on stack
void welcome_init(void){
  window = window_create();
  text_layer = text_layer_create(layer_get_bounds(window_get_root_layer(window)));
  text_layer_set_text(text_layer, "Welcome!\n\nTo get started, press any button. The next screen will let you select what this Pebble is monitoring.");
  layer_add_child(window_get_root_layer(window), text_layer_get_layer(text_layer));

  window_set_click_config_provider(window, (ClickConfigProvider) click_config_provider);

  window_stack_push(window, true);
}

// Get rid of everything once we are finished
void welcome_deinit(void){
  menu_deinit();
  layer_remove_from_parent(text_layer_get_layer(text_layer));
  text_layer_destroy(text_layer);
  window_destroy(window);
  APP_LOG(APP_LOG_LEVEL_DEBUG, "Finished welcome");
}
