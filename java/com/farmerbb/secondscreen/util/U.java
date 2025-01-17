/* Copyright 2015 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.secondscreen.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.MainActivity;
import com.farmerbb.secondscreen.activity.TaskerQuickActionsActivity;
import com.farmerbb.secondscreen.service.ProfileLoadService;
import com.farmerbb.secondscreen.service.TurnOffService;
import com.jrummyapps.android.os.SystemProperties;
import com.melnykov.fab.FloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import eu.chainfire.libsuperuser.Shell;

// Utility class to store common methods and objects shared between multiple classes
public final class U {

    // Intents and extras
    // NOTE: these intents are only sent in certain scenarios and should not be relied upon by third-party apps

    // Re-lists the profiles in ProfileListActivity
    public static final String LIST_PROFILES = "com.farmerbb.secondscreen.LIST_PROFILES";

    // Sent when SecondScreen detects a screen connection
    public static final String SCREEN_CONNECT = "com.farmerbb.secondscreen.SCREEN_CONNECT";

    // Sent when SecondScreen detects a screen disconnection
    public static final String SCREEN_DISCONNECT = "com.farmerbb.secondscreen.SCREEN_DISCONNECT";

    // Used by debug mode
    public static final String SIMULATE_REBOOT = "com.farmerbb.secondscreen.SIMULATE_REBOOT";
    public static final String SIMULATE_APP_UPGRADE = "com.farmerbb.secondscreen.SIMULATE_APP_UPGRADE";

    // Extras for intents sent via homescreen shortcuts or Tasker
    public static final String NAME = "com.farmerbb.secondscreen.NAME";
    public static final String KEY = "com.farmerbb.secondscreen.KEY";
    public static final String VALUE = "com.farmerbb.secondscreen.VALUE";

    // Arrays of sysfs files that turn the backlight or vibration off.
    // Add new files to the end of the respective array to add backlight/vibration off support to a device

    public static final File[] backlightOff = {
            new File("/sys/class/leds/lcd-backlight", "brightness"),
            new File("/sys/class/backlight/pwm-backlight", "brightness"),
            new File("/sys/class/backlight/intel_backlight", "brightness"),
            new File("/sys/class/backlight/tegra-dsi-backlight.0", "brightness"),
            new File("/sys/devices/platform/i2c-gpio.24/i2c-24/24-002c/backlight/panel", "brightness")};

    public static final File[] vibrationOff = {
            new File("/sys/class/timed_output/vibrator", "amp"),
            new File("/sys/drv2605", "rtp_strength")};

    // Tests if backlight/vibration off files exist
    public static boolean filesExist(File[] array) {
        boolean exists = false;

        for(File file : array) {
            if(file.exists())
                exists = true;
        }

        return exists;
    }

    // Superuser commands

    // Commands for features with boolean values.
    // "true" to turn a feature on, "false" to turn it off
    private static final String navbarCommand = "settings put secure dev_force_show_navbar ";
    public static String navbarCommand(boolean checked) {
        if(checked)
            return navbarCommand + "1";
        else
            return navbarCommand + "0";
    }

    private static final String showTouchesCommand = "settings put system show_touches ";
    public static String showTouchesCommand(boolean checked) {
        if(checked)
            return showTouchesCommand + "1";
        else
            return showTouchesCommand + "0";
    }

    private static final String daydreamsCommand = "settings put secure screensaver_enabled ";
    public static String daydreamsCommand(boolean checked) {
        if(checked)
            return daydreamsCommand + "1";
        else
            return daydreamsCommand + "0";
    }

    private static final String daydreamsChargingCommand = "settings put secure screensaver_activate_on_sleep ";
    public static String daydreamsChargingCommand(boolean checked) {
        if(checked)
            return daydreamsChargingCommand + "1";
        else
            return daydreamsChargingCommand + "0";
    }

    // Non-boolean commands.  Most of these take a variable value either as an argument to the method,
    // or by tacking the argument onto the end of the string.
    public static final String chromeCommandRemove = "rm /data/local/chrome-command-line";
    public static final String rotationCommand = "am broadcast -a android.intent.action.DOCK_EVENT --ei android.intent.extra.DOCK_STATE ";
    public static final String rotationPrePostCommands = "settings put secure screensaver_activate_on_dock ";
    public static final String safeModeSizeCommand = "settings put global display_size_forced ";
    public static final String safeModeDensityCommand = "settings put global display_density_forced ";
    public static final String overscanCommand = "wm overscan ";
    public static final String stayOnCommand = "settings put global stay_on_while_plugged_in ";
    public static final String timeoutCommand = "settings put secure lock_screen_lock_after_timeout ";

    public static String sizeCommand(String args) {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1)
            return "wm size " + args;
        else
            return "am display-size " + args;
    }

    public static String densityCommand(String args) {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1)
            return "wm density " + args;
        else
            return "am display-density " + args;
    }

    public static String chromeCommand(String chromeVersion) {
        return "echo 'chrome --user-agent=\"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeVersion + " Safari/537.36\"' > /data/local/chrome-command-line && chmod 644 /data/local/chrome-command-line";
    }

    public static String chromeCommand2(int channel) {
        String returnCommand = "am force-stop ";

        switch(channel) {
            case 0:
                returnCommand = returnCommand + "com.android.chrome";
                break;
            case 1:
                returnCommand = returnCommand + "com.chrome.beta";
                break;
            case 2:
                returnCommand = returnCommand + "com.chrome.dev";
                break;
        }

        return returnCommand;
    }

    public static String immersiveCommand(String pref) {
        String returnCommand = "settings put global policy_control ";

        switch(pref) {
            case "status-only":
                returnCommand = returnCommand + "immersive.navigation=*";
                break;
            case "immersive-mode":
                returnCommand = returnCommand + "immersive.full=*";
                break;
            case "do-nothing":
                returnCommand = returnCommand + "null";
                break;
        }

        return returnCommand;
    }

    public static String uiRefreshCommand(Context context, boolean restartActivityManager) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> pids = am.getRunningAppProcesses();
        int processid = 0;

        if(restartActivityManager) {
            // Kill surfaceflinger if on a Jelly Bean device; run "am restart" if on KitKat or later
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                for(ActivityManager.RunningAppProcessInfo process : pids) {
                    if(process.processName.equalsIgnoreCase("/system/bin/surfaceflinger"))
                        processid = process.pid;
                }

                return "sleep 1 && kill " + Integer.toString(processid);
            } else
                return "sleep 1 && am restart";
        } else {
            // Use "pkill" on Android M to kill SystemUI, and the standard "kill" command on earlier versions
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
                    || "MNC".equals(Build.VERSION.CODENAME))
                return "sleep 2 && pkill com.android.systemui";
            else {
                // Get SystemUI pid
                for(ActivityManager.RunningAppProcessInfo process : pids) {
                    if(process.processName.equalsIgnoreCase("com.android.systemui"))
                        processid = process.pid;
                }

                return "sleep 2 && kill " + Integer.toString(processid);
            }
        }
    }

    public static String uiRefreshCommand2(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        // For better reliability, we execute the UI refresh while on the home screen
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addCategory(Intent.CATEGORY_DEFAULT);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(homeIntent);

        // Kill all background processes, in order to fully refresh UI
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(0);
        for(ApplicationInfo packageInfo : packages) {
            if(!packageInfo.packageName.equalsIgnoreCase(context.getPackageName()))
                am.killBackgroundProcesses(packageInfo.packageName);
        }

        // Get launcher package name
        final ResolveInfo mInfo = pm.resolveActivity(homeIntent, 0);

        return "sleep 1 && am force-stop " + mInfo.activityInfo.applicationInfo.packageName;
    }

    // Runs checks to determine if size or density commands need to be run.
    // Don't run these commands if we don't need to.
    public static boolean runSizeCommand(Context context, String requestedRes) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealMetrics(metrics);

        SharedPreferences prefMain = getPrefMain(context);
        String currentRes = " ";
        String nativeRes;

        if(prefMain.getBoolean("landscape", false))
            nativeRes = Integer.toString(prefMain.getInt("height", 0))
                    + "x"
                    + Integer.toString(prefMain.getInt("width", 0));
        else
            nativeRes = Integer.toString(prefMain.getInt("width", 0))
                    + "x"
                    + Integer.toString(prefMain.getInt("height", 0));

        if(prefMain.getBoolean("debug_mode", false)) {
            SharedPreferences prefCurrent = getPrefCurrent(context);
            currentRes = prefCurrent.getString("size", "reset");

            if("reset".equals(currentRes))
                currentRes = nativeRes;
        } else {
            if((context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && !prefMain.getBoolean("landscape", false))
                    || (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && prefMain.getBoolean("landscape", false))) {
                currentRes = Integer.toString(metrics.widthPixels)
                        + "x"
                        + Integer.toString(metrics.heightPixels);
            } else if((context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !prefMain.getBoolean("landscape", false))
                    || (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && prefMain.getBoolean("landscape", false))) {
                currentRes = Integer.toString(metrics.heightPixels)
                        + "x"
                        + Integer.toString(metrics.widthPixels);
            }
        }

        if(requestedRes.equals("reset"))
            requestedRes = nativeRes;

        return !requestedRes.equals(currentRes);
    }

    public static boolean runDensityCommand(Context context, String requestedDpi) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealMetrics(metrics);

        SharedPreferences prefMain = getPrefMain(context);
        String currentDpi;
        String nativeDpi = Integer.toString(SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0)));

        if(prefMain.getBoolean("debug_mode", false)) {
            SharedPreferences prefCurrent = getPrefCurrent(context);
            currentDpi = prefCurrent.getString("density", "reset");

            if("reset".equals(currentDpi))
                currentDpi = nativeDpi;
        } else
            currentDpi = Integer.toString(metrics.densityDpi);

        if(requestedDpi.equals("reset"))
            requestedDpi = nativeDpi;

        return !requestedDpi.equals(currentDpi);
    }

    // Methods used to retrieve SharedPreferences objects

    // XML file containing the main application preferences; normally set to the MainActivity preferences file.
    public static SharedPreferences getPrefMain(Context context) {
        SharedPreferences prefMain;
        if(context.getPackageName().equals("com.farmerbb.secondscreen"))
            prefMain = context.getSharedPreferences(MainActivity.class.getName().replace("com.farmerbb.secondscreen.", ""), Context.MODE_PRIVATE);
        else
            prefMain = context.getSharedPreferences(MainActivity.class.getName(), Context.MODE_PRIVATE);

        return prefMain;
    }

    // XML file containing the current state of any running profiles
    public static SharedPreferences getPrefCurrent(Context context) {
        return getPrefSaved(context, "current");
    }

    // XML file containing the current Quick Actions temporary profile
    public static SharedPreferences getPrefQuickActions(Context context) {
        return getPrefSaved(context, "quick_actions");
    }

    // XML file containing any changes to profiles that have not yet been saved;
    // normally set to the global application preferences file due to the way PreferenceActivity works.
    public static SharedPreferences getPrefNew(Context context) {
        return getPrefSaved(context, context.getPackageName() + "_preferences");
    }

    // Gets any XML file corresponding to a saved profile filename.
    public static SharedPreferences getPrefSaved(Context context, String filename) {
        return context.getSharedPreferences(filename, Context.MODE_PRIVATE);
    }

    // Methods used for generating the list of saved profiles

    // Returns list of filenames in /data/data/com.farmerbb.secondscreen/files/, plus a fake entry for "Turn Off"
    private static String[] getListOfProfiles(File file) {
        return file.list();
    }

    private static String[] getListOfProfiles(File file, String fakeEntry) {
        String[] list = file.list();
        String[] realList = new String[list.length + 1];

        System.arraycopy(list, 0, realList, 0, list.length);

        realList[list.length] = fakeEntry;

        return realList;
    }

    // Returns an integer with number of files in /data/data/com.farmerbb.secondscreen/files/
    public static int getNumOfFiles(File file) {
        return new File(file.getPath()).list().length;
    }

    // Loads first line of a profile for display in the ListView
    public static String getProfileTitle(Context context, String filename) throws IOException {
        // Open the file on disk
        FileInputStream input = context.openFileInput(filename);
        InputStreamReader reader = new InputStreamReader(input);
        BufferedReader buffer = new BufferedReader(reader);

        // Load the file
        String line = buffer.readLine();

        // Close file on disk
        reader.close();

        return(line);
    }

    // Gathers list of profile names and their corresponding filenames, optionally with fake entries
    public static String[][] listProfiles(Context context) {
        return listProfiles(context, false, null, 0);
    }

    public static String[][] listProfiles(Context context, String fakeEntryValue, int fakeEntryTitle) {
        return listProfiles(context, true, fakeEntryValue, fakeEntryTitle);
    }

    private static String[][] listProfiles(Context context, boolean fakeEntry, String fakeEntryValue, int fakeEntryTitle) {
        // Get number of files
        int numOfFiles = getNumOfFiles(context.getFilesDir());

        // Get array of filenames
        String[] listOfFiles;
        if(fakeEntry)
            listOfFiles = getListOfProfiles(context.getFilesDir(), fakeEntryValue);
        else
            listOfFiles = getListOfProfiles(context.getFilesDir());

        // Newer Samsung devices (Galaxy S5, Galaxy Note 4) like to save junk files inside the app's internal storage.
        // Delete them so that they don't cause problems with lists
        for(int i = 0; i < numOfFiles; i++) {
            File file = new File(context.getFilesDir() + File.separator + listOfFiles[i]);

            if(listOfFiles[i].contains("rList"))
                file.delete();

            listOfFiles[i] = "";
        }

        // Refresh number of files
        numOfFiles = getNumOfFiles(context.getFilesDir());

        if(numOfFiles == 0)
            return null;
        else {
            // Get "fake" number of files, if applicable
            int fakeNumOfFiles;
            if(fakeEntry)
                fakeNumOfFiles = numOfFiles + 1;
            else
                fakeNumOfFiles = numOfFiles;

            // Create arrays of profile lists
            String[] listOfProfilesByDate;
            if(fakeEntry)
                listOfProfilesByDate = getListOfProfiles(context.getFilesDir(), fakeEntryValue);
            else
                listOfProfilesByDate = getListOfProfiles(context.getFilesDir());

            String[] listOfProfilesByName = new String[fakeNumOfFiles];
            String[] listOfTitlesByDate = new String[fakeNumOfFiles];
            String[] listOfTitlesByName = new String[fakeNumOfFiles];

            // Get array of first lines of each profile
            for(int i = 0; i < numOfFiles; i++) {
                try {
                    listOfTitlesByDate[i] = getProfileTitle(context, listOfProfilesByDate[i]);
                } catch (IOException e) {
                    showToast(context, R.string.error_loading_list);
                }
            }

            // Add fake entry, if applicable
            if(fakeEntry)
                listOfTitlesByDate[numOfFiles] = " " + context.getResources().getString(R.string.bullet) + " " + context.getResources().getString(fakeEntryTitle) + " " + context.getResources().getString(R.string.bullet);

            // Sort alphabetically
            // Copy titles array
            System.arraycopy(listOfTitlesByDate, 0, listOfTitlesByName, 0, fakeNumOfFiles);

            // Sort titles
            Arrays.sort(listOfTitlesByName);

            // Initialize profiles array
            for(int i = 0; i < fakeNumOfFiles; i++)
                listOfProfilesByName[i] = "new";

            // Copy filenames array with new sort order of titles and nullify date arrays
            for(int i = 0; i < fakeNumOfFiles; i++) {
                for(int j = 0; j < fakeNumOfFiles; j++) {
                    if(listOfTitlesByName[i].equals(listOfTitlesByDate[j]) && listOfProfilesByName[i].equals("new")) {
                        listOfProfilesByName[i] = listOfProfilesByDate[j];
                        listOfProfilesByDate[j] = "";
                        listOfTitlesByDate[j] = "";
                    }
                }
            }

            return new String[][] {listOfProfilesByName, listOfTitlesByName};
        }
    }

    // Sends broadcast to refresh list of profiles
    public static void listProfilesBroadcast(Context context) {
        Intent listProfilesIntent = new Intent();
        listProfilesIntent.setAction(U.LIST_PROFILES);
        context.sendBroadcast(listProfilesIntent);
    }

    // Miscellaneous utility methods

    // Checks if superuser access is available.
    // If debug mode is enabled, the app acts as if superuser access is always available,
    // even on non-rooted devices.
    public static boolean hasRoot(Context context) {
        return Shell.SU.available() || getPrefMain(context).getBoolean("debug_mode", false);
    }

    // Executes multiple superuser commands.
    // If debug mode is enabled, the command is not actually run; instead, this will show a
    // notification containing the command that would have been run instead.
    public static void runCommands(Context context, String[] commands) {
        if(getPrefMain(context).getBoolean("debug_mode", false)) {
            String dump = "";

            for(String command : commands) {
                if(!command.equals(""))
                    dump = dump + context.getResources().getString(R.string.bullet) + " " + command + "\n";
            }

            Notification notification = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_action_settings)
                    .setContentTitle(context.getResources().getString(R.string.debug_mode_enabled))
                    .setContentText(dump)
                    .setStyle(new Notification.BigTextStyle().bigText(dump))
                    .build();

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(new Random().nextInt(), notification);

            // Some devices (Android TV) don't show notifications, so let's also print the commands
            // to the log just in case.
            System.out.println(dump);
        } else
            Shell.SU.run(commands);
    }

    // Executes a single superuser command.  Same debug mode behavior applies.
    public static void runCommand(Context context, String command) {
        runCommands(context, new String[]{command});
    }

    // Loads a profile with the given filename
    public static void loadProfile(Context context, String filename) {
        // Set filename in current.xml
        SharedPreferences prefCurrent = getPrefCurrent(context);
        SharedPreferences.Editor editor = prefCurrent.edit();
        editor.putString("filename", filename);
        editor.apply();

        // Start ProfileLoadService
        Intent intent = new Intent(context, ProfileLoadService.class);
        // Get filename of selected profile
        intent.putExtra(NAME, filename);
        context.startService(intent);
    }

    // Turns off the currently active profile
    public static void turnOffProfile(Context context) {
        // Set filename in current.xml
        SharedPreferences prefCurrent = getPrefCurrent(context);
        SharedPreferences.Editor editor = prefCurrent.edit();
        editor.putString("filename_backup", prefCurrent.getString("filename", "0"));
        editor.putString("filename", "0");
        editor.apply();

        // Start TurnOffService
        Intent intent = new Intent(context, TurnOffService.class);
        context.startService(intent);
    }

    // Shows toast notifications
    public static void showToast(Context context, int message){
        Toast toast = Toast.makeText(context, context.getResources().getString(message), Toast.LENGTH_SHORT);
        toast.show();
    }

    public static void showToastLong(Context context, int message){
        Toast toast = Toast.makeText(context, context.getResources().getString(message), Toast.LENGTH_LONG);
        toast.show();
    }

    // Generates blurb text for profile options, used in various places in the UI
    public static String generateBlurb(Activity a, String key, String value, boolean isNotification) {
        String blurb = " ";

        if(a instanceof TaskerQuickActionsActivity) {
            blurb = a.getResources().getStringArray(R.array.pref_notification_action_list)[1];

            // If this blurb is being generated for the notification, and the value is "Toggle",
            // set value to the actual "On" or "Off" state
            if(isNotification && value.equals("Toggle")) {
                SharedPreferences prefCurrent = getPrefCurrent(a);
                switch(key) {
                    case "temp_backlight_off":
                        if(prefCurrent.getBoolean("backlight_off", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        break;
                    case "temp_chrome":
                        if(prefCurrent.getBoolean("chrome", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        break;
                    case "temp_immersive":
                    case "temp_immersive_new":
                        if(key.equals("temp_immersive_new"))
                            key = "temp_immersive";

                        switch(prefCurrent.getString("immersive_new", "fallback")) {
                            case "immersive-mode":
                                value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                                break;
                            default:
                                value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                                break;
                        }
                        break;
                    case "temp_overscan":
                        if(prefCurrent.getBoolean("overscan", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        break;
                    case "temp_vibration_off":
                        if(prefCurrent.getBoolean("vibration_off", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        break;
                }
            }

            // Modifications for non-English locales
            if(value.equals(a.getResources().getStringArray(R.array.pref_quick_actions_values)[0]))
                value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
            else if(value.equals(a.getResources().getStringArray(R.array.pref_quick_actions_values)[1])) {
                if(key.equals("temp_overscan"))
                    value = a.getResources().getStringArray(R.array.pref_quick_actions_overscan)[0];
                else
                    value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
            }
        }

        switch(key) {
            case "turn_off":
                blurb = a.getResources().getString(R.string.quick_turn_off);
                break;
            case "lock_device":
                blurb = a.getResources().getStringArray(R.array.pref_notification_action_list)[2];
                break;
            case "temp_backlight_off":
                blurb = a.getResources().getString(R.string.quick_backlight) + " " + value;
                break;
            case "temp_chrome":
                blurb = a.getResources().getString(R.string.quick_chrome) + " " + value;
                break;
            case "temp_immersive":
                blurb = a.getResources().getString(R.string.quick_immersive) + " " + value;
                break;
            case "temp_immersive_new":
                switch(value) {
                    case "do-nothing":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[0];
                        break;
                    case "status-only":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[1];
                        break;
                    case "immersive-mode":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[2];
                        break;
                    case "Toggle":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[3];
                        break;
                }
                break;
            case "density":
            case "temp_density":
                switch(value) {
                    case "reset":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[0];
                        break;
                    case "120":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[1];
                        break;
                    case "160":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[2];
                        break;
                    case "213":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[3];
                        break;
                    case "240":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[4];
                        break;
                    case "320":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[6];
                        break;
                    case "480":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[8];
                        break;
                    case "640":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[10];
                        break;
                    default:
                        blurb = value + a.getResources().getString(R.string.dpi);
                        break;
                }
                break;
            case "temp_overscan":
                blurb = a.getResources().getString(R.string.quick_overscan) + " " + value;
                break;
            case "size":
            case "temp_size":
                SharedPreferences prefMain = getPrefMain(a);
                if(value.equals("reset"))
                    blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[0];
                else if(prefMain.getBoolean("landscape", false)) {
                    switch(value) {
                        case "1920x1080":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[1];
                            break;
                        case "1280x720":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[2];
                            break;
                        case "854x480":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[3];
                            break;
                        default:
                            blurb = value;
                            break;
                    }
                } else {
                    switch(value) {
                        case "1080x1920":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[1];
                            break;
                        case "720x1280":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[2];
                            break;
                        case "480x854":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[3];
                            break;
                        default:
                            Scanner scanner = new Scanner(value);
                            scanner.useDelimiter("x");

                            int height = scanner.nextInt();
                            int width = scanner.nextInt();
                            scanner.close();

                            blurb = Integer.toString(width) + "x" + Integer.toString(height);
                            break;
                    }
                }
                break;
            case "temp_rotation_lock_new":
                switch(value) {
                    case "do-nothing":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[0];
                        break;
                    case "auto-rotate":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[1];
                        break;
                    case "landscape":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[2];
                        break;
                }
                break;
            case "temp_vibration_off":
                blurb = a.getResources().getString(R.string.quick_vibration) + " " + value;
                break;
        }

        return blurb;
    }

    // Updates a preference summary with the currently set value
    public static void bindPreferenceSummaryToValue(Preference preference, Preference.OnPreferenceChangeListener listener) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(listener);

        // Trigger the listener immediately with the preference's current value.
        listener.onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }

    // Determine if a resolution/density combo is blacklisted (unsafe to use under normal circumstances)
    public static boolean isBlacklisted(String requestedRes, String requestedDpi, int currentHeight, int currentWidth, int currentDpi, boolean landscape) {
        boolean blacklisted = false;

        if(landscape) {
            if(((currentDpi >= 480 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("480")
                    || requestedDpi.equals("560")
                    || requestedDpi.equals("640"))
                    && ((currentWidth <= 1280 && currentHeight <= 800 && requestedRes.equals("reset"))
                    || requestedRes.equals("1280x800")
                    || requestedRes.equals("1280x768")
                    || requestedRes.equals("1280x720")
                    || requestedRes.equals("1024x768")
                    || requestedRes.equals("960x600")
                    || requestedRes.equals("854x480")
                    || requestedRes.equals("800x600")
                    || requestedRes.equals("800x480")))
                blacklisted = true;
            else if(((currentDpi >= 320 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("320")
                    || requestedDpi.equals("400")
                    || requestedDpi.equals("480")
                    || requestedDpi.equals("560")
                    || requestedDpi.equals("640"))
                    && ((currentWidth <= 960 && currentHeight <= 600 && requestedRes.equals("reset"))
                    || requestedRes.equals("960x600")
                    || requestedRes.equals("854x480")
                    || requestedRes.equals("800x600")
                    || requestedRes.equals("800x480")))
                blacklisted = true;
            else if(((currentDpi <= 160 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("120")
                    || requestedDpi.equals("160"))
                    && ((currentWidth >= 2560 && currentHeight >= 1440 && requestedRes.equals("reset"))
                    || requestedRes.equals("2560x1440")
                    || requestedRes.equals("2560x1600")))
                blacklisted = true;
            else if(((currentDpi <= 120 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("120"))
                    && ((currentWidth >= 1920 && currentHeight >= 1080 && requestedRes.equals("reset"))
                    || requestedRes.equals("1920x1080")
                    || requestedRes.equals("1920x1200")
                    || requestedRes.equals("2048x1536")
                    || requestedRes.equals("2560x1440")
                    || requestedRes.equals("2560x1600")))
                blacklisted = true;
        } else {
            if(((currentDpi >= 480 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("480")
                    || requestedDpi.equals("560")
                    || requestedDpi.equals("640"))
                    && ((currentHeight <= 1280 && currentWidth <= 800 && requestedRes.equals("reset"))
                    || requestedRes.equals("800x1280")
                    || requestedRes.equals("768x1280")
                    || requestedRes.equals("720x1280")
                    || requestedRes.equals("768x1024")
                    || requestedRes.equals("600x960")
                    || requestedRes.equals("480x854")
                    || requestedRes.equals("600x800")
                    || requestedRes.equals("480x800")))
                blacklisted = true;
            else if(((currentDpi >= 320 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("320")
                    || requestedDpi.equals("400")
                    || requestedDpi.equals("480")
                    || requestedDpi.equals("560")
                    || requestedDpi.equals("640"))
                    && ((currentHeight <= 960 && currentWidth <= 600 && requestedRes.equals("reset"))
                    || requestedRes.equals("600x960")
                    || requestedRes.equals("480x854")
                    || requestedRes.equals("600x800")
                    || requestedRes.equals("480x800")))
                blacklisted = true;
            else if(((currentDpi <= 160 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("120")
                    || requestedDpi.equals("160"))
                    && ((currentHeight >= 2560 && currentWidth >= 1440 && requestedRes.equals("reset"))
                    || requestedRes.equals("1440x2560")
                    || requestedRes.equals("1600x2560")))
                blacklisted = true;
            else if(((currentDpi <= 120 && requestedDpi.equals("reset"))
                    || requestedDpi.equals("120"))
                    && ((currentHeight >= 1920 && currentWidth >= 1080 && requestedRes.equals("reset"))
                    || requestedRes.equals("1080x1920")
                    || requestedRes.equals("1200x1920")
                    || requestedRes.equals("1536x2048")
                    || requestedRes.equals("1440x2560")
                    || requestedRes.equals("1600x2560")))
                blacklisted = true;
        }

        return blacklisted;
    }

    // Hides the floating action button
    public static void hideFab(Activity a, int id) {
        FloatingActionButton floatingActionButton = (FloatingActionButton) a.findViewById(id);
        floatingActionButton.hide();
    }

    // Detects if we are currently casting the screen using Chromecast
    public static boolean castScreenActive(Context context) {
        boolean castScreenActive = false;
        String castScreenService;

        // On KitKat, we can look for the CastRemoteDisplayProviderService,
        // as it only runs when the "Cast screen" feature is active.
        // On Lollipop, this service is ALWAYS running, so we can't look for it.
        // However, if we are casting something while an external display is connected,
        // then we can reasonably assume that it most likely is casting the screen anyway.
        // So, look for the CastSocketMultiplexerLifeCycleService instead.
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            castScreenService = "com.google.android.gms.cast.media.CastRemoteDisplayProviderService";
        else
            castScreenService = "com.google.android.gms.cast.service.CastSocketMultiplexerLifeCycleService";

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(service.service.getClassName().equals(castScreenService))
                castScreenActive = true;
        }

        return castScreenActive;
    }

    // Directs the user to check for updates
    public static void checkForUpdates(Context context) {
        // If Google Play Store is installed, direct the user to the Play Store page for SecondScreen.
        // Otherwise, direct them to the Downloads page on the xda thread.
        String url;
        try {
            context.getPackageManager().getPackageInfo("com.android.vending", 0);
            url = "https://play.google.com/store/apps/details?id=" + context.getPackageName();
        } catch (PackageManager.NameNotFoundException e) {
            url = "http://forum.xda-developers.com/devdb/project/?id=5032#downloads";
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}