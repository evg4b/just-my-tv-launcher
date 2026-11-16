# Just My TV Launcher user guide

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
- Move puts a coral ring around the tile. Move it with the arrows and press OK to put it down, or Back to put it
  back where it was.
- Change image lets you pick a picture for the tile from the TV's storage. It is cropped to the tile's shape from
  the centre and kept even when the app updates. Reset image, shown once you have changed it, brings back the app's
  own banner.
- Hide removes the tile from the grid, and a message says where to find it: Settings → Apps brings it back.
- App details opens the system page for the app, where you can force stop, clear data or uninstall it.

New apps appear at the end of the grid. An app you uninstall and install again also goes to the end.

### The header

The clock and date are at the top left, and the weather sits under them once a city is set. Up to three round icons
sit at the top right:

- Inputs, to switch to HDMI or another source. It shows only when the TV reports at least one input.
- A crossed-out Wi-Fi icon, only when something is wrong with the network: the TV is not connected, or it is connected
  but has no working internet. Select it to open the TV's network and Wi-Fi settings. While the network works, there is
  no icon.
- Settings, which opens the launcher settings panel. The Menu (≡) button on the remote does the same. You can hide it
  (see Appearance) once a remote button opens the launcher settings.

## Settings

The panel slides in from the right. Back steps out of a section, and Back at the top closes the panel. Changes apply
at once; there is no Save button.

Rows work in one of three ways:

- A row with › opens a section.
- An on/off row flips when you press OK.
- A row with ‹ value › steps through its values: OK or Right goes to the next value, Left to the previous one.

### Wallpaper

Type chooses between Gradient, Picture, Photos, Video and Aerials. The rows under it belong to the chosen type only:
"Choose picture…" for Picture, "Choose video…" for Video, the key and interval for Photos, Collection and Themes for
Aerials. "Choose picture…" and "Choose video…" open the TV's file picker. Dimming darkens the wallpaper by 0, 20, 40 or
60 %; it is not offered for the gradient. Until a picture or video is chosen, those types show the gradient.

Stepping through the types with Left and Right does not switch the wallpaper at every step: it changes once the choice
has rested for about a second, so passing Photos or Aerials on the way does not download or stream anything.

A video plays silently and loops without a break. Whenever Home appears, you first see a still frame taken from the
start of the clip (black the very first time), and the video fades in over about a second once it is playing. The
short wait is deliberate: the TV blanks and re-tunes its picture as a video starts, and that now happens out of sight.
If the clip has not started after three seconds, the still frame stays and the launcher tries again the next time Home
opens.

Videos play best in this format: H.264, 1920×1080, 25 or 30 fps, no sound, 20 to 60 seconds long. A clip with a higher
resolution or another codec gets a warning, but you can still use it. With `ffmpeg` on a computer:

```bash
ffmpeg -i in.mov -an -vf "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080" \
       -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -movflags +faststart out.mp4
```

Copy the result to the TV's `Movies` folder and choose it with "Choose video…".

Aerials streams clips from Apple's and Amazon's servers in random order, one after another, while the home screen is
visible. Each time Home appears, the screen is black for a few seconds while the clip loads, then the clip fades in; the
gradient never shows under it. Between clips the picture fades to black and the next clip fades in. Aerials need a
network; without one you see the gradient and nothing is requested. If Apple's servers fail on your TV, the launcher
skips them and plays Amazon's clips until it restarts.

With Aerials chosen, two more rows appear under Type:

- Collection plays clips from All sources, only Apple's (139) or only Amazon's (112).
- Themes picks what the clips show: Landscapes, Space and night sky, Cities, Underwater, Wildlife, and Abstract and
  fireplaces. All six are on at first. At least one stays on: the last one cannot be turned off.

A line under the rows says how many clips will play. Some mixes have none, for example Apple with Wildlife or Abstract
and fireplaces, because Apple has no such clips; then Home shows the gradient. A new Collection starts a new clip once
the choice has rested; theme changes start one new clip when you leave the Themes screen.

