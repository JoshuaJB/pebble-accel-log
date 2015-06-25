# pebble-accel-log
A example application of Pebble's Data Logging API which sends accelerometer data to an Android app.

## Building and Running the Pebble App
1. Open [CloudPebble](https://cloudpebble.net)
2. Click the "Import" button on the right
3. Click "Import From GitHub"
4. Give the project a name of your choice
5. Type "github.com/JoshuaJB/pebble-accel-log" in the textbox for "GitHub Project"
6. Leave the "Branch" textbox empty
7. Press "Import"
8. Once the IDE loads, choose "Compilation" from the left menu
9. Press "Run Build"
10. Once the build finishes do one of the following
  1. Click "Emulator" and "Install on APLITE" **OR**
  2. Click "Phone" and "Install and Run"

## Building and Running the Android App
1. Install [Android Studio](http://developer.android.com/sdk/index.html)
2. Either
 1. Clone this Git repository with your favorite client **OR**
 2. Click "Download ZIP" on the bottom right of the GitHub page
3. In Android Studio, open the project by left clicking the "File" menu and pressing "Open...". In the dialog that comes up, navigate to the directory where you unziped your download or cloned this repository. Select the "android" directory and press "Open".
4. While the project loads (it may take a couple minutes) you can setup your device. Follow [Google's Official Guide](http://developer.android.com/tools/device.html) then turn on "USB Debugging" in "Developer Settings" on your phone.
5. If you haven't done so yet, connect your device to your computer.
6. Press the play icon in Android Studio's main toolbar and wait for the build to complete.
7. Once the build finishes, a dialog titled "Choose Device" should appear. Select "Choose a running device", select your connected device, and press "OK".
8. The application should be automatically loaded onto your device and run.

## Usage
1. Start the Android App
2. Start the Pebble App
3. Begin recording on the Pebble App
4. Finish recording on the Pebble App
5. The Pebble's accelerometer readings are displayed on the Android app in a scrollable list.

## Help!
Contact jbak ita at cs z unc z edu where the zs are dots and there are no spaces.
