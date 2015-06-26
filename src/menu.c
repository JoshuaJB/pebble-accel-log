#include <pebble.h>
	
// Constants
#define NUMBER_OF_SECTIONS 1
#define NUMBER_OF_ITEMS 11

// Global variables
static Window *window;
static SimpleMenuLayer *menu_layer;
static SimpleMenuSection section_array[NUMBER_OF_SECTIONS];
static SimpleMenuItem item_array[NUMBER_OF_ITEMS];

// Function pointers
void prerun_init(void);
void prerun_deinit(void);

// Loads the next screen whenever a menu item is selected
static void menu_select_callback(int index, void *context){
	prerun_init();
}

void menu_init(void){
	window = window_create();
	
	Layer *window_layer = window_get_root_layer(window);
	GRect bounds = layer_get_bounds(window_layer);
	
	// Creating the menu entries
	item_array[0] = (SimpleMenuItem) {
		.title = "Dominant Wrist",
		.callback = menu_select_callback,
	};	
	item_array[1] = (SimpleMenuItem) {
		.title = "Non-Dominant Wrist",
	};	
	item_array[2] = (SimpleMenuItem) {
		.title = "Waist",
	};	
	item_array[3] = (SimpleMenuItem) {
		.title = "Right Ankle",
	};	
	item_array[4] = (SimpleMenuItem) {
		.title = "Left Ankle",
	};	
	item_array[5] = (SimpleMenuItem) {
		.title = "Upper Dominant Arm",
	};	
	item_array[6] = (SimpleMenuItem) {
		.title = "Upper Non-Dominant Arm",
	};	
	item_array[7] = (SimpleMenuItem) {
		.title = "Right Thigh",
	};	
	item_array[8] = (SimpleMenuItem) {
		.title = "Left Thigh",
	};		
	item_array[9] = (SimpleMenuItem) {
		.title = "Chest",
	};	
	item_array[10] = (SimpleMenuItem) {
		.title = "Neck"
	};

	// Adding entries to the section
	section_array[0] = (SimpleMenuSection) {
		.num_items = NUMBER_OF_ITEMS,
		.items = item_array
	};
	
	// Adding the menu to the window
	menu_layer = simple_menu_layer_create(bounds, window, section_array, NUMBER_OF_SECTIONS, NULL);
	layer_remove_child_layers(window_get_root_layer(window));
	layer_add_child(window_layer, simple_menu_layer_get_layer(menu_layer));
	
	window_stack_push(window, true);
}

void menu_deinit(void){
	prerun_deinit();
	layer_remove_from_parent(simple_menu_layer_get_layer(menu_layer));
	simple_menu_layer_destroy(menu_layer);
	window_destroy(window);
}