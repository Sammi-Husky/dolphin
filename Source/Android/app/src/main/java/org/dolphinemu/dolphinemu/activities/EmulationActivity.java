package org.dolphinemu.dolphinemu.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity;
import org.dolphinemu.dolphinemu.features.settings.utils.SettingsFile;
import org.dolphinemu.dolphinemu.fragments.EmulationFragment;
import org.dolphinemu.dolphinemu.fragments.MenuFragment;
import org.dolphinemu.dolphinemu.fragments.SaveLoadStateFragment;
import org.dolphinemu.dolphinemu.model.GameFile;
import org.dolphinemu.dolphinemu.overlay.InputOverlay;
import org.dolphinemu.dolphinemu.overlay.InputOverlayPointer;
import org.dolphinemu.dolphinemu.services.GameFileCacheService;
import org.dolphinemu.dolphinemu.ui.main.MainActivity;
import org.dolphinemu.dolphinemu.ui.main.TvMainActivity;
import org.dolphinemu.dolphinemu.ui.platform.Platform;
import org.dolphinemu.dolphinemu.utils.AfterDirectoryInitializationRunner;
import org.dolphinemu.dolphinemu.utils.ControllerMappingHelper;
import org.dolphinemu.dolphinemu.utils.FileBrowserHelper;
import org.dolphinemu.dolphinemu.utils.IniFile;
import org.dolphinemu.dolphinemu.utils.MotionListener;
import org.dolphinemu.dolphinemu.utils.Rumble;
import org.dolphinemu.dolphinemu.utils.TvUtil;