Photos shows a random landscape photo from Unsplash and changes it while the home screen is visible. It needs your own
free Unsplash access key:

1. On a computer or phone, sign in at [unsplash.com/developers](https://unsplash.com/developers), choose "New
   Application", accept the terms and give it a name.
2. Copy the Access Key (not the Secret key).
3. On the TV, open Settings → Wallpaper, choose Photos, open "Unsplash access key", type the key and press OK. The
   phone remote app's keyboard is easier than the on-screen one. The row then shows the key's last four characters.

A new key allows 50 requests an hour and each photo takes two, so the shortest interval is 5 minutes. "Change every"
sets it: 5, 15 or 30 minutes, or an hour (5 minutes at first). "Next photo" changes it at once and says Loading… until
the new photo has arrived. A new interval keeps the photo on screen and counts from when it was downloaded. A line under
the rows says when the key was rejected, the hourly limit is used up (the next try is in 15 minutes) or Unsplash cannot
be reached (the next try is in 2 minutes). "Remove the key" on the key screen deletes it from the TV. A change uses the
same transition as the aerials: the photo fades to black and the new one fades in. The last photo is kept on the TV, so
Home shows it straight away next time, also without a network; the countdown continues from when it was downloaded. The
photographer's name and Unsplash show in small text under the round buttons at the top right of Home, and under the rows
in the settings. Until a photo has been downloaded, you see the gradient.

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
- Edge dimming: a dark gradient at the top and bottom of the screen, the top only (the default), the bottom only, or off.
- Settings icon: shown or hidden. This option works only after you assign "Launcher settings" to a remote button
  (Remote buttons, below); until then it says "Unavailable". The gear comes back on its own if you take that action
  off the button, or while the remote button service is not running, so you can always reach these settings.

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
3. Turn on "Just My TV Launcher remote buttons" and confirm.

After the launcher is updated, this TV keeps the service switched on but stops running it. If the "Remote button
service" row says "Turn off and on", do exactly that in the accessibility settings.

Then pick a button from the list and choose what it does:

- "As before" gives the button back its normal job.
- "Disabled" switches the button off: it does nothing in any app, as long as the service is running.
- An action from the Actions list: Home, Back, Launcher settings (opens Home with this settings panel; press again to
  close it), Inputs (the same for the Inputs panel), TV settings, Network and Wi-Fi, Picture settings, Sound settings
  (the last three open the TV's main settings if this TV does not have that page), Power menu, Mute (like the remote's
  own Mute key), or Play / pause (for the app that is playing).
- An app from the Apps list, which then opens from anywhere.

There is no action that switches Wi-Fi or Bluetooth on and off: Android 10 and later lets only system apps do that,
and this TV's remote itself works over Bluetooth. "Network and Wi-Fi" opens the page where you can do it.

The YouTube button opens SmartTube by default, if SmartTube is installed. A button whose app has been uninstalled works
as it did before.

You can assign Menu, Bookmark, Gear, TV, Numbers, Layers, Grid, NETFLIX, YouTube, TCL CHANNEL, prime video, Volume +,
Volume − and Mute. The D-pad, OK, Back, Home and Power cannot be assigned, so you cannot lock yourself out of the
screen that undoes an assignment.

The button names match the TCL RC902N_M1 remote. Other remotes may send different codes.

### Clock

24-hour clock, "Show the date", and "Greeting above the clock", which changes with the time of day.

### About and TV settings

About shows the version and the credits. TV settings opens the system settings.

## Where your settings live

Settings, the grid order and hidden apps are kept in one file inside the app's private storage. Clearing the launcher's
data or uninstalling it resets everything. The wallpaper files stay where you put them; the launcher only keeps
permission to read them.

## Going back to your old launcher

Press Home, choose the other launcher and pick "Always". If you recorded the TV's state before installing (see the
[README](../README.md)), `tools/restore.sh` puts back the old home screen and accessibility services for you.
