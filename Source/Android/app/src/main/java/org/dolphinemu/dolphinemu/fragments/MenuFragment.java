package org.dolphinemu.dolphinemu.fragments;

import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;

public final class MenuFragment extends Fragment implements View.OnClickListener
{
  private TextView mTitleText;

  private static final String KEY_TITLE = "title";
  private static SparseIntArray buttonsActionsMap = new SparseIntArray();
  static
  {
    buttonsActionsMap.append(R.id.menu_emulation_screenshot, EmulationActivity.MENU_ACTION_TAKE_SCREENSHOT);
    buttonsActionsMap.append(R.id.menu_quicksave, EmulationActivity.MENU_ACTION_QUICK_SAVE);
    buttonsActionsMap.append(R.id.menu_overlay_controls, EmulationActivity.MENU_ACTION_OVERLAY_CONTROLS);
    buttonsActionsMap.append(R.id.menu_running_setting, EmulationActivity.MENU_ACTION_RUNNING_SETTING);
    buttonsActionsMap.append(R.id.menu_settings_wiimote, EmulationActivity.MENU_ACTION_REFRESH_WIIMOTES);
    buttonsActionsMap.append(R.id.menu_change_disc, EmulationActivity.MENU_ACTION_CHANGE_DISC);
    buttonsActionsMap.append(R.id.menu_emulation_exit, EmulationActivity.MENU_ACTION_EXIT);
  }

  public static MenuFragment newInstance(String title)
  {
    MenuFragment fragment = new MenuFragment();

    Bundle arguments = new Bundle();
    arguments.putSerializable(KEY_TITLE, title);
    fragment.setArguments(arguments);

    return fragment;
  }

  // This is primarily intended to account for any navigation bar at the bottom of the screen
  private int getBottomPaddingRequired()
  {
    Rect visibleFrame = new Rect();
    requireActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleFrame);
    return visibleFrame.bottom - visibleFrame.top - getResources().getDisplayMetrics().heightPixels;
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
  {
    View rootView = inflater.inflate(R.layout.fragment_ingame_menu, container, false);

    LinearLayout options = rootView.findViewById(R.id.layout_options);

    PackageManager packageManager = requireActivity().getPackageManager();

    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
    {
      options.findViewById(R.id.menu_overlay_controls).setVisibility(View.GONE);
    }

    if (EmulationActivity.isGameCubeGame())
    {
      options.findViewById(R.id.menu_settings_wiimote).setVisibility(View.GONE);
    }

    int bottomPaddingRequired = getBottomPaddingRequired();

    // Provide a safe zone between the navigation bar and Exit Emulation to avoid accidental touches
    float density = getResources().getDisplayMetrics().density;

    if (bottomPaddingRequired >= 32 * density)
    {
      bottomPaddingRequired += 32 * density;
    }

    if (bottomPaddingRequired > rootView.getPaddingBottom())
    {
      rootView.setPadding(rootView.getPaddingLeft(), rootView.getPaddingTop(),
              rootView.getPaddingRight(), bottomPaddingRequired);
    }

    for (int childIndex = 0; childIndex < options.getChildCount(); childIndex++)
    {
      Button button = (Button) options.getChildAt(childIndex);

      button.setOnClickListener(this);
    }

    rootView.findViewById(R.id.menu_emulation_exit).setOnClickListener(this);

    mTitleText = rootView.findViewById(R.id.text_game_title);
    String title = getArguments().getString(KEY_TITLE);
    if (title != null)
    {
      mTitleText.setText(title);
    }

    return rootView;
  }

  @SuppressWarnings("WrongConstant")
  @Override
  public void onClick(View button)
  {
    int action = buttonsActionsMap.get(button.getId());
    EmulationActivity activity = (EmulationActivity) requireActivity();
    if (action == EmulationActivity.MENU_ACTION_OVERLAY_CONTROLS)
    {
      // We could use the button parameter as the anchor here, but this often results in a tiny menu
      // (because the button often is in the middle of the screen), so let's use mTitleText instead
      activity.showOverlayControlsMenu(mTitleText);
    }
    if (action >= 0)
    {
      activity.handleMenuAction(action);
    }
  }
}