import java.io.File;
import java.lang.annotation.Retention;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public final class EmulationActivity extends AppCompatActivity
{
  private static final String BACKSTACK_NAME_MENU = "menu";
  private static final String BACKSTACK_NAME_SUBMENU = "submenu";
  public static final int REQUEST_CHANGE_DISC = 1;

  private EmulationFragment mEmulationFragment;

  private SharedPreferences mPreferences;
  private MotionListener mMotionListener;

  private Settings mSettings;

  private boolean mDeviceHasTouchScreen;
  private boolean mMenuVisible;

  private static boolean sIgnoreLaunchRequests = false;
  private static boolean sIsGameCubeGame;

  private boolean activityRecreated;
  private String mSelectedTitle;
  private String mSelectedGameId;
  private int mPlatform;
  private String[] mPaths;
  private static boolean sUserPausedEmulation;

  public static final String EXTRA_SELECTED_GAMES = "SelectedGames";
  public static final String EXTRA_SELECTED_TITLE = "SelectedTitle";
  public static final String EXTRA_SELECTED_GAMEID = "SelectedGameId";
  public static final String EXTRA_PLATFORM = "Platform";
  public static final String EXTRA_USER_PAUSED_EMULATION = "sUserPausedEmulation";

  @Retention(SOURCE)
  @IntDef({MENU_ACTION_EDIT_CONTROLS_PLACEMENT, MENU_ACTION_TOGGLE_CONTROLS, MENU_ACTION_ADJUST_SCALE,
          MENU_ACTION_CHOOSE_CONTROLLER, MENU_ACTION_REFRESH_WIIMOTES, MENU_ACTION_TAKE_SCREENSHOT,
          MENU_ACTION_QUICK_SAVE, MENU_ACTION_QUICK_LOAD, MENU_ACTION_SAVE_ROOT,
          MENU_ACTION_LOAD_ROOT, MENU_ACTION_SAVE_SLOT1, MENU_ACTION_SAVE_SLOT2,
          MENU_ACTION_SAVE_SLOT3, MENU_ACTION_SAVE_SLOT4, MENU_ACTION_SAVE_SLOT5,
          MENU_ACTION_SAVE_SLOT6, MENU_ACTION_LOAD_SLOT1, MENU_ACTION_LOAD_SLOT2,
          MENU_ACTION_LOAD_SLOT3, MENU_ACTION_LOAD_SLOT4, MENU_ACTION_LOAD_SLOT5,
          MENU_ACTION_LOAD_SLOT6, MENU_ACTION_EXIT, MENU_ACTION_CHANGE_DISC,
          MENU_ACTION_RESET_OVERLAY, MENU_SET_IR_SENSITIVITY, MENU_ACTION_CHOOSE_DOUBLETAP,
          MENU_ACTION_SCREEN_ORIENTATION, MENU_ACTION_MOTION_CONTROLS, MENU_ACTION_PAUSE_EMULATION,
          MENU_ACTION_UNPAUSE_EMULATION, MENU_ACTION_OVERLAY_CONTROLS, MENU_ACTION_SETTINGS_CORE,
          MENU_ACTION_SETTINGS_GRAPHICS})
  public @interface MenuAction
  {
  }

  public static final int MENU_ACTION_EDIT_CONTROLS_PLACEMENT = 0;
  public static final int MENU_ACTION_TOGGLE_CONTROLS = 1;
  public static final int MENU_ACTION_ADJUST_SCALE = 2;
  public static final int MENU_ACTION_CHOOSE_CONTROLLER = 3;
  public static final int MENU_ACTION_REFRESH_WIIMOTES = 4;
  public static final int MENU_ACTION_TAKE_SCREENSHOT = 5;
  public static final int MENU_ACTION_QUICK_SAVE = 6;
  public static final int MENU_ACTION_QUICK_LOAD = 7;
  public static final int MENU_ACTION_SAVE_ROOT = 8;
  public static final int MENU_ACTION_LOAD_ROOT = 9;
  public static final int MENU_ACTION_SAVE_SLOT1 = 10;
  public static final int MENU_ACTION_SAVE_SLOT2 = 11;
  public static final int MENU_ACTION_SAVE_SLOT3 = 12;
  public static final int MENU_ACTION_SAVE_SLOT4 = 13;
  public static final int MENU_ACTION_SAVE_SLOT5 = 14;
  public static final int MENU_ACTION_SAVE_SLOT6 = 15;
  public static final int MENU_ACTION_LOAD_SLOT1 = 16;
  public static final int MENU_ACTION_LOAD_SLOT2 = 17;
  public static final int MENU_ACTION_LOAD_SLOT3 = 18;
  public static final int MENU_ACTION_LOAD_SLOT4 = 19;
  public static final int MENU_ACTION_LOAD_SLOT5 = 20;
  public static final int MENU_ACTION_LOAD_SLOT6 = 21;
  public static final int MENU_ACTION_EXIT = 22;
  public static final int MENU_ACTION_CHANGE_DISC = 23;
  public static final int MENU_ACTION_JOYSTICK_REL_CENTER = 24;
  public static final int MENU_ACTION_RUMBLE = 25;
  public static final int MENU_ACTION_RESET_OVERLAY = 26;
  public static final int MENU_SET_IR_SENSITIVITY = 27;
  public static final int MENU_ACTION_CHOOSE_DOUBLETAP = 28;
  public static final int MENU_ACTION_SCREEN_ORIENTATION = 29;
  public static final int MENU_ACTION_MOTION_CONTROLS = 30;
  public static final int MENU_ACTION_PAUSE_EMULATION = 31;
  public static final int MENU_ACTION_UNPAUSE_EMULATION = 32;
  public static final int MENU_ACTION_OVERLAY_CONTROLS = 33;
  public static final int MENU_ACTION_SETTINGS_CORE = 34;
  public static final int MENU_ACTION_SETTINGS_GRAPHICS = 35;

  private static SparseIntArray buttonsActionsMap = new SparseIntArray();

  static
  {
    buttonsActionsMap.append(R.id.menu_emulation_edit_layout,
            EmulationActivity.MENU_ACTION_EDIT_CONTROLS_PLACEMENT);
    buttonsActionsMap.append(R.id.menu_emulation_toggle_controls,
            EmulationActivity.MENU_ACTION_TOGGLE_CONTROLS);
    buttonsActionsMap
            .append(R.id.menu_emulation_adjust_scale, EmulationActivity.MENU_ACTION_ADJUST_SCALE);
    buttonsActionsMap.append(R.id.menu_emulation_choose_controller,
            EmulationActivity.MENU_ACTION_CHOOSE_CONTROLLER);
    buttonsActionsMap.append(R.id.menu_emulation_joystick_rel_center,
            EmulationActivity.MENU_ACTION_JOYSTICK_REL_CENTER);
    buttonsActionsMap.append(R.id.menu_emulation_rumble, EmulationActivity.MENU_ACTION_RUMBLE);
    buttonsActionsMap
            .append(R.id.menu_emulation_reset_overlay, EmulationActivity.MENU_ACTION_RESET_OVERLAY);
    buttonsActionsMap.append(R.id.menu_emulation_set_ir_sensitivity,
            EmulationActivity.MENU_SET_IR_SENSITIVITY);
    buttonsActionsMap.append(R.id.menu_emulation_choose_doubletap,
            EmulationActivity.MENU_ACTION_CHOOSE_DOUBLETAP);
    buttonsActionsMap.append(R.id.menu_emulation_motion_controls,
            EmulationActivity.MENU_ACTION_MOTION_CONTROLS);
  }

  private static String[] scanForSecondDisc(GameFile gameFile)
  {
    GameFile secondFile = GameFileCacheService.findSecondDisc(gameFile);
    if (secondFile == null)
      return new String[]{gameFile.getPath()};
    else
      return new String[]{gameFile.getPath(), secondFile.getPath()};
  }

  public static void launch(FragmentActivity activity, GameFile gameFile)
  {
    if (sIgnoreLaunchRequests)
      return;

    sIgnoreLaunchRequests = true;

    Intent launcher = new Intent(activity, EmulationActivity.class);

    launcher.putExtra(EXTRA_SELECTED_GAMES, scanForSecondDisc(gameFile));
    launcher.putExtra(EXTRA_SELECTED_TITLE, gameFile.getTitle());
    launcher.putExtra(EXTRA_SELECTED_GAMEID, gameFile.getGameId());
    launcher.putExtra(EXTRA_PLATFORM, gameFile.getPlatform());

    new AfterDirectoryInitializationRunner().run(activity, true,
            () -> activity.startActivity(launcher));
  }

  public static void launchFile(FragmentActivity activity, String[] filePaths)
  {
    if (sIgnoreLaunchRequests)
      return;

    sIgnoreLaunchRequests = true;

    Intent launcher = new Intent(activity, EmulationActivity.class);
    launcher.putExtra(EXTRA_SELECTED_GAMES, filePaths);

    // Try parsing a GameFile first. This should succeed for disc images.
    GameFile gameFile = GameFile.parse(filePaths[0]);
    if (gameFile != null)
    {
      // We don't want to pollute the game file cache with this new file,
      // so we can't just call launch() and let it handle the setup.
      launcher.putExtra(EXTRA_SELECTED_TITLE, gameFile.getTitle());
      launcher.putExtra(EXTRA_SELECTED_GAMEID, gameFile.getGameId());
      launcher.putExtra(EXTRA_PLATFORM, gameFile.getPlatform());
    }
    else
    {
      // Display the path to the file as the game title in the menu.
      launcher.putExtra(EXTRA_SELECTED_TITLE, filePaths[0]);

      // Use 00000000 as the game ID. This should match the Desktop version behavior.
      // TODO: This should really be pulled from the Core.
      launcher.putExtra(EXTRA_SELECTED_GAMEID, "00000000");

      // GameFile might be a FIFO log. Assume GameCube for the platform. It doesn't really matter
      // anyway, since this only controls the input, and the FIFO player doesn't take any input.
      launcher.putExtra(EXTRA_PLATFORM, Platform.GAMECUBE);
    }

    new AfterDirectoryInitializationRunner().run(activity, true,
            () -> activity.startActivity(launcher));
  }

  public static void stopIgnoringLaunchRequests()
  {
    sIgnoreLaunchRequests = false;
  }

  public static void clearWiimoteNewIniLinkedPreferences(Context context)
  {
    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
    editor.remove("wiiController");
    editor.remove("motionControlsEnabled");
    editor.apply();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    if (TvUtil.isLeanback(getApplicationContext()))
    {
      TvMainActivity.skipRescanningLibrary();
    }
    else
    {
      MainActivity.skipRescanningLibrary();
    }

    if (savedInstanceState == null)
    {
      // Get params we were passed
      Intent gameToEmulate = getIntent();
      mPaths = gameToEmulate.getStringArrayExtra(EXTRA_SELECTED_GAMES);
      mSelectedTitle = gameToEmulate.getStringExtra(EXTRA_SELECTED_TITLE);
      mSelectedGameId = gameToEmulate.getStringExtra(EXTRA_SELECTED_GAMEID);
      mPlatform = gameToEmulate.getIntExtra(EXTRA_PLATFORM, 0);
      sUserPausedEmulation = gameToEmulate.getBooleanExtra(EXTRA_USER_PAUSED_EMULATION, false);
      activityRecreated = false;
    }
    else
    {
      activityRecreated = true;
      restoreState(savedInstanceState);
    }

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    mSettings = new Settings();
    mSettings.loadSettings(null);

    updateOrientation();

    // TODO: The accurate way to find out which console we're emulating is to first
    //       launch emulation and then ask the core which console we're emulating
    sIsGameCubeGame = Platform.fromNativeInt(mPlatform) == Platform.GAMECUBE;
    mDeviceHasTouchScreen = getPackageManager().hasSystemFeature("android.hardware.touchscreen");
    mMotionListener = new MotionListener(this);

    // Set these options now so that the SurfaceView the game renders into is the right size.
    enableFullscreenImmersive();

    Toast.makeText(this, getString(R.string.emulation_menu_help), Toast.LENGTH_LONG).show();

    Rumble.initRumble(this);

    setContentView(R.layout.activity_emulation);

    // Find or create the EmulationFragment
    mEmulationFragment = (EmulationFragment) getSupportFragmentManager()
            .findFragmentById(R.id.frame_emulation_fragment);
    if (mEmulationFragment == null)
    {
      mEmulationFragment = EmulationFragment.newInstance(mPaths);
      getSupportFragmentManager().beginTransaction()
              .add(R.id.frame_emulation_fragment, mEmulationFragment)
              .commit();
    }

    setTitle(mSelectedTitle);
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState)
  {
    if (!isChangingConfigurations())
    {
      mEmulationFragment.saveTemporaryState();
    }
    outState.putStringArray(EXTRA_SELECTED_GAMES, mPaths);
    outState.putString(EXTRA_SELECTED_TITLE, mSelectedTitle);
    outState.putString(EXTRA_SELECTED_GAMEID, mSelectedGameId);
    outState.putInt(EXTRA_PLATFORM, mPlatform);
    outState.putBoolean(EXTRA_USER_PAUSED_EMULATION, sUserPausedEmulation);
    super.onSaveInstanceState(outState);
  }

  protected void restoreState(Bundle savedInstanceState)
  {
    mPaths = savedInstanceState.getStringArray(EXTRA_SELECTED_GAMES);
    mSelectedTitle = savedInstanceState.getString(EXTRA_SELECTED_TITLE);
    mSelectedGameId = savedInstanceState.getString(EXTRA_SELECTED_GAMEID);
    mPlatform = savedInstanceState.getInt(EXTRA_PLATFORM);
    sUserPausedEmulation = savedInstanceState.getBoolean(EXTRA_USER_PAUSED_EMULATION);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus)
  {
    if (hasFocus)
    {
      enableFullscreenImmersive();
    }
  }

  @Override
  protected void onResume()
  {
    super.onResume();

    if (!sIsGameCubeGame && mPreferences.getInt("motionControlsEnabled", 0) != 2)
      mMotionListener.enable();
  }

  @Override
  protected void onPause()
  {
    super.onPause();
    mMotionListener.disable();
  }

  @Override
  protected void onStop()
  {
    super.onStop();
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    mSettings.close();
  }

  @Override
  public void onBackPressed()
  {
    if (!closeSubmenu())
    {
      toggleMenu();
    }
  }

  @Override
  public boolean onKeyLongPress(int keyCode, @NonNull KeyEvent event)
  {
    if (keyCode == KeyEvent.KEYCODE_BACK)
    {
      mEmulationFragment.stopEmulation();
      finish();
      return true;
    }
    return super.onKeyLongPress(keyCode, event);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent result)
  {
    super.onActivityResult(requestCode, resultCode, result);
    if (requestCode == REQUEST_CHANGE_DISC)
    {
      // If the user picked a file, as opposed to just backing out.
      if (resultCode == MainActivity.RESULT_OK)
      {
        String newDiscPath = FileBrowserHelper.getSelectedPath(result);
        if (!TextUtils.isEmpty(newDiscPath))
        {
          NativeLibrary.ChangeDisc(newDiscPath);
        }
      }
    }
  }

  private void enableFullscreenImmersive()
  {
    getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  private void updateOrientation()
  {
    setRequestedOrientation(mPreferences.getInt("emulationActivityOrientation",
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
  }

  private boolean closeSubmenu()
  {
    return getSupportFragmentManager().popBackStackImmediate(BACKSTACK_NAME_SUBMENU,
            FragmentManager.POP_BACK_STACK_INCLUSIVE);
  }

  private boolean closeMenu()
  {
    mMenuVisible = false;
    return getSupportFragmentManager().popBackStackImmediate(BACKSTACK_NAME_MENU,
            FragmentManager.POP_BACK_STACK_INCLUSIVE);
  }

  private void toggleMenu()
  {
    if (!closeMenu())
    {
      // Removing the menu failed, so that means it wasn't visible. Add it.
      Fragment fragment = MenuFragment.newInstance(mSelectedTitle);
      getSupportFragmentManager().beginTransaction()
              .setCustomAnimations(
                      R.animator.menu_slide_in_from_start,
                      R.animator.menu_slide_out_to_start,
                      R.animator.menu_slide_in_from_start,
                      R.animator.menu_slide_out_to_start)
              .add(R.id.frame_menu, fragment)
              .addToBackStack(BACKSTACK_NAME_MENU)
              .commit();
      mMenuVisible = true;
    }
  }

  public void showOverlayControlsMenu(@NonNull View anchor)
  {
    PopupMenu popup = new PopupMenu(this, anchor);
    Menu menu = popup.getMenu();

    int id = sIsGameCubeGame ? R.menu.menu_overlay_controls_gc : R.menu.menu_overlay_controls_wii;
    popup.getMenuInflater().inflate(id, menu);

    // Populate the checkbox value for joystick center on touch
    menu.findItem(R.id.menu_emulation_joystick_rel_center)
            .setChecked(mPreferences.getBoolean("joystickRelCenter", true));
    menu.findItem(R.id.menu_emulation_rumble)
            .setChecked(mPreferences.getBoolean("phoneRumble", true));

    popup.setOnMenuItemClickListener(this::onOptionsItemSelected);

    popup.show();
  }

  @SuppressWarnings("WrongConstant")
  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    int action = buttonsActionsMap.get(item.getItemId(), -1);
    if (action >= 0)
    {
      if (item.isCheckable())
      {
        // Need to pass a reference to the item to set the checkbox state, since it is not done automatically
        handleCheckableMenuAction(action, item);
      }
      else
      {
        handleMenuAction(action);
      }
    }
    return true;
  }

  public void handleCheckableMenuAction(@MenuAction int menuAction, MenuItem item)
  {
    switch (menuAction)
    {
      case MENU_ACTION_JOYSTICK_REL_CENTER:
        item.setChecked(!item.isChecked());
        toggleJoystickRelCenter(item.isChecked());
        break;
      case MENU_ACTION_RUMBLE:
        item.setChecked(!item.isChecked());
        toggleRumble(item.isChecked());
        break;
    }
  }

  public void handleMenuAction(@MenuAction int menuAction)
  {
    switch (menuAction)
    {
      // Edit the placement of the controls
      case MENU_ACTION_EDIT_CONTROLS_PLACEMENT:
        editControlsPlacement();
        break;

      // Reset overlay placement
      case MENU_ACTION_RESET_OVERLAY:
        resetOverlay();
        break;

      // Enable/Disable specific buttons or the entire input overlay.
      case MENU_ACTION_TOGGLE_CONTROLS:
        toggleControls();
        return;

      // Adjust the scale of the overlay controls.
      case MENU_ACTION_ADJUST_SCALE:
        adjustScale();
        return;

      // (Wii games only) Change the controller for the input overlay.
      case MENU_ACTION_CHOOSE_CONTROLLER:
        chooseController();
        return;

      case MENU_ACTION_REFRESH_WIIMOTES:
        NativeLibrary.RefreshWiimotes();
        return;

      case MENU_ACTION_PAUSE_EMULATION:
        sUserPausedEmulation = true;
        NativeLibrary.PauseEmulation();
        return;

      case MENU_ACTION_UNPAUSE_EMULATION:
        sUserPausedEmulation = false;
        NativeLibrary.UnPauseEmulation();
        return;

      // Screenshot capturing
      case MENU_ACTION_TAKE_SCREENSHOT:
        NativeLibrary.SaveScreenShot();
        return;

      // Quick save / load
      case MENU_ACTION_QUICK_SAVE:
        NativeLibrary.SaveState(9, false);
        return;

      case MENU_ACTION_QUICK_LOAD:
        NativeLibrary.LoadState(9);
        return;

      case MENU_ACTION_SAVE_ROOT:
        showSubMenu(SaveLoadStateFragment.SaveOrLoad.SAVE);
        return;

      case MENU_ACTION_LOAD_ROOT:
        showSubMenu(SaveLoadStateFragment.SaveOrLoad.LOAD);
        return;

      // Save state slots
      case MENU_ACTION_SAVE_SLOT1:
        NativeLibrary.SaveState(0, false);
        return;

      case MENU_ACTION_SAVE_SLOT2:
        NativeLibrary.SaveState(1, false);
        return;

      case MENU_ACTION_SAVE_SLOT3:
        NativeLibrary.SaveState(2, false);
        return;

      case MENU_ACTION_SAVE_SLOT4:
        NativeLibrary.SaveState(3, false);
        return;

      case MENU_ACTION_SAVE_SLOT5:
        NativeLibrary.SaveState(4, false);
        return;

      case MENU_ACTION_SAVE_SLOT6:
        NativeLibrary.SaveState(5, false);
        return;

      // Load state slots
      case MENU_ACTION_LOAD_SLOT1:
        NativeLibrary.LoadState(0);
        return;

      case MENU_ACTION_LOAD_SLOT2:
        NativeLibrary.LoadState(1);
        return;

      case MENU_ACTION_LOAD_SLOT3:
        NativeLibrary.LoadState(2);
        return;

      case MENU_ACTION_LOAD_SLOT4:
        NativeLibrary.LoadState(3);
        return;

      case MENU_ACTION_LOAD_SLOT5:
        NativeLibrary.LoadState(4);
        return;

      case MENU_ACTION_LOAD_SLOT6:
        NativeLibrary.LoadState(5);
        return;

      case MENU_ACTION_CHANGE_DISC:
        FileBrowserHelper.openFilePicker(this, REQUEST_CHANGE_DISC, false,
                FileBrowserHelper.GAME_EXTENSIONS);
        return;

      case MENU_SET_IR_SENSITIVITY:
        setIRSensitivity();
        return;

      case MENU_ACTION_CHOOSE_DOUBLETAP:
        chooseDoubleTapButton();
        return;

      case MENU_ACTION_SCREEN_ORIENTATION:
        chooseOrientation();
        return;

      case MENU_ACTION_MOTION_CONTROLS:
        showMotionControlsOptions();
        return;

      case MENU_ACTION_SETTINGS_CORE:
        SettingsActivity.launch(this, MenuTag.CONFIG);
        return;

      case MENU_ACTION_SETTINGS_GRAPHICS:
        SettingsActivity.launch(this, MenuTag.GRAPHICS);
        return;

      case MENU_ACTION_EXIT:
        mEmulationFragment.stopEmulation();
        finish();
        return;
    }
  }

  public static boolean getHasUserPausedEmulation()
  {
    return sUserPausedEmulation;
  }

  private void toggleJoystickRelCenter(boolean state)
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    editor.putBoolean("joystickRelCenter", state);
    editor.commit();
  }

  private void toggleRumble(boolean state)
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    editor.putBoolean("phoneRumble", state);
    editor.apply();
    Rumble.setPhoneVibrator(state, this);
  }

  private void editControlsPlacement()
  {
    if (mEmulationFragment.isConfiguringControls())
    {
      mEmulationFragment.stopConfiguringControls();
    }
    else
    {
      closeSubmenu();
      closeMenu();
      mEmulationFragment.startConfiguringControls();
    }
  }

  // Gets button presses
  @Override
  public boolean dispatchKeyEvent(KeyEvent event)
  {
    if (mMenuVisible || event.getKeyCode() == KeyEvent.KEYCODE_BACK)
    {
      return super.dispatchKeyEvent(event);
    }

    int action;

    switch (event.getAction())
    {
      case KeyEvent.ACTION_DOWN:
        action = NativeLibrary.ButtonState.PRESSED;
        break;
      case KeyEvent.ACTION_UP:
        action = NativeLibrary.ButtonState.RELEASED;
        break;
      default:
        return false;
    }
    InputDevice input = event.getDevice();
    return NativeLibrary.onGamePadEvent(input.getDescriptor(), event.getKeyCode(), action);
  }

  private void toggleControls()
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    boolean[] enabledButtons = new boolean[14];
    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
    builder.setTitle(R.string.emulation_toggle_controls);
    if (sIsGameCubeGame || mPreferences.getInt("wiiController", 3) == 0)
    {
      for (int i = 0; i < enabledButtons.length; i++)
      {
        enabledButtons[i] = mPreferences.getBoolean("buttonToggleGc" + i, true);
      }
      builder.setMultiChoiceItems(R.array.gcpadButtons, enabledButtons,
              (dialog, indexSelected, isChecked) -> editor
                      .putBoolean("buttonToggleGc" + indexSelected, isChecked));
    }
    else if (mPreferences.getInt("wiiController", 3) == 4)
    {
      for (int i = 0; i < enabledButtons.length; i++)
      {
        enabledButtons[i] = mPreferences.getBoolean("buttonToggleClassic" + i, true);
      }
      builder.setMultiChoiceItems(R.array.classicButtons, enabledButtons,
              (dialog, indexSelected, isChecked) -> editor
                      .putBoolean("buttonToggleClassic" + indexSelected, isChecked));
    }
    else
    {
      for (int i = 0; i < enabledButtons.length; i++)
      {
        enabledButtons[i] = mPreferences.getBoolean("buttonToggleWii" + i, true);
      }
      if (mPreferences.getInt("wiiController", 3) == 3)
      {
        builder.setMultiChoiceItems(R.array.nunchukButtons, enabledButtons,
                (dialog, indexSelected, isChecked) -> editor
                        .putBoolean("buttonToggleWii" + indexSelected, isChecked));
      }
      else
      {
        builder.setMultiChoiceItems(R.array.wiimoteButtons, enabledButtons,
                (dialog, indexSelected, isChecked) -> editor
                        .putBoolean("buttonToggleWii" + indexSelected, isChecked));
      }
    }
    builder.setNeutralButton(R.string.emulation_toggle_all,
            (dialogInterface, i) -> mEmulationFragment.toggleInputOverlayVisibility());
    builder.setPositiveButton(R.string.ok, (dialogInterface, i) ->
    {
      editor.apply();

      mEmulationFragment.refreshInputOverlay();
    });

    builder.show();
  }

  public void chooseDoubleTapButton()
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);

    int currentController =
            mPreferences.getInt("wiiController", InputOverlay.OVERLAY_WIIMOTE_NUNCHUK);

    int currentValue = mPreferences.getInt("doubleTapButton",
            InputOverlayPointer.DOUBLE_TAP_OPTIONS.get(InputOverlayPointer.DOUBLE_TAP_A));

    int buttonList = currentController == InputOverlay.OVERLAY_WIIMOTE_CLASSIC ?
            R.array.doubleTapWithClassic : R.array.doubleTap;

    if (currentController != InputOverlay.OVERLAY_WIIMOTE_CLASSIC &&
            currentValue == InputOverlay.OVERLAY_WIIMOTE_CLASSIC)
    {
      currentValue = InputOverlay.OVERLAY_WIIMOTE;
    }

    builder.setSingleChoiceItems(buttonList, currentValue, (DialogInterface dialog, int which) ->
            editor.putInt("doubleTapButton", InputOverlayPointer.DOUBLE_TAP_OPTIONS.get(which)));

    builder.setPositiveButton(R.string.ok, (dialogInterface, i) ->
    {
      editor.commit();
      mEmulationFragment.initInputPointer();
    });

    builder.show();
  }

  private void adjustScale()
  {
    LayoutInflater inflater = LayoutInflater.from(this);
    View view = inflater.inflate(R.layout.dialog_seekbar, null);

    final SeekBar seekbar = view.findViewById(R.id.seekbar);
    final TextView value = view.findViewById(R.id.text_value);
    final TextView units = view.findViewById(R.id.text_units);

    seekbar.setMax(150);
    seekbar.setProgress(mPreferences.getInt("controlScale", 50));
    seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        value.setText(String.valueOf(progress + 50));
      }

      public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });

    value.setText(String.valueOf(seekbar.getProgress() + 50));
    units.setText("%");

    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
    builder.setTitle(R.string.emulation_control_scale);
    builder.setView(view);
    builder.setPositiveButton(R.string.ok, (dialogInterface, i) ->
    {
      SharedPreferences.Editor editor = mPreferences.edit();
      editor.putInt("controlScale", seekbar.getProgress());
      editor.apply();

      mEmulationFragment.refreshInputOverlay();
    });

    builder.show();
  }

  private void chooseController()
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
    builder.setTitle(R.string.emulation_choose_controller);
    builder.setSingleChoiceItems(R.array.controllersEntries,
            mPreferences.getInt("wiiController", 3),
            (dialog, indexSelected) ->
            {
              editor.putInt("wiiController", indexSelected);

              File wiimoteNewFile = SettingsFile.getSettingsFile(Settings.FILE_WIIMOTE);
              IniFile wiimoteNewIni = new IniFile(wiimoteNewFile);
              wiimoteNewIni.setString("Wiimote1", "Extension",
                      getResources().getStringArray(R.array.controllersValues)[indexSelected]);
              wiimoteNewIni.setBoolean("Wiimote1", "Options/Sideways Wiimote", indexSelected == 2);
              wiimoteNewIni.save(wiimoteNewFile);

              NativeLibrary.ReloadWiimoteConfig();
            });
    builder.setPositiveButton(R.string.ok, (dialogInterface, i) ->
    {
      editor.apply();
      mEmulationFragment.refreshInputOverlay();
    });

    builder.show();
  }

  private void showMotionControlsOptions()
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
    builder.setTitle(R.string.emulation_motion_controls);
    builder.setSingleChoiceItems(R.array.motionControlsEntries,
            mPreferences.getInt("motionControlsEnabled", 1),
            (dialog, indexSelected) ->
            {
              editor.putInt("motionControlsEnabled", indexSelected);

              if (indexSelected != 2)
                mMotionListener.enable();
              else
                mMotionListener.disable();

              File wiimoteNewFile = SettingsFile.getSettingsFile(Settings.FILE_WIIMOTE);
              IniFile wiimoteNewIni = new IniFile(wiimoteNewFile);
              wiimoteNewIni.setBoolean("Wiimote1", "IMUIR/Enabled", indexSelected != 1);
              wiimoteNewIni.save(wiimoteNewFile);

              NativeLibrary.ReloadWiimoteConfig();
            });
    builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> editor.apply());

    builder.show();
  }

  private void chooseOrientation()
  {
    final int[] orientationValues = getResources().getIntArray(R.array.orientationValues);
    int initialChoice = mPreferences.getInt("emulationActivityOrientation",
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    int initialIndex = -1;
    for (int i = 0; i < orientationValues.length; i++)
    {
      if (orientationValues[i] == initialChoice)
        initialIndex = i;
    }

    final SharedPreferences.Editor editor = mPreferences.edit();
    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
    builder.setTitle(R.string.emulation_screen_orientation);
    builder.setSingleChoiceItems(R.array.orientationEntries, initialIndex,
            (dialog, indexSelected) ->
            {
              int orientation = orientationValues[indexSelected];
              editor.putInt("emulationActivityOrientation", orientation);
            });
    builder.setPositiveButton(R.string.ok, (dialogInterface, i) ->
    {
      editor.apply();
      updateOrientation();
    });

    builder.show();
  }

  private void setIRSensitivity()
  {
    int ir_pitch = Integer.parseInt(
            mPreferences.getString(SettingsFile.KEY_WIIBIND_IR_PITCH + mSelectedGameId, "15"));

    LayoutInflater inflater = LayoutInflater.from(this);
    View view = inflater.inflate(R.layout.dialog_ir_sensitivity, null);

    TextView text_slider_value_pitch = view.findViewById(R.id.text_ir_pitch);
    TextView units = view.findViewById(R.id.text_ir_pitch_units);
    SeekBar seekbar_pitch = view.findViewById(R.id.seekbar_pitch);

    text_slider_value_pitch.setText(String.valueOf(ir_pitch));
    units.setText(getString(R.string.pitch));
    seekbar_pitch.setMax(100);
    seekbar_pitch.setProgress(ir_pitch);
    seekbar_pitch.setKeyProgressIncrement(5);
    seekbar_pitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        text_slider_value_pitch.setText(String.valueOf(progress));
      }

      @Override public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      @Override public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });

    int ir_yaw = Integer.parseInt(
            mPreferences.getString(SettingsFile.KEY_WIIBIND_IR_YAW + mSelectedGameId, "15"));

    TextView text_slider_value_yaw = view.findViewById(R.id.text_ir_yaw);
    TextView units_yaw = view.findViewById(R.id.text_ir_yaw_units);
    SeekBar seekbar_yaw = view.findViewById(R.id.seekbar_width);

    text_slider_value_yaw.setText(String.valueOf(ir_yaw));
    units_yaw.setText(getString(R.string.yaw));
    seekbar_yaw.setMax(100);
    seekbar_yaw.setProgress(ir_yaw);
    seekbar_yaw.setKeyProgressIncrement(5);
    seekbar_yaw.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        text_slider_value_yaw.setText(String.valueOf(progress));
      }

      @Override public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      @Override public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });


    int ir_vertical_offset = Integer.parseInt(
            mPreferences.getString(SettingsFile.KEY_WIIBIND_IR_VERTICAL_OFFSET + mSelectedGameId,
                    "10"));

    TextView text_slider_value_vertical_offset = view.findViewById(R.id.text_ir_vertical_offset);
    TextView units_vertical_offset = view.findViewById(R.id.text_ir_vertical_offset_units);
    SeekBar seekbar_vertical_offset = view.findViewById(R.id.seekbar_vertical_offset);

    text_slider_value_vertical_offset.setText(String.valueOf(ir_vertical_offset));
    units_vertical_offset.setText(getString(R.string.vertical_offset));
    seekbar_vertical_offset.setMax(100);
    seekbar_vertical_offset.setProgress(ir_vertical_offset);
    seekbar_vertical_offset.setKeyProgressIncrement(5);
    seekbar_vertical_offset.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        text_slider_value_vertical_offset.setText(String.valueOf(progress));
      }

      @Override public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      @Override public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });

    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
    builder.setTitle(getString(R.string.emulation_ir_sensitivity));
    builder.setView(view);
    builder.setPositiveButton(R.string.ok, (dialogInterface, i) ->
    {
      File file = SettingsFile.getCustomGameSettingsFile(mSelectedGameId);
      IniFile ini = new IniFile(file);
      ini.setString(Settings.SECTION_CONTROLS, SettingsFile.KEY_WIIBIND_IR_PITCH,
              text_slider_value_pitch.getText().toString());
      ini.setString(Settings.SECTION_CONTROLS, SettingsFile.KEY_WIIBIND_IR_YAW,
              text_slider_value_yaw.getText().toString());
      ini.setString(Settings.SECTION_CONTROLS, SettingsFile.KEY_WIIBIND_IR_VERTICAL_OFFSET,
              text_slider_value_vertical_offset.getText().toString());
      ini.save(file);

      NativeLibrary.ReloadWiimoteConfig();

      SharedPreferences.Editor editor = mPreferences.edit();
      editor.putString(SettingsFile.KEY_WIIBIND_IR_PITCH + mSelectedGameId,
              text_slider_value_pitch.getText().toString());
      editor.putString(SettingsFile.KEY_WIIBIND_IR_YAW + mSelectedGameId,
              text_slider_value_yaw.getText().toString());
      editor.putString(SettingsFile.KEY_WIIBIND_IR_VERTICAL_OFFSET + mSelectedGameId,
              text_slider_value_vertical_offset.getText().toString());
      editor.apply();
    });
    builder.setNegativeButton(R.string.cancel, (dialogInterface, i) ->
    {
      // Do nothing
    });
    builder.show();
  }

  private void resetOverlay()
  {
    new AlertDialog.Builder(this, R.style.DolphinDialogBase)
            .setTitle(getString(R.string.emulation_touch_overlay_reset))
            .setPositiveButton(R.string.yes, (dialogInterface, i) ->
                    mEmulationFragment.resetInputOverlay())
            .setNegativeButton(R.string.cancel, (dialogInterface, i) ->
            {
            })
            .show();
  }

  private static boolean areCoordinatesOutside(@Nullable View view, float x, float y)
  {
    if (view == null)
    {
      return true;
    }

    Rect viewBounds = new Rect();
    view.getGlobalVisibleRect(viewBounds);
    return !viewBounds.contains(Math.round(x), Math.round(y));
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event)
  {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
    {
      boolean anyMenuClosed = false;

      Fragment submenu = getSupportFragmentManager().findFragmentById(R.id.frame_submenu);
      if (submenu != null && areCoordinatesOutside(submenu.getView(), event.getX(), event.getY()))
      {
        closeSubmenu();
        submenu = null;
        anyMenuClosed = true;
      }

      if (submenu == null)
      {
        Fragment menu = getSupportFragmentManager().findFragmentById(R.id.frame_menu);
        if (menu != null && areCoordinatesOutside(menu.getView(), event.getX(), event.getY()))
        {
          closeMenu();
          anyMenuClosed = true;
        }
      }

      if (anyMenuClosed)
      {
        return true;
      }
    }

    return super.dispatchTouchEvent(event);
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event)
  {
    if (mMenuVisible)
    {
      return false;
    }

    if (((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0))
    {
      return super.dispatchGenericMotionEvent(event);
    }

    // Don't attempt to do anything if we are disconnecting a device.
    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL)
      return true;

    InputDevice input = event.getDevice();
    List<InputDevice.MotionRange> motions = input.getMotionRanges();

    for (InputDevice.MotionRange range : motions)
    {
      int axis = range.getAxis();
      float origValue = event.getAxisValue(axis);
      float value = ControllerMappingHelper.scaleAxis(input, axis, origValue);
      // If the input is still in the "flat" area, that means it's really zero.
      // This is used to compensate for imprecision in joysticks.
      if (Math.abs(value) > range.getFlat())
      {
        NativeLibrary.onGamePadMoveEvent(input.getDescriptor(), axis, value);
      }
      else
      {
        NativeLibrary.onGamePadMoveEvent(input.getDescriptor(), axis, 0.0f);
      }
    }

    return true;
  }

  private void showSubMenu(SaveLoadStateFragment.SaveOrLoad saveOrLoad)
  {
    // Get rid of any visible submenu
    getSupportFragmentManager().popBackStack(
            BACKSTACK_NAME_SUBMENU, FragmentManager.POP_BACK_STACK_INCLUSIVE);

    Fragment fragment = SaveLoadStateFragment.newInstance(saveOrLoad);
    getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(
                    R.animator.menu_slide_in_from_end,
                    R.animator.menu_slide_out_to_end,
                    R.animator.menu_slide_in_from_end,
                    R.animator.menu_slide_out_to_end)
            .replace(R.id.frame_submenu, fragment)
            .addToBackStack(BACKSTACK_NAME_SUBMENU)
            .commit();
  }

  public String getSelectedTitle()
  {
    return mSelectedTitle;
  }

  public static boolean isGameCubeGame()
  {
    return sIsGameCubeGame;
  }

  public boolean isActivityRecreated()
  {
    return activityRecreated;
  }

  public Settings getSettings()
  {
    return mSettings;
  }

  public void initInputPointer()
  {
    if (mDeviceHasTouchScreen)
      mEmulationFragment.initInputPointer();
  }
}
