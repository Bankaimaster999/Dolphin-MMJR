package org.dolphinemu.dolphinemu.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.text.TextUtils;
import android.util.DisplayMetrics;
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

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.dialogs.RunningSettingDialog;
import org.dolphinemu.dolphinemu.dialogs.StateSavesDialog;
import org.dolphinemu.dolphinemu.fragments.EmulationFragment;
import org.dolphinemu.dolphinemu.fragments.MenuFragment;
import org.dolphinemu.dolphinemu.model.GameFile;
import org.dolphinemu.dolphinemu.overlay.InputOverlay;
import org.dolphinemu.dolphinemu.services.GameFileCacheService;
import org.dolphinemu.dolphinemu.ui.main.MainActivity;
import org.dolphinemu.dolphinemu.ui.platform.Platform;
import org.dolphinemu.dolphinemu.utils.ControllerMappingHelper;
import org.dolphinemu.dolphinemu.utils.FileBrowserHelper;
import org.dolphinemu.dolphinemu.utils.Java_GCAdapter;
import org.dolphinemu.dolphinemu.utils.Java_WiimoteAdapter;
import org.dolphinemu.dolphinemu.utils.Rumble;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.ref.WeakReference;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public final class EmulationActivity extends AppCompatActivity
{
  private static WeakReference<EmulationActivity> sActivity = new WeakReference<>(null);

  private static final String BACKSTACK_NAME_MENU = "menu";
  private static final String BACKSTACK_NAME_SUBMENU = "submenu";
  public static final int REQUEST_CHANGE_DISC = 1;

  private SensorManager mSensorManager;
  private View mDecorView;
  private EmulationFragment mEmulationFragment;

  private SharedPreferences mPreferences;
  private ControllerMappingHelper mControllerMappingHelper;

  private boolean mStopEmulation;
  private boolean mMenuVisible;
  private String mBindingDevice;
  private int mBindingButton;

  private String mSelectedTitle = "";
  private String mSelectedGameId = "";
  private int mPlatform = 0;
  private String[] mPaths;
  private String mSavedState;

  private static boolean sIsGameCubeGame;

  public static final String RUMBLE_PREF_KEY = "PhoneRumble";

  public static final String EXTRA_SELECTED_GAMES = "SelectedGames";
  public static final String EXTRA_SELECTED_TITLE = "SelectedTitle";
  public static final String EXTRA_SELECTED_GAMEID = "SelectedGameId";
  public static final String EXTRA_PLATFORM = "Platform";
  public static final String EXTRA_SAVED_STATE = "SavedState";

  @Retention(SOURCE)
  @IntDef({MENU_ACTION_EDIT_CONTROLS_PLACEMENT, MENU_ACTION_TOGGLE_CONTROLS, MENU_ACTION_ADJUST_SCALE,
          MENU_ACTION_CHOOSE_CONTROLLER, MENU_ACTION_REFRESH_WIIMOTES, MENU_ACTION_TAKE_SCREENSHOT,
          MENU_ACTION_QUICK_SAVE, MENU_ACTION_EXIT, MENU_ACTION_CHANGE_DISC, MENU_ACTION_JOYSTICK_SETTINGS,
          MENU_ACTION_RUNNING_SETTING, MENU_ACTION_EMULATION_SENSOR, MENU_ACTION_OVERLAY_CONTROLS})
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
  public static final int MENU_ACTION_EXIT = 7;
  public static final int MENU_ACTION_CHANGE_DISC = 8;
  public static final int MENU_ACTION_JOYSTICK_SETTINGS = 9;
  public static final int MENU_ACTION_RUNNING_SETTING = 10;
  public static final int MENU_ACTION_EMULATION_SENSOR = 11;
  public static final int MENU_ACTION_OVERLAY_CONTROLS = 12;

  private static SparseIntArray buttonsActionsMap = new SparseIntArray();

  static
  {
    buttonsActionsMap.append(R.id.menu_emulation_edit_layout, EmulationActivity.MENU_ACTION_EDIT_CONTROLS_PLACEMENT);
    buttonsActionsMap.append(R.id.menu_emulation_toggle_controls, EmulationActivity.MENU_ACTION_TOGGLE_CONTROLS);
    buttonsActionsMap.append(R.id.menu_emulation_adjust_scale, EmulationActivity.MENU_ACTION_ADJUST_SCALE);
    buttonsActionsMap.append(R.id.menu_emulation_choose_controller, EmulationActivity.MENU_ACTION_CHOOSE_CONTROLLER);
    buttonsActionsMap.append(R.id.menu_emulation_screenshot, EmulationActivity.MENU_ACTION_TAKE_SCREENSHOT);

    buttonsActionsMap.append(R.id.menu_quicksave, EmulationActivity.MENU_ACTION_QUICK_SAVE);
    buttonsActionsMap.append(R.id.menu_change_disc, EmulationActivity.MENU_ACTION_CHANGE_DISC);
    buttonsActionsMap.append(R.id.menu_emulation_joystick_settings, EmulationActivity.MENU_ACTION_JOYSTICK_SETTINGS);
    buttonsActionsMap.append(R.id.menu_running_setting, EmulationActivity.MENU_ACTION_RUNNING_SETTING);
    buttonsActionsMap.append(R.id.menu_emulation_sensor_settings, EmulationActivity.MENU_ACTION_EMULATION_SENSOR);
    buttonsActionsMap.append(R.id.menu_emulation_exit, EmulationActivity.MENU_ACTION_EXIT);
  }

  public static void launch(Context context, GameFile game, String savedState)
  {
    Intent intent = new Intent(context, EmulationActivity.class);
    intent.putExtra(EXTRA_SELECTED_GAMES, new String[]{game.getPath()});
    intent.putExtra(EXTRA_SAVED_STATE, savedState);
    context.startActivity(intent);
  }

  public static EmulationActivity get()
  {
    return sActivity.get();
  }

  public static void launchFile(Context context, String[] filePaths)
  {
    Intent launcher = new Intent(context, EmulationActivity.class);
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

    context.startActivity(launcher);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    sActivity = new WeakReference<>(this);

    if (savedInstanceState == null)
    {
      Intent gameToEmulate = getIntent();
      mPaths = gameToEmulate.getStringArrayExtra(EXTRA_SELECTED_GAMES);
      mSavedState = gameToEmulate.getStringExtra(EXTRA_SAVED_STATE);
      if (mPaths != null && mPaths.length > 0)
      {
        GameFile game = GameFileCacheService.getGameFileByPath(mPaths[0]);
        if (game != null)
        {
          mSelectedGameId = game.getGameId();
          mSelectedTitle = game.getTitle();
          mPlatform = game.getPlatform();
          if (mPaths.length == 1)
          {
            mPaths = GameFileCacheService.getAllDiscPaths(game);
          }
        }
      }
    }
    else
    {
      restoreState(savedInstanceState);
    }

    mControllerMappingHelper = new ControllerMappingHelper();
    sIsGameCubeGame = Platform.fromNativeInt(mPlatform) == Platform.GAMECUBE;

    // Set these options now so that the SurfaceView the game renders into is the right size.
    enableFullscreenImmersive();

    //Toast.makeText(this, getString(R.string.emulation_menu_help), Toast.LENGTH_LONG).show();

    Java_GCAdapter.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
    Java_WiimoteAdapter.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
    Rumble.initDeviceRumble();

    DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics);
    NativeLibrary.SetScaledDensity(metrics.scaledDensity);

    setTitle(mSelectedTitle);
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

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    loadPreferences();
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    sActivity.clear();
    savePreferences();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState)
  {
    if (!isChangingConfigurations())
    {
      mSavedState = getFilesDir() + File.separator + "temp.sav";
      NativeLibrary.SaveStateAs(mSavedState, true);
    }
    outState.putStringArray(EXTRA_SELECTED_GAMES, mPaths);
    outState.putString(EXTRA_SELECTED_TITLE, mSelectedTitle);
    outState.putString(EXTRA_SELECTED_GAMEID, mSelectedGameId);
    outState.putInt(EXTRA_PLATFORM, mPlatform);
    outState.putString(EXTRA_SAVED_STATE, mSavedState);
    super.onSaveInstanceState(outState);
  }

  protected void restoreState(Bundle savedInstanceState)
  {
    mPaths = savedInstanceState.getStringArray(EXTRA_SELECTED_GAMES);
    mSelectedTitle = savedInstanceState.getString(EXTRA_SELECTED_TITLE);
    mSelectedGameId = savedInstanceState.getString(EXTRA_SELECTED_GAMEID);
    mPlatform = savedInstanceState.getInt(EXTRA_PLATFORM);
    mSavedState = savedInstanceState.getString(EXTRA_SAVED_STATE);
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
    switch (requestCode)
    {
      case REQUEST_CHANGE_DISC:
        // If the user picked a file, as opposed to just backing out.
        if (resultCode == MainActivity.RESULT_OK)
        {
          String newDiscPath = FileBrowserHelper.getSelectedDirectory(result);
          if (!TextUtils.isEmpty(newDiscPath))
          {
            NativeLibrary.ChangeDisc(newDiscPath);
          }
        }
        break;
    }
  }

  private void enableFullscreenImmersive()
  {
    if (mStopEmulation)
    {
      return;
    }
    mMenuVisible = false;
    // It would be nice to use IMMERSIVE_STICKY, but that doesn't show the toolbar.
    // SYSTEM_UI_FLAG_IMMERSIVE: Show toolbar
    // SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Don't show toolbar
    getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  private void disableFullscreenImmersive()
  {
    mMenuVisible = true;
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }

  public boolean closeSubmenu()
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

    int id = isGameCubeGame() ? R.menu.menu_overlay_controls_gc : R.menu.menu_overlay_controls_wii;
    popup.getMenuInflater().inflate(id, menu);

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
      handleMenuAction(action);
    }
    return true;
  }

  public void handleMenuAction(@MenuAction int menuAction)
  {
    switch (menuAction)
    {
      // Edit the placement of the controls
      case MENU_ACTION_EDIT_CONTROLS_PLACEMENT:
        editControlsPlacement();
        return;

      case MENU_ACTION_JOYSTICK_SETTINGS:
        showJoystickSettings();
        return;

      case MENU_ACTION_EMULATION_SENSOR:
        showSensorSettings();
        return;

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

      // Screenshot capturing
      case MENU_ACTION_TAKE_SCREENSHOT:
        NativeLibrary.SaveScreenShot();
        return;

      // Quick save / load
      case MENU_ACTION_QUICK_SAVE:
        showStateSaves();
        return;

      case MENU_ACTION_CHANGE_DISC:
        FileBrowserHelper.openFilePicker(this, REQUEST_CHANGE_DISC, false);
        return;

      case MENU_ACTION_RUNNING_SETTING:
        RunningSettingDialog.newInstance()
                .show(getSupportFragmentManager(), "RunningSettingDialog");
        return;

      case MENU_ACTION_EXIT:
        mEmulationFragment.stopEmulation();
        finish();
        return;
    }
  }

  private void showStateSaves()
  {
    StateSavesDialog.newInstance(mSelectedGameId)
            .show(getSupportFragmentManager(), "StateSavesDialog");
  }

  private void showJoystickSettings()
  {
    final int joystick = InputOverlay.sJoyStickSetting;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_joystick_settings);

    builder.setSingleChoiceItems(R.array.wiiJoystickSettings, joystick,
            (dialog, indexSelected) ->
            {
              InputOverlay.sJoyStickSetting = indexSelected;
            });
    builder.setOnDismissListener((dialogInterface) ->
    {
      if (InputOverlay.sJoyStickSetting != joystick)
      {
        mEmulationFragment.refreshInputOverlay();
      }
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void showSensorSettings()
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_sensor_settings);

    if (isGameCubeGame())
    {
      int sensor = InputOverlay.sSensorGCSetting;
      builder.setSingleChoiceItems(R.array.gcSensorSettings, sensor,
              (dialog, indexSelected) ->
              {
                InputOverlay.sSensorGCSetting = indexSelected;
              });
      builder.setOnDismissListener((dialogInterface) ->
      {
        setSensorState(InputOverlay.sSensorGCSetting > 0);
      });
    }
    else
    {
      int sensor = InputOverlay.sSensorWiiSetting;
      builder.setSingleChoiceItems(R.array.wiiSensorSettings, sensor,
              (dialog, indexSelected) ->
              {
                InputOverlay.sSensorWiiSetting = indexSelected;
              });
      builder.setOnDismissListener((dialogInterface) ->
      {
        setSensorState(InputOverlay.sSensorWiiSetting > 0);
      });
    }

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void setSensorState(boolean enabled)
  {
    if (enabled)
    {
      if (mSensorManager == null)
      {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor rotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotationVector != null)
        {
          mSensorManager.registerListener(mEmulationFragment, rotationVector,
                  SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
      }
    }
    else
    {
      if (mSensorManager != null)
      {
        mSensorManager.unregisterListener(mEmulationFragment);
        mSensorManager = null;
      }
    }

    //
    mEmulationFragment.onAccuracyChanged(null, 0);
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

  @Override
  protected void onResume()
  {
    super.onResume();

    if (mSensorManager != null)
    {
      Sensor rotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
      if (rotationVector != null)
      {
        mSensorManager.registerListener(mEmulationFragment, rotationVector,
                SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
      }
    }
  }

  @Override
  protected void onPause()
  {
    super.onPause();

    if (mSensorManager != null)
    {
      mSensorManager.unregisterListener(mEmulationFragment);
    }
  }

  private void loadPreferences()
  {
    String id = mSelectedGameId.length() > 3 ? mSelectedGameId.substring(0, 3) : mSelectedGameId;
    String scaleKey = InputOverlay.CONTROL_SCALE_PREF_KEY + "_" + id;
    String alphaKey = InputOverlay.CONTROL_ALPHA_PREF_KEY + "_" + id;
    String typeKey = InputOverlay.CONTROL_TYPE_PREF_KEY + "_" + id;
    String joystickKey = InputOverlay.JOYSTICK_PREF_KEY + "_" + id;
    String recenterKey = InputOverlay.RECENTER_PREF_KEY + "_" + id;

    InputOverlay.sControllerScale = mPreferences.getInt(scaleKey, 50);
    InputOverlay.sControllerAlpha = mPreferences.getInt(alphaKey, 100);
    InputOverlay.sControllerType = mPreferences.getInt(typeKey, InputOverlay.CONTROLLER_WIINUNCHUK);
    InputOverlay.sJoyStickSetting =
            mPreferences.getInt(joystickKey, InputOverlay.JOYSTICK_EMULATE_NONE);
    InputOverlay.sJoystickRelative = mPreferences.getBoolean(InputOverlay.RELATIVE_PREF_KEY, true);
    InputOverlay.sIRRecenter = mPreferences.getBoolean(recenterKey, false);

    if (isGameCubeGame())
      InputOverlay.sJoyStickSetting = InputOverlay.JOYSTICK_EMULATE_NONE;

    InputOverlay.sSensorGCSetting = InputOverlay.SENSOR_GC_NONE;
    InputOverlay.sSensorWiiSetting = InputOverlay.SENSOR_WII_NONE;

    Rumble.setPhoneRumble(this, mPreferences.getBoolean(RUMBLE_PREF_KEY, true));
  }

  private void savePreferences()
  {
    String id = mSelectedGameId.length() > 3 ? mSelectedGameId.substring(0, 3) : mSelectedGameId;
    String scaleKey = InputOverlay.CONTROL_SCALE_PREF_KEY + "_" + id;
    String alphaKey = InputOverlay.CONTROL_ALPHA_PREF_KEY + "_" + id;
    String typeKey = InputOverlay.CONTROL_TYPE_PREF_KEY + "_" + id;
    String joystickKey = InputOverlay.JOYSTICK_PREF_KEY + "_" + id;
    String recenterKey = InputOverlay.RECENTER_PREF_KEY + "_" + id;

    SharedPreferences.Editor editor = mPreferences.edit();
    editor.putInt(typeKey, InputOverlay.sControllerType);
    editor.putInt(scaleKey, InputOverlay.sControllerScale);
    editor.putInt(alphaKey, InputOverlay.sControllerAlpha);
    editor.putInt(joystickKey, InputOverlay.sJoyStickSetting);
    editor.putBoolean(InputOverlay.RELATIVE_PREF_KEY, InputOverlay.sJoystickRelative);
    editor.putBoolean(recenterKey, InputOverlay.sIRRecenter);
    editor.apply();
  }

  // Gets button presses
  @Override
  public boolean dispatchKeyEvent(KeyEvent event)
  {
    InputDevice input = event.getDevice();
    int button = event.getKeyCode();
    if (button == mBindingButton && input != null && mBindingDevice.equals(input.getDescriptor()))
    {
      if (event.getAction() == KeyEvent.ACTION_DOWN)
        onBackPressed();
      return true;
    }

    if (mMenuVisible || event.getKeyCode() == KeyEvent.KEYCODE_BACK)
      return super.dispatchKeyEvent(event);

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

    if (input != null)
      return NativeLibrary.onGamePadEvent(input.getDescriptor(), button, action);
    else
      return false;
  }

  private void toggleControls()
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    final int controller = InputOverlay.sControllerType;
    boolean[] enabledButtons = new boolean[16];
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_toggle_controls);

    int resId;
    String keyPrefix;
    if (isGameCubeGame() || controller == InputOverlay.CONTROLLER_GAMECUBE)
    {
      resId = R.array.gcpadButtons;
      keyPrefix = "ToggleGc_";
    }
    else if (controller == InputOverlay.CONTROLLER_CLASSIC)
    {
      resId = R.array.classicButtons;
      keyPrefix = "ToggleClassic_";
    }
    else
    {
      resId = controller == InputOverlay.CONTROLLER_WIINUNCHUK ?
              R.array.nunchukButtons : R.array.wiimoteButtons;
      keyPrefix = "ToggleWii_";
    }

    for (int i = 0; i < enabledButtons.length; i++)
    {
      enabledButtons[i] = mPreferences.getBoolean(keyPrefix + i, true);
    }
    builder.setMultiChoiceItems(resId, enabledButtons,
            (dialog, indexSelected, isChecked) -> editor
                    .putBoolean(keyPrefix + indexSelected, isChecked));

    builder.setNeutralButton(getString(R.string.emulation_toggle_all), (dialogInterface, i) ->
    {
      editor.putBoolean("showInputOverlay",
              !mPreferences.getBoolean("showInputOverlay", true));
      editor.apply();
      mEmulationFragment.refreshInputOverlay();
    });
    builder.setPositiveButton(getString(android.R.string.ok), (dialogInterface, i) ->
    {
      editor.apply();
      mEmulationFragment.refreshInputOverlay();
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void adjustScale()
  {
    LayoutInflater inflater = LayoutInflater.from(this);
    View view = inflater.inflate(R.layout.dialog_input_adjust, null);

    // scale
    final SeekBar seekbarScale = view.findViewById(R.id.input_scale_seekbar);
    final TextView valueScale = view.findViewById(R.id.input_scale_value);

    seekbarScale.setMax(150);
    seekbarScale.setProgress(InputOverlay.sControllerScale);
    seekbarScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        valueScale.setText((progress + 50) + "%");
      }

      public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });
    valueScale.setText((seekbarScale.getProgress() + 50) + "%");

    // alpha
    final SeekBar seekbarAlpha = view.findViewById(R.id.input_alpha_seekbar);
    final TextView valueAlpha = view.findViewById(R.id.input_alpha_value);
    seekbarAlpha.setMax(100);
    seekbarAlpha.setProgress(InputOverlay.sControllerAlpha);
    seekbarAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        valueAlpha.setText(progress + "%");
      }

      public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });
    valueAlpha.setText(seekbarAlpha.getProgress() + "%");

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setView(view);
    builder.setOnDismissListener((dialogInterface) ->
    {
      InputOverlay.sControllerScale = seekbarScale.getProgress();
      InputOverlay.sControllerAlpha = seekbarAlpha.getProgress();
      mEmulationFragment.refreshInputOverlay();
    });
    builder.setNeutralButton(getString(R.string.emulation_control_reset_layout),
            (dialogInterface, i) ->
            {
              mEmulationFragment.resetCurrentLayout();
            });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void chooseController()
  {
    int controller = InputOverlay.sControllerType;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_choose_controller);
    builder.setSingleChoiceItems(R.array.controllersEntries, controller,
            (dialog, indexSelected) ->
            {
              InputOverlay.sControllerType = indexSelected;
            });
    builder.setNeutralButton(getString(R.string.emulation_reload_wiimote_config),
            (dialogInterface, i) ->
            {
              NativeLibrary.SetConfig("WiimoteNew.ini", "Wiimote1", "Extension",
                      getResources().getStringArray(
                              R.array.controllersValues)[InputOverlay.sControllerType]);
              mEmulationFragment.refreshInputOverlay();
              NativeLibrary.ReloadWiimoteConfig();
            });
    builder.setOnDismissListener((dialogInterface) ->
    {
      NativeLibrary.SetConfig("WiimoteNew.ini", "Wiimote1", "Extension",
              getResources()
                      .getStringArray(R.array.controllersValues)[InputOverlay.sControllerType]);
      mEmulationFragment.refreshInputOverlay();
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
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
      float value = mControllerMappingHelper.scaleAxis(input, axis, origValue);
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

  public String getSelectedGameId()
  {
    return mSelectedGameId;
  }

  public static boolean isGameCubeGame()
  {
    return sIsGameCubeGame;
  }

  public String getSavedState()
  {
    return mSavedState;
  }

  public void setTouchPointer(int type)
  {
    mEmulationFragment.setTouchPointer(type);
  }

  public void updateTouchPointer()
  {
    mEmulationFragment.updateTouchPointer();
  }

  public void bindSystemBack(String binding)
  {
    mBindingDevice = "";
    mBindingButton = -1;

    int descPos = binding.indexOf("Device ");
    if (descPos == -1)
      return;

    int codePos = binding.indexOf("-Button ");
    if (codePos == -1)
      return;

    String descriptor = binding.substring(descPos + 8, codePos - 1);
    String code = binding.substring(codePos + 8);
    mBindingDevice = descriptor;
    mBindingButton = Integer.valueOf(code);
  }
}
