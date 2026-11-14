# TV Home user guide

Everything here works with the D-pad, OK and Back on the remote.

## The home screen

Your apps fill a grid five tiles wide. The first row is the bar: at rest it sits at the bottom of the screen and the
next row shows as a thin strip below it. Press Down to scroll the rest of the grid into view and Up to bring the bar
back.

Left and Right move along a row and carry on into the next or previous row at the ends. Up from the bar goes to the
icons at the top right; Down from there returns to the bar.

OK opens the app in focus. When you come back with Home, focus returns to the tile you left. After a restart it goes to
the app you opened last.

Back on the home screen does nothing. There is nowhere to go back to.

### The tile menu

Hold OK on a tile to open its menu:

- Open starts the app.
- Move puts an amber ring around the tile. Move it with the arrows and press OK or Back to put it down.
- Hide removes the tile from the grid. Settings → Apps brings it back.
- App details opens the system page for the app, where you can force stop, clear data or uninstall it.

New apps appear at the end of the grid. An app you uninstall and install again also goes to the end.

### The header

The clock and date are at the top left, and the weather sits under them once a city is set. The three round icons at
the top right are:

- Inputs, to switch to HDMI or another source. It shows only when the TV reports at least one input.
- Wi-Fi, which opens the system network settings.
- Settings, which opens the TV Home settings panel. The Menu (≡) button on the remote does the same.

## Settings

The panel slides in from the right. Back steps out of a section, and Back at the top closes the panel. Changes apply
at once; there is no Save button.

Rows work in one of three ways:

- A row with › opens a section.
- An on/off row flips when you press OK.
- A row with ‹ value › steps through its values: OK or Right goes to the next value, Left to the previous one.

### Wallpaper

Type chooses between Gradient, Picture, Video and Aerials. "Choose video…" and "Choose picture…" open the TV's file
picker and switch to that type when you pick a file. Dimming darkens a picture or video by 0, 20, 40 or 60 %; it does
not apply to the gradient.

A video plays silently and loops. Until its first frame is ready, and whenever Home comes back, you see a still frame
taken from the start of the clip, so the screen never goes black. If the clip has not started after three seconds,
the still frame stays and TV Home tries again the next time Home opens.

Videos play best in this format: H.264, 1920×1080, 25 or 30 fps, no sound, 20 to 60 seconds long. A clip with a higher
resolution or another codec gets a warning, but you can still use it. With `ffmpeg` on a computer:

```bash
ffmpeg -i in.mov -an -vf "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080" \
       -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart out.mp4
```

Copy the result to the TV's `Movies` folder and choose it with "Choose video…".

Aerials streams clips from Apple's and Amazon's servers in random order, one after another, while the home screen is
visible. Each time Home appears, the clip fades in from black; the gradient never shows under it. Between clips the
picture fades to black and the next clip fades in. It needs a network; without one you see the gradient and nothing is requested. If Apple's servers fail on
your TV, TV Home skips them and plays Amazon's clips until it restarts.

### Weather

Turn the weather on or off, set the city, and choose °C or °F. To set the city, open City, press OK on the text field,
type the name with the on-screen keyboard and confirm. Pick the right place from the list.

The weather refreshes every 30 minutes while the home screen is visible, and at once when Home opens if the last
update is older than that. When the network is down, the last result stays on screen with the time it was fetched. An
orange dot means the data is more than three hours old. "Refresh now" asks again straight away.

### Apps

Every app on the TV, in grid order. OK on a row shows or hides that app. "Show all" brings back every hidden app.

### Appearance

- Columns: 4 to 7. Fewer columns means bigger tiles.
- Rows below the bar: 0 to 3 rows of the grid kept visible under the bar at rest.
- Name under the focused tile: on or off.
- Corner radius: 0, 8 or 16 dp.

### Inputs

These change the Inputs icon and panel. "Inputs icon at the top" shows or hides the icon. "Connected only" hides HDMI
ports with nothing plugged in. Some TCL sets report empty ports as waiting, which is why this option exists. "Hide AV"
removes the AV inputs.

The Inputs panel lists the HDMI inputs with a coloured dot: green for connected, grey for waiting, faint for
disconnected. AV inputs are under More. If an input does not open, "System input selection" opens the TV's own picker.

### Remote buttons

This section lets a remote button open an app from anywhere on the TV, even inside another app. It needs the remote
button service, which Android lets only you turn on:

1. If another app already remaps remote buttons (such as Home Button), turn its service off first, or both will react
   to the same press.
2. Open Settings → Remote buttons and press OK on "Remote button service". This opens the TV's accessibility
   settings.
3. Turn on "TV Home remote buttons" and confirm.

After TV Home is updated, this TV keeps the service switched on but stops running it. If the "Remote button service"
row says "Turn off and on", do exactly that in the accessibility settings.

Then pick a button from the list and choose an app for it, or "As before" to give the button back its normal job. The
YouTube button opens SmartTube by default, if SmartTube is installed. A button whose app has been uninstalled works as
it did before.

You can assign Menu, Bookmark, Gear, TV, Numbers, Layers, Grid, NETFLIX, YouTube, TCL CHANNEL, prime video, Volume +,
Volume − and Mute. The D-pad, OK, Back, Home and Power cannot be assigned, so you cannot lock yourself out of the
screen that undoes an assignment.

The button names match the TCL RC902N_M1 remote. Other remotes may send different codes.

### Clock

24-hour clock, "Show the date", and "Greeting above the clock", which changes with the time of day.

### About and TV settings

About shows the version and the credits. TV settings opens the system settings.

## Where your settings live

Settings, the grid order and hidden apps are kept in one file inside the app's private storage. Clearing TV Home's
data or uninstalling it resets everything. The wallpaper files stay where you put them; TV Home only keeps permission
to read them.

## Going back to your old launcher

Press Home, choose the other launcher and pick "Always". If you recorded the TV's state before installing (see the
[README](../README.md)), `tools/restore.sh` puts back the old home screen and accessibility services for you.